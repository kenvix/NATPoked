@file:JvmName("WebServerModules")
package com.kenvix.natpoked.server

import com.kenvix.natpoked.utils.AppEnv
import com.kenvix.utils.exception.*
import com.kenvix.web.utils.respondError
import io.ktor.application.*
import io.ktor.features.*
import io.ktor.features.BadRequestException
import io.ktor.features.NotFoundException
import io.ktor.http.*
import io.ktor.http.cio.websocket.*
import io.ktor.http.content.*
import io.ktor.locations.*
import io.ktor.serialization.*
import io.ktor.util.date.*
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import org.slf4j.event.Level
import java.net.URI
import java.time.Duration

@Suppress("unused", "DuplicatedCode") // Referenced in application.conf
fun Application.module() {
    val isTesting = System.getProperty("testing")?.toBoolean() ?: false


    install(Locations) {

    }

    if (AppEnv.EnableCompression) {
        install(Compression) {
            gzip {
                priority = 1.0
            }
            deflate {
                priority = 10.0
                minimumSize(1024) // condition
            }
        }
    }

    install(CORS) {
        method(HttpMethod.Options)
        method(HttpMethod.Put)
        method(HttpMethod.Delete)
        method(HttpMethod.Patch)

        //header(HttpHeaders.Authorization)
        //header("MyCustomHeader")
        allowCredentials = AppEnv.CorsAllowCredentials

        if (AppEnv.CorsOriginAnyHost)
            anyHost()

        if (AppEnv.CorsOriginHosts.isNotBlank())
            AppEnv.CorsOriginHosts.split(',').forEach { host(it) }
    }

    install(CachingHeaders) {
        options { outgoingContent ->
            when (outgoingContent.contentType?.withoutParameters()) {
                ContentType.Text.CSS -> CachingOptions(CacheControl.MaxAge(maxAgeSeconds = 24 * 60 * 60), expires = null as? GMTDate?)
                else -> null
            }
        }
    }

    install(DefaultHeaders) {
        //header("X-Engine", "Ktor") // will send this header with each response
    }

    if (AppEnv.XForwardedHeadersSupport) {
        install(XForwardedHeaderSupport)

        NATServer.logger.info("X-Forwarded-For Header Support is enabled. be aware of being spoofed by a malicious client")
    }

    install(io.ktor.websocket.WebSockets) {
        pingPeriod = Duration.ofSeconds(15)
        timeout = Duration.ofSeconds(15)
        maxFrameSize = Long.MAX_VALUE
        masking = false
    }

    install(PartialContent) {
        // Maximum number of ranges that will be accepted from a HTTP request.
        // If the HTTP request specifies more ranges, they will all be merged into a single range.
        maxRangeCount = 10
    }

    // Easy '304 Not Modified' Responses
    install(ConditionalHeaders)

    // Enable Automatic HEAD Responses
    install(AutoHeadResponse)

    if (AppEnv.DebugMode) {
        install(CallLogging) {
            level = Level.DEBUG
            logger = LoggerFactory.getLogger("CallLogging")
        }
    }

    install(ContentNegotiation) {
        json(Json {
            prettyPrint = true
            isLenient = true
        })
    }

    install(StatusPages) {
        exception<BadRequestException> { respondError(HttpStatusCode.BadRequest, it) }
        exception<InvalidResultException> { respondError(HttpStatusCode.BadRequest, it) }
        exception<NumberFormatException> { respondError(HttpStatusCode.BadRequest, it) }
        exception<com.kenvix.utils.exception.BadRequestException> { respondError(HttpStatusCode.BadRequest, it) }

        exception<InvalidAuthorizationException> {
            respondError(HttpStatusCode.Unauthorized, it, URI("/user/login"))
        }

        exception<ForbiddenOperationException> { respondError(HttpStatusCode.Forbidden, it) }
        exception<CommonBusinessException> { respondError(HttpStatusCode.NotAcceptable, it) }

        exception<NotFoundException> { respondError(HttpStatusCode.NotFound, it) }
        exception<com.kenvix.utils.exception.NotFoundException> { respondError(HttpStatusCode.NotFound, it) }

        exception<TooManyRequestException> { respondError(HttpStatusCode.TooManyRequests, it) }
        exception<NotSupportedException> { respondError(HttpStatusCode.UnsupportedMediaType, it) }

        exception<NotImplementedError> { respondError(HttpStatusCode.NotImplemented, it) }

        exception<Throwable> {
            com.kenvix.web.utils.error("HTTP request failed unexpectedly", it, NATServer.logger)
            respondError(HttpStatusCode.InternalServerError, it)
        }

        status(HttpStatusCode.BadRequest, HttpStatusCode.Unauthorized, HttpStatusCode.Forbidden,
                HttpStatusCode.NotFound, HttpStatusCode.TooManyRequests, HttpStatusCode.InternalServerError,
                HttpStatusCode.MethodNotAllowed, HttpStatusCode.NotAcceptable, HttpStatusCode.RequestTimeout,
                HttpStatusCode.Gone, HttpStatusCode.UnsupportedMediaType, HttpStatusCode.BadGateway,
                HttpStatusCode.PayloadTooLarge, HttpStatusCode.NotImplemented, HttpStatusCode.ServiceUnavailable,
                HttpStatusCode.UpgradeRequired, HttpStatusCode.Locked) {
            respondError(it)
        }
    }

    NATServer.registerRoutes(this, isTesting)
}