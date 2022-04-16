package com.kenvix.web.server

import com.kenvix.natpoked.AppConstants
import com.kenvix.web.utils.ConsoleCommands
import com.kenvix.web.utils.stringPrintStream
import io.ktor.application.*
import io.ktor.util.*
import io.ktor.util.pipeline.*
import kotlinx.coroutines.*
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

object KtorVisitCounter : ApplicationFeature<Application, Unit, Unit>, AutoCloseable {
    override val key: AttributeKey<Unit>
        get() = AttributeKey("Visit Counter")

    private val job = Job() + CoroutineName("Visit Counter")
    private val coroutineScope = CoroutineScope(job)

    val totalVisitCounter = AtomicInteger()
    val visitPerMinute = ConcurrentHashMap<Int, AtomicInteger>().also {
        for (i in 0 until 60)
            it[i] = AtomicInteger(0)
    }
    val maxVisitPerMinute = AtomicInteger()

    var installed = false
        private set

    override fun install(pipeline: Application, configure: Unit.() -> Unit) {
        val counterPhase = PipelinePhase("VisitCounter")
        pipeline.insertPhaseAfter(ApplicationCallPipeline.Monitoring, counterPhase)

        ConsoleCommands["qps"] = {
            print(getQpsString())
        }

        pipeline.intercept(counterPhase) {
            coroutineScope.launch(Dispatchers.IO) {
                totalVisitCounter.incrementAndGet()
                visitPerMinute[Calendar.getInstance().get(Calendar.MINUTE)]!!.incrementAndGet().run {
                    if (this > maxVisitPerMinute.get())
                        maxVisitPerMinute.set(this)
                }
            }
        }

        installed = true
    }

    fun getQpsString(): String {
        if (!installed)
            return "QPS Counter is disabled"

        return stringPrintStream {
            val now = Calendar.getInstance().get(Calendar.MINUTE)
            val format = "%6d %7d %7.2f\n"

            it.println("Visit QPS Stat [$now]")
            it.printf("%6s %7s %7s\n", "Minute", "Count", "QPS")

            it.printf("%6s %7d %7.2f\n", "Total", totalVisitCounter.get(), totalVisitCounter.get() * 1000f /
                    (System.currentTimeMillis() - AppConstants.startedAt))
            it.printf("%6s %7d %7.2f\n", "Max", maxVisitPerMinute.get(), maxVisitPerMinute.get() / 60f)
            it.printf(format, 0, visitPerMinute[now]!!.get(), visitPerMinute[now]!!.get() / 60f)

            getRangeCount(now, 1).run { it.printf(format, 1, this, this / (60f * 1)) }
            getRangeCount(now, 5).run { it.printf(format, 5, this, this / (60f * 5)) }
            getRangeCount(now, 10).run { it.printf(format, 10, this, this / (60f * 10)) }
            getRangeCount(now, 25).run { it.printf(format, 25, this, this / (60f * 25)) }
            getRangeCount(now, 45).run { it.printf(format, 45, this, this / (60f * 45)) }
            getRangeCount(now, 59).run { it.printf(format, 59, this, this / (60f * 59)) }
        }
    }

    fun getRangeCount(now: Int, rangeNum: Int): Int {
        if (!installed)
            return 0

        val begin = ((now - rangeNum) % 60).run { if (this < 0) this + 60 else this }
        var total = 0

        for (i in 0 until rangeNum) {
            total += visitPerMinute[(begin + i) % 60]!!.get()
        }

        return total
    }

    override fun close() {
        job.cancel()
    }
}