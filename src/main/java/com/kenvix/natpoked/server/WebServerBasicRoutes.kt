@file:JvmName("WebServerBasicRoutes")

package com.kenvix.natpoked.server

import com.kenvix.natpoked.utils.AppEnv
import com.kenvix.utils.lang.toUnit
import com.kenvix.web.server.KtorModule
import io.ktor.application.*
import io.ktor.features.*
import io.ktor.http.content.*
import io.ktor.request.*
import io.ktor.response.*
import io.ktor.routing.*
import io.ktor.sessions.*
import org.slf4j.LoggerFactory

@Suppress("unused", "DuplicatedCode") // Referenced in application.conf
internal object WebServerBasicRoutes : KtorModule {
    val logger = LoggerFactory.getLogger(javaClass)!!

    override fun module(application: Application, testing: Boolean) = application.apply {
        install(Sessions) {

        }

        routing {
            static(AppEnv.PublicDirUrl) {
                files(AppEnv.PublicDirPath)
                resources("public")
            }

            route("/api") {
                route("/tools") {
                    get("/ip") { call.respondText(call.request.origin.remoteHost) }

                    post("/test") {
                        val params = call.receiveParameters()
                        call.respondText("Input: " + params["test_in"])
                    }
                }
            }
        }
    }.toUnit()
}