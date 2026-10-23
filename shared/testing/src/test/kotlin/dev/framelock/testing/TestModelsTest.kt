package dev.framelock.testing

import dev.framelock.kernel.model.ModelVersion
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class TestModelsTest {
    @Test
    fun `artifact carries a valid manifest`() {
        val artifact = TestModels.artifact("v1")

        assertEquals(ModelVersion("v1"), artifact.manifest.version)
        assertEquals(64, artifact.manifest.sha256.length)
        assertTrue(artifact.manifest.signature.isNotBlank())
        assertTrue(artifact.manifest.outputSpec.isNotEmpty())
        assertTrue(artifact.location.isNotBlank())
    }

    @Test
    fun `artifacts of different versions differ and the same version is stable`() {
        assertEquals(TestModels.artifact("v1"), TestModels.artifact("v1"))
        assertNotEquals(TestModels.artifact("v1").manifest.sha256, TestModels.artifact("v2").manifest.sha256)
        assertNotEquals(TestModels.artifact("v1").location, TestModels.artifact("v2").location)
    }

    @Test
    fun `an invalid version is rejected`() {
        assertThrows<IllegalArgumentException> { TestModels.artifact("not a version!") }
    }
}
