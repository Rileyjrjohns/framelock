package dev.framelock.app

import android.content.Context
import dev.framelock.kernel.model.ModelArtifact
import dev.framelock.kernel.model.ModelManifest
import dev.framelock.kernel.model.ModelVersion
import dev.framelock.kernel.model.TensorDataType
import dev.framelock.kernel.model.TensorSpec
import dev.framelock.live.domain.inference.LabelId
import java.io.File
import java.security.MessageDigest
import java.util.zip.ZipFile

/**
 * The model used until the model store of M5: the public EfficientDet-Lite0 (docs/soup.md), fetched by
 * `scripts/fetch-model.sh` into the debug assets and copied once into `filesDir/models/<version>/` (ADR-003 layout).
 * It is NOT signed: the signature check arrives with the store. Release builds have no asset, so no model.
 */
internal class DevelopmentModel(
    private val context: Context,
) {
    @Volatile private var installed: ModelArtifact? = null

    @Volatile private var labels: List<String> = emptyList()

    /** The installed model, or null until [install] has run (or when the asset is absent). */
    fun artifact(): ModelArtifact? = installed

    fun labelOf(label: LabelId): String = labels.getOrNull(label.value) ?: "#${label.value}"

    /** Copies and hashes the model off the main thread; does nothing in a build without the asset. */
    fun install() {
        val asset = runCatching { context.assets.open(ASSET) }.getOrNull() ?: return
        val target = File(context.filesDir, "models/${VERSION.value}/model.tflite")
        target.parentFile?.mkdirs()
        asset.use { input -> target.outputStream().use { input.copyTo(it) } }
        // The model embeds its labels as a zip entry (TFLite metadata).
        labels =
            runCatching {
                ZipFile(target).use { zip ->
                    zip.getInputStream(zip.getEntry(LABELS)).bufferedReader().readLines()
                }
            }.getOrDefault(emptyList())
        installed = ModelArtifact(manifest(sha256(target)), target.absolutePath)
    }

    private fun manifest(sha256: String) =
        ModelManifest(
            version = VERSION,
            sha256 = sha256,
            signature = "unsigned-development",
            inputSpec = TensorSpec(listOf(1, INPUT_SIZE, INPUT_SIZE, RGB), TensorDataType.UINT8),
            outputSpec =
                listOf(
                    TensorSpec(listOf(1, DETECTIONS, BOX), TensorDataType.FLOAT32),
                    TensorSpec(listOf(1, DETECTIONS), TensorDataType.FLOAT32),
                    TensorSpec(listOf(1, DETECTIONS), TensorDataType.FLOAT32),
                    TensorSpec(listOf(1), TensorDataType.FLOAT32),
                ),
        )

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(BUFFER_SIZE)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private companion object {
        val VERSION = ModelVersion("efficientdet-lite0-int8")
        const val ASSET = "models/efficientdet_lite0.tflite"
        const val LABELS = "labels.txt"
        const val INPUT_SIZE = 320
        const val RGB = 3
        const val DETECTIONS = 25
        const val BOX = 4
        const val BUFFER_SIZE = 64 * 1024
    }
}
