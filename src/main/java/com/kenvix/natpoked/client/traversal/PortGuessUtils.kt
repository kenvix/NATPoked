package com.kenvix.natpoked.client.traversal

import org.apache.commons.math3.distribution.PoissonDistribution
import kotlin.math.round

fun poissonSampling(avg: Double, timeSpan: Long): Int {
    return PoissonDistribution(avg, timeSpan.toDouble()).sample()
}

fun poissonPortGuess(avg: Double, now: Long, timeElapsed: Long, lastPort: Int, guessPortNum: Int = 50): List<Int> {
    val guessedPorts = ArrayList<Int>()
    var n: Int = 0

    for (i in 1 .. guessPortNum) {
        val sample = poissonSampling(avg, timeElapsed)
        n += sample
        val port: Double = lastPort.toDouble() + i * avg * now + n
        var validPort = round(port).toInt() % 65536
        if (validPort < 1024)
            validPort += 1024

        guessedPorts.add(validPort)
    }

    return guessedPorts
}

fun expectedValuePortGuess(avg: Double, now: Long, timeElapsed: Long, lastPort: Int, guessPortNum: Int = 50): List<Int> {
    return (1 .. guessPortNum).map {
        ((lastPort + it + it * avg * (now + timeElapsed)).toInt() % 65536).run {
            if (this < 1024) this + 1024 else this
        }
    }
}