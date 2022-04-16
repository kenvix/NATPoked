@file:OptIn(ExperimentalSerializationApi::class)

package com.kenvix.natpoked.server

import com.kenvix.natpoked.contacts.PeerId
import com.kenvix.natpoked.contacts.RequestTypes
import com.kenvix.web.utils.receiveData
import com.kenvix.web.utils.receiveProtobuf
import io.ktor.application.*
import io.ktor.http.cio.websocket.*
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

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