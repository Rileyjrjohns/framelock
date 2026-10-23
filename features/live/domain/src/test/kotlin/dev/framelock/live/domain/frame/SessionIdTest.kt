package dev.framelock.live.domain.frame

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class SessionIdTest {
    @Test
    fun `first session id is 1`() {
        assertEquals(1L, SessionId.FIRST.value)
    }

    @Test
    fun `next increments by exactly one`() {
        assertEquals(SessionId(8), SessionId(7).next())
    }

    @Test
    fun `session ids compare by value`() {
        assertTrue(SessionId(1) < SessionId(2))
        assertEquals(SessionId(2), SessionId(2))
    }

    @Test
    fun `session id below 1 is rejected`() {
        assertThrows<IllegalArgumentException> { SessionId(0) }
    }

    @Test
    fun `SessionIdSequence starts at the first session id and increases by one`() {
        val sequence = SessionIdSequence()

        val ids = List(3) { sequence.next() }

        assertEquals(listOf(SessionId.FIRST, SessionId(2), SessionId(3)), ids)
    }

    @Test
    fun `sources sharing a SessionIdSequence never reuse an id`() {
        val sequence = SessionIdSequence()
        val first = sequence.next()
        val other = sequence.next()

        assertTrue(other > first)
    }

    @Test
    fun `SessionIdSequence hands out distinct ids to concurrent callers`() {
        val sequence = SessionIdSequence()
        val ids = java.util.concurrent.ConcurrentLinkedQueue<Long>()
        val threads = List(8) { Thread { repeat(500) { ids += sequence.next().value } } }

        threads.forEach { it.start() }
        threads.forEach { it.join() }

        assertEquals((1L..4000L).toList(), ids.sorted())
    }
}
