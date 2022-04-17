package com.kenvix.web.utils

import com.kenvix.natpoked.AppConstants
import com.kenvix.natpoked.utils.PlatformDetection
import kotlinx.coroutines.*
import org.apache.commons.io.IOUtils
import org.slf4j.LoggerFactory
import java.io.Closeable
import java.io.File
import java.nio.charset.Charset
import kotlin.io.path.exists

object ProcessUtils : Closeable, CoroutineScope by CoroutineScope(Dispatchers.IO) {
    data class ProcessInfo(
        var process: Process,
        var onProcessDiedHandler: ((Process) -> Unit)? = null
    )

    private val processes: MutableMap<String, ProcessInfo> = mutableMapOf()
    private val platform: PlatformDetection = PlatformDetection.getInstance()
    private val extraPath: String
    private val extraPathFile: File
    private val logger = LoggerFactory.getLogger(ProcessUtils::class.java)

    data class RunningProcess(
        val key: String,
        val pid: Long,
        val processName: String,
    )

    // TODO
    val runningProcessFilePath = "${AppConstants.workingPath}/running_process.json"

    fun saveRunningProcessList() {
        val list = processes.map { (key, process) ->
            RunningProcess(
                key,
                process.process.pid(),
                process.process.info().arguments().get()[0]
            )
        }.toList()
    }

    init {
        Runtime.getRuntime().addShutdownHook(Thread {
            cancel("System exit")
            close()
        })

        val basePath = AppConstants.workingFolder
        val loc = AppConstants.workingPath.resolve("Library").resolve(platform.os).resolve(platform.arch)
        if (!loc.exists())
            loc.toFile().mkdirs()

        extraPath = loc.toString()
        extraPathFile = loc.toFile()

        val path = System.getenv("PATH")
        if (path != null) {
            if (platform.os == PlatformDetection.OS_WINDOWS) {
                System.setProperty("PATH", "$path;$extraPath")
            } else {
                System.setProperty("PATH", "$path:$extraPath")
            }
        } else {
            System.setProperty("PATH", extraPath)
        }
    }

    operator fun get(name: String): Process? {
        return processes[name]?.process
    }

    fun buildProcess(
        builder: ProcessBuilder,
        redirectDir: Boolean = false,
    ) {
        builder.environment().let { env ->
            env["PATH"]?.let {
                if (platform.os == PlatformDetection.OS_WINDOWS) {
                    env["PATH"] = "$it;$extraPath"
                } else {
                    env["PATH"] = "$it:$extraPath"
                }
            } ?: run {
                env["PATH"] = extraPath
            }
        }

        if (redirectDir) {
            builder.directory(extraPathFile)
        }

        if (PlatformDetection.OS_WINDOWS == platform.os) {
            if (builder.command()[0].length < 4 || builder.command()[0].takeLast(4).first() != '.') {
                builder.command()[0] = builder.command()[0] + ".exe"
            }
        }

        if (extraPathFile.resolve(builder.command()[0]).exists()) {
            builder.command()[0] = extraPathFile.resolve(builder.command()[0]).toString()
        }
    }

    fun runProcess(
        key: String,
        builder: ProcessBuilder,
        redirectDir: Boolean = false,
        keepAlive: Boolean = false,
        onProcessDiedHandler: ((Process) -> Unit)? = null,
    ): Process {
        if (processes[key] != null && processes[key]!!.process.isAlive)
            throw IllegalStateException("Process $key is already running, cannot run again")

        buildProcess(builder, redirectDir)

        val process = builder.start()
        processes[key] = ProcessInfo(process, onProcessDiedHandler)
        val processLoggerControl = LoggerFactory.getLogger("Process.$key.control")
        val processLoggerStdout = LoggerFactory.getLogger("Process.$key.out")
        val processLoggerStdErr = LoggerFactory.getLogger("Process.$key.err")
        // logger.debug("ENV: ${builder.environment()}")

        processLoggerControl.debug("EXEC$ " + builder.command().joinToString(" "))
        processLoggerControl.info("Started process $key: PID #${process.pid()}: $process")

        launch(Dispatchers.IO) {
            process.inputStream.bufferedReader().use {
                while (process.isAlive) {
                    val line = runInterruptible { it.readLine() } ?: break
                    processLoggerStdout.info(line)
                }
            }

            processLoggerControl.info("Process $key: PID #${process.pid()} exited with code ${process.exitValue()}")

            if (this@ProcessUtils.isActive && keepAlive) {
                processLoggerControl.error("Process $key exited unexpectedly: PID #${process.pid()} exited with code ${process.exitValue()}")
                stopProcess(key)
                runProcess(key, builder, redirectDir, true)
            }
        }

        launch(Dispatchers.IO) {
            process.errorStream.bufferedReader().use {
                while (process.isAlive) {
                    val line = runInterruptible { it.readLine() } ?: break
                    processLoggerStdErr.info(line)
                }
            }
        }

        return process
    }

    fun execAndReadProcessOutput(
        builder: ProcessBuilder,
        charset: Charset = Charsets.UTF_8,
        input: String? = null,
        sendNewLineWhenInputWritten: Boolean = false,
        sendTermSignalWhenInputWritten: Boolean = false
    ): String {
        val proc = builder.start()
        if (input != null) {
            IOUtils.write(input, proc.outputStream, charset)
            if (sendNewLineWhenInputWritten) {
                IOUtils.write(System.lineSeparator(), proc.outputStream, charset)
            }
            proc.outputStream.flush()
            if (sendTermSignalWhenInputWritten) {
                proc.destroy()
            }
        }

        return IOUtils.toString(proc.inputStream, charset)
    }

    fun stopProcess(key: String) {
        processes[key]?.apply {
            process.destroy()
            onProcessDiedHandler?.invoke(process)
        }
        processes.remove(key)
    }

    override fun close() {
        processes.forEach {
            it.value.process.destroy()
        }
        processes.clear()
    }
}