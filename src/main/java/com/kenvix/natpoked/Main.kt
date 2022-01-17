package com.kenvix.natpoked

import ch.qos.logback.classic.Level
import com.kenvix.natpoked.server.NATServer
import com.kenvix.natpoked.utils.AppEnv
import com.kenvix.web.utils.ConsoleCommands
import com.kenvix.web.utils.ExceptionHandler
import com.kenvix.web.utils.error
import kotlinx.coroutines.*
import org.slf4j.LoggerFactory
import org.apache.commons.cli.*
import kotlin.coroutines.CoroutineContext
import kotlin.system.exitProcess

object Main : CoroutineScope {
    private val mainJob = Job() + CoroutineName("Main")
    private val logger = LoggerFactory.getLogger(javaClass)
    private const val CLI_HEADER = "NATPoked By Kenvix"

    @JvmStatic
    fun main(args: Array<String>) = runBlocking {
        logger.info("Application start")
        logger.debug("Working directory: " + AppConstants.workingFolder)
        ExceptionHandler.registerGlobalExceptionHandler()
        val cmd = loadCommandLine(args)

        try {
            AppEnv
        } catch (e: Throwable) {
            showErrorAndExit(e, 5, "错误：无法解析环境变量文件，有些配置项格式有误，请尝试阅读下面的错误报" +
                    "告并试着解决。如果无法解决，请删除 ${AppConstants.workingFolder}.env 以恢复默认设置")
        }

        registerCommands()
        registerShutdownHandler()

        launch(Dispatchers.IO) {
            runCatching {
                logger.info("Starting server ...")
                NATServer.start()
            }.onFailure { showErrorAndExit(it, 2, "Server initialization failed") }
        }

        beginReadSystemConsole()
    }

    @JvmOverloads
    fun showErrorAndExit(throwable: Throwable, exitCode: Int = 1, extraMessage: String? = null) {
        showErrorAndExit(
            message = extraMessage ?: throwable.localizedMessage,
            exitCode = exitCode,
            throwable = throwable
        )
    }

    @JvmOverloads
    fun showErrorAndExit(message: String, exitCode: Int = 1, throwable: Throwable? = null, simpleMessage: Boolean = false) {
        val title = "Application Critical Error! Code #$exitCode"
        logger.error(title)

        when {
            throwable == null -> {
                logger.error(message)
            }
            simpleMessage -> {
                logger.error(message)
                logger.error(throwable.localizedMessage)
            }
            else -> {
                logger.error(message, throwable)
            }
        }

        exitProcess(exitCode)
    }

    @Throws(ParseException::class)
    private fun loadCommandLine(args: Array<String>): CommandLine {
        val ops = Options()

        ops.addOption("i", "target-id", true, "要打洞的对端的客户端ID，例如 12345678ABCDEF")
        ops.addOption("p", "forward-port", true, "要代理的本机端口，本参数可以多次出现。格式为 " +
                "协议:本机端口:远端端口，例如 -p tcp:80:8080 使得对方能够通过 127.0.0.2:8080 访问本机的 80 端口。地址 127.0.0.2 可以通过 .env 文件或环境变量修改。")

        ops.addOption("s", "server", false, "以中介服务器模式运行")
        ops.addOption("c", "client", false, "以P2P通信端运行")

        ops.addOption("d", "dump-settings", false, "导出设置参数到文件 ${AppConstants.workingFolder}.env")

        ops.addOption("v", "verbose", false, "Verbose logging mode.")
        ops.addOption("vv", "very-verbose", false, "Very Verbose logging mode.")
        ops.addOption("h", "help", false, "Print help messages and exit")
        ops.addOption(null, "nogui", false, "No GUI")

        val parser = DefaultParser()
        val cmd = parser.parse(ops, args)

        if (cmd.hasOption("nogui"))
            System.setProperty("nogui", "1")

        AppConstants.appMode = if (cmd.hasOption('s')) AppConstants.AppMode.SERVER else AppConstants.AppMode.CLIENT

        //TODO: Log Level
        if (cmd.hasOption('v')) {
            AppConstants.rootLogger.level = Level.toLevel("debug")
        } else if (cmd.hasOption("vv")) {
            AppConstants.rootLogger.level = Level.toLevel("trace")
            logger.info("Very Verbose logging mode enabled.")
        } else {
            AppConstants.rootLogger.level = Level.toLevel("info")
        }

        if (cmd.hasOption('d')) {
            AppEnv.save()
        }

        if (cmd.hasOption('h')) {
            logger.trace("Fall into help mode")

            val formatter = HelpFormatter()
            formatter.printHelp("NATPoked", CLI_HEADER, ops, "", true)
            exitProcess(0)
        }

        logger.trace("Running at JDK ${System.getProperty("java.version")}")
        return cmd
    }

    private fun registerCommands() {
        ConsoleCommands["stop"] = {
            logger.info("Stopping application gracefully ...")
            exitProcess(0)
        }

        ConsoleCommands["halt"] = {
            logger.info("Halt VM now! (DISCARD all unsaved cached)")
            Runtime.getRuntime().halt(255)
        }

        ConsoleCommands["saveprop"] = {
            logger.info("Saving properties...")
            AppEnv.save()
        }

        ConsoleCommands["help"] = {
            println("Available commands: ")
            ConsoleCommands.forEach { t, u -> print("$t ") }
            println()
        }

        ConsoleCommands["gc"] = {
            logger.info("Going to vm safe point and running garbage collection ...")
            System.gc()
        }
    }

    private fun registerShutdownHandler() {
        Runtime.getRuntime().addShutdownHook(Thread ({
            logger.info("Preparing for shutdown ... ( enter 'halt' to discard all unsaved data and force quit )")
            AppConstants.shutdownHandler.forEach {
                kotlin.runCatching { it(Unit) }.onFailure { e -> logger.warn("Shutdown handler failed", e) }
            }
            logger.info("Application shutdown handler finished. can halt safely now.")
        }, "Shutdown Handler"))
    }

    private fun beginReadSystemConsole() {
        while (!Thread.interrupted()) {
            kotlin.runCatching {
                val input = readLine()

                if (input?.isNotBlank() == true) {
                    launch {
                        runCatching {  ConsoleCommands.invoke(input) }.onFailure {
                            error("Command failed", it, logger)
                        }
                    }
                }
            }.onFailure { error("Console read line failed", it, logger) }
        }
    }

    override val coroutineContext: CoroutineContext = mainJob + Dispatchers.IO + CoroutineName("Main")
}