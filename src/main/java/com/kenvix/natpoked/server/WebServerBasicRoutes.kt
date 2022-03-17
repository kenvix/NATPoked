@file:JvmName("WebServerBasicRoutes")

package com.kenvix.natpoked.server

import com.kenvix.natpoked.contacts.*
import com.kenvix.natpoked.contacts.RequestTypes.*
import com.kenvix.natpoked.server.WebServerBasicRoutes.handlePeerControlSocketFrame
import com.kenvix.natpoked.utils.AES256GCM
import com.kenvix.natpoked.utils.AppEnv
import com.kenvix.utils.lang.toUnit
import com.kenvix.web.server.KtorModule
import com.kenvix.web.utils.*
import io.ktor.application.*
import io.ktor.features.*
import io.ktor.http.cio.websocket.*
import io.ktor.http.content.*
import io.ktor.locations.*
import io.ktor.request.*
import io.ktor.response.*
import io.ktor.routing.*
import io.ktor.sessions.*
import io.ktor.websocket.*
import kotlinx.serialization.ExperimentalSerializationApi
import org.slf4j.LoggerFactory
import kotlin.time.Duration
import kotlin.time.toJavaDuration

@Suppress("unused", "DuplicatedCode", "UNCHECKED_CAST") // Referenced in application.conf
internal object WebServerBasicRoutes : KtorModule {
    val logger = LoggerFactory.getLogger(javaClass)!!
    val encryptor = AES256GCM(AppEnv.ServerPSK)

    @OptIn(KtorExperimentalLocationsAPI::class, ExperimentalSerializationApi::class)
    override fun module(application: Application, testing: Boolean) = application.apply {
        install(Sessions) {

        }

        routing {
            static(AppEnv.PublicDirUrl) {
                files(AppEnv.PublicDirPath)
                resources("public")
            }

            route("/api/v1") {
                route("/tools") {
                    get("/ip") { call.respondText(call.request.origin.remoteHost) }

                    post("/test") {
                        val params = call.receiveParameters()
                        call.respondText("Input: " + params["test_in"])
                    }
                }

                route("/peers") {
                    /**
                     * 添加或更新 Peer 信息
                     * Content-Type: application/octet-stream IF USES PROTOBUF
                     * Content-Type: application/json IF USES JSON
                     */
                    post("/") {
                        val data: NATClientItem = call.receiveInternalData()
                        NATServer.peerConnections[data.clientId] = NATPeerToBrokerConnection(data)
                        call.respondSuccess()
                    }

                    get<PeerIDLocation> { peerId ->
                        call.respondData(NATServer.peerConnections[peerId.id])
                    }

                    delete<PeerIDLocation> { peerId ->
                        NATServer.peerConnections.remove(peerId.id)
                        call.respondSuccess()
                    }

                    post("/connect") {
                        val (myPeerId, targetPeerId) = call.receiveInternalData<PeerConnectRequest>()
                        val my: NATPeerToBrokerConnection = NATServer.peerConnections.getOrFail(myPeerId)
                        my.wantToConnect[targetPeerId] = NATPeerToPeerConnectionStage.HANDSHAKE_TO_BROKER

                        val targetPeer = NATServer.peerConnections[targetPeerId]
                        if (targetPeer != null) {
                            val serverRolePeer: NATPeerToBrokerConnection = maxOf(targetPeer, my, NATPeerToBrokerConnection.natTypeComparator)
                            val clientRolePeer: NATPeerToBrokerConnection = minOf(targetPeer, my, NATPeerToBrokerConnection.natTypeComparator)

                            when (serverRolePeer.client.clientNatType) {
                                NATType.PUBLIC, NATType.FULL_CONE -> {
                                    requestPeerMakeConnection(clientRolePeer.session!!, serverRolePeer.client)
                                    clientRolePeer.wantToConnect[serverRolePeer.client.clientId] =
                                        NATPeerToPeerConnectionStage.REQUESTED_TO_CONNECT_SERVER_PEER

                                    call.respondSuccess("Requested to connect. One of Network type is " +
                                            "FullCone/Public. Server is ${serverRolePeer.client.clientId}")
                                }

                                NATType.RESTRICTED_CONE -> {
                                    requestPeerMakeConnection(serverRolePeer.session!!, clientRolePeer.client)
                                    serverRolePeer.wantToConnect[clientRolePeer.client.clientId] =
                                        NATPeerToPeerConnectionStage.REQUESTED_TO_CONNECT_CLIENT_PEER

                                    requestPeerMakeConnection(clientRolePeer.session!!, serverRolePeer.client)
                                    call.respondSuccess("Requested to connect each other. One of Network type is " +
                                            "RESTRICTED_CONE. Server is ${serverRolePeer.client.clientId}")
                                }

                                // TODO: 其他类型的 NAT
                            }
                        } else {
                            call.respondData(code = 30001, info = "Target offline. Wait target peer online and try again")
                        }
                    }

                    webSocket("/") {
                        logger.debug("Peer stage 2 ws connected : ")
                        this.pingInterval = AppEnv.PeerToBrokenPingIntervalDuration.toJavaDuration()
                        this.timeout = AppEnv.PeerToBrokenTimeoutDuration.toJavaDuration()

                        for (frame in incoming) {
                            handlePeerControlSocketFrame(frame, call)
                        }
                    }

                    get("/events") {

                    }
                }
            }
        }
    }.toUnit()

