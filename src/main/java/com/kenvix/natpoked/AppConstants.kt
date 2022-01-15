package com.kenvix.natpoked

import com.kenvix.utils.event.EventSet
import com.kenvix.utils.event.eventSetOf
import java.io.File

object AppConstants {
    val startedAt = System.currentTimeMillis()
    val shutdownHandler: EventSet<Unit> = eventSetOf()
    val workingFolder = File("").absolutePath + File.separatorChar
}