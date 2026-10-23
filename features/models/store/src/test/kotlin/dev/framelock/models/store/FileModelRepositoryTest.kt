package dev.framelock.models.store

import dev.framelock.kernel.model.ModelVersion
import dev.framelock.models.domain.ModelRejectedException
import dev.framelock.models.domain.ModelRejectionReason
import dev.framelock.models.domain.ModelSource
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.security.KeyPair
import java.util.Base64

class FileModelRepositoryTest {
    @TempDir
    lateinit var filesDir: File

    private val key: KeyPair = TestPackages.newKeyPair()

    private fun repository(vararg trusted: KeyPair = arrayOf(key)) =
        FileModelRepository(filesDir, trusted.map { it.public })

    private fun v(name: String) = ModelVersion(name)

    private suspend fun FileModelRepository.install(
        version: String,
        labels: String? = null,
    ) = import(TestPackages.source(TestPackages.build(key, version = version, labels = labels))).getOrThrow()

    private fun reasonOf(result: Result<*>): ModelRejectionReason =
        (result.exceptionOrNull() as ModelRejectedException).reason

    private fun stagingLeftovers() = File(filesDir, "model-staging").list().orEmpty().toList()

    @Test
    fun `a signed package is imported and readable`() =
        runTest {
            val repository = repository()

            val manifest = repository.install("v1", labels = TestPackages.LABELS_TEXT)

            assertEquals(v("v1"), manifest.version)
            assertEquals(TestPackages.sha256(TestPackages.MODEL_BYTES), manifest.sha256)
            assertEquals(TestPackages.INPUT, manifest.inputSpec)
            assertEquals(TestPackages.OUTPUTS, manifest.outputSpec)
            val signature = Base64.getDecoder().decode(manifest.signature)
            val signed = File(filesDir, "models/v1/manifest.properties").readBytes()
            assertArrayEquals(File(filesDir, "models/v1/manifest.sig").readBytes(), signature)
            assertEquals(true, SignatureVerifier(listOf(key.public)).verify(signed, signature))
            val artifact = repository.artifact(v("v1"))
            assertEquals(manifest, artifact?.manifest)
            assertArrayEquals(TestPackages.MODEL_BYTES, File(artifact!!.location).readBytes())
            assertEquals(listOf(manifest), repository.list())
            assertEquals(listOf<String>(), stagingLeftovers())
        }

    @Test
    fun `a signature from an untrusted key is rejected and nothing is installed`() =
        runTest {
            val stranger = TestPackages.newKeyPair()

            val result = repository().import(TestPackages.source(TestPackages.build(stranger)))

            assertEquals(ModelRejectionReason.BAD_SIGNATURE, reasonOf(result))
            assertEquals(emptyList<Any>(), repository().list())
            assertFalse(File(filesDir, "models/v1").exists())
            assertEquals(listOf<String>(), stagingLeftovers())
        }

    @Test
    fun `any of several trusted keys is accepted`() =
        runTest {
            val other = TestPackages.newKeyPair()

            assertTrue(repository(other, key).import(TestPackages.source(TestPackages.build(key))).isSuccess)
        }

    @Test
    fun `a manifest changed after signing is rejected, even with a matching model hash`() =
        runTest {
            val signed = ManifestFile.serialize(TestPackages.manifest())
            val tampered = ManifestFile.serialize(TestPackages.manifest().copy(input = TestPackages.OUTPUTS[0]))
            val bytes =
                TestPackages.build(key, manifestBytes = tampered, signature = TestPackages.sign(key, signed))

            assertEquals(ModelRejectionReason.BAD_SIGNATURE, reasonOf(repository().import(TestPackages.source(bytes))))
        }

    @Test
    fun `a package without a signature is rejected as BAD_SIGNATURE`() =
        runTest {
            val result = repository().import(TestPackages.source(TestPackages.build(key, signature = null)))

            assertEquals(ModelRejectionReason.BAD_SIGNATURE, reasonOf(result))
        }

    @Test
    fun `a garbage signature is rejected`() =
        runTest {
            val result =
                repository().import(
                    TestPackages.source(TestPackages.build(key, signature = ByteArray(70) { 3 })),
                )

            assertEquals(ModelRejectionReason.BAD_SIGNATURE, reasonOf(result))
        }

    @Test
    fun `a model that does not match its signed hash is rejected`() =
        runTest {
            val altered = TestPackages.MODEL_BYTES.copyOf().also { it[0] = (it[0] + 1).toByte() }
            val bytes = TestPackages.build(key, modelInPackage = altered)

            assertEquals(ModelRejectionReason.BAD_HASH, reasonOf(repository().import(TestPackages.source(bytes))))
            assertFalse(File(filesDir, "models/v1").exists())
        }

