@file:JvmName("WebServerBasicRoutes")

package com.kenvix.natpoked.server

import com.kenvix.natpoked.client.NATClient
import com.kenvix.natpoked.contacts.NATClientItem
import com.kenvix.natpoked.utils.AES256GCM
import com.kenvix.natpoked.utils.AppEnv
import com.kenvix.utils.lang.toUnit
import com.kenvix.web.server.KtorModule
import io.ktor.application.*
import io.ktor.features.*
import io.ktor.http.content.*
import io.ktor.locations.*
import io.ktor.request.*
import io.ktor.response.*
import io.ktor.routing.*
import io.ktor.routing.get
import io.ktor.sessions.*
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.decodeFromByteArray
import kotlinx.serialization.protobuf.ProtoBuf
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
                        val data: NATClientItem = receiveInternalProtobuf()

                    }

                    //
                    get<PeerIDLocation> {

                    }

                    delete<PeerIDLocation> {

                    }
                }
            }
        }
    }.toUnit()
}