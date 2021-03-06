package com.kenvix.natpoked.server

import com.kenvix.natpoked.AppConstants
import com.kenvix.natpoked.contacts.*
import com.kenvix.natpoked.server.NATServer.brokerServer
import com.kenvix.natpoked.server.NATServer.peerConnectionsImpl
import com.kenvix.natpoked.utils.*
import com.kenvix.web.server.CachedClasses
import com.kenvix.web.utils.ProcessUtils
import io.ktor.application.Application
import io.ktor.http.cio.websocket.*
import io.ktor.server.cio.CIO
import io.ktor.server.engine.embeddedServer
import kotlinx.coroutines.*
import org.slf4j.LoggerFactory
import java.io.Closeable
import java.io.File
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.io.path.exists
import kotlin.math.log

object NATServer : Closeable {
    internal val logger = LoggerFactory.getLogger(javaClass)
    private val peerConnectionsImpl: MutableMap<PeerId, NATPeerToBrokerConnection> = ConcurrentHashMap(64)
    val peerConnections: Map<PeerId, NATPeerToBrokerConnection>
        get() = peerConnectionsImpl

    lateinit var brokerServer: BrokerServer
        private set

    lateinit var echoServer: SocketAddrEchoServer
        private set

    init {
        Runtime.getRuntime().addShutdownHook(Thread {
            close()
        })
    }

    val peerWebsocketSessionMap: MutableMap<DefaultWebSocketSession, NATPeerToBrokerConnection> = ConcurrentHashMap(64)

    fun addPeerConnection(peerId: PeerId, connection: NATPeerToBrokerConnection) {
        peerConnectionsImpl[peerId] = connection
    }

    fun addPeerConnection(clientItem: NATClientItem) {
        addPeerConnection(clientItem.clientId, NATPeerToBrokerConnection(clientItem))
    }

    fun addPeerWebsocketSession(session: DefaultWebSocketSession, connection: NATPeerToBrokerConnection) {
        peerWebsocketSessionMap[session] = connection
    }

    fun removePeerWebsocketSession(session: DefaultWebSocketSession) {
        peerWebsocketSessionMap.remove(session)
    }

    fun removePeerConnection(peerId: PeerId) {
        peerConnectionsImpl.remove(peerId)
    }

    val ktorEmbeddedServer = embeddedServer(CIO, port = AppEnv.ServerHttpPort, host = AppEnv.ServerHttpHost, watchPaths = run {
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
        logger.info("Starting NATServer...")

        brokerServer = BrokerServer(UUID.randomUUID().toString(), AppEnv.ServerMqttPort)
        echoServer = SocketAddrEchoServer(AppEnv.EchoPortList.asIterable())
        echoServer.startAsync()

        val tempPath = AppConstants.workingPath.resolve("Temp")
        if (!tempPath.exists()) {
            tempPath.toFile().mkdirs()
        }

        if (AppEnv.DebugMode) {
            logger.info("<!> MQTT Server random key: ${brokerServer.token}")
        }

        val web = async {
            logger.info("Starting web server")
            preload()
            startHttpServer()
        }

        val mqttConfig = async(Dispatchers.IO) {
            logger.info("Pre-Configuring MQTT broker")
            val mqttPasswd =  tempPath.resolve("mqtt.passwd")
            mqttPasswd.toFile().writeText(
                "server:${brokerServer.token}" + "\n" +
                "broker:${sha256Of(AppEnv.ServerPSK).toBase58String()}"
            )

            ProcessUtils.runProcess("mqtt_passwd", ProcessBuilder().command(
                "mosquitto_passwd", "-U", "\"${mqttPasswd.toAbsolutePath()}\"",
            ))

            ProcessUtils["mqtt_passwd"]!!.waitFor()
        }

        fun launchMqttBrokerAsync() = async {
            mqttConfig.await()
            logger.info("Starting MQTT Broker")

            var mqttBrokerConfig = """
listener ${AppEnv.ServerMqttPort}
socket_domain ipv4
protocol websockets
listener ${AppEnv.ServerMqttPort}
socket_domain ipv6
protocol websockets
allow_anonymous false
password_file ${tempPath.resolve("mqtt.passwd").toAbsolutePath()}
    """.trimIndent()

            if (PlatformDetection.getInstance().os in arrayOf(PlatformDetection.OS_LINUX, PlatformDetection.OS_OSX, PlatformDetection.OS_SOLARIS)) {
                mqttBrokerConfig = "listener 0 \"${tempPath.resolve("mqtt.sock").toAbsolutePath()}\"\n$this"
            }

            val mqttConf = tempPath.resolve("mqtt.conf")
            mqttConf.toFile().writeText(mqttBrokerConfig)

            ProcessUtils.runProcess(
                "mqtt", ProcessBuilder().command(
                    "mosquitto",
                    "-c", "\"${mqttConf.toAbsolutePath()}\"",
                    "-v"
                ), keepAlive = true
            )
        }

        val mqtt = launchMqttBrokerAsync()
        mqtt.await()

        val brokerServerTask = async {
            brokerServer.connect()
        }

        web.await()
        brokerServerTask.await()
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

    @JvmStatic
    fun main(args: Array<String>) {
        logger.info("NATPoked Broker -- Standalone Mode")
        logger.warn("Stand-alone mode is not recommended for production use")
        runBlocking {
            start()
        }
    }

    override fun close() {

    }
}