package com.kenvix.natpoked.server

import com.kenvix.utils.exception.*
import com.kenvix.web.utils.JSON
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString

@Serializable
data class CommonJsonResult<T>(
    /**
     * Http status code
     */
    val status: Int,

    /**
     * User defined message code. default is http code when error occurs.
     */
    val code: Int = if (status in 200..299) 0 else status,
    val info: String = "",
    val data: T? = null
) {
    fun checkException() {
        when (status) {
            in 200..299 -> {  }
            400 -> throw BadRequestException(info.ifBlank { "Unknown error" })
            401 -> throw InvalidAuthorizationException(info.ifBlank { "Unauthorized" })
            403 -> throw ForbiddenOperationException(info.ifBlank { "Forbidden" })
            404 -> throw NotFoundException(info.ifBlank { "Not found" })
            429 -> throw TooManyRequestException(info.ifBlank { "Too many request" })
            500, 502 -> throw ServerFaultException(info.ifBlank { "Server fault" }, code)
            501 -> throw NotSupportedException(info.ifBlank { "Not supported" })
            else -> throw CommonBusinessException(info.ifBlank { "Unknown error" }, code)
        }
    }

    @Suppress("UNCHECKED_CAST")
    inline fun <reified R: T> toJsonString() = JSON.encodeToString<CommonJsonResult<R>>(this as CommonJsonResult<R>)
}