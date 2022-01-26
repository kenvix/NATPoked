package com.kenvix.natpoked.server

import com.kenvix.natpoked.AppConstants
import com.kenvix.natpoked.contacts.*
import com.kenvix.natpoked.utils.AppEnv
import com.kenvix.web.server.CachedClasses
import com.kenvix.web.server.KtorModule
import io.ktor.application.*
import io.ktor.server.cio.*
import io.ktor.util.*
import io.ktor.application.Application
import io.ktor.http.cio.websocket.*
import io.ktor.server.cio.CIO
import io.ktor.server.engine.embeddedServer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory
import java.io.File
import java.util.concurrent.ConcurrentHashMap

object NATServer {
    internal val logger = LoggerFactory.getLogger(javaClass)
    val peerConnections: MutableMap<PeerId, NATPeerToBrokerConnection> = ConcurrentHashMap(64)
    val peerWebsocketSessionMap: MutableMap<DefaultWebSocketSession, NATPeerToBrokerConnection> = ConcurrentHashMap(64)

    val ktorEmbeddedServer = embeddedServer(CIO, port = AppEnv.HttpPort, host = AppEnv.HttpHost, watchPaths = run {
        if (AppEnv.DebugMode && System.getProperty("hotReloadSupported")?.toBoolean() == true) {
            listOf( // Substring match rules for classpath
                "${AppConstants.workingFolder.replace('\\', '/')}out",
                "${AppConstants.workingFolder.replace('\\', '/')}build/classes",
                "/out/",
                "/build/classes/"
            ).also {
                logger.debug("Ktor auto reload enabled on: ${it.joinToString(" , ")}")
            }
        } else {
            emptyList()
        }
    }, configure = {
        // Size of the event group for accepting connections
        connectionGroupSize = parallelism * AppEnv.ServerWorkerPoolSizeRate
        // Size of the event group for processing connections,
        // parsing messages and doing engine's internal work
        workerGroupSize = parallelism * AppEnv.ServerWorkerPoolSizeRate
        // Size of the event group for running application code
        callGroupSize = parallelism * AppEnv.ServerWorkerPoolSizeRate

        /** Options for CIO **/

        /** Options for CIO **/
        // Number of seconds that the server will keep HTTP IDLE connections open.
        // A connection is IDLE if there are no active requests running.
        connectionIdleTimeoutSeconds = AppEnv.ServerMaxIdleSecondsPerHttpConnection

        /** Options for Netty **/

        /** Options for Netty **/
        // Size of the queue to store [ApplicationCall] instances that cannot be immediately processed
        // requestQueueLimit = parallelism * ServerEnv.ServerWorkerPoolSizeRate
        // Do not create separate call event group and reuse worker group for processing calls
        // shareWorkGroup = true
        // Timeout in seconds for sending responses to client
        // responseWriteTimeoutSeconds = 120
        // configureBootstrap = {

        // }
    }, module = Application::module)

    suspend fun start() = withContext(Dispatchers.IO) {
        logger.info("Starting web server")
        preload()
        startHttpServer()
    }

    internal suspend fun preload() {
        checkFiles()
        CachedClasses
        // TODO: More registry & service autowired
    }

    private fun startHttpServer() {
        ktorEmbeddedServer.start(false)
    }

    private suspend fun checkFiles() = withContext(Dispatchers.IO) {
        val publicDir = File(AppEnv.PublicDirPath)

        if (!publicDir.exists())
            publicDir.mkdirs()
    }

    fun registerRoutes(application: Application, testing: Boolean = false) {
        WebServerBasicRoutes.module(application, testing)

        // Deprecated due to performance issue. use hard code instead.
//        val reflections = Reflections(BuildConfig.PACKAGE_NAME + ".http.routes", SubTypesScanner(true))
//        val modules = reflections.getSubTypesOf(KtorModule::class.java)
//        for (module in modules) {
//            val instance = module.getConstructor().newInstance()
//            if (instance is KtorModule) {
//                instance.module(application, testing)
//            } else {
//                logger.warn("Invalid route class: ${module.name}")
//            }
//        }
    }
}