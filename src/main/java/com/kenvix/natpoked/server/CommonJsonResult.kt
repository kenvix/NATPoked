package com.kenvix.natpoked.server

data class CommonJsonResult(
        val status: Int,
        val code: Int = status,
        val info: String = "",
        val data: Any? = null
)