    @Test
    fun `labels that do not match their signed hash are rejected`() =
        runTest {
            val bytes = TestPackages.build(key, labels = "person\n", labelsInPackage = "dog\n")

            assertEquals(ModelRejectionReason.BAD_HASH, reasonOf(repository().import(TestPackages.source(bytes))))
        }

    @Test
    fun `labels without a hash in the manifest, or a hash without labels, are malformed`() =
        runTest {
            val noHash = TestPackages.build(key, labelsInPackage = "person\n")
            val noFile = TestPackages.build(key, labels = "person\n", labelsInPackage = null)

            assertEquals(
                ModelRejectionReason.MALFORMED_MANIFEST,
                reasonOf(repository().import(TestPackages.source(noHash))),
            )
            assertEquals(
                ModelRejectionReason.MALFORMED_MANIFEST,
                reasonOf(repository().import(TestPackages.source(noFile))),
            )
        }

    @Test
    fun `a signed but malformed manifest is rejected as MALFORMED_MANIFEST`() =
        runTest {
            val bytes = TestPackages.build(key, manifestBytes = "version=v1\n".toByteArray())

            assertEquals(
                ModelRejectionReason.MALFORMED_MANIFEST,
                reasonOf(repository().import(TestPackages.source(bytes))),
            )
        }

    @Test
    fun `a signed version that names a parent directory is rejected`() =
        runTest {
            val text = String(ManifestFile.serialize(TestPackages.manifest())).replace("version=v1", "version=..")
            val bytes = TestPackages.build(key, manifestBytes = text.toByteArray())

            assertEquals(
                ModelRejectionReason.MALFORMED_MANIFEST,
                reasonOf(repository().import(TestPackages.source(bytes))),
            )
        }

    @Test
    fun `a package without a model or manifest is malformed`() =
        runTest {
            val noModel = TestPackages.build(key, modelInPackage = null)
            val noManifest = TestPackages.zip(listOf(PackageFiles.MODEL to TestPackages.MODEL_BYTES))

            assertEquals(
                ModelRejectionReason.MALFORMED_MANIFEST,
                reasonOf(repository().import(TestPackages.source(noModel))),
            )
            assertEquals(
                ModelRejectionReason.MALFORMED_MANIFEST,
                reasonOf(repository().import(TestPackages.source(noManifest))),
            )
        }

    @Test
    fun `a package that breaks the zip rules is rejected before anything is verified`() =
        runTest {
            val bytes = TestPackages.build(key, extra = listOf("../evil" to ByteArray(4)))

            assertEquals(
                ModelRejectionReason.MALFORMED_MANIFEST,
                reasonOf(repository().import(TestPackages.source(bytes))),
            )
            assertFalse(File(filesDir, "evil").exists())
        }

    @Test
    fun `a source that cannot be read gives an I O failure`() =
        runTest {
            val failing =
                object : ModelSource {
                    override val displayName = "broken"

                    override fun open(): InputStream = throw IOException("unreadable")
                }

            val error = repository().import(failing).exceptionOrNull()

            assertTrue(error is IOException)
            assertEquals(listOf<String>(), stagingLeftovers())
        }

    @Test
    fun `importing the same package again changes nothing`() =
        runTest {
            val repository = repository()
            val first = repository.install("v1")
            val before = File(filesDir, "models/v1/manifest.sig").readBytes()

            val second = repository.import(TestPackages.source(TestPackages.build(key, version = "v1")))

            assertEquals(first, second.getOrThrow().copy(signature = first.signature))
            assertArrayEquals(before, File(filesDir, "models/v1/manifest.sig").readBytes())
            assertEquals(1, repository.list().size)
        }

    @Test
    fun `a version that exists with other content is refused and kept`() =
        runTest {
            val repository = repository()
            repository.install("v1")
            val other = TestPackages.MODEL_BYTES.copyOf().also { it[5] = 99 }

            val result = repository.import(TestPackages.source(TestPackages.build(key, version = "v1", model = other)))

            assertEquals(ModelRejectionReason.BAD_HASH, reasonOf(result))
            assertArrayEquals(TestPackages.MODEL_BYTES, File(filesDir, "models/v1/model.tflite").readBytes())
        }

    @Test
    fun `an incomplete leftover directory is replaced by an import`() =
        runTest {
            File(filesDir, "models/v1").mkdirs()
            File(filesDir, "models/v1/model.tflite").writeBytes(ByteArray(3))
            val repository = repository()
            assertNull(repository.artifact(v("v1")))

            repository.install("v1")

            assertNotNull(repository.artifact(v("v1")))
        }

