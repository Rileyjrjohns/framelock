package dev.framelock.live.inference.litert

import android.util.Log
import dev.framelock.kernel.model.ModelArtifact
import dev.framelock.kernel.port.TraceSection
import dev.framelock.kernel.port.Tracer
import dev.framelock.kernel.port.trace
import dev.framelock.live.domain.frame.Frame
import dev.framelock.live.domain.frame.ImageFormat
import dev.framelock.live.domain.inference.InferenceResult
import dev.framelock.live.domain.port.InferenceEngine
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.tensorflow.lite.DataType
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.gpu.CompatibilityList
import org.tensorflow.lite.gpu.GpuDelegate
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel
import java.util.concurrent.Executors

/** Which runtime path the loaded model uses (ADR-005). */
enum class Backend { GPU, CPU }

/**
 * Which backend the engine is asked to use. [AUTO] is the production behaviour (GPU when supported, CPU otherwise);
 * [GPU] and [CPU] force one for a measurement and are offered by debug builds only. A forced [GPU] that cannot be
 * built fails the load instead of falling back: a benchmark must not silently measure the other backend.
 */
enum class BackendPreference { AUTO, GPU, CPU }

/**
 * [InferenceEngine] over LiteRT 1.4 (ADR-005): the GPU delegate when [CompatibilityList] supports this device, the
 * CPU with XNNPACK otherwise or if the GPU path fails to build. Everything runs on [dispatcher], a single thread
 * created here (ADR-002): the GPU delegate is created, invoked and closed on it.
 *
 * Expects a detection model with a uint8 `[1, H, W, 3]` input and the four outputs of the TFLite detection
 * post-processing op (boxes, classes, scores, count), such as EfficientDet-Lite0 (docs/soup.md). Input and output
 * buffers are allocated at load and reused for every frame (ADR-012).
 *
 * [preference] is read at each load. The backend actually used is logged once per load (tag `LiteRtEngine`) and
 * published in [backend].
 */
