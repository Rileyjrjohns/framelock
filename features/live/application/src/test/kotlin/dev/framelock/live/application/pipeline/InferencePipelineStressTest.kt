package dev.framelock.live.application.pipeline

import dev.framelock.live.application.metrics.MetricsAggregator
import dev.framelock.live.domain.frame.DisconnectReason
import dev.framelock.live.domain.frame.SessionId
import dev.framelock.live.domain.pipeline.PipelineState
import dev.framelock.live.domain.policy.ReconnectConfig
import dev.framelock.live.domain.policy.ReconnectPolicy
import dev.framelock.live.domain.system.MemoryPressure
import dev.framelock.live.domain.system.ThermalLevel
import dev.framelock.testing.FakeFrameSource
import dev.framelock.testing.FakeImageBufferPool
import dev.framelock.testing.FakeInferenceEngine
import dev.framelock.testing.FakeMemoryPressureMonitor
import dev.framelock.testing.FakeThermalMonitor
import dev.framelock.testing.ManualClock
import dev.framelock.testing.RecordingTracer
import dev.framelock.testing.TestFrames
import dev.framelock.testing.TestModels
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import kotlin.random.Random
import kotlin.time.Duration.Companion.milliseconds

/**
 * Real threads, random schedule: producers on three threads, random disconnections, sessions, thermal and memory
 * changes, engine failures and stop/start. Checks the invariants that must hold whatever the interleaving.
 */
class InferencePipelineStressTest {
    @Test
    fun `random schedules keep every buffer closed exactly once and every result with its own frame`() {
        repeat(ROUNDS) { round -> runRound(round) }
    }

    // A scenario script: reads best top to bottom.
    @Suppress("LongMethod", "CyclomaticComplexMethod")
    private fun runRound(round: Int) =
        runBlocking {
            val inferenceThread = Executors.newSingleThreadExecutor { Thread(it, "inference") }.asCoroutineDispatcher()
            val uncaught = CopyOnWriteArrayList<Throwable>()
            val scope =
                CoroutineScope(
                    SupervisorJob() + Dispatchers.Default + CoroutineExceptionHandler { _, e -> uncaught += e },
                )
            val pool = FakeImageBufferPool(capacity = 1_000_000)
            val source = FakeFrameSource()
            val engine = FakeInferenceEngine(inferenceThread)
            val thermal = FakeThermalMonitor()
            val memory = FakeMemoryPressureMonitor()
            val rnd = Random(round)
            engine.onInfer = { if (rnd.nextInt(5) == 0) Thread.sleep(rnd.nextLong(0, 2)) }
            engine.failInferWhen = { it.id.value % 17 == 0L }
            val pipeline =
                InferencePipeline(
                    source,
                    engine,
                    thermal,
                    memory,
                    ManualClock(),
                    RecordingTracer(),
                    MetricsAggregator(ManualClock(), {}),
                    {},
                    ReconnectPolicy(ReconnectConfig(1.milliseconds, 5.milliseconds, 0.0), Random(1)),
                    scope,
                )
            val violations = CopyOnWriteArrayList<String>()
            scope.launch(Dispatchers.Unconfined) {
                pipeline.state.collect {
                    val live =
                        it.state is PipelineState.Running || it.state is PipelineState.Degraded ||
                            it.state is PipelineState.Paused
                    if (!live && it.annotated != null) violations += "annotated with ${it.state}"
                    val a = it.annotated
                    if (a != null &&
                        (a.result.frameId != a.frameId || a.result.sessionId != a.sessionId)
                    ) {
                        violations +=
                            "mismatch $a"
                    }
                }
            }
            val session = AtomicLong(0)
            val nextId = AtomicLong(0)
            val stopFlag = AtomicBoolean(false)
            val producers =
                List(3) {
                    Thread {
                        while (!stopFlag.get()) {
                            val s = session.get()
                            if (s >= 1) source.emit(TestFrames.frame(pool, nextId.getAndIncrement(), s))
                            Thread.sleep(0, 200_000)
                        }
                    }.also { it.start() }
                }
            val model = TestModels.artifact("m")
            val end = System.nanoTime() + ROUND_NANOS
            var lastStopStart = System.nanoTime()
            pipeline.start(model)
            while (System.nanoTime() < end) {
                when (rnd.nextInt(10)) {
                    0 -> {
                        source.disconnect(DisconnectReason.STREAM_ERROR)
                    }

                    1, 2 -> {
                        val s = session.incrementAndGet()
                        nextId.set(0)
                        source.connect(SessionId(s))
                    }

                    3 -> {
                        thermal.set(ThermalLevel.entries[rnd.nextInt(ThermalLevel.entries.size)])
                    }

                    4 -> {
                        memory.set(MemoryPressure.entries[rnd.nextInt(MemoryPressure.entries.size)])
                    }

                    5 -> {
                        if (System.nanoTime() - lastStopStart > 200_000_000L) {
                            pipeline.stop()
                            pipeline.start(model)
                            lastStopStart = System.nanoTime()
                        }
                    }
                }
                delay(rnd.nextLong(1, 4))
            }
            stopFlag.set(true)
            producers.forEach { it.join() }
            pipeline.stop()
            delay(100)
            val outstanding = pool.outstandingLabels
            scope.cancel()
            inferenceThread.close()
            assertEquals(emptyList<String>(), violations.toList())
            assertEquals(0, pool.doubleCloseCount, "double close")
            assertTrue(outstanding.isEmpty(), "leaked: $outstanding")
            assertTrue(uncaught.isEmpty(), "uncaught $uncaught")
            assertEquals(PipelineState.Idle, pipeline.state.value.state)
        }

    private companion object {
        const val ROUNDS = 3
        const val ROUND_NANOS = 1_000_000_000L
    }
}
