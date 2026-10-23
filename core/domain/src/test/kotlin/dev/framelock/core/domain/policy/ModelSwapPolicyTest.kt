package dev.framelock.core.domain.policy

import dev.framelock.core.domain.frame.DisconnectReason
import dev.framelock.core.domain.pipeline.DegradationCause
import dev.framelock.core.domain.pipeline.InferenceRate
import dev.framelock.core.domain.pipeline.PauseCause
import dev.framelock.core.domain.pipeline.PipelineState
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ModelSwapPolicyTest {
    private val sessionActive = SwapDecision.Refused(SwapRefusal.SESSION_ACTIVE)

    @Test
    fun `swap allowed when Idle`() {
        assertEquals(SwapDecision.Allowed, ModelSwapPolicy.decide(PipelineState.Idle))
    }

    @Test
    fun `swap allowed when NoSignal for every reason`() {
        DisconnectReason.entries.forEach { reason ->
            assertEquals(SwapDecision.Allowed, ModelSwapPolicy.decide(PipelineState.NoSignal(reason)), "$reason")
        }
    }

    @Test
    fun `swap refused when Connecting`() {
        assertEquals(sessionActive, ModelSwapPolicy.decide(PipelineState.Connecting))
    }

    @Test
    fun `swap refused when Running at any rate`() {
        rates.forEach { rate ->
            assertEquals(sessionActive, ModelSwapPolicy.decide(PipelineState.Running(rate)), "$rate")
        }
    }

    @Test
    fun `swap refused when Degraded for every cause`() {
        DegradationCause.entries.forEach { cause ->
            rates.forEach { rate ->
                assertEquals(
                    sessionActive,
                    ModelSwapPolicy.decide(PipelineState.Degraded(cause, rate)),
                    "$cause, $rate",
                )
            }
        }
    }

    @Test
    fun `swap refused when Paused for every cause`() {
        PauseCause.entries.forEach { cause ->
            assertEquals(sessionActive, ModelSwapPolicy.decide(PipelineState.Paused(cause)), "$cause")
        }
    }

    @Test
    fun `refusal carries reason SessionActive`() {
        val decision = ModelSwapPolicy.decide(PipelineState.Running(InferenceRate.UNLIMITED))

        assertEquals(SwapRefusal.SESSION_ACTIVE, (decision as SwapDecision.Refused).reason)
    }

    @Test
    fun `every PipelineState subtype has a decision`() {
        val oneOfEach: List<PipelineState> =
            listOf(
                PipelineState.Idle,
                PipelineState.Connecting,
                PipelineState.NoSignal(DisconnectReason.DEVICE_DETACHED),
                PipelineState.Running(InferenceRate.UNLIMITED),
                PipelineState.Degraded(DegradationCause.THERMAL, InferenceRate.capped(10)),
                PipelineState.Paused(PauseCause.MEMORY),
            )

        oneOfEach.forEach { state ->
            // No `else`: a new PipelineState subtype stops this test from compiling until its rule is decided here.
            val expected =
                when (state) {
                    PipelineState.Idle -> SwapDecision.Allowed
                    is PipelineState.NoSignal -> SwapDecision.Allowed
                    PipelineState.Connecting -> sessionActive
                    is PipelineState.Running -> sessionActive
                    is PipelineState.Degraded -> sessionActive
                    is PipelineState.Paused -> sessionActive
                }

            assertEquals(expected, ModelSwapPolicy.decide(state), "$state")
        }
    }

    private companion object {
        val rates = listOf(InferenceRate.UNLIMITED, InferenceRate.capped(1), InferenceRate.capped(15))
    }
}