class LiteRtInferenceEngine(
    private val tracer: Tracer,
    private val minScore: Float = DEFAULT_MIN_SCORE,
    private val cpuThreads: Int = DEFAULT_CPU_THREADS,
    private val preference: () -> BackendPreference = { BackendPreference.AUTO },
) : InferenceEngine {
    private val executor = Executors.newSingleThreadExecutor { Thread(it, THREAD_NAME) }
    override val dispatcher: CoroutineDispatcher = executor.asCoroutineDispatcher()

    private var loaded: Loaded? = null

    private val activeBackend = MutableStateFlow<Backend?>(null)

    /** The backend of the loaded model, for the debug HUD; null before the first load. */
    val backend: StateFlow<Backend?> = activeBackend.asStateFlow()

    override suspend fun load(model: ModelArtifact) {
        val requested = preference()
        val next = open(model, requested)
        // Warm-up: the first run initialises the delegate and the kernels; it must not hit the first real frame.
        next.interpreter.runForMultipleInputsOutputs(arrayOf(next.input), next.outputs)
        loaded?.close()
        loaded = next
        activeBackend.value = next.backend
        Log.i(LOG_TAG, "Backend ${next.backend} (requested $requested)")
    }

    override fun infer(frame: Frame): InferenceResult {
        val model = checkNotNull(loaded) { "No model loaded" }
        tracer.trace(TraceSection.PREPROCESS) {
            model.input.clear()
            val image = frame.image
            when (image.format) {
                ImageFormat.YUV_420_888 -> {
                    YuvToRgb.scaleInto(
                        image.planes,
                        frame.width,
                        frame.height,
                        model.input,
                        model.inputWidth,
                        model.inputHeight,
                    )
                }

                ImageFormat.RGBA_8888 -> {
                    RgbaToRgb.scaleInto(
                        image.planes.single(),
                        frame.width,
                        frame.height,
                        model.input,
                        model.inputWidth,
                        model.inputHeight,
                    )
                }

                else -> {
                    throw IllegalArgumentException("Unsupported frame format ${image.format}")
                }
            }
            model.input.rewind()
            model.outputs.values.forEach { it.clear() }
        }
        val started = System.nanoTime()
        model.interpreter.runForMultipleInputsOutputs(model.inputs, model.outputs)
        val inferenceNanos = System.nanoTime() - started
        val detections =
            tracer.trace(TraceSection.POSTPROCESS) {
                model.readOutputs()
                model.decoder.decode(model.boxes, model.classes, model.scores, model.count())
            }
        return InferenceResult(frame.id, frame.sessionId, model.version, detections, inferenceNanos)
    }

    override fun close() {
        loaded?.close()
        loaded = null
        executor.shutdown()
    }

    private fun open(
        model: ModelArtifact,
        requested: BackendPreference,
    ): Loaded {
        val buffer = map(File(model.location))
        val gpu =
            CompatibilityList().use { compatibility ->
                if (requested == BackendPreference.CPU) {
                    null
                } else if (requested == BackendPreference.GPU || compatibility.isDelegateSupportedOnThisDevice) {
                    runCatching { GpuDelegate(compatibility.bestOptionsForThisDevice) }.getOrNull()
                } else {
                    null
                }
            }
        // The GPU path is used only if the interpreter builds with the delegate; any failure falls back to the CPU.
        val onGpu =
            gpu?.let { delegate ->
                runCatching { Interpreter(buffer, Interpreter.Options().addDelegate(delegate)) }
                    .onFailure { delegate.close() }
                    .getOrNull()
            }
        check(
            requested != BackendPreference.GPU || onGpu != null,
        ) { "The GPU backend was requested but could not be built" }
        val interpreter =
            onGpu ?: Interpreter(buffer, Interpreter.Options().setNumThreads(cpuThreads).setUseXNNPACK(true))
        val delegate = if (onGpu != null) gpu else null
        return try {
            Loaded(model, interpreter, delegate, DetectionDecoder(minScore))
        } catch (e: IllegalArgumentException) {
            interpreter.close()
            delegate?.close()
            throw e
        }
    }

    private fun map(file: File): ByteBuffer =
        RandomAccessFile(file, "r").use { access ->
            access.channel.map(FileChannel.MapMode.READ_ONLY, 0, access.length())
        }

    /** A loaded model, its delegate and its preallocated tensors. */
    private class Loaded(
        model: ModelArtifact,
        val interpreter: Interpreter,
        private val gpu: GpuDelegate?,
        val decoder: DetectionDecoder,
    ) {
        val version = model.manifest.version
        val backend = if (gpu != null) Backend.GPU else Backend.CPU
        val inputHeight: Int
        val inputWidth: Int
        val input: ByteBuffer
        val inputs: Array<Any>
        val outputs: Map<Int, ByteBuffer>
        val boxes: FloatArray
        val classes: FloatArray
        val scores: FloatArray
        private val countValue = FloatArray(1)

        init {
            val inputTensor = interpreter.getInputTensor(0)
            val shape = inputTensor.shape()
            require(shape.size == INPUT_RANK && shape[0] == 1 && shape[INPUT_CHANNELS_INDEX] == RGB) {
                "Expected a [1, H, W, 3] input, got ${shape.toList()}"
            }
            require(
                inputTensor.dataType() == DataType.UINT8,
            ) { "Expected a uint8 input, got ${inputTensor.dataType()}" }
            require(interpreter.outputTensorCount == OUTPUTS) {
                "Expected $OUTPUTS outputs (boxes, classes, scores, count), got ${interpreter.outputTensorCount}"
            }
            val boxesShape = interpreter.getOutputTensor(BOXES).shape()
            require(boxesShape.size == INPUT_RANK - 1 && boxesShape.last() == BOX_VALUES) {
                "Expected boxes [1, N, 4] as output 0, got ${boxesShape.toList()}"
            }
            require(interpreter.getOutputTensor(COUNT).numElements() == 1) { "Expected the count as output 3" }
            inputHeight = shape[1]
            inputWidth = shape[2]
            input = ByteBuffer.allocateDirect(inputHeight * inputWidth * RGB).order(ByteOrder.nativeOrder())
            inputs = arrayOf(input)
            outputs =
                (0 until OUTPUTS).associateWith { index ->
                    val tensor = interpreter.getOutputTensor(index)
                    ByteBuffer.allocateDirect(tensor.numBytes()).order(ByteOrder.nativeOrder())
                }
            val detections = interpreter.getOutputTensor(SCORES).shape().last()
            boxes = FloatArray(detections * BOX_VALUES)
            classes = FloatArray(detections)
            scores = FloatArray(detections)
        }

        fun readOutputs() {
            read(BOXES, boxes)
            read(CLASSES, classes)
            read(SCORES, scores)
            read(COUNT, countValue)
        }

        fun count(): Int = countValue[0].toInt()

        private fun read(
            index: Int,
            into: FloatArray,
        ) {
            val buffer = outputs.getValue(index)
            buffer.rewind()
            buffer.asFloatBuffer().get(into)
        }

        fun close() {
            interpreter.close()
            gpu?.close()
        }

        private companion object {
            const val INPUT_RANK = 4
            const val INPUT_CHANNELS_INDEX = 3
            const val RGB = 3
            const val OUTPUTS = 4
            const val BOXES = 0
            const val CLASSES = 1
            const val SCORES = 2
            const val COUNT = 3
            const val BOX_VALUES = 4
        }
    }

    private companion object {
        const val THREAD_NAME = "inference"
        const val LOG_TAG = "LiteRtEngine"
        const val DEFAULT_MIN_SCORE = 0.5f
        const val DEFAULT_CPU_THREADS = 4
    }
}
