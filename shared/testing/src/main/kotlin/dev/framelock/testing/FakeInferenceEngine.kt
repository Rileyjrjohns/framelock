package dev.framelock.testing

import dev.framelock.kernel.model.ModelArtifact
import dev.framelock.kernel.model.ModelVersion
import dev.framelock.live.domain.frame.Frame
import dev.framelock.live.domain.inference.InferenceResult
import dev.framelock.live.domain.port.InferenceEngine
import kotlinx.coroutines.CoroutineDispatcher
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CopyOnWriteArraySet
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

/**
 * An [InferenceEngine] that records how it is called and fails or blocks on demand. It never reads pixels and never
 * closes the frame's buffer (the caller owns it).
 *
 * The test supplies the [dispatcher]: a test dispatcher for virtual time, or a single-thread executor to check thread
 * confinement (ADR-002) through [loadThreads], [inferThreads] and [closeThread]. Using the engine after [close]
 * throws, to catch a pipeline that outlives its engine.
 *
 * Thread-safe: the recordings can be read from the test thread while the inference thread writes them.
 */
class FakeInferenceEngine(
    override val dispatcher: CoroutineDispatcher,
    private val version: ModelVersion = ModelVersion("test-1"),
) : InferenceEngine {
    private val loaded = CopyOnWriteArrayList<ModelArtifact>()
    private val inferred = CopyOnWriteArrayList<Pair<Long, Long>>()
    private val inferThreadNames = CopyOnWriteArraySet<String>()
    private val loadThreadNames = CopyOnWriteArraySet<String>()
    private val closeThreadName = AtomicReference<String?>(null)
    private val closeCalls = AtomicInteger()
    private val closed = AtomicBoolean(false)
    private val loadFailure = AtomicReference<Exception?>(null)
    private val infersBlocker = AtomicReference<CountDownLatch?>(null)
    private val running = AtomicInteger()
    private val maxRunning = AtomicInteger()

    /** Runs at the start of [infer], before it can block or fail. Tests push source events from here. */
    @Volatile
    var onInfer: (Frame) -> Unit = {}

    /** The result of [infer]: by default no detection, [DEFAULT_INFERENCE_NANOS], and the loaded model's version. */
    @Volatile
    var resultFor: (Frame) -> InferenceResult = ::defaultResult

    /** When true for a frame, [infer] throws an [IllegalStateException]. The call is still recorded. */
    @Volatile
    var failInferWhen: (Frame) -> Boolean = { false }

    /** Models loaded successfully, in order. A failed load is not listed. */
    val loadedModels: List<ModelArtifact> get() = loaded.toList()

    val loadedVersion: ModelVersion? get() = loaded.lastOrNull()?.manifest?.version

    /** `(session, frame id)` of every call of [infer], in call order. */
    val inferredFrames: List<Pair<Long, Long>> get() = inferred.toList()

    /** Names of the threads that called [infer]. */
    val inferThreads: Set<String> get() = inferThreadNames.toSet()

    /** Names of the threads that called [load]. */
    val loadThreads: Set<String> get() = loadThreadNames.toSet()

    /** Name of the thread of the first [close], or null if the engine is not closed. */
    val closeThread: String? get() = closeThreadName.get()

    val closeCount: Int get() = closeCalls.get()

    val isClosed: Boolean get() = closed.get()

    /** The highest number of [infer] calls that were running at the same time. Above 1 breaks ADR-002. */
    val maxConcurrentInfers: Int get() = maxRunning.get()

    /** Makes the next [load] throw [error]; the previous model stays loaded. */
    fun failNextLoad(error: Exception = IllegalStateException("load failed")) {
        loadFailure.set(error)
    }

    /**
     * Makes the next [infer] wait until the returned latch is released, after [onInfer] has run. Meant for tests on
     * a real thread: it blocks the calling thread, for at most [BLOCK_TIMEOUT_SECONDS].
     */
    fun blockNextInfer(): CountDownLatch = CountDownLatch(1).also { infersBlocker.set(it) }

    override suspend fun load(model: ModelArtifact) {
        checkNotClosed("load")
        loadThreadNames += currentThreadName()
        loadFailure.getAndSet(null)?.let { throw it }
        loaded += model
    }

    override fun infer(frame: Frame): InferenceResult {
        checkNotClosed("infer")
        inferThreadNames += currentThreadName()
        inferred += frame.sessionId.value to frame.id.value
        maxRunning.accumulateAndGet(running.incrementAndGet()) { a, b -> maxOf(a, b) }
        try {
            onInfer(frame)
            infersBlocker.getAndSet(null)?.let(::awaitRelease)
            check(!failInferWhen(frame)) { "infer failed for $frame" }
            return resultFor(frame)
        } finally {
            running.decrementAndGet()
        }
    }

    override fun close() {
        closeThreadName.compareAndSet(null, currentThreadName())
        closeCalls.incrementAndGet()
        closed.set(true)
    }

    private fun defaultResult(frame: Frame) =
        InferenceResult(frame.id, frame.sessionId, loadedVersion ?: version, emptyList(), DEFAULT_INFERENCE_NANOS)

    private fun checkNotClosed(operation: String) {
        check(!closed.get()) { "$operation() called on a closed engine" }
    }

    private fun awaitRelease(latch: CountDownLatch) {
        if (!latch.await(BLOCK_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
            throw AssertionError("infer() still blocked after $BLOCK_TIMEOUT_SECONDS s: was the latch released?")
        }
    }

    private companion object {
        const val DEFAULT_INFERENCE_NANOS = 5_000_000L
        const val BLOCK_TIMEOUT_SECONDS = 30L
    }
}
