@file:JvmName("WebServerBasicRoutes")

package com.kenvix.natpoked.server

import com.kenvix.natpoked.contacts.*
import com.kenvix.natpoked.contacts.RequestTypes.*
import com.kenvix.natpoked.utils.AES256GCM
import com.kenvix.natpoked.utils.AppEnv
import com.kenvix.natpoked.utils.emptyByteArray
import com.kenvix.utils.exception.NotFoundException
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
import kotlinx.serialization.encodeToString
import org.slf4j.LoggerFactory
import java.util.Collections
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

            get("/") {
                throw BadRequestException("NATPoked server works well. But you should not access this page manually.")
            }

            route("/api/v1") {
                route("/tools") {
                    get("/ip") {
                        call.respondInfo(call.request.origin.remoteHost)
                    }

                    post("/test") {
                        val params = call.receiveParameters()
                        call.response.headers.append("Content-Type", "text/plain")
                        call.respondText("Input: " + params["test_in"])
                    }
                }

                route("/peers") {
                    /**
                     * ??????????????? Peer ??????
                     * Content-Type: application/octet-stream IF USES PROTOBUF
                     * Content-Type: application/json IF USES JSON
                     * /peers
                     */
                    post("/") {
                        val data: NATClientItem = call.receiveInternalData()
                        NATServer.addPeerConnection(data)
                        call.respondSuccess()
                    }

                    get<PeerIDLocation> { peerId ->
                        call.respondData(NATServer.peerConnections[peerId.id])
                    }

                    delete<PeerIDLocation> { peerId ->
                        NATServer.removePeerConnection(peerId.id)
                        call.respondSuccess()
                    }

                    /**
                     * ????????? Peer????????? PeerId-Port ????????????
                     * /peers/:peerId/connections
                     */
//                    post<PeerIDLocation.Connections> { peerId ->
//                        val req: PeerAddPortMapRequest = call.receiveInternalData()
//                        NATServer
//                            .peerConnections[peerId.parent.id]
//                            .assertExist("Peer ${peerId.parent.id} not found")
//                            .connections[req.targetPeerId]
//                            .assertExist("Peer connections ${req.targetPeerId} not found")
//                            .port = req.port
//                        call.respondSuccess()
//                    }

                    /**
                     * ????????? Peer????????? PeerId-Port ????????????
                     * /peers/:peerId/connections/:targetPeerId
                     */
//                    delete<PeerIDLocation.Connections.TargetPeer> { peerId ->
//                        NATServer
//                            .peerConnections[peerId.parent.id]
//                            .assertExist("Peer ${peerId.parent.id} not found")
//                            .connections[peerId.targetPeerId]
//                            .assertExist("Peer connections ${peerId.targetPeerId} not found")
//                            .port = -1
//                        call.respondSuccess()
//                    }

                    /**
                     * ????????? Peer????????? PeerId-Port ????????????
                     * /peers/:peerId/connections
                     */
//                    get<PeerIDLocation.Connections.TargetPeer> { peerId ->
//                        val port = NATServer
//                            .peerConnections[peerId.parent.id]
//                            .assertExist("Peer ${peerId.parent.id} not found")
//                            .connections[peerId.targetPeerId]
//                            .assertExist("Peer connections ${peerId.targetPeerId} not found")
//                            .port
//                        call.respondSuccess(data = "port" to port)
//                    }

                    post("/connect") {
                        val (myPeerId, targetPeerId) = call.receiveInternalData<PeerConnectRequest>()
                        val my: NATPeerToBrokerConnection = NATServer.peerConnections.getOrFail(myPeerId)
                        my.addConnection(targetPeerId)

                        val targetPeer = NATServer.peerConnections[targetPeerId]
                        if (targetPeer != null) {
                            val serverRolePeer: NATPeerToBrokerConnection = maxOf(targetPeer, my, NATPeerToBrokerConnection.natTypeComparator)
                            val clientRolePeer: NATPeerToBrokerConnection = minOf(targetPeer, my, NATPeerToBrokerConnection.natTypeComparator)

                            when (serverRolePeer.client.clientNatType) {
                                NATType.PUBLIC, NATType.FULL_CONE -> {
                                    requestPeerMakeConnection(myPeerId, serverRolePeer.client, )
                                    clientRolePeer.setConnectionStage(serverRolePeer.client.clientId,
                                        NATPeerToPeerConnectionStage.REQUESTED_TO_CONNECT_SERVER_PEER)

                                    call.respondSuccess("Requested to connect. One of Network type is " +
                                            "FullCone/Public. Server is ${serverRolePeer.client.clientId}")
                                }

                                NATType.RESTRICTED_CONE -> {
                                    requestPeerMakeConnection(myPeerId, clientRolePeer.client)
                                    clientRolePeer.setConnectionStage(serverRolePeer.client.clientId,
                                        NATPeerToPeerConnectionStage.REQUESTED_TO_CONNECT_CLIENT_PEER)

                                    requestPeerMakeConnection(myPeerId, serverRolePeer.client)
                                    call.respondSuccess("Requested to connect each other. One of Network type is " +
                                            "RESTRICTED_CONE. Server is ${serverRolePeer.client.clientId}")
                                }

                                else -> TODO("??????????????? NAT")
                            }
                        } else {
                            call.respondInfo(code = 30001, info = "Target offline. Wait target peer online and try again")
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

                    MESSAGE_SEND_PACKET_TO_CLIENT_PEER.typeId -> {

                    }

                    else -> {
                        throw NotImplementedError("Broker not implemented")
                    }
                }
            }

            is Frame.Close -> {
                try {
                    NATServer.peerWebsocketSessionMap[this]?.apply {
                        NATServer.removePeerConnection(client.clientId)
                    }

                    NATServer.peerWebsocketSessionMap.remove(this)
                } catch (e: Exception) {
                    logger.error("Unable to unregister", e)
                }
            }
        }
    }

    private suspend fun requestPeerMakeConnection(myPeerId: PeerId, targetPeerClientInfo: NATClientItem, targetPorts: List<Int>? = null) {
        val infoCopy = targetPeerClientInfo.copy()
        infoCopy.peersConfig = null

        val peerConfigCopy: PeersConfig.Peer = targetPeerClientInfo.peersConfig?.peers?.get(myPeerId)?.copy() ?: throw NotFoundException("Peer $targetPeerClientInfo->$myPeerId config not found")
        peerConfigCopy.key = ""
        peerConfigCopy.keySha = emptyByteArray()

        val json = JSON.encodeToString(BrokerMessage(
            ACTION_CONNECT_PEER.typeId,
            targetPeerClientInfo.clientId,
            NATConnectReq(
                targetClientItem = infoCopy,
                ports = targetPorts,
                configForMe = peerConfigCopy,
            )
        ))

        NATServer.brokerServer.sendPeerMessage(myPeerId, "control/connect", json.toByteArray(), 2)
    }
}