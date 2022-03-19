@file:OptIn(ExperimentalSerializationApi::class)

package com.kenvix.natpoked.server

import com.kenvix.natpoked.contacts.NATClientItem
import com.kenvix.natpoked.contacts.PeerId
import com.kenvix.natpoked.contacts.RequestTypes
import com.kenvix.web.utils.receiveBytes
import com.kenvix.web.utils.receiveData
import com.kenvix.web.utils.receiveProtobuf
import io.ktor.application.*
import io.ktor.features.*
import io.ktor.application.*
import io.ktor.features.*
import io.ktor.http.cio.websocket.*
import io.ktor.http.content.*
import io.ktor.locations.*
import io.ktor.request.*
import io.ktor.response.*
import io.ktor.routing.*
import io.ktor.sessions.*
import io.ktor.websocket.*
import io.ktor.request.*
import io.ktor.util.pipeline.*
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.decodeFromByteArray
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.protobuf.ProtoBuf

// TODO: Encryption Support
// TODO: Check Token
suspend inline fun <reified T> ApplicationCall.receiveInternalData(): T {
    return receiveData()
}

// TODO: Encryption Support
// TODO: Check Token
suspend inline fun <reified T> ApplicationCall.receiveInternalProtobuf(): T {
    return receiveProtobuf()
}

suspend fun <T> DefaultWebSocketSession.sendInternalJson(type: Int, peerId: PeerId, data: T) {
    send(Json.encodeToString(BrokerMessage<T>(type, peerId, data)))
}

suspend fun <T> DefaultWebSocketSession.sendInternalJson(typeId: RequestTypes,  peerId: PeerId, data: T) =
    sendInternalJson(typeId.typeId, peerId, data)