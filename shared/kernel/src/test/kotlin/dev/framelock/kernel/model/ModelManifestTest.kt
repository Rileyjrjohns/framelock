package dev.framelock.kernel.model

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

class ModelManifestTest {
    private val sha = "a".repeat(64)
    private val tensor = TensorSpec(listOf(1, 320, 320, 3), TensorDataType.UINT8)

    @Test
    fun `valid manifest is accepted`() {
        assertDoesNotThrow { ModelManifest(ModelVersion("ssd-1.0"), sha, "c2ln", tensor, listOf(tensor)) }
    }

    @ParameterizedTest
    @ValueSource(strings = ["", "abc", "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"])
    fun `sha256 must be 64 lowercase hex chars`(value: String) {
        assertThrows<IllegalArgumentException> {
            ModelManifest(
                ModelVersion("v1"),
                value,
                "c2ln",
                tensor,
                listOf(tensor),
            )
        }
    }

    @ParameterizedTest
    @ValueSource(strings = ["", " ", "v 1", "v/1", "é", ".", ".."])
    fun `model version outside the bounded token charset is rejected`(value: String) {
        assertThrows<IllegalArgumentException> { ModelVersion(value) }
    }

    @Test
    fun `model version longer than 64 chars is rejected`() {
        assertThrows<IllegalArgumentException> { ModelVersion("v".repeat(65)) }
    }

    @Test
    fun `tensor shape must have positive dimensions`() {
        assertThrows<IllegalArgumentException> { TensorSpec(listOf(1, 0, 3), TensorDataType.FLOAT32) }
        assertThrows<IllegalArgumentException> { TensorSpec(emptyList(), TensorDataType.FLOAT32) }
    }
}
