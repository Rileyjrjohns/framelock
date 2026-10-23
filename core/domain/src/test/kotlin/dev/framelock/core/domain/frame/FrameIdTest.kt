package dev.framelock.core.domain.frame

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class FrameIdTest {
    @Test
    fun `frame ids compare by value`() {
        assertEquals(FrameId(3), FrameId(3))
        assertTrue(FrameId(2) < FrameId(3))
    }

    @Test
    fun `negative frame id is rejected`() {
        assertThrows<IllegalArgumentException> { FrameId(-1) }
    }

    @Test
    fun `isAfter is false for equal ids`() {
        assertFalse(FrameId(5).isAfter(FrameId(5)))
        assertTrue(FrameId(6).isAfter(FrameId(5)))
    }

    @Test
    fun `FrameIdSequence yields strictly increasing ids`() {
        val sequence = FrameIdSequence()

        val ids = List(100) { sequence.next() }

        assertEquals((0L until 100L).toList(), ids.map { it.value })
    }

    @Test
    fun `FrameIdSequence restarts for a new session`() {
        val sequence = FrameIdSequence()
        repeat(3) { sequence.next() }

        sequence.restart()

        assertEquals(FrameId(0), sequence.next())
    }
}
