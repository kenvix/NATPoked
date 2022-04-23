@file:JvmName("HttpUtils")
@file:OptIn(ExperimentalSerializationApi::class)

package com.kenvix.web.utils

import com.kenvix.natpoked.server.CommonJsonResult
import com.kenvix.natpoked.server.ErrorResult
import com.kenvix.natpoked.utils.AppEnv
import com.kenvix.utils.exception.CommonBusinessException
import io.ktor.application.*
import io.ktor.features.*
import io.ktor.http.*
import io.ktor.request.*
import io.ktor.response.*
import io.ktor.routing.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.EmptySerializersModule
import kotlinx.serialization.protobuf.ProtoBuf
import org.slf4j.LoggerFactory
import java.io.File
import java.net.URI
import java.sql.Timestamp

val JSON = Json {
    isLenient = true
    ignoreUnknownKeys = true
    serializersModule = EmptySerializersModule
}

val PROTO = ProtoBuf {
    serializersModule = EmptySerializersModule
}

private val utilsLogger = LoggerFactory.getLogger("HttpUtils")!!
val defaultRpcProtocol = "json"

fun Route.controller(path: String, controller: Controller) {
    this.route(path, controller::route)
}


data class Result2<T, U>(val component1: T, val component2: U)
data class Result3<T, U, V>(val component1: T, val component2: U, val component3: V)
data class Result4<T, U, V, W>(val component1: T, val component2: U, val component3: V, val component4: W)
data class Result5<T, U, V, W, X>(
    val component1: T,
    val component2: U,
    val component3: V,
    val component4: W,
    val component5: X
)

suspend inline fun <reified T> ApplicationCall.respondJson(
    data: T? = null, info: String? = null,
    code: Int = 0, status: HttpStatusCode = HttpStatusCode.OK
) {
    this.respond(
        JSON.encodeToString(
            CommonJsonResult(
                status.value,
                info = info ?: status.description,
                code = code,
                data = data
            )
        )
    )
}

suspend inline fun <reified T> ApplicationCall.respondProtobuf(
    data: T? = null, info: String? = null,
    code: Int = 0, status: HttpStatusCode = HttpStatusCode.OK
) {
    this.respond(
        ProtoBuf.encodeToByteArray(
            CommonJsonResult(
                status.value,
                info = info ?: status.description,
                code = code,
                data = data
            )
        )
    )
}

suspend inline fun <reified T> ApplicationCall.respondData(
    data: T? = null, info: String? = null,
    code: Int = 0, status: HttpStatusCode = HttpStatusCode.OK
) {
    val type = this.request.contentType().contentSubtype
    if (type.contains("json")) {
        respondJson(data, info, code, status)
    } else if (type.contains("protobuf")) {
        respondProtobuf(data, info, code, status)
    } else {
        if (defaultRpcProtocol == "json")
            respondJson(data, info, code, status)
        else
            respondProtobuf(data, info, code, status)
    }
}

suspend fun ApplicationCall.respondInfo(
    info: String? = null,
    code: Int = 0, status: HttpStatusCode = HttpStatusCode.OK
) {
    respondData<Unit>(null, info, code, status)
}

suspend fun ApplicationCall.respondJsonText(
    jsonText: String,
    status: HttpStatusCode = HttpStatusCode.OK
) {
    this.respondText(jsonText, ContentType.Application.Json, status)
}

/**
 * Respond a success message to user
 * @param msg message
 * @param data return data. If data is [URI] it will be equals to redirectUrl
 * @param redirectURI Redirect user to this url, will be attached to Header "X-Redirect-Location" Redirection is only available if useragent is a valid user browser.
 */
suspend inline fun <reified T> ApplicationCall.respondSuccess(
    msg: String? = null,
    data: T? = null,
    redirectURI: URI? = null,
    statusCode: HttpStatusCode = HttpStatusCode.TemporaryRedirect
) {
    respondData(data, msg)
}

suspend inline fun ApplicationCall.respondSuccess(
    msg: String? = null,
    redirectURI: URI? = null,
    statusCode: HttpStatusCode = HttpStatusCode.TemporaryRedirect
) {
    respondData<Unit>(null, msg)
}

fun businessException(description: String): Nothing {
    throw CommonBusinessException(description, HttpStatusCode.NotAcceptable.value)
}

fun ApplicationCall.isUserBrowserRequest(): Boolean {
    val userAgent = this.request.userAgent()
    return userAgent != null && userAgent.contains("Mozilla", true) &&
            this.request.header("X-Requested-With") == null
}



