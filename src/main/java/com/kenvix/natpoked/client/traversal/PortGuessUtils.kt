package com.kenvix.natpoked.client.traversal

import org.apache.commons.math3.distribution.PoissonDistribution
import kotlin.math.round

data class PortAllocationPredictionParam(
    val avg: Double,
    val timeElapsed: Long,
    val lastPort: Int
)

fun poissonSampling(avg: Double, timeSpan: Long): Int {
    return PoissonDistribution(avg, timeSpan.toDouble()).sample()
}

/**
 * 使用泊松分布法进行端口预测.
 * Guess port by using a Poisson distribution
 *
 * @param avg 端口分布均值 average value of the distribution
 * @param timeSpan 回声测试所用时间 time span during the port echo test in milliseconds
 * @param now 自回声测试结束后的当前时间（减去回声结束后的时间） current time in milliseconds after the echo test ends, minus the time after the echo test ends
 * @param lastPort 最后一次回声端口的结果 last port returned by the echo test
 * @param guessPortNum 预测端口数 guess port number
 * @return 预测结果 guess result
 */
fun poissonPortGuess(now: Long, param: PortAllocationPredictionParam, guessPortNum: Int = 50): List<Int> {
    val guessedPorts = ArrayList<Int>()
    var n: Int = 0

    for (i in 1 .. guessPortNum) {
        val sample = poissonSampling(param.avg, param.timeElapsed)
        n += sample
        val port: Double = param.lastPort.toDouble() + i * param.avg * now + n
        var validPort = round(port).toInt() % 65536
        if (validPort < 1024)
            validPort += 1024

        guessedPorts.add(validPort)
    }

    return guessedPorts
}

/**
 * 使用均值法进行端口预测. Use average method to guess port
 *
 * @param avg 端口分布均值 average value of the distribution
 * @param timeSpan 回声测试所用时间 time span during the port echo test in milliseconds
 * @param now 自回声测试结束后的当前时间（减去回声结束后的时间） current time in milliseconds after the echo test ends, minus the time after the echo test ends
 * @param lastPort 最后一次回声端口的结果 last port returned by the echo test
 * @param guessPortNum 预测端口数 guess port number
 * @return 预测结果 guess result
 */
fun expectedValuePortGuess(now: Long, param: PortAllocationPredictionParam, guessPortNum: Int = 50): List<Int> {
    return (1 .. guessPortNum).map {
        ((param.lastPort + it + it * param.avg * (now + param.timeElapsed)).toInt() % 65536).run {
            if (this < 1024) this + 1024 else this
        }
    }
}