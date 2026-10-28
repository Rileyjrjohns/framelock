package dev.framelock.live.inference.litert

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.framelock.kernel.port.TraceSection
import dev.framelock.kernel.port.Tracer
import dev.framelock.live.domain.frame.ImageFormat
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** LRT-1: high-confidence people match image annotations on each forced backend and input format. */
@RunWith(AndroidJUnit4::class)
class LiteRtGoldenTest {
    @Test
    fun cpuDetectsPeopleInRgba() = checkPeople(Backend.CPU, ImageFormat.RGBA_8888)

    @Test
    fun cpuDetectsPeopleInYuv() = checkPeople(Backend.CPU, ImageFormat.YUV_420_888)

    @Test
    fun gpuDetectsPeopleInRgba() = checkPeople(Backend.GPU, ImageFormat.RGBA_8888)

    @Test
    fun gpuDetectsPeopleInYuv() = checkPeople(Backend.GPU, ImageFormat.YUV_420_888)

    private fun checkPeople(
        backend: Backend,
        format: ImageFormat,
    ) = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val fixture = ClassroomFixture(instrumentation.context.assets)
        val model = fixture.model(instrumentation.targetContext.cacheDir)
        val frame = fixture.frame(format)
        val engine =
            LiteRtInferenceEngine(
                NoTracing,
                minScore = MIN_CONFIDENCE,
                preference = { BackendPreference.valueOf(backend.name) },
            )
        withContext(engine.dispatcher) {
            try {
                engine.load(model)
                assertEquals("Requested backend must not fall back", backend, engine.backend.value)
                val detections = engine.infer(frame).detections
                Log.i("LiteRtFixture", "$backend $format: $detections")
                assertTrue(
                    "$backend $format: expected ${fixture.references}, got $detections",
                    matchesDetections(fixture.references, detections),
                )
            } finally {
                frame.image.close()
                engine.close()
            }
        }
    }

    private object NoTracing : Tracer {
        override fun begin(section: TraceSection) = Unit

        override fun end() = Unit
    }
}