suspend fun ApplicationCall.respondError(
    status: HttpStatusCode,
    exception: Throwable? = null,
) {
    var info = ""
    var trace = ""

    if (exception != null) {
        if (AppEnv.DebugMode) {
            info = "${exception.message}"
            trace = exception.getStringStackTrace()
        } else {
            if (status.value in 400..499 || status.value != 501)
                info = exception.message ?: exception.hashCode().toString()
            else
                info = exception.hashCode().toString()
        }
    }

//    if (isUserBrowserRequest()) {
//        this.respond(code, FreeMarkerContent("error.ftl", mapOf(
//                "code" to code.value,
//                "description" to code.description,
//                "info" to StringEscapeUtils.escapeHtml4(info),
//                "trace" to trace,
//                "isDebugMode" to ServerEnv.DebugMode,
//                "redirectUrl" to redirectURI?.appendQuery("msg=$info&code=${code.value}&description=${code.description}")?.toString()
//        )))
//    } else {
    respondData(
        info = info,
        status = status,
        code = if (exception is CommonBusinessException) exception.code else if (status.value in 200..299) 0 else status.value,
        data = ErrorResult(
            exception = exception?.javaClass?.simpleName ?: "",
            exceptionFullName = exception?.javaClass?.name ?: "",
            trace = trace
        )
    )
//    }
}

fun currentTimeStamp() = Timestamp(System.currentTimeMillis())

suspend fun <R> File.useTempFile(then: (suspend (File) -> R)) = withContext(Dispatchers.IO) {

    try {
        then(this@useTempFile)
    } catch (exception: Throwable) {
        kotlin.runCatching { this@useTempFile.delete() }
            .onFailure { warn("Delete temp file failed", it, utilsLogger) }

        throw exception
    }
}

inline fun <reified T : Any> T?.validateValue(errorMessage: String, passCondition: (check: T) -> Boolean): T {
    if (this == null)
        throw BadRequestException("Illegal input data: Required form param not found")

    if (!passCondition(this))
        throw BadRequestException(errorMessage)

    return this
}

inline fun <reified T : Any> T?.validateValue(passCondition: (check: T) -> Boolean): T =
    validateValue("Illegal input data: $this", passCondition)


fun <T : Any> T?.assertNotNull(msg: String = "Illegal input data: Required data is null"): T {
    if (this == null)
        throw BadRequestException(msg)

    return this
}

fun <T : Any> T?.assertExist(msg: String = "Specified data not exist"): T {
    if (this == null)
        throw NotFoundException(msg)

    return this
}

fun <T : Collection<*>> T?.assertNotEmpty(): T {
    if (this == null || this.isEmpty())
        throw NotFoundException("Specified data not exist")

    return this
}

inline fun <reified E : Enum<E>> validatedEnumValueOf(value: String?, default: E? = null): E {
    if (value.isNullOrEmpty()) {
        if (default == null)
            throw BadRequestException("${E::class.java.simpleName} cannot be empty")
        else
            return default
    }

    return enumValues<E>().find { it.name == value }
        ?: throw BadRequestException("Illegal value $value for ${E::class.java.simpleName}")
}

suspend fun ApplicationCall.receiveBytes(): ByteArray {
    return this.receive<ByteArray>()
}

suspend inline fun <reified T> ApplicationCall.receiveProtobuf(): T {
    return ProtoBuf.decodeFromByteArray(receiveBytes())
}

suspend inline fun <reified T> ApplicationCall.receiveJson(): T {
    return JSON.decodeFromString(receiveText())
}

suspend inline fun <reified T> ApplicationCall.receiveData(): T {
    val type = request.contentType().contentSubtype
    return if (type.contains("json")) {
        JSON.decodeFromString(receiveText())
    } else if (type.contains("protobuf")) {
        ProtoBuf.decodeFromByteArray(receiveBytes())
    } else {
        if (defaultRpcProtocol == "json")
            JSON.decodeFromString(this.receiveText())
        else
            ProtoBuf.decodeFromByteArray(receiveBytes())
    }
}


//suspend fun <T> DefaultWebSocketSession.sendProtobuf(typeId: Int, data: T) {
//    send(ProtoBuf.encodeToByteArray(CommonRequest<T>(typeId, data)))
//}
//
//suspend fun <T> DefaultWebSocketSession.sendProtobuf(typeId: RequestTypes, data: T) = sendProtobuf(typeId.typeId, data)
