package com.kenvix.natpoked.client.traversal

import com.kenvix.natpoked.client.NATClient
import com.kenvix.natpoked.client.SocketAddrEchoClient
import com.kenvix.natpoked.utils.AppEnv
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import org.apache.commons.math3.distribution.PoissonDistribution
import java.net.DatagramPacket
import java.net.InetAddress
import java.nio.channels.DatagramChannel
import kotlin.math.round

@Serializable
data class PortAllocationPredictionParam(
    val avg: Double,
    val timeElapsed: Long,
    val lastPort: Int,
    val testFinishedAt: Long,
    val firstPort: Int = -1,
) {
    val trend: Int
        get() = if (avg > 0) 1 else if (avg < 0) -1 else 0
}

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
fun poissonPortGuess(
    now: Long,
    param: PortAllocationPredictionParam,
    guessPortNum: Int = 50,
    skipLowPorts: Boolean = AppEnv.PortGuessSkipLowPorts
): List<Int> {
    val guessedPorts = ArrayList<Int>()
    var n: Int = 0

    for (i in 1..guessPortNum) {
        val sample = poissonSampling(param.avg, param.timeElapsed)
        n += sample
        val port: Double = param.lastPort.toDouble() + i * param.avg * now + n
        var validPort = Math.floorMod(round(port).toInt(), 65536)
        if (validPort < 1024 && skipLowPorts) {
            validPort += 1024

            guessedPorts.add(validPort)
        }
    }

    return guessedPorts
}

/**
 * 使用泊松分布法进行端口预测. (迭代式)
 * Guess port by using a Poisson distribution
 *
 * @param avg 端口分布均值 average value of the distribution
 * @param timeSpan 回声测试所用时间 time span during the port echo test in milliseconds
 * @param now 自回声测试结束后的当前时间（减去回声结束后的时间） current time in milliseconds after the echo test ends, minus the time after the echo test ends
 * @param lastPort 最后一次回声端口的结果 last port returned by the echo test
 * @param guessPortNum 预测端口数 guess port number
 * @return 预测结果 guess result
 */
fun poissonPortGuess(
    param: PortAllocationPredictionParam,
    skipLowPorts: Boolean = AppEnv.PortGuessSkipLowPorts,
    maxGuessPortNum: Int = -1,
): Iterator<Int> {
    return object : Iterator<Int> {
        private var n: Int = 0
        private var i: Int = 1

        override fun hasNext(): Boolean {
            if (maxGuessPortNum == -1) return true
            return i <= maxGuessPortNum
        }

        override fun next(): Int {
            val now = System.currentTimeMillis() - param.testFinishedAt
            val sample = poissonSampling(param.avg, param.timeElapsed)
            n += sample
            val port: Double = param.lastPort.toDouble() + i * param.avg * now + n
            var validPort = Math.floorMod(round(port).toInt(), 65536)
            if (validPort < 1024 && skipLowPorts) {
                validPort += 1024
            }

            i++
            return validPort
        }
    }
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
fun expectedValuePortGuess(
    now: Long,
    param: PortAllocationPredictionParam,
    guessPortNum: Int = 50,
    skipLowPorts: Boolean = AppEnv.PortGuessSkipLowPorts
): List<Int> {
    return (1..guessPortNum).map {
        Math.floorMod((param.lastPort + it * param.avg * (now + param.timeElapsed)).toInt(), 65536).run {
            if (this < 1024 && skipLowPorts) this + 1024 else this
        }
    }
}

fun linearPortGuess(trend: Int, lastPort: Int, guessPortNum: Int = 50, skipLowPorts: Boolean = AppEnv.PortGuessSkipLowPorts): Iterator<Int> {
    return object : Iterator<Int> {
        private var i: Int = 1

        override fun hasNext(): Boolean {
            if (guessPortNum == -1) return true
            return i <= guessPortNum
        }

        override fun next(): Int {
            val validPort = Math.floorMod(lastPort + i * trend, 65536)
            i++
            return validPort
        }
    }
}

suspend fun getPortAllocationPredictionParam(
    echoClient: SocketAddrEchoClient,
    ports: Iterable<Int>,
    srcChannel: DatagramChannel? = null,
    manualReceiver: Channel<DatagramPacket>? = null
): PortAllocationPredictionParam = withContext(
    Dispatchers.IO
) {
    val startTime = System.currentTimeMillis()
    val result = echoClient.requestEcho(
        ports = ports,
        address = InetAddress.getByName(NATClient.brokerClient.brokerHost),
        srcChannel = srcChannel,
        manualReceiver = manualReceiver,
    )
    val endTime = System.currentTimeMillis()
    val timeElapsed = endTime - startTime

    var avg: Double = 0.0
    for (i in 1 until result.size) {
        avg += (result[i].port - result[i - 1].port).toDouble() / (result[i].finishedTime - result[i - 1].finishedTime).toDouble()
    }

    avg /= (result.size - 1).toDouble()
    return@withContext PortAllocationPredictionParam(
        avg,
        timeElapsed,
        result.last().port,
        result.last().finishedTime,
        firstPort = result.first().port
    )
}