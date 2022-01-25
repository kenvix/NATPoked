//--------------------------------------------------
// Class CommonRequest
//--------------------------------------------------
// Written by Kenvix <i@kenvix.com>
//--------------------------------------------------

package com.kenvix.natpoked.server

import kotlinx.serialization.Serializable

@Serializable
data class CommonRequest<T>(
    val type: Int,
    val data: T
)