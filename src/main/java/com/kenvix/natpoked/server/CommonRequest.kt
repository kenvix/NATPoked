//--------------------------------------------------
// Class CommonRequest
//--------------------------------------------------
// Written by Kenvix <i@kenvix.com>
//--------------------------------------------------

package com.kenvix.natpoked.server

import com.kenvix.natpoked.contacts.PeerId
import kotlinx.serialization.Serializable

interface CommonRequest<T> {
    val type: Int
    val data: T

    private data class CommonRequestImpl<T>(
        override val type: Int,
        override val data: T
    ) : CommonRequest<T>

    operator fun <R> invoke(type: Int, data: R): CommonRequest<R> {
        return CommonRequestImpl(type, data)
    }
}

@Serializable
data class BrokerMessage<T> (
    override val type: Int,
    val peerId: PeerId = -1,
    override val data: T,
) : CommonRequest<T>