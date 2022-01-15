package com.kenvix.web.server

import com.google.common.cache.Cache
import com.google.common.cache.CacheBuilder
import com.google.common.cache.CacheStats
import java.util.concurrent.Callable
import java.util.concurrent.TimeUnit

object KeyValueCache : Cached {
    val cache: Cache<String, Any> = CacheBuilder
        .newBuilder()
        .expireAfterAccess(10, TimeUnit.MINUTES)
        .build()

    fun invalidate(key: String) {
        cache.invalidate(key)
    }

    @Suppress("UNCHECKED_CAST")
    inline operator fun <reified T> get(key: String): T? = cache.getIfPresent(key) as T

    @Suppress("UNCHECKED_CAST")
    inline fun <reified T> get(key: String, loader: Callable<T>): T = cache.get(key, loader) as T

    operator fun contains(key: String) = cache.asMap().contains(key)

    operator fun <T> set(key: String, value: T) {
        cache.put(key, value)
    }

    override fun invalidateAll() {
        cache.invalidateAll()
    }

    override fun cleanUpAll() {
        cache.cleanUp()
    }

    override fun getStats(): List<CacheStats> = listOf(cache.stats())
}