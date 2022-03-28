package com.kenvix.web.utils

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory
import java.io.Closeable

object ProcessUtils : Closeable, CoroutineScope by CoroutineScope(Dispatchers.IO) {
    private val processes: MutableMap<String, Process> = mutableMapOf()

    init {
        Runtime.getRuntime().addShutdownHook(Thread {
            close()
        })
    }

    fun runProcess(key: String, builder: ProcessBuilder): Process {
        val process = builder.start()
        processes[key] = process
        val logger = LoggerFactory.getLogger("Process.$key")
        logger.info("Started process $key: PID #${process.pid()}: $process")

        launch(Dispatchers.IO) {
            process.inputStream.bufferedReader().use {
                while (process.isAlive) {
                    val line = it.readLine() ?: break
                    logger.info(line)
                }

                logger.info("Process $key: PID #${process.pid()} exited with code ${process.exitValue()}")
            }
        }

        launch(Dispatchers.IO) {
            process.errorStream.bufferedReader().use {
                while (process.isAlive) {
                    val line = it.readLine() ?: break
                    logger.warn(line)
                }
            }
        }

        return process
    }

    fun stopProcess(key: String) {
        processes[key]?.destroy()
        processes.remove(key)
    }

    override fun close() {
        processes.forEach {
            it.value.destroy()
        }
        processes.clear()
    }
}