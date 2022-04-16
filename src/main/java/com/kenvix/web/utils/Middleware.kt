//--------------------------------------------------
// Interface Middleware
//--------------------------------------------------
// Written by Kenvix <i@kenvix.com>
//--------------------------------------------------

package com.kenvix.web.utils

import io.ktor.application.*
import io.ktor.util.*
import io.ktor.util.pipeline.*

sealed class BaseMiddleware<T: Any> {
    val attributeKey: AttributeKey<T> = AttributeKey(this.javaClass.name)
    fun getMiddlewareValueOrNull(pipeline: PipelineContext<*, ApplicationCall>) =
        pipeline.context.attributes.getOrNull(attributeKey)
}

abstract class Middleware<T: Any> : BaseMiddleware<T>() {
    abstract fun handle(pipeline: PipelineContext<*, ApplicationCall>): T

    fun callMiddleware(pipeline: PipelineContext<*, ApplicationCall>): T {
        return getMiddlewareValueOrNull(pipeline).run {
            if (this == null) {
                val result = handle(pipeline)
                pipeline.context.attributes.put(attributeKey, result)
                result
            } else {
                this
            }
        }
    }
}

abstract class MiddlewareSuspend<T: Any> : BaseMiddleware<T>() {
    abstract suspend fun handle(pipeline: PipelineContext<*, ApplicationCall>): T

    suspend fun callMiddleware(pipeline: PipelineContext<*, ApplicationCall>): T {
        return getMiddlewareValueOrNull(pipeline).run {
            if (this == null) {
                val result = handle(pipeline)
                pipeline.context.attributes.put(attributeKey, result)
                result
            } else {
                this
            }
        }
    }
}