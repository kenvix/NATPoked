@file:JvmName("HttpClient")
package com.kenvix.natpoked.utils

import okhttp3.OkHttpClient
import kotlin.time.toJavaDuration

val httpClient: OkHttpClient = OkHttpClient.Builder()
    .retryOnConnectionFailure(true)
    .connectTimeout(AppEnv.PeerToBrokenTimeoutDuration.toJavaDuration())
    .pingInterval(AppEnv.PeerToBrokenPingIntervalDuration.toJavaDuration())
    .build()