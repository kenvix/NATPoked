package com.kenvix.natpoked.test

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlin.coroutines.Continuation

fun main() {
    runBlocking {
        val channel = Channel<String>()
        var j: Continuation<Unit>? = null

        val scope = launch {
            withTimeout(1000) {
                try {
                    suspendCancellableCoroutine<Unit> { job ->
                        j = job
                    }

                    println("job completed")
                } catch (e: CancellationException) {
                    if (j != null) {
                        println("<<<<<<<<<<Job $j cancelled")
                    }
                    e.printStackTrace()
                    throw e
                }
            }
        }
    }
}