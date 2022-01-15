@file:JvmName("HttpUtils")

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
import io.ktor.util.pipeline.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.apache.commons.io.FileUtils
import org.apache.commons.text.StringEscapeUtils
import org.slf4j.LoggerFactory
import java.io.File
import java.net.URI
import java.sql.Timestamp

private val utilsLogger = LoggerFactory.getLogger("HttpUtils")!!

fun Route.controller(path: String, controller: Controller) {
    this.route(path, controller::route)
}

fun <T: Any> PipelineContext<*, ApplicationCall>.middlewareResultOrNull(clazz: BaseMiddleware<T>): T? {
    return clazz.getMiddlewareValueOrNull(this)
}

data class Result2<T, U>(val component1: T, val component2: U)
data class Result3<T, U, V>(val component1: T, val component2: U, val component3: V)
data class Result4<T, U, V, W>(val component1: T, val component2: U, val component3: V, val component4: W)
data class Result5<T, U, V, W, X>(val component1: T, val component2: U, val component3: V, val component4: W, val component5: X)

fun <T: Any> PipelineContext<*, ApplicationCall>.middleware(clazz: Middleware<T>): T {
    return clazz.callMiddleware(this)
}

suspend fun <T: Any> PipelineContext<*, ApplicationCall>.middleware(clazz: MiddlewareSuspend<T>): T {
    return clazz.callMiddleware(this)
}

fun <T: Any, U: Any> PipelineContext<*, ApplicationCall>.middleware(clazz1: Middleware<T>, clazz2: Middleware<U>) {
    clazz1.callMiddleware(this)
    clazz2.callMiddleware(this)
}

suspend fun <T: Any, U: Any> PipelineContext<*, ApplicationCall>.middleware(clazz1: MiddlewareSuspend<T>, clazz2: MiddlewareSuspend<U>) {
    clazz1.callMiddleware(this)
    clazz2.callMiddleware(this)
}

fun <T: Any, U: Any, V: Any> PipelineContext<*, ApplicationCall>.middleware(clazz1: Middleware<T>, clazz2: Middleware<U>, clazz3: Middleware<V>) {
    clazz1.callMiddleware(this)
    clazz2.callMiddleware(this)
    clazz3.callMiddleware(this)
}

suspend fun <T: Any, U: Any, V: Any> PipelineContext<*, ApplicationCall>.middleware(clazz1: MiddlewareSuspend<T>, clazz2: MiddlewareSuspend<U>, clazz3: MiddlewareSuspend<V>) {
    clazz1.callMiddleware(this)
    clazz2.callMiddleware(this)
    clazz3.callMiddleware(this)
}

fun <T: Any, U: Any, V: Any, W: Any> PipelineContext<*, ApplicationCall>.middleware(clazz1: Middleware<T>, clazz2: Middleware<U>, clazz3: Middleware<V>, clazz4: Middleware<W>) {
    clazz1.callMiddleware(this)
    clazz2.callMiddleware(this)
    clazz3.callMiddleware(this)
    clazz4.callMiddleware(this)
}

suspend fun <T: Any, U: Any, V: Any, W: Any> PipelineContext<*, ApplicationCall>.middleware(clazz1: MiddlewareSuspend<T>, clazz2: MiddlewareSuspend<U>, clazz3: MiddlewareSuspend<V>, clazz4: MiddlewareSuspend<W>) {
    clazz1.callMiddleware(this)
    clazz2.callMiddleware(this)
    clazz3.callMiddleware(this)
    clazz4.callMiddleware(this)
}

fun PipelineContext<*, ApplicationCall>.middleware(vararg clazz: Middleware<*>) {
    clazz.forEach { it.callMiddleware(this) }
}

suspend fun PipelineContext<*, ApplicationCall>.middleware(vararg clazz: MiddlewareSuspend<*>) {
    clazz.forEach { it.callMiddleware(this) }
}

suspend fun PipelineContext<*, ApplicationCall>.respondJson(data: Any?, info: String? = null,
                                                            code: Int = 0, status: HttpStatusCode = HttpStatusCode.OK) {
    call.respond(CommonJsonResult(status.value, info = info ?: status.description, code = code, data = data))
}

suspend fun PipelineContext<*, ApplicationCall>.respondJsonText(jsonText: String,
                                                            status: HttpStatusCode = HttpStatusCode.OK) {
    call.respondText(jsonText, ContentType.Application.Json, status)
}

