package dev.framelock.core.domain.frame

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
}
