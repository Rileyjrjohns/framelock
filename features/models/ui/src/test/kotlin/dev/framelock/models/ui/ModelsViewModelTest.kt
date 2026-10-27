package dev.framelock.models.ui

import dev.framelock.kernel.model.ModelManifest
import dev.framelock.kernel.model.ModelVersion
import dev.framelock.kernel.model.TensorDataType
import dev.framelock.kernel.model.TensorSpec
import dev.framelock.models.application.ImportResult
import dev.framelock.models.application.ModelChangeResult
import dev.framelock.models.domain.ModelRejectionReason
import dev.framelock.models.domain.ModelSource
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class ModelsViewModelTest {
    private val v1 = ModelVersion("1.0.0")
    private val v2 = ModelVersion("2.0.0")
    private val dispatcher = StandardTestDispatcher()

    private var rows =
        listOf(ModelRow(v2, active = true, previous = false), ModelRow(v1, active = false, previous = true))
    private var listCalls = 0
    private var importResult: suspend () -> ImportResult = { ImportResult.Imported(manifest(v2)) }
    private var activateResult: suspend (ModelVersion) -> ModelChangeResult = { ModelChangeResult.Changed(v2, it) }
    private var rollbackResult: suspend () -> ModelChangeResult = { ModelChangeResult.Changed(v2, v1) }
    private val tracked = mutableListOf<dev.framelock.kernel.telemetry.ElementId>()

    @BeforeEach
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterEach
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel() =
        ModelsViewModel(
            actions =
                object : ModelsActions {
                    override suspend fun list(): List<ModelRow> {
                        listCalls++
                        return rows
                    }

                    override suspend fun import(source: ModelSource) = importResult()

                    override suspend fun activate(version: ModelVersion) = activateResult(version)

                    override suspend fun rollback() = rollbackResult()
                },
            canSwap = MutableStateFlow(true),
            track = { tracked += it },
        )

    private fun manifest(version: ModelVersion) =
        ModelManifest(
            version,
            "a".repeat(SHA_LENGTH),
            "sig",
            TensorSpec(listOf(1, 2), TensorDataType.FLOAT32),
            emptyList(),
        )

    private val source =
        object : ModelSource {
            override val displayName = "m.flmodel"

            override fun open() = ByteArrayInputStream(ByteArray(0))
        }

    @Test
    fun `lists the models on init`() {
        val vm = viewModel()
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(rows, vm.state.value.rows)
        assertTrue(vm.state.value.canRollBack)
        assertFalse(vm.state.value.busy)
    }

    @Test
    fun `cannot roll back without a previous version`() {
        rows = listOf(ModelRow(v1, active = true, previous = false))
        val vm = viewModel()
        dispatcher.scheduler.advanceUntilIdle()

        assertFalse(vm.state.value.canRollBack)
    }

    @Test
    fun `activation maps to a change outcome and refreshes the list`() {
        val vm = viewModel()
        dispatcher.scheduler.advanceUntilIdle()
        rows = listOf(ModelRow(v1, active = true, previous = false))

        vm.activate(v1)
        assertTrue(vm.state.value.busy)
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(ModelsOutcome.Changed(v2, v1), vm.state.value.outcome)
        assertEquals(rows, vm.state.value.rows)
        assertEquals(2, listCalls)
        assertFalse(vm.state.value.busy)
    }

    @Test
    fun `an unchanged activation and a rollback are reported`() {
        activateResult = { ModelChangeResult.Unchanged(it) }
        val vm = viewModel()
        dispatcher.scheduler.advanceUntilIdle()

        vm.activate(v2)
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(ModelsOutcome.Unchanged(v2), vm.state.value.outcome)

        vm.rollback()
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(ModelsOutcome.Changed(v2, v1), vm.state.value.outcome)
    }

    @Test
    fun `import maps imported and refused results`() {
        val vm = viewModel()
        dispatcher.scheduler.advanceUntilIdle()

        vm.import(source)
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(ModelsOutcome.Imported(v2), vm.state.value.outcome)

        importResult = { ImportResult.Refused(ModelRejectionReason.BAD_SIGNATURE) }
        vm.import(source)
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(ModelsOutcome.Refused(ModelRejectionReason.BAD_SIGNATURE), vm.state.value.outcome)
    }

    @Test
    fun `a refused change keeps its reason`() {
        rollbackResult = { ModelChangeResult.Refused(ModelRejectionReason.SWAP_REFUSED) }
        val vm = viewModel()
        dispatcher.scheduler.advanceUntilIdle()

        vm.rollback()
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(ModelsOutcome.Refused(ModelRejectionReason.SWAP_REFUSED), vm.state.value.outcome)
    }

    @Test
    fun `only one action runs at a time`() {
        val gate = CompletableDeferred<ModelChangeResult>()
        var activations = 0
        activateResult = {
            activations++
            gate.await()
        }
        val vm = viewModel()
        dispatcher.scheduler.advanceUntilIdle()

        vm.activate(v1)
        dispatcher.scheduler.runCurrent()
        vm.activate(v2)
        vm.rollback()
        vm.import(source)
        gate.complete(ModelChangeResult.Changed(v2, v1))
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(1, activations)
        assertEquals(ModelsOutcome.Changed(v2, v1), vm.state.value.outcome)
        assertFalse(vm.state.value.busy)
    }

    @Test
    fun `an unexpected failure is reported and releases the busy flag`() {
        importResult = { throw IOException("disk") }
        val vm = viewModel()
        dispatcher.scheduler.advanceUntilIdle()

        vm.import(source)
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(ModelsOutcome.Refused(ModelRejectionReason.IO_ERROR), vm.state.value.outcome)
        assertFalse(vm.state.value.busy)
    }

    private companion object {
        const val SHA_LENGTH = 64
    }
}
