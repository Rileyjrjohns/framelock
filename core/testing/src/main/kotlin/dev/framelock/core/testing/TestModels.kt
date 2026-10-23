package dev.framelock.core.testing

import dev.framelock.core.domain.model.ModelArtifact
import dev.framelock.core.domain.model.ModelManifest
import dev.framelock.core.domain.model.ModelVersion
import dev.framelock.core.domain.model.TensorDataType
import dev.framelock.core.domain.model.TensorSpec

/** Builds valid model artifacts for tests. Nothing is verified or loaded: the fakes only pass them around. */
object TestModels {
    private const val SIGNATURE = "dGVzdC1zaWduYXR1cmU="
    private const val HEX_RADIX = 16
    private const val HASH_HEX_DIGITS = 8
    private const val HASH_REPEATS = 8
    private const val INPUT_SIZE = 320
    private const val RGB_CHANNELS = 3
    private const val MAX_DETECTIONS = 10
    private const val BOX_COORDINATES = 4

    private val INPUT = TensorSpec(listOf(1, INPUT_SIZE, INPUT_SIZE, RGB_CHANNELS), TensorDataType.UINT8)
    private val OUTPUTS =
        listOf(
            TensorSpec(listOf(1, MAX_DETECTIONS, BOX_COORDINATES), TensorDataType.FLOAT32),
            TensorSpec(listOf(1, MAX_DETECTIONS), TensorDataType.FLOAT32),
        )

    /** The same [version] always gives the same artifact; different versions give different hashes and locations. */
    fun artifact(version: String): ModelArtifact {
        val manifest =
            ModelManifest(
                version = ModelVersion(version),
                sha256 = hashOf(version),
                signature = SIGNATURE,
                inputSpec = INPUT,
                outputSpec = OUTPUTS,
            )
        return ModelArtifact(manifest, "/fake/models/$version/model.tflite")
    }

    private fun hashOf(version: String): String =
        version
            .hashCode()
            .toUInt()
            .toString(HEX_RADIX)
            .padStart(HASH_HEX_DIGITS, '0')
            .repeat(HASH_REPEATS)
}
