package com.kenvix.web.server

import com.google.common.cache.CacheStats
import com.kenvix.natpoked.AppConstants
import com.kenvix.natpoked.utils.AppEnv
import com.kenvix.web.utils.ConsoleCommands
import com.kenvix.web.utils.warn
import org.slf4j.LoggerFactory
import java.io.ByteArrayOutputStream
import java.io.PrintStream
import java.util.*
import kotlin.concurrent.schedule

object CachedClasses : HashSet<Cached>() {
    private val cacheCleanUpIntervalSeconds: Long = AppEnv.CacheCleanUpCheckIntervalSeconds
    private val logger = LoggerFactory.getLogger(this::class.java)
    private val cleanUpTimer = Timer("Cache Cleaner").also {
        it.schedule(cacheCleanUpIntervalSeconds) { internalScheduledCleanUp() }
    }

    init {
        AppConstants.shutdownHandler += {
            cleanUpTimer.cancel()
            invalidateAll()
        }

        ConsoleCommands["commit"] = {
            logger.info("Committing and invalidating all unsaved cache ...")
            invalidateAll()
            logger.info("Committed all unsaved cache")
        }

        ConsoleCommands["cleanup"] = {
            logger.info("cleanup all expired cache ...")
            cleanUpAll()
            logger.info("clean up all expired cache")
        }

        ConsoleCommands["cache"] = { _ ->
            println("Cache stats (Commit for commit command.)")
            println(getStatsString())
        }
    }

    fun invalidateAll(
        beforeInvalidate: ((Cached) -> Unit)? = { logger.info("Invalidating cache: ${it.javaClass.simpleName}") }
    ) {
        forEach {
            beforeInvalidate?.invoke(it)
            it.invalidateAll()
        }
    }

    fun getStats(): Map<Cached, List<CacheStats>> {
        return this.asSequence().map { it to it.getStats() }.toMap()
    }

    fun getStatsString(): String {
        return ByteArrayOutputStream().use { b ->
            PrintStream(b).use { p ->
                p.printf(
                    "%1s %25s %7s %7s %7s %7s %7s %7s %7s %14s %15s\n", "#", "ProviderName", "Hit", "Miss", "HitRate", "Success",
                    "Exception", "Eviction", "Request", "TotalLoadTime", "AvgLoadPenalty"
                )
                getStats().forEach { (cached, list) ->
                    list.forEachIndexed { i, it ->
                        p.printf(
                            "%1d %25s %7d %7d %7.3f %7d %7d %7d %7d %14d %15.2f\n",
                            i,
                            cached.javaClass.simpleName,
                            it.hitCount(),
                            it.missCount(),
                            it.hitRate() * 100,
                            it.loadSuccessCount(),
                            it.loadExceptionCount(),
                            it.evictionCount(),
                            it.requestCount(),
                            it.totalLoadTime(),
                            it.averageLoadPenalty()
                        )
                    }
                }
            }

            b.toByteArray().toString(Charsets.UTF_8)
        }
    }

    fun cleanUpAll(beforeCleanUp: ((Cached) -> Unit)? = null) {
        forEach {
            beforeCleanUp?.invoke(it)
            it.cleanUpAll()
        }
    }

    private fun internalScheduledCleanUp() {
        kotlin.runCatching { cleanUpAll() }.onFailure { warn("ScheduledCleanUp Failed", it, logger) }
        cleanUpTimer.schedule(cacheCleanUpIntervalSeconds) { internalScheduledCleanUp() }
    }
}