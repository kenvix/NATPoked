//--------------------------------------------------
// InetAddress and URL serializer for kotlinx.serialization
//--------------------------------------------------
// Written by Kenvix <i@kenvix.com>
// Licensed under MIT license
//--------------------------------------------------

package com.kenvix.natpoked.utils

import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import java.net.*

object InetAddressSerializer : KSerializer<InetAddress> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("InetAddress") {}

    override fun serialize(encoder: Encoder, value: InetAddress) {
        encoder.encodeString(value.hostAddress)
    }

    override fun deserialize(decoder: Decoder): InetAddress {
        return InetAddress.getByName(decoder.decodeString())
    }
}

object Inet4AddressSerializer : KSerializer<Inet4Address> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("Inet4Address") {}

    override fun serialize(encoder: Encoder, value: Inet4Address) {
        encoder.encodeString(value.hostAddress)
    }

    override fun deserialize(decoder: Decoder): Inet4Address {
        return Inet4Address.getByName(decoder.decodeString()) as Inet4Address
    }
}

object Inet6AddressSerializer : KSerializer<Inet6Address> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("Inet6Address") {}

    override fun serialize(encoder: Encoder, value: Inet6Address) {
        encoder.encodeString(value.hostAddress)
    }

    override fun deserialize(decoder: Decoder): Inet6Address {
        return Inet6Address.getByName(decoder.decodeString()) as Inet6Address
    }
}

object URLSerializer : KSerializer<URL> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("URL") {}

    override fun serialize(encoder: Encoder, value: URL) {
        encoder.encodeString(value.toString())
    }

    override fun deserialize(decoder: Decoder): URL {
        return URL(decoder.decodeString())
    }
}

object URISerializer : KSerializer<URI> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("URI") {}

    override fun serialize(encoder: Encoder, value: URI) {
        encoder.encodeString(value.toString())
    }

    override fun deserialize(decoder: Decoder): URI {
        return URI(decoder.decodeString())
    }
}