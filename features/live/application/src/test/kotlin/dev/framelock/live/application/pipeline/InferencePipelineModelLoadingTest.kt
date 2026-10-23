package dev.framelock.live.application.pipeline

import dev.framelock.kernel.model.ModelVersion
import dev.framelock.live.domain.frame.DisconnectReason
import dev.framelock.live.domain.frame.SessionId
import dev.framelock.testing.TestModels
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

/** ADR-003: models are loaded on the engine's thread, between sessions only, and a failed load changes nothing. */
@OptIn(ExperimentalCoroutinesApi::class)
class InferencePipelineModelLoadingTest {
    @Test
    fun `loading a model while a session is running is refused`() =
        runTest {
            val h = PipelineHarness(this)
            h.startConnected()

            assertThrows<SessionActiveException> { h.pipeline.loadModel(TestModels.artifact("model-2")) }
            assertEquals(listOf(h.model), h.engine.loadedModels)
        }

    @Test
    fun `loading a model without signal is accepted`() =
        runTest {
            val h = PipelineHarness(this)
            h.startConnected()
            h.source.disconnect(DisconnectReason.DEVICE_DETACHED)
            runCurrent()

            h.pipeline.loadModel(TestModels.artifact("model-2"))

            assertEquals(ModelVersion("model-2"), h.engine.loadedVersion)
        }

    @Test
    fun `a commit that throws loads the previous model back`() =
        runTest {
            val h = PipelineHarness(this)
            h.pipeline.loadModel(h.model)

            val outcome = h.pipeline.swapModel(TestModels.artifact("model-2"), restore = h.model) { error("disk full") }

            assertTrue(outcome is SwapOutcome.CommitFailed && outcome.restored, "$outcome")
            assertEquals(h.model.manifest.version, h.engine.loadedVersion)
        }

    @Test
    fun `a swap is refused while a session is connected`() =
        runTest {
            val h = PipelineHarness(this)
            h.startConnected()

            val outcome =
                h.pipeline.swapModel(
                    TestModels.artifact("model-2"),
                    restore = h.model,
                ) { Result.success(Unit) }

            assertEquals(SwapOutcome.SessionActive, outcome)
        }

    @Test
    fun `an artifact with the same version but other content is loaded again`() =
        runTest {
            val h = PipelineHarness(this)
            h.pipeline.loadModel(h.model)
            val sameVersion = h.model.copy(manifest = h.model.manifest.copy(sha256 = "b".repeat(64)))

            h.pipeline.loadModel(sameVersion)

            assertEquals(2, h.engine.loadedModels.size)
        }

    @Test
    fun `a failed load keeps the previous model`() =
        runTest {
            val h = PipelineHarness(this)
            h.pipeline.loadModel(h.model)
            h.engine.failNextLoad()

            assertThrows<IllegalStateException> { h.pipeline.loadModel(TestModels.artifact("model-2")) }

            assertEquals(h.model.manifest.version, h.current.model)
            assertEquals(h.model.manifest.version, h.engine.loadedVersion)
        }

    @Test
    fun `a loaded model is reported in the state`() =
        runTest {
            val h = PipelineHarness(this)
            val next = TestModels.artifact("model-2")

            h.pipeline.loadModel(next)

            assertEquals(next.manifest.version, h.current.model)
        }

    @Test
    fun `results after a model swap carry the new model version`() =
        runTest {
            val h = PipelineHarness(this)
            val next = TestModels.artifact("model-2")
            h.pipeline.loadModel(next)

            h.pipeline.start(next)
            h.source.connect(SessionId.FIRST)
            h.emit(h.frame(0))
            runCurrent()

            assertEquals(
                next.manifest.version,
                h.current.annotated
                    ?.result
                    ?.modelVersion,
            )
        }
}