    @Test
    fun `nothing is active until a model is activated`() =
        runTest {
            val repository = repository()
            repository.install("v1")

            assertNull(repository.active())
            assertNull(repository.previous())
        }

    @Test
    fun `activation points at the version and keeps the one before as previous`() =
        runTest {
            val repository = repository()
            repository.install("v1")
            repository.install("v2")

            assertTrue(repository.activate(v("v1")).isSuccess)
            assertEquals(v("v1"), repository.active()?.manifest?.version)
            assertNull(repository.previous())

            assertTrue(repository.activate(v("v2")).isSuccess)
            assertEquals(v("v2"), repository.active()?.manifest?.version)
            assertEquals(v("v1"), repository.previous()?.manifest?.version)
        }

    @Test
    fun `activating the active version changes nothing`() =
        runTest {
            val repository = repository()
            repository.install("v1")
            repository.install("v2")
            repository.activate(v("v1"))
            repository.activate(v("v2"))

            assertTrue(repository.activate(v("v2")).isSuccess)

            assertEquals(v("v1"), repository.previous()?.manifest?.version)
        }

    @Test
    fun `activating an unknown version is refused and the pointer stays`() =
        runTest {
            val repository = repository()
            repository.install("v1")
            repository.activate(v("v1"))

            val result = repository.activate(v("nope"))

            assertEquals(ModelRejectionReason.UNKNOWN_VERSION, reasonOf(result))
            assertEquals(v("v1"), repository.active()?.manifest?.version)
        }

    @Test
    fun `the pointers survive a new repository and leave no temporary file`() =
        runTest {
            val repository = repository()
            repository.install("v1")
            repository.install("v2")
            repository.activate(v("v1"))
            repository.activate(v("v2"))

            val reopened = repository()

            assertEquals(v("v2"), reopened.active()?.manifest?.version)
            assertEquals(v("v1"), reopened.previous()?.manifest?.version)
            assertEquals(listOf("active", "previous"), File(filesDir, "model-state").list()!!.sorted())
        }

    @Test
    fun `rollback goes back to the previous version and a second one returns`() =
        runTest {
            val repository = repository()
            repository.install("v1")
            repository.install("v2")
            repository.activate(v("v1"))
            repository.activate(v("v2"))

            assertTrue(repository.rollback().isSuccess)
            assertEquals(v("v1"), repository.active()?.manifest?.version)
            assertEquals(v("v2"), repository.previous()?.manifest?.version)

            assertTrue(repository.rollback().isSuccess)
            assertEquals(v("v2"), repository.active()?.manifest?.version)
        }

    @Test
    fun `rollback without a previous version is refused`() =
        runTest {
            val repository = repository()
            repository.install("v1")
            repository.activate(v("v1"))

            assertEquals(ModelRejectionReason.NO_PREVIOUS_VERSION, reasonOf(repository.rollback()))
            assertEquals(v("v1"), repository.active()?.manifest?.version)
        }

    @Test
    fun `a pointer to a version that is gone reads as no model`() =
        runTest {
            val repository = repository()
            repository.install("v1")
            repository.activate(v("v1"))
            File(filesDir, "models/v1").deleteRecursively()

            assertNull(repository.active())
        }

    @Test
    fun `a version named active does not collide with the pointer files`() =
        runTest {
            val repository = repository()
            repository.install("active")
            repository.install("previous")

            assertTrue(repository.activate(v("active")).isSuccess)
            assertTrue(repository.activate(v("previous")).isSuccess)

            assertEquals(v("previous"), repository.active()?.manifest?.version)
            assertEquals(v("active"), repository.previous()?.manifest?.version)
        }

    @Test
    fun `labels come from labels txt when the package has it`() =
        runTest {
            val repository = repository()
            repository.install("v1", labels = TestPackages.LABELS_TEXT)

            assertEquals(listOf("person", "bicycle", "car"), repository.labels(v("v1")))
        }

    @Test
    fun `without labels txt the labels embedded in the model are read, none when it has no metadata`() =
        runTest {
            val repository = repository()
            repository.install("v1")

            assertEquals(emptyList<String>(), repository.labels(v("v1")))
        }

    @Test
    fun `list shows only complete versions, sorted`() =
        runTest {
            val repository = repository()
            repository.install("b")
            repository.install("a")
            File(filesDir, "models/junk").mkdirs()
            File(filesDir, "models/not a version").mkdirs()

            assertEquals(listOf(v("a"), v("b")), repository.list().map { it.version })
        }
}
