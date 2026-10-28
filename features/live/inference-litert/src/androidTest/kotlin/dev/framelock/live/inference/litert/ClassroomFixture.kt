package dev.framelock.live.inference.litert

import android.content.res.AssetManager
import android.graphics.BitmapFactory
import dev.framelock.kernel.model.ModelArtifact
import dev.framelock.kernel.model.ModelManifest
import dev.framelock.kernel.model.ModelVersion
import dev.framelock.kernel.model.TensorDataType
import dev.framelock.kernel.model.TensorSpec
import dev.framelock.live.domain.frame.Frame
import dev.framelock.live.domain.frame.FrameId
import dev.framelock.live.domain.frame.ImageBuffer
import dev.framelock.live.domain.frame.ImageFormat
import dev.framelock.live.domain.frame.Plane
import dev.framelock.live.domain.frame.SessionId
import dev.framelock.live.domain.inference.LabelId
import dev.framelock.live.domain.inference.NormalizedRect
import java.io.File
import java.nio.ByteBuffer

/** Image provenance, hand-annotated boxes and extraction commands: assets/classroom-004s.md. */
internal class ClassroomFixture(
    private val assets: AssetManager,
) {
    val references =
        listOf(
            ReferenceDetection(LabelId(0), NormalizedRect(61f / WIDTH, 189f / HEIGHT, 154f / WIDTH, 299f / HEIGHT)),
            ReferenceDetection(LabelId(0), NormalizedRect(374f / WIDTH, 137f / HEIGHT, 468f / WIDTH, 289f / HEIGHT)),
        )

    fun model(cache: File): ModelArtifact {
        val file = File(cache, MODEL)
        assets.open(MODEL).use { input -> file.outputStream().use(input::copyTo) }
        val manifest =
            ModelManifest(
                ModelVersion("efficientdet-lite0-int8"),
                sha256 = "2e04c53bfeac0ac2a30c057c7e2a777594ce39baaac35a92f74fb1e8c4fc4e0b",
                signature = "unsigned-test",
                inputSpec = TensorSpec(listOf(1, 320, 320, 3), TensorDataType.UINT8),
                outputSpec =
                    listOf(
                        TensorSpec(listOf(1, 25, 4), TensorDataType.FLOAT32),
                        TensorSpec(listOf(1, 25), TensorDataType.FLOAT32),
                        TensorSpec(listOf(1, 25), TensorDataType.FLOAT32),
                        TensorSpec(listOf(1), TensorDataType.FLOAT32),
                    ),
            )
        return ModelArtifact(manifest, file.absolutePath)
    }

    fun frame(format: ImageFormat): Frame {
        val planes =
            when (format) {
                ImageFormat.RGBA_8888 -> rgba()
                ImageFormat.YUV_420_888 -> yuv()
                ImageFormat.JPEG -> error("JPEG is not an engine input")
            }
        val buffer =
            object : ImageBuffer {
                override val format = format
                override val planes = planes

                override fun close() = Unit
            }
        return Frame(FrameId(0), SessionId.FIRST, 0, WIDTH, HEIGHT, buffer)
    }

    private fun rgba(): List<Plane> {
        val bitmap = assets.open("classroom-004s.png").use(BitmapFactory::decodeStream)
        require(bitmap.width == WIDTH && bitmap.height == HEIGHT)
        val pixels = IntArray(WIDTH * HEIGHT)
        bitmap.getPixels(pixels, 0, WIDTH, 0, 0, WIDTH, HEIGHT)
        bitmap.recycle()
        val bytes = ByteBuffer.allocateDirect(pixels.size * 4)
        pixels.forEach { pixel ->
            bytes.put((pixel ushr 16).toByte())
            bytes.put((pixel ushr 8).toByte())
            bytes.put(pixel.toByte())
            bytes.put(0xff.toByte())
        }
        bytes.flip()
        return listOf(Plane(bytes, WIDTH * 4, 4))
    }

    private fun yuv(): List<Plane> {
        val bytes = assets.open("classroom-004s.i420").use { it.readBytes() }
        val luma = WIDTH * HEIGHT
        val chroma = luma / 4
        require(bytes.size == luma + 2 * chroma)
        return listOf(
            plane(bytes, 0, luma, WIDTH),
            plane(bytes, luma, chroma, WIDTH / 2),
            plane(bytes, luma + chroma, chroma, WIDTH / 2),
        )
    }

    private fun plane(
        bytes: ByteArray,
        offset: Int,
        length: Int,
        stride: Int,
    ): Plane {
        val buffer = ByteBuffer.allocateDirect(length)
        buffer.put(bytes, offset, length).flip()
        return Plane(buffer, stride, 1)
    }

    private companion object {
        const val MODEL = "efficientdet_lite0.tflite"
        const val WIDTH = 640
        const val HEIGHT = 360
    }
}
