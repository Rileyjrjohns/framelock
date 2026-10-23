package dev.framelock.models.store

import dev.framelock.models.domain.ModelRejectedException
import dev.framelock.models.domain.ModelRejectionReason
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.ByteArrayInputStream
import java.io.File

class PackageExtractorTest {
    @TempDir
    lateinit var dir: File

    private fun extract(bytes: ByteArray) = PackageExtractor.extract(ByteArrayInputStream(bytes), dir)

    private fun assertMalformed(bytes: ByteArray) {
        val error = assertThrows(ModelRejectedException::class.java) { extract(bytes) }
        assertEquals(ModelRejectionReason.MALFORMED_MANIFEST, error.reason)
    }

    private val small = ByteArray(8)

    @Test
    fun `writes the four known files`() {
        val names =
            extract(
                TestPackages.zip(
                    listOf(
                        PackageFiles.MODEL to small,
                        PackageFiles.MANIFEST to small,
                        PackageFiles.SIGNATURE to small,
                        PackageFiles.LABELS to small,
                    ),
                ),
            )

        assertEquals(setOf("model.tflite", "manifest.properties", "manifest.sig", "labels.txt"), names)
        assertEquals(8, File(dir, "model.tflite").length())
    }

    @Test
    fun `an unknown name is refused`() {
        assertMalformed(TestPackages.zip(listOf("extra.bin" to small)))
    }

    @Test
    fun `names with path separators or parent references are refused and write nothing outside`() {
        listOf("../escape.txt", "sub/model.tflite", "/abs.txt", "..\\win.txt", "dir/").forEach { name ->
            assertMalformed(TestPackages.zip(listOf(name to small)))
        }
        assertEquals(emptyList<String>(), dir.parentFile!!.list { _, n -> n == "escape.txt" }?.toList())
        assertEquals(0, dir.list()?.size)
    }

    @Test
    fun `an entry given twice is refused`() {
        // ZipOutputStream refuses duplicates, so rename the second entry in the bytes (same length, same CRC).
        val zip = TestPackages.zip(listOf(PackageFiles.MODEL to small, "model.tflitX" to small))
        val renamed = String(zip, Charsets.ISO_8859_1).replace("model.tflitX", PackageFiles.MODEL)

        assertMalformed(renamed.toByteArray(Charsets.ISO_8859_1))
    }

    @Test
    fun `an entry over its size limit is refused, counted on the bytes read`() {
        val tooBig = ByteArray((PackageFiles.LIMITS.getValue(PackageFiles.MANIFEST) + 1).toInt())

        assertMalformed(TestPackages.zip(listOf(PackageFiles.MANIFEST to tooBig)))
    }

    @Test
    fun `an entry at its size limit is accepted`() {
        val atLimit = ByteArray(PackageFiles.LIMITS.getValue(PackageFiles.MANIFEST).toInt())

        extract(TestPackages.zip(listOf(PackageFiles.MANIFEST to atLimit)))

        assertEquals(atLimit.size.toLong(), File(dir, PackageFiles.MANIFEST).length())
    }

    @Test
    fun `bytes that are not a zip give no entry`() {
        assertEquals(emptySet<String>(), extract(ByteArray(64) { 1 }))
    }

    @Test
    fun `a truncated zip is refused`() {
        val zip = TestPackages.zip(listOf(PackageFiles.MODEL to ByteArray(4_096) { it.toByte() }))

        assertMalformed(zip.copyOf(zip.size / 2))
    }
}
