@file:JvmName("HttpClient")
package com.kenvix.natpoked.utils

import okhttp3.OkHttpClient

val httpClient: OkHttpClient = OkHttpClient.Builder()
    .retryOnConnectionFailure(true)
    .connectTimeout(8, java.util.concurrent.TimeUnit.SECONDS)
    .build()