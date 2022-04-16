//--------------------------------------------------
// Interface KtorModule
//--------------------------------------------------
// Written by Kenvix <i@kenvix.com>
//--------------------------------------------------

package com.kenvix.web.server

import io.ktor.application.*

interface KtorModule {
    fun module(application: Application, testing: Boolean = false)
}