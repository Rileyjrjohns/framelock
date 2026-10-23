package dev.framelock.live.application.pipeline

import dev.framelock.live.domain.frame.SessionId
import dev.framelock.testing.TestModels
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
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
