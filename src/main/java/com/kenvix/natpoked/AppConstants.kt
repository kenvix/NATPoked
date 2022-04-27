package com.kenvix.natpoked

import ch.qos.logback.classic.Logger
import com.kenvix.utils.event.EventSet
import com.kenvix.utils.event.eventSetOf
import org.slf4j.LoggerFactory
import java.io.File
import java.nio.file.Path


object AppConstants {
    val startedAt = System.currentTimeMillis()
    val shutdownHandler: EventSet<Unit> = eventSetOf()
    val workingFolder = File("").absolutePath + File.separatorChar
    val workingPath = Path.of(workingFolder)
    val tempPath = workingPath.resolve("Temp")
    var appMode: AppMode = AppMode.PEER
    val rootLogger = LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME) as Logger
//    var nettyGroup: EventLoopGroup = NioEventLoopGroup()

    enum class AppMode {
        PEER, BROKER
    }
}