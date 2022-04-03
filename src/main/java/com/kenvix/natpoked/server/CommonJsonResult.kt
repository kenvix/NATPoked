package com.kenvix.natpoked.server

import com.kenvix.utils.exception.*
import com.kenvix.web.utils.JSON
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString

@Serializable
data class CommonJsonResult<T>(
    val status: Int,
    val code: Int = status,
    val info: String = "",
    val data: T? = null
) {
    fun checkException() {
        when (code) {
            400 -> throw BadRequestException(info.ifBlank { "Unknown error" })
            401 -> throw InvalidAuthorizationException(info.ifBlank { "Unauthorized" })
            403 -> throw ForbiddenOperationException(info.ifBlank { "Forbidden" })
            404 -> throw NotFoundException(info.ifBlank { "Not found" })
            429 -> throw TooManyRequestException(info.ifBlank { "Too many request" })
            500 -> throw ServerFaultException(info.ifBlank { "Server fault" })
            501 -> throw NotSupportedException(info.ifBlank { "Not supported" })
            else -> throw CommonBusinessException(info.ifBlank { "Unknown error" }, code)
        }
    }
}