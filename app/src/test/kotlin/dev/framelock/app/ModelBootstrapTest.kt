package dev.framelock.app

import dev.framelock.kernel.model.ModelManifest
import dev.framelock.kernel.model.ModelVersion
import dev.framelock.kernel.model.TensorDataType
import dev.framelock.kernel.model.TensorSpec
import dev.framelock.models.application.ImportResult
import dev.framelock.models.application.ModelChangeResult
import dev.framelock.models.domain.ModelRejectionReason
import dev.framelock.models.domain.ModelSource
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream
import java.io.InputStream

class ModelBootstrapTest {
    private val version = ModelVersion("bundled")
    private val manifest =
        ModelManifest(
            version,
            "a".repeat(64),
            "sig",
            TensorSpec(listOf(1), TensorDataType.UINT8),
            listOf(TensorSpec(listOf(1), TensorDataType.FLOAT32)),
        )
    private val source =
        object : ModelSource {
            override val displayName = "bundled.flmodel"

            override fun open(): InputStream = ByteArrayInputStream(ByteArray(0))
        }
    private val calls = mutableListOf<String>()

    private fun bootstrap(
        active: Boolean = false,
        bundled: ModelSource? = source,
        imported: ImportResult = ImportResult.Imported(manifest),
        activated: ModelChangeResult = ModelChangeResult.Changed(null, version),
    ) = ModelBootstrap(
        hasActiveModel = { active },
        bundledPackage = { bundled },
        import = {
            calls += "import"
            imported
        },
        activate = {
            calls += "activate ${it.value}"
            activated
        },
    )

    @Test
    fun `without an active model the bundled package is imported, then that version is activated`() =
        runTest {
            val result = bootstrap().run()

            assertEquals(BootstrapResult.Done(BootstrapOutcome.ACTIVATED), result)
            assertEquals(listOf("import", "activate bundled"), calls)
        }

    @Test
    fun `an active model is left alone`() =
        runTest {
            val result = bootstrap(active = true).run()

            assertEquals(BootstrapResult.Done(BootstrapOutcome.NOT_NEEDED), result)
            assertEquals(emptyList<String>(), calls)
        }

    @Test
    fun `a build without a package does nothing`() =
        runTest {
            val result = bootstrap(bundled = null).run()

            assertEquals(BootstrapResult.Done(BootstrapOutcome.NO_PACKAGE), result)
            assertEquals(emptyList<String>(), calls)
        }

    @Test
    fun `a refused import is reported and nothing is activated`() =
        runTest {
            val result = bootstrap(imported = ImportResult.Refused(ModelRejectionReason.BAD_SIGNATURE)).run()

            assertEquals(BootstrapResult.Refused(ModelRejectionReason.BAD_SIGNATURE), result)
            assertEquals(listOf("import"), calls)
        }

    @Test
    fun `a refused activation is reported`() =
        runTest {
            val result = bootstrap(activated = ModelChangeResult.Refused(ModelRejectionReason.LOAD_FAILED)).run()

            assertEquals(BootstrapResult.Refused(ModelRejectionReason.LOAD_FAILED), result)
        }
}
