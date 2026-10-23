package dev.framelock.models.store

import dev.framelock.kernel.model.TensorDataType
import dev.framelock.kernel.model.TensorSpec
import dev.framelock.models.domain.ModelRejectedException
import dev.framelock.models.domain.ModelRejectionReason
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class ManifestFileTest {
    private val sha = "a".repeat(64)

    private fun text(
        version: String = "v1",
        input: String = "1x320x320x3:UINT8",
        outputs: String = "1x25x4:FLOAT32;1:FLOAT32",
        extra: String = "",
    ) = "version=$version\nsha256=$sha\ninput=$input\noutputs=$outputs\n$extra".toByteArray()

    private fun assertMalformed(bytes: ByteArray) {
        val error = assertThrows(ModelRejectedException::class.java) { ManifestFile.parse(bytes) }
        assertEquals(ModelRejectionReason.MALFORMED_MANIFEST, error.reason)
    }

    @Test
    fun `parses version, hash and tensor specs`() {
        val parsed = ManifestFile.parse(text())

        assertEquals("v1", parsed.version.value)
        assertEquals(sha, parsed.sha256)
        assertEquals(TensorSpec(listOf(1, 320, 320, 3), TensorDataType.UINT8), parsed.input)
        assertEquals(
            listOf(TensorSpec(listOf(1, 25, 4), TensorDataType.FLOAT32), TensorSpec(listOf(1), TensorDataType.FLOAT32)),
            parsed.outputs,
        )
        assertEquals(null, parsed.labelsSha256)
    }

    @Test
    fun `serialize then parse gives the same manifest, with labels`() {
        val manifest = TestPackages.manifest(labels = TestPackages.LABELS_TEXT)

        assertEquals(manifest, ManifestFile.parse(ManifestFile.serialize(manifest)))
    }

    @Test
    fun `reads the labels hash when present and ignores unknown keys`() {
        val parsed = ManifestFile.parse(text(extra = "labels.sha256=$sha\nfuture=1\n"))

        assertEquals(sha, parsed.labelsSha256)
    }

    @Test
    fun `a missing or blank field is malformed`() {
        listOf("version", "sha256", "input", "outputs").forEach { key ->
            assertMalformed(
                text()
                    .decodeToString()
                    .lines()
                    .filterNot { it.startsWith("$key=") }
                    .joinToString("\n")
                    .toByteArray(),
            )
        }
        assertMalformed(text(version = ""))
    }

    @Test
    fun `an invalid version, hash or spec is malformed`() {
        assertMalformed(text(version = ".."))
        assertMalformed(text(version = "a/b"))
        assertMalformed("version=v1\nsha256=ABC\ninput=1:UINT8\noutputs=1:UINT8\n".toByteArray())
        assertMalformed(text(input = "1x320"))
        assertMalformed(text(input = "1x0x3:UINT8"))
        assertMalformed(text(input = "1xax3:UINT8"))
        assertMalformed(text(input = "1x3:FLOAT64"))
        assertMalformed(text(outputs = "1:FLOAT32;"))
        assertMalformed(text(extra = "labels.sha256=zz\n"))
    }

    @Test
    fun `tensor specs round trip`() {
        val spec = TensorSpec(listOf(1, 25, 4), TensorDataType.INT8)

        assertEquals("1x25x4:INT8", TensorSpecCodec.encode(spec))
        assertEquals(spec, TensorSpecCodec.decode("1x25x4:INT8"))
    }
}
