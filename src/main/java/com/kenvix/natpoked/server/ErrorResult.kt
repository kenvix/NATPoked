//--------------------------------------------------
// Class ErrorResult
//--------------------------------------------------
// Written by Kenvix <i@kenvix.com>
//--------------------------------------------------

package com.kenvix.natpoked.server

import kotlinx.serialization.Serializable

@Serializable
data class ErrorResult(
        val exception: String = "",
        val exceptionFullName: String = "",
        val trace: String = ""
)