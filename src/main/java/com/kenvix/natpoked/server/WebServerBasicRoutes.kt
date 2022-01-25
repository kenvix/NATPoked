@file:JvmName("WebServerBasicRoutes")

package com.kenvix.natpoked.server

import com.kenvix.natpoked.contacts.NATClientItem
import com.kenvix.natpoked.utils.AES256GCM
import com.kenvix.natpoked.utils.AppEnv
import com.kenvix.utils.lang.toUnit
import com.kenvix.web.server.KtorModule
import com.kenvix.web.utils.respondData
import com.kenvix.web.utils.respondSuccess
import io.ktor.application.*
import io.ktor.features.*
import io.ktor.http.content.*
import io.ktor.locations.*
import io.ktor.request.*
import io.ktor.response.*
import io.ktor.routing.*
import io.ktor.sessions.*
import io.ktor.websocket.*
import kotlinx.serialization.ExperimentalSerializationApi
import org.slf4j.LoggerFactory

@Suppress("unused", "DuplicatedCode") // Referenced in application.conf
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
                     * 请求信息可以使用 AES-256-GCM 加密的 Protobuf（根据设置决定是否加密）
                     * Content-Type: application/octet-stream
                     */
                    post("/") {
                        val data: NATClientItem = call.receiveInternalData()
                        NATServer.peerRegistry += data
                        call.respondSuccess()
                    }

                    get<PeerIDLocation> { peerId ->
                        call.respondData(NATServer.peerRegistry[peerId.id])
                    }

                    delete<PeerIDLocation> { peerId ->
                        NATServer.peerRegistry.removePeer(peerId.id)
                        call.respondSuccess()
                    }

                    webSocket("/") {
                        logger.debug("Peer stage 2 ws connected : ")
                        for (frame in incoming) {
                            val incomingReq: CommonRequest<*> = call.receiveInternalData()

                        }
                    }

                    get("/events") {

                    }
                }
            }
        }
    }.toUnit()
}