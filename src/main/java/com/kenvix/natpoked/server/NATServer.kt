package com.kenvix.natpoked.server

import com.kenvix.natpoked.AppConstants
import com.kenvix.natpoked.contacts.NATClientItem
import com.kenvix.natpoked.contacts.NATPeerToBrokerConnection
import com.kenvix.natpoked.contacts.PeerId
import com.kenvix.natpoked.utils.AppEnv
import com.kenvix.natpoked.utils.sha256Of
import com.kenvix.natpoked.utils.toBase58String
import com.kenvix.web.server.CachedClasses
import com.kenvix.web.utils.ProcessUtils
import io.ktor.application.*
import io.ktor.http.cio.websocket.*
import io.ktor.server.cio.*
import io.ktor.server.engine.*
import kotlinx.coroutines.*
import org.slf4j.LoggerFactory
import java.io.Closeable
import java.io.File
import java.net.Inet6Address
import java.net.InetAddress
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import kotlin.io.path.exists


object NATServer : Closeable {
    private const val mqttPasswdFileName = "mqtt.passwd"
    private const val mqttConfigFileName = "mqtt.conf"

    internal val logger = LoggerFactory.getLogger(javaClass)
    private val peerConnectionsImpl: MutableMap<PeerId, NATPeerToBrokerConnection> = ConcurrentHashMap(64)
    val peerConnections: Map<PeerId, NATPeerToBrokerConnection> = object : Map<PeerId, NATPeerToBrokerConnection> by peerConnectionsImpl {
        override fun containsKey(key: PeerId): Boolean {
            val v = peerConnectionsImpl[key] ?: return false
            if (v.client.isClientLastContactTimeExpired()) {
                peerConnectionsImpl.remove(key)
                return false
            }

            return true
        }

        override fun get(key: PeerId): NATPeerToBrokerConnection? {
            val v = peerConnectionsImpl[key] ?: return null
            if (v.client.isClientLastContactTimeExpired()) {
                peerConnectionsImpl.remove(key)
                return null
            }

            return v
        }
    }

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
        connection.client.updateClientLastContactTime()
        peerConnectionsImpl[peerId] = connection
    }

    fun addPeerConnection(clientItem: NATClientItem, encodedKey: String) {
        addPeerConnection(clientItem.clientId, NATPeerToBrokerConnection(clientItem, encodedKey))
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

    val ktorEmbeddedServer =
        embeddedServer(CIO, port = AppEnv.ServerHttpPort, host = AppEnv.ServerHttpHost, watchPaths = run {
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

        val tempPath = AppConstants.tempPath
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
            val mqttPasswd = tempPath.resolve(mqttPasswdFileName)
            mqttPasswd.toFile().writeText(
                "server:${brokerServer.token}" + "\n" +
                        "broker:${sha256Of(AppEnv.ServerPSK).toBase58String()}"
            )

            while (isActive) {
                ProcessUtils.runProcess(
                    "mqtt_passwd", ProcessBuilder().command(
                        "mosquitto_passwd", "-U", mqttPasswd.toAbsolutePath().toString(),
                    )
                )

                val proc = ProcessUtils["mqtt_passwd"]!!
                proc.waitFor()
                if (proc.exitValue() != 0)
                    logger.warn("Failed to configure MQTT broker: mosquitto_passwd failed with exit code ${proc.exitValue()}")
                else
                    break
            }
        }

        fun launchMqttBrokerAsync() = async {
            if (mqttConfig.isActive)
                mqttConfig.await()

            val mqttProtocol = if (InetAddress.getByName(AppEnv.ServerHttpHost) is Inet6Address) "ipv6" else "ipv4"

            logger.info("Starting MQTT Broker")
            var mqttBrokerConfig = """
listener ${AppEnv.ServerMqttPort}
socket_domain $mqttProtocol
protocol websockets

allow_anonymous false
password_file ${tempPath.resolve(mqttPasswdFileName).toAbsolutePath()}
    """

            if (AppEnv.IsRunningInDocker) {
                mqttBrokerConfig += """
user root
                """
            }

            mqttBrokerConfig = mqttBrokerConfig.trimIndent()

            val mqttConf = tempPath.resolve(mqttConfigFileName)
            mqttConf.toFile().writeText(mqttBrokerConfig)

            ProcessUtils.runProcess(
                "mqtt", ProcessBuilder().command(
                    "mosquitto",
                    "-c", mqttConf.toAbsolutePath().toString(),
//                    "-v"
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