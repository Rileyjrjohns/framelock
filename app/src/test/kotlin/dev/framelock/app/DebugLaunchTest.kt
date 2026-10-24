package dev.framelock.app

import dev.framelock.live.inference.litert.BackendPreference
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DebugLaunchTest {
    private fun parse(
        strings: Map<String, String> = emptyMap(),
        flags: Set<String> = emptySet(),
    ) = DebugLaunch.parse({ strings[it] }, { it in flags })

    @Test
    fun `no extras ask for nothing`() {
        val launch = parse()

        assertEquals(DebugLaunch.NONE, launch)
        assertFalse(launch.isActive)
    }

    @Test
    fun `a full measurement launch`() {
        val launch =
            parse(
                strings = mapOf("source" to "file", "backend" to "gpu"),
                flags = setOf("autostart", "hud"),
            )

        assertEquals(DebugLaunch("file", autostart = true, hud = true, backend = BackendPreference.GPU), launch)
        assertTrue(launch.isActive)
    }

    @Test
    fun `source keywords map to option ids whatever their case`() {
        listOf("auto", "back", "front", "external", "file").forEach {
            assertEquals(it, parse(mapOf("source" to it.uppercase())).sourceId)
        }
    }

    @Test
    fun `a UVC device id maps to its option id, with or without the prefix`() {
        assertEquals("uvc:046d:085b", parse(mapOf("source" to "046d:085b")).sourceId)
        assertEquals("uvc:046d:085b", parse(mapOf("source" to "uvc:046d:085b")).sourceId)
    }

    @Test
    fun `a blank source is ignored`() {
        assertEquals(null, parse(mapOf("source" to "  ")).sourceId)
    }

    @Test
    fun `backends parse whatever their case`() {
        assertEquals(BackendPreference.AUTO, parse(mapOf("backend" to "auto")).backend)
        assertEquals(BackendPreference.GPU, parse(mapOf("backend" to "GPU")).backend)
        assertEquals(BackendPreference.CPU, parse(mapOf("backend" to " cpu ")).backend)
    }

    @Test
    fun `an unknown backend is ignored rather than forced`() {
        assertEquals(null, parse(mapOf("backend" to "nnapi")).backend)
    }

    @Test
    fun `flags alone are enough to be active`() {
        assertTrue(parse(flags = setOf("hud")).isActive)
    }
}
