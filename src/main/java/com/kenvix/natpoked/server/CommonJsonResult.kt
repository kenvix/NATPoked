package com.kenvix.natpoked.server

import kotlinx.serialization.Serializable

@Serializable
data class CommonJsonResult<T>(
        val status: Int,
        val code: Int = status,
        val info: String = "",
        val data: T? = null
)