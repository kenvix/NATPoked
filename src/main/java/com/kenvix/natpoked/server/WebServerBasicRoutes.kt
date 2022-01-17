@file:JvmName("WebServerBasicRoutes")

package com.kenvix.natpoked.server

import com.kenvix.natpoked.server.controller.api.PeerController
import com.kenvix.natpoked.server.controller.api.ToolsController
import com.kenvix.natpoked.utils.AppEnv
import com.kenvix.utils.lang.toUnit
import com.kenvix.web.server.KtorModule
import com.kenvix.web.utils.controller
import io.ktor.application.*
import io.ktor.http.content.*
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
                controller("/tools", ToolsController)
                controller("/peer", PeerController)
            }
        }
    }.toUnit()
}