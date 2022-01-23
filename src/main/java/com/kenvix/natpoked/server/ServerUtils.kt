@file:OptIn(ExperimentalSerializationApi::class)

package com.kenvix.natpoked.server

import io.ktor.application.*
import io.ktor.request.*
import io.ktor.util.pipeline.*
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.decodeFromByteArray
import kotlinx.serialization.protobuf.ProtoBuf

suspend fun PipelineContext<*, ApplicationCall>.receiveBytes(): ByteArray {
    return call.receive<ByteArray>()
}

suspend inline fun <reified T> PipelineContext<*, ApplicationCall>.receiveInternalProtobuf(): T {
    return ProtoBuf.decodeFromByteArray<T>(receiveBytes())
}