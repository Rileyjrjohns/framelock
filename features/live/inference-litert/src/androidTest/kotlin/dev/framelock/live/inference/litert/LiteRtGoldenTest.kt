package dev.framelock.live.inference.litert

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.framelock.kernel.model.ModelArtifact
import dev.framelock.kernel.model.ModelManifest
import dev.framelock.kernel.model.ModelVersion
import dev.framelock.kernel.model.TensorDataType
import dev.framelock.kernel.model.TensorSpec
import dev.framelock.kernel.port.TraceSection
import dev.framelock.kernel.port.Tracer
import dev.framelock.live.domain.frame.Frame
import dev.framelock.live.domain.frame.FrameId
import dev.framelock.live.domain.frame.ImageBuffer
import dev.framelock.live.domain.frame.ImageFormat
import dev.framelock.live.domain.frame.Plane
import dev.framelock.live.domain.frame.SessionId
import dev.framelock.live.domain.inference.Detection
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.nio.ByteBuffer
import kotlin.math.abs

/**
 * LRT-1: the model's raw outputs on a fixed, synthetic image match a recorded golden, within a tolerance (GPU and CPU
 * differ slightly). Needs `scripts/fetch-model.sh`. Without `golden.txt` in the test assets, the test writes the
 * outputs to the app's external files directory and fails: review them, copy them into
 * `src/androidTest/assets/golden.txt` and run again.
 */
@RunWith(AndroidJUnit4::class)
class LiteRtGoldenTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    @Test
    fun outputsOnTheFixedImageMatchTheGolden() =
        runBlocking {
            val engine = LiteRtInferenceEngine(NoTracing, minScore = 0f)
            val detections =
                withContext(engine.dispatcher) {
                    engine.load(artifact())
                    val first = engine.infer(fixedFrame()).detections.top()
                    val second = engine.infer(fixedFrame()).detections.top()
                    assertEquals("inference must be deterministic", first, second)
                    engine.close()
                    first
                }
            val golden = readGolden()
            if (golden == null) {
                val out = File(instrumentation.targetContext.getExternalFilesDir(null), GOLDEN)
                out.writeText(detections.joinToString("\n", transform = ::line))
                fail("No golden yet: outputs written to ${out.absolutePath}; review and add them as assets/$GOLDEN")
            }
            assertEquals(golden!!.size, detections.size)
            golden.zip(detections).forEach { (expected, actual) ->
                assertEquals(expected.label, actual.label)
                assertTrue("score $actual vs $expected", abs(expected.score - actual.score) <= TOLERANCE)
                assertTrue("box $actual vs $expected", abs(expected.box.left - actual.box.left) <= TOLERANCE)
                assertTrue("box $actual vs $expected", abs(expected.box.top - actual.box.top) <= TOLERANCE)
            }
        }

    private fun List<Detection>.top() = sortedByDescending { it.score }.take(TOP)

    private fun line(detection: Detection) =
        with(detection) { "${label.value} $score ${box.left} ${box.top} ${box.right} ${box.bottom}" }

    private fun readGolden(): List<Detection>? =
        runCatching {
            instrumentation.context.assets
                .open(GOLDEN)
                .bufferedReader()
                .readLines()
        }.getOrNull()
            ?.filter { it.isNotBlank() }
            ?.map { row ->
                val values = row.split(' ')
                Detection(
                    dev.framelock.live.domain.inference.NormalizedRect(
                        values[2].toFloat(),
                        values[3].toFloat(),
                        values[4].toFloat(),
                        values[5].toFloat(),
                    ),
                    dev.framelock.live.domain.inference
                        .LabelId(values[0].toInt()),
                    values[1].toFloat(),
                )
            }

    private fun artifact(): ModelArtifact {
        val file = File(instrumentation.targetContext.cacheDir, MODEL)
        instrumentation.context.assets
            .open(MODEL)
            .use { input -> file.outputStream().use { input.copyTo(it) } }
        val manifest =
            ModelManifest(
                ModelVersion("efficientdet-lite0-int8"),
                sha256 = "0720bf247bd76e6594ea28fa9c6f7c5242be774818997dbbeffc4da460c723bb",
                signature = "unsigned-test",
                inputSpec = TensorSpec(listOf(1, SIZE, SIZE, 3), TensorDataType.UINT8),
                outputSpec = listOf(TensorSpec(listOf(1, OUTPUTS, 4), TensorDataType.FLOAT32)),
            )
        return ModelArtifact(manifest, file.absolutePath)
    }

    /** A deterministic gradient with a bright square: the same pixels on every device and run. */
    private fun fixedFrame(): Frame {
        val y = ByteBuffer.allocateDirect(WIDTH * HEIGHT)
        for (row in 0 until HEIGHT) {
            for (column in 0 until WIDTH) {
                val inSquare = row in SQUARE && column in SQUARE
                y.put((if (inSquare) BRIGHT else (row + column) / 4).toByte())
            }
        }
        y.flip()
        val chroma = {
            ByteBuffer.allocateDirect(WIDTH * HEIGHT / 4).apply {
                repeat(capacity()) { put(MID) }
                flip()
            }
        }
        val buffer =
            object : ImageBuffer {
                override val format = ImageFormat.YUV_420_888
                override val planes =
                    listOf(Plane(y, WIDTH, 1), Plane(chroma(), WIDTH / 2, 1), Plane(chroma(), WIDTH / 2, 1))

                override fun close() = Unit
            }
        return Frame(FrameId(0), SessionId.FIRST, 0, WIDTH, HEIGHT, buffer)
    }

    private object NoTracing : Tracer {
        override fun begin(section: TraceSection) = Unit

        override fun end() = Unit
    }

    private companion object {
        const val MODEL = "efficientdet_lite0.tflite"
        const val GOLDEN = "golden.txt"
        const val TOP = 5
        const val TOLERANCE = 0.05f
        const val WIDTH = 640
        const val HEIGHT = 480
        const val SIZE = 320
        const val OUTPUTS = 25
        const val BRIGHT = 230
        const val MID: Byte = -128
        val SQUARE = 160 until 320
    }
}
