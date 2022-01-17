package com.kenvix.natpoked.server.controller.api

import com.kenvix.natpoked.server.controller.BaseController
import io.ktor.application.*
import io.ktor.features.*
import io.ktor.request.*
import io.ktor.response.*
import io.ktor.routing.*


object ToolsController : BaseController() {
    override fun route(route: Route) {
        route {
            get("/ip") { call.respondText(call.request.origin.remoteHost) }


            post("/test") {
                val params = call.receiveParameters()
                call.respondText("Input: " + params["test_in"])
            }
        }
    }
}