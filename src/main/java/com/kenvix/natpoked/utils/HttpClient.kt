@file:JvmName("HttpClient")
package com.kenvix.natpoked.utils

import okhttp3.OkHttpClient
import kotlin.time.DurationUnit
import kotlin.time.toDuration
import kotlin.time.toJavaDuration

val httpClient: OkHttpClient = OkHttpClient.Builder()
    .retryOnConnectionFailure(true)
    .connectTimeout(AppEnv.PeerToBrokenTimeoutDuration.toJavaDuration())
    .readTimeout(if (AppEnv.DebugMode) 1.toDuration(DurationUnit.DAYS).toJavaDuration() else AppEnv.PeerToBrokenTimeoutDuration.toJavaDuration()) // allow server debugging
    .pingInterval(AppEnv.PeerToBrokenPingIntervalDuration.toJavaDuration())
    .build()