    // TODO: respondSuccess, respondError, Exception handler
    private suspend fun DefaultWebSocketSession.handlePeerControlSocketFrame(frame: Frame, call: ApplicationCall) {
        @Suppress("NON_EXHAUSTIVE_WHEN_STATEMENT")
        when (frame) {
            is Frame.Binary -> {
                val incomingReq: CommonRequest<*> = call.receiveInternalData()
                when (incomingReq.type) {
                    /**
                     * @throws NotFoundException (HTTP 404) if client not exist
                     */
                    MESSAGE_HANDSHAKE.typeId -> {
                        val req = incomingReq as CommonRequest<NATClientItem>
                        val client = if (req.data.clientId in NATServer.peerConnections)
                            NATServer.peerConnections.getOrFail(req.data.clientId) else NATPeerToBrokerConnection(
                            req.data
                        )
                        client.session = this
                        NATServer.peerWebsocketSessionMap[this] = client


                        call.respondSuccess()
                        client.stage = NATPeerToBrokerConnectionStage.READY
                    }

                    MESSAGE_KEEP_ALIVE.typeId -> {

                    }

                    /**
                     * @throws NoSuchElementException (HTTP 404) if client not exist
                     */
                    MESSAGE_GET_PEER_INFO.typeId, MESSAGE_GET_PEER_INFO_NOCHECK.typeId -> {
                        val id = (incomingReq as CommonRequest<PeerId>).data
                        call.respondProtobuf(NATServer.peerConnections.getOrFail(id).client)
                    }

                    MESSAGE_CONNECT_PEER.typeId -> {
                        // deprecated
                    }

                    MESSAGE_SENT_PACKET_TO_CLIENT_PEER.typeId -> {

                    }

                    else -> {
                        throw NotImplementedError("Broker not implemented")
                    }
                }
            }

            is Frame.Close -> {
                try {
                    NATServer.peerWebsocketSessionMap[this]?.apply {
                        NATServer.peerConnections.remove(client.clientId)
                    }

                    NATServer.peerWebsocketSessionMap.remove(this)
                } catch (e: Exception) {
                    logger.error("Unable to unregister", e)
                }
            }
        }
    }

    private suspend fun requestPeerMakeConnection(requestedPeer: DefaultWebSocketSession, targetPeerClientInfo: NATClientItem) {
        requestedPeer.sendProtobuf(ACTION_CONNECT_PEER, targetPeerClientInfo)
    }
}