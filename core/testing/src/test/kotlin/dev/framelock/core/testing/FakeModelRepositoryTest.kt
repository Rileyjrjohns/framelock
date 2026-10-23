package dev.framelock.core.testing

import dev.framelock.core.domain.model.ModelManifest
import dev.framelock.core.domain.model.ModelSource
import dev.framelock.core.domain.model.ModelVersion
import dev.framelock.core.testing.FakeModelRepository.Operation
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.io.ByteArrayInputStream
import java.io.InputStream

class FakeModelRepositoryTest {
    private val v1 = TestModels.artifact("v1")
    private val v2 = TestModels.artifact("v2")
    private val v3 = TestModels.artifact("v3")
    private val repository = FakeModelRepository(listOf(v1, v2, v3), active = ModelVersion("v1"))

    @Test
    fun `starts with the initial models and active version`() {
        assertEquals(v1, repository.active())
        assertNull(repository.previous())
        assertEquals(listOf(v1, v2, v3).map { it.manifest }, repository.list())
        assertEquals(v2, repository.artifact(ModelVersion("v2")))
        assertNull(repository.artifact(ModelVersion("v9")))
        assertNull(FakeModelRepository().active())
    }

    @Test
    fun `an active version must be among the initial models`() {
        assertThrows<IllegalArgumentException> { FakeModelRepository(listOf(v1), active = ModelVersion("v2")) }
    }

    @Test
    fun `activate switches active`() =
        runTest {
            val result = repository.activate(ModelVersion("v2"))

            assertTrue(result.isSuccess)
            assertEquals(v2, repository.active())
            assertEquals(v1, repository.previous())
            assertEquals(listOf(ModelVersion("v2")), repository.activateCalls)
        }

    @Test
    fun `activating the first model has no previous`() =
        runTest {
            val empty = FakeModelRepository(listOf(v1))

            assertTrue(empty.activate(ModelVersion("v1")).isSuccess)

            assertEquals(v1, empty.active())
            assertNull(empty.previous())
        }

    @Test
    fun `activating the active version changes nothing`() =
        runTest {
            assertTrue(repository.activate(ModelVersion("v1")).isSuccess)

            assertEquals(v1, repository.active())
            assertNull(repository.previous())
            assertTrue(repository.rollback().isFailure)
        }

    @Test
    fun `activating an unknown version fails and leaves state unchanged`() =
        runTest {
            val result = repository.activate(ModelVersion("v9"))

            assertTrue(result.exceptionOrNull() is IllegalStateException)
            assertEquals(v1, repository.active())
            assertEquals(listOf(ModelVersion("v9")), repository.activateCalls)
        }

    @Test
    fun `rollback restores previous`() =
        runTest {
            repository.activate(ModelVersion("v2"))
            repository.activate(ModelVersion("v3"))
            assertEquals(v2, repository.previous())

            assertTrue(repository.rollback().isSuccess)
            assertEquals(v2, repository.active())
            assertEquals(v1, repository.previous())

            assertTrue(repository.rollback().isSuccess)
            assertEquals(v1, repository.active())
            assertNull(repository.previous())
            assertEquals(2, repository.rollbackCalls)
        }

    @Test
    fun `rollback without a previous version fails`() =
        runTest {
            val result = repository.rollback()

            val error = result.exceptionOrNull()
            assertTrue(error is IllegalStateException)
            assertTrue("NO_PREVIOUS_VERSION" in error?.message.orEmpty(), error?.message)
            assertEquals(v1, repository.active())
        }

    @Test
    fun `injected failure leaves state unchanged`() =
        runTest {
            val failure = IllegalArgumentException("disk full")
            repository.activate(ModelVersion("v2"))
            val before = snapshot()

            repository.failNext(Operation.ACTIVATE, failure)
            assertSame(failure, repository.activate(ModelVersion("v3")).exceptionOrNull())
            assertEquals(before, snapshot())

            repository.failNext(Operation.ROLLBACK, failure)
            assertSame(failure, repository.rollback().exceptionOrNull())
            assertEquals(before, snapshot())

            repository.failNext(Operation.IMPORT, failure)
            assertSame(failure, repository.import(source("v4")).exceptionOrNull())
            assertEquals(before, snapshot())

            // Each injected failure is used once.
            assertTrue(repository.activate(ModelVersion("v3")).isSuccess)
            assertTrue(repository.rollback().isSuccess)
            assertTrue(repository.import(source("v4")).isSuccess)
        }

    @Test
    fun `failures are queued per operation`() =
        runTest {
            val first = IllegalStateException("first")
            val second = IllegalStateException("second")
            repository.failNext(Operation.ACTIVATE, first)
            repository.failNext(Operation.ACTIVATE, second)

            assertSame(first, repository.activate(ModelVersion("v2")).exceptionOrNull())
            assertTrue(repository.rollback().exceptionOrNull() !== second)
            assertSame(second, repository.activate(ModelVersion("v2")).exceptionOrNull())
            assertTrue(repository.activate(ModelVersion("v2")).isSuccess)
        }

    @Test
    fun `import adds a model derived from the source name by default`() =
        runTest {
            val result = repository.import(source("model-v4"))

            assertEquals(ModelVersion("model-v4"), result.getOrThrow().version)
            assertEquals(ModelVersion("model-v4"), repository.list().last().version)
            assertEquals(ModelVersion("v1"), repository.active()?.manifest?.version)
            assertEquals(1, repository.importCalls)
        }

    @Test
    fun `import turns an arbitrary display name into a valid version`() =
        runTest {
            val manifest = repository.import(source("my model (final).tflite")).getOrThrow()

            assertEquals(ModelVersion("my_model__final_.tflite"), manifest.version)
        }

    @Test
    fun `importResult scripts the outcome`() =
        runTest {
            val manifest: ModelManifest = TestModels.artifact("scripted").manifest
            repository.importResult = { Result.success(manifest) }
            assertEquals(manifest, repository.import(source("ignored")).getOrThrow())
            assertEquals(manifest, repository.list().last())
            assertEquals(manifest, repository.artifact(manifest.version)?.manifest)

            val rejection = IllegalArgumentException("bad signature")
            repository.importResult = { Result.failure(rejection) }
            val sizeBefore = repository.list().size
            assertSame(rejection, repository.import(source("bad")).exceptionOrNull())
            assertEquals(sizeBefore, repository.list().size)
        }

    private fun snapshot() = Triple(repository.active(), repository.previous(), repository.list())

    private fun source(name: String) =
        object : ModelSource {
            override val displayName = name

            override fun open(): InputStream = ByteArrayInputStream(ByteArray(0))
        }
}
