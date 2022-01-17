//--------------------------------------------------
// Class BaseController
//--------------------------------------------------
// Written by Kenvix <i@com.kenvix.com>
//--------------------------------------------------

package com.kenvix.natpoked.server.controller

import com.kenvix.web.utils.Controller
import io.ktor.application.*
import io.ktor.util.pipeline.*
import org.slf4j.LoggerFactory

abstract class BaseController : Controller {
    val logger = LoggerFactory.getLogger(this::class.java)!!
    open val baseTemplatePath: String
        get() = "./"

    open fun newTemplateVariableMap(pipeline: PipelineContext<*, ApplicationCall>): MutableMap<String, Any?> = HashMap()
}