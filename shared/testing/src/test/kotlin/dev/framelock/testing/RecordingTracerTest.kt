package dev.framelock.testing

import dev.framelock.kernel.port.TraceSection
import dev.framelock.kernel.port.trace
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

class RecordingTracerTest {
    private val tracer = RecordingTracer()
    private val me = Thread.currentThread().name

    @Test
    fun `records nested sections in order`() {
        tracer.trace(TraceSection.INFERENCE) {
            assertEquals(TraceSection.INFERENCE, tracer.currentSection())
            tracer.trace(TraceSection.POSTPROCESS) {
                assertEquals(TraceSection.POSTPROCESS, tracer.currentSection())
            }
            assertEquals(TraceSection.INFERENCE, tracer.currentSection())
        }

        assertEquals(
            listOf(
                "begin INFERENCE @$me",
                "begin POSTPROCESS @$me",
                "end POSTPROCESS @$me",
                "end INFERENCE @$me",
            ),
            tracer.records,
        )
        assertNull(tracer.currentSection())
        assertDoesNotThrow { tracer.assertBalanced() }
    }

    @Test
    fun `detects unbalanced sections`() {
        tracer.begin(TraceSection.CAPTURE)
        tracer.begin(TraceSection.RENDER)
        tracer.end()

        val open = assertThrows<AssertionError> { tracer.assertBalanced() }
        assertTrue("CAPTURE" in open.message.orEmpty(), open.message)
        assertTrue(me in open.message.orEmpty(), open.message)

        tracer.end()
        assertDoesNotThrow { tracer.assertBalanced() }

        tracer.end()
        val extra = assertThrows<AssertionError> { tracer.assertBalanced() }
        assertTrue("end without begin" in extra.message.orEmpty(), extra.message)
    }

    @Test
    fun `an exception inside a section still ends it`() {
        assertThrows<IllegalStateException> {
            tracer.trace(TraceSection.INFERENCE) { error("boom") }
        }

        assertNull(tracer.currentSection())
        assertDoesNotThrow { tracer.assertBalanced() }
    }

    @Test
    fun `sections are tracked per thread`() {
        val began = CountDownLatch(1)
        val finish = CountDownLatch(1)
        val worker =
            thread(name = "tracer-worker") {
                tracer.begin(TraceSection.PREPROCESS)
                began.countDown()
                finish.await(TIMEOUT_SECONDS, TimeUnit.SECONDS)
                tracer.end()
            }
        assertTrue(began.await(TIMEOUT_SECONDS, TimeUnit.SECONDS))

        assertNull(tracer.currentSection())
        val open = assertThrows<AssertionError> { tracer.assertBalanced() }
        assertTrue("tracer-worker" in open.message.orEmpty(), open.message)
        assertFalse(me in open.message.orEmpty(), open.message)

        finish.countDown()
        worker.join(TimeUnit.SECONDS.toMillis(TIMEOUT_SECONDS))
        assertDoesNotThrow { tracer.assertBalanced() }
        assertEquals(listOf("begin PREPROCESS @tracer-worker", "end PREPROCESS @tracer-worker"), tracer.records)
    }

    @Test
    fun `records carry the thread name without the coroutine suffix`() {
        runBlocking(Dispatchers.Default) {
            withContext(Dispatchers.Default) { tracer.trace(TraceSection.RENDER) {} }
        }

        assertTrue(tracer.records.all { " @" in it && "#" !in it && "coroutine" !in it }, tracer.records.toString())
        assertEquals(2, tracer.records.size)
    }

    private companion object {
        const val TIMEOUT_SECONDS = 10L
    }
}
