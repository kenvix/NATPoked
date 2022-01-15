//--------------------------------------------------
// Interface Controller
//--------------------------------------------------
// Written by Kenvix <i@kenvix.com>
//--------------------------------------------------

package com.kenvix.web.utils

import io.ktor.routing.Route

interface Controller {
    fun route(route: Route)
}