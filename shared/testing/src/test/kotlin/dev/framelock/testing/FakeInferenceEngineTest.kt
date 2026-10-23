package dev.framelock.testing

import dev.framelock.kernel.model.ModelVersion
import dev.framelock.live.domain.frame.FrameId
import dev.framelock.live.domain.frame.SessionId
import dev.framelock.live.domain.inference.InferenceResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

class FakeInferenceEngineTest {
    private val pool = FakeImageBufferPool()
    private val executor = Executors.newSingleThreadExecutor { Thread(it, INFERENCE_THREAD) }
    private val dispatcher = executor.asCoroutineDispatcher()

    @AfterEach
    fun tearDown() {
        dispatcher.close()
    }

    @Test
    fun `records the calling thread`() {
        val engine = FakeInferenceEngine(dispatcher)

        runBlocking {
            withContext(engine.dispatcher) {
                engine.load(TestModels.artifact("v1"))
                engine.infer(frame(0))
            }
        }

        assertEquals(setOf(INFERENCE_THREAD), engine.loadThreads)
        assertEquals(setOf(INFERENCE_THREAD), engine.inferThreads)

        engine.infer(frame(1))
        assertEquals(setOf(INFERENCE_THREAD, Thread.currentThread().name), engine.inferThreads)
        assertEquals(setOf(INFERENCE_THREAD), engine.loadThreads)
    }

    @Test
    fun `infer returns an empty result of the loaded model by default`() {
        val engine = FakeInferenceEngine(Dispatchers.Unconfined, version = ModelVersion("fallback"))
        val before = engine.infer(frame(4, session = 2))

        runBlocking { engine.load(TestModels.artifact("v7")) }
        val after = engine.infer(frame(5, session = 2))

        assertEquals(result(FrameId(4), "fallback"), before)
        assertEquals(result(FrameId(5), "v7"), after)
    }

    @Test
    fun `resultFor overrides the result`() {
        val engine = FakeInferenceEngine(Dispatchers.Unconfined)
        engine.resultFor = { InferenceResult(it.id, it.sessionId, ModelVersion("custom"), emptyList(), 1L) }

        assertEquals(ModelVersion("custom"), engine.infer(frame(0)).modelVersion)
        assertEquals(1L, engine.infer(frame(1)).inferenceNanos)
    }

    @Test
    fun `inferredFrames lists session and id in call order`() {
        val engine = FakeInferenceEngine(Dispatchers.Unconfined)

        engine.infer(frame(3, session = 1))
        engine.infer(frame(0, session = 2))
        engine.infer(frame(1, session = 2))

        assertEquals(listOf(1L to 3L, 2L to 0L, 2L to 1L), engine.inferredFrames)
    }

    @Test
    fun `onInfer hook runs during infer`() {
        val engine = FakeInferenceEngine(Dispatchers.Unconfined)
        val order = mutableListOf<String>()
        engine.onInfer = { order += "hook ${it.id.value}" }
        engine.resultFor = { frame ->
            order += "result ${frame.id.value}"
            InferenceResult(frame.id, frame.sessionId, ModelVersion("v1"), emptyList(), 0)
        }

        engine.infer(frame(8))

        assertEquals(listOf("hook 8", "result 8"), order)
    }

    @Test
    fun `scripted exception and load failure`() {
        val engine = FakeInferenceEngine(Dispatchers.Unconfined)
        engine.failInferWhen = { it.id.value == 1L }

        engine.infer(frame(0))
        assertThrows<IllegalStateException> { engine.infer(frame(1)) }
        engine.infer(frame(2))
        assertEquals(listOf(1L to 0L, 1L to 1L, 1L to 2L), engine.inferredFrames)

        runBlocking { engine.load(TestModels.artifact("v1")) }
        val failure = IllegalArgumentException("bad model")
        engine.failNextLoad(failure)
        val thrown = assertThrows<IllegalArgumentException> { runBlocking { engine.load(TestModels.artifact("v2")) } }
        assertSame(failure, thrown)
        assertEquals(ModelVersion("v1"), engine.loadedVersion)
        assertEquals(listOf("v1"), engine.loadedModels.map { it.manifest.version.value })

        runBlocking { engine.load(TestModels.artifact("v2")) }
        assertEquals(ModelVersion("v2"), engine.loadedVersion)
        assertEquals(listOf("v1", "v2"), engine.loadedModels.map { it.manifest.version.value })
    }

    @Test
    fun `default load failure is an illegal state`() {
        val engine = FakeInferenceEngine(Dispatchers.Unconfined)
        engine.failNextLoad()

        val error = assertThrows<IllegalStateException> { runBlocking { engine.load(TestModels.artifact("v1")) } }

        assertEquals("load failed", error.message)
        assertNull(engine.loadedVersion)
    }

    @Test
    fun `concurrency detector reports overlap`() {
        val engine = FakeInferenceEngine(Dispatchers.Unconfined)
        engine.infer(frame(0))
        engine.infer(frame(1))
        assertEquals(1, engine.maxConcurrentInfers)

        val release = engine.blockNextInfer()
        val entered = CountDownLatch(1)
        engine.onInfer = { if (it.id.value == 2L) entered.countDown() }
        val blocked = thread(name = "blocked-infer") { engine.infer(frame(2)) }
        assertTrue(entered.await(TIMEOUT_SECONDS, TimeUnit.SECONDS))

        // The second call is not blocked: only the next infer waits on the latch.
        engine.infer(frame(3))
        release.countDown()
        blocked.join(TimeUnit.SECONDS.toMillis(TIMEOUT_SECONDS))

        assertFalse(blocked.isAlive)
        assertEquals(2, engine.maxConcurrentInfers)
        assertEquals(setOf("blocked-infer", Thread.currentThread().name), engine.inferThreads)
    }

    @Test
    fun `blocked infer completes once the latch is released`() {
        val engine = FakeInferenceEngine(Dispatchers.Unconfined)
        val release = engine.blockNextInfer()
        var result: InferenceResult? = null
        val worker = thread { result = engine.infer(frame(0)) }

        worker.join(BLOCKED_PROBE_MILLIS)
        assertTrue(worker.isAlive)
        assertNull(result)

        release.countDown()
        worker.join(TimeUnit.SECONDS.toMillis(TIMEOUT_SECONDS))
        assertEquals(FrameId(0), result?.frameId)
    }

    @Test
    fun `records close and its thread`() {
        val engine = FakeInferenceEngine(dispatcher)
        assertFalse(engine.isClosed)
        assertNull(engine.closeThread)

        runBlocking { withContext(engine.dispatcher) { engine.close() } }

        assertTrue(engine.isClosed)
        assertEquals(INFERENCE_THREAD, engine.closeThread)
        assertEquals(1, engine.closeCount)
    }

    @Test
    fun `using a closed engine fails`() {
        val engine = FakeInferenceEngine(Dispatchers.Unconfined)
        engine.close()

        assertThrows<IllegalStateException> { engine.infer(frame(0)) }
        assertThrows<IllegalStateException> { runBlocking { engine.load(TestModels.artifact("v1")) } }
        assertTrue(engine.inferredFrames.isEmpty())
    }

    private fun frame(
        id: Long,
        session: Long = 1,
    ) = TestFrames.frame(pool, id, session)

    private fun result(
        id: FrameId,
        model: String,
    ) = InferenceResult(id, SessionId(2), ModelVersion(model), emptyList(), 5_000_000L)

    private companion object {
        const val INFERENCE_THREAD = "fake-inference"
        const val TIMEOUT_SECONDS = 10L
        const val BLOCKED_PROBE_MILLIS = 100L
    }
}
