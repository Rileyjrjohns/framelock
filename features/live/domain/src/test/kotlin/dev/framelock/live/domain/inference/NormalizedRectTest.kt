package dev.framelock.live.domain.inference

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

class NormalizedRectTest {
    @Test
    fun `accepts coordinates within 0 to 1`() {
        assertDoesNotThrow { NormalizedRect(0f, 0f, 1f, 1f) }
        assertDoesNotThrow { NormalizedRect(0.5f, 0.5f, 0.5f, 0.5f) }
    }

    @Test
    fun `rejects left greater than right`() {
        assertThrows<IllegalArgumentException> { NormalizedRect(0.6f, 0f, 0.5f, 1f) }
    }

    @Test
    fun `rejects top greater than bottom`() {
        assertThrows<IllegalArgumentException> { NormalizedRect(0f, 0.6f, 1f, 0.5f) }
    }

    @ParameterizedTest
    @ValueSource(floats = [-0.01f, 1.01f, Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY])
    fun `rejects out-of-range coordinate`(value: Float) {
        assertThrows<IllegalArgumentException> { NormalizedRect(value, 0f, 1f, 1f) }
        assertThrows<IllegalArgumentException> { NormalizedRect(0f, 0f, 1f, value) }
    }

    @Test
    fun `clamped factory clips model outputs into range`() {
        assertEquals(NormalizedRect(0f, 0.2f, 1f, 1f), NormalizedRect.clamped(-0.3f, 0.2f, 1.4f, 2f))
    }

    @Test
    fun `clamped factory orders swapped edges`() {
        assertEquals(NormalizedRect(0.2f, 0.1f, 0.6f, 0.9f), NormalizedRect.clamped(0.6f, 0.9f, 0.2f, 0.1f))
    }

    @Test
    fun `width and height are derived from edges`() {
        val rect = NormalizedRect(0.25f, 0.5f, 0.75f, 1f)

        assertEquals(0.5f, rect.width)
        assertEquals(0.5f, rect.height)
    }
}