/**
 * Respond a success message to user
 * @param msg message
 * @param data return data. If data is [URI] it will be equals to redirectUrl
 * @param redirectURI Redirect user to this url, will be attached to Header "X-Redirect-Location" Redirection is only available if useragent is a valid user browser.
 */
suspend fun PipelineContext<*, ApplicationCall>.respondSuccess(
    msg: String? = null,
    data: Any? = null,
    redirectURI: URI? = null,
    statusCode: HttpStatusCode = HttpStatusCode.TemporaryRedirect
) {
//    if (isUserBrowserRequest()) {
//        val redirectTo: String? = (if (redirectURI == null && data != null && data is URI) data else redirectURI)?.run {
//            appendQuery("msg=$msg").toString()
//        }
//        redirectTo.ifNotNull {  call.response.headers.append("X-Redirect-Location", it) }
//        if (redirectTo != null) {
//            call.respond(
//                statusCode, FreeMarkerContent("redirect.ftl", mapOf(
//                    "msg" to (msg ?: "请稍候"),
//                    "redirectUrl" to redirectTo
//                ))
//            )
//        } else {
//            call.respond(
//                HttpStatusCode.OK, FreeMarkerContent("success.ftl", mapOf(
//                    "msg" to (msg ?: "操作成功"),
//                ))
//            )
//        }
//    } else {
        respondJson(data, msg)
//    }
}

fun businessException(description: String): Nothing {
    throw CommonBusinessException(description, HttpStatusCode.NotAcceptable.value)
}

fun PipelineContext<*, ApplicationCall>.isUserBrowserRequest(): Boolean {
    val userAgent = call.request.userAgent()
    return userAgent != null && userAgent.contains("Mozilla", true) &&
            call.request.header("X-Requested-With") == null
}

suspend fun PipelineContext<*, ApplicationCall>.respondError(code: HttpStatusCode, exception: Throwable? = null, redirectURI: URI? = null) {
    var info = ""
    var trace = ""

    if (exception != null) {
        if (AppEnv.DebugMode) {
            info = "${exception.message}"
            trace = exception.getStringStackTrace()
        } else {
            if (code.value in 400..499 || code.value != 501)
                info = exception.message ?: exception.hashCode().toString()
            else
                info = exception.hashCode().toString()
        }
    }

//    if (isUserBrowserRequest()) {
//        call.respond(code, FreeMarkerContent("error.ftl", mapOf(
//                "code" to code.value,
//                "description" to code.description,
//                "info" to StringEscapeUtils.escapeHtml4(info),
//                "trace" to trace,
//                "isDebugMode" to ServerEnv.DebugMode,
//                "redirectUrl" to redirectURI?.appendQuery("msg=$info&code=${code.value}&description=${code.description}")?.toString()
//        )))
//    } else {
        call.respond(code, CommonJsonResult(status = code.value, info = info,
                code = if (exception is CommonBusinessException) exception.code else code.value,
                data = ErrorResult(
                        exception = exception?.javaClass?.simpleName ?: "",
                        exceptionFullName = exception?.javaClass?.name ?: "",
                        trace = trace
                )
        ))
//    }
}

fun currentTimeStamp() = Timestamp(System.currentTimeMillis())

suspend fun <R> File.useTempFile(then: (suspend (File) -> R))
        = withContext(Dispatchers.IO) {

    try {
        then(this@useTempFile)
    } catch (exception: Throwable) {
        kotlin.runCatching { this@useTempFile.delete() }
                .onFailure { warn("Delete temp file failed", it, utilsLogger) }

        throw exception
    }
}

inline fun <T: Any> T?.validateValue(errorMessage: String, passCondition: (check: T) -> Boolean): T {
    if (this == null)
        throw BadRequestException("Illegal input data: Required form param not found")

    if (!passCondition(this))
        throw BadRequestException(errorMessage)

    return this
}

inline fun <T: Any> T?.validateValue(passCondition: (check: T) -> Boolean): T
        = validateValue("Illegal input data: $this", passCondition)


fun <T: Any> T?.assertNotNull(): T {
    if (this == null)
        throw BadRequestException("Illegal input data: Required data is null")

    return this
}

fun <T: Any> T?.assertExist(): T {
    if (this == null)
        throw NotFoundException("Specified data not exist")

    return this
}

fun <T: Collection<*>> T?.assertNotEmpty(): T {
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

    return enumValues<E>().find { it.name == value } ?:
    throw BadRequestException("Illegal value $value for ${E::class.java.simpleName}")
}