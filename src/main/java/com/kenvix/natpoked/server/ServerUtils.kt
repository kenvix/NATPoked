@file:OptIn(ExperimentalSerializationApi::class)

package com.kenvix.natpoked.server

import com.kenvix.natpoked.contacts.NATClientItem
import com.kenvix.web.utils.receiveBytes
import com.kenvix.web.utils.receiveData
import com.kenvix.web.utils.receiveProtobuf
import io.ktor.application.*
import io.ktor.features.*
import io.ktor.application.*
import io.ktor.features.*
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