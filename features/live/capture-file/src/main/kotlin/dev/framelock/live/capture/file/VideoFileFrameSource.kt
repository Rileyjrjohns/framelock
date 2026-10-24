package dev.framelock.live.capture.file

import android.media.Image
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaExtractor
import android.media.MediaFormat
import android.util.Log
import dev.framelock.kernel.port.Clock
import dev.framelock.kernel.port.TraceSection
import dev.framelock.kernel.port.Tracer
import dev.framelock.kernel.port.trace
import dev.framelock.live.domain.frame.DisconnectReason
import dev.framelock.live.domain.frame.Frame
import dev.framelock.live.domain.frame.FrameIdSequence
import dev.framelock.live.domain.frame.ImageFormat
import dev.framelock.live.domain.frame.SessionId
import dev.framelock.live.domain.frame.SessionIdSequence
import dev.framelock.live.domain.frame.StreamSpec
import dev.framelock.live.domain.frame.TimestampSource
import dev.framelock.live.domain.port.FrameSource
import dev.framelock.live.domain.port.SourceEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * [FrameSource] that plays an H.264 MP4 in a loop, for repeatable measurements (M7): the same pixels at the same rate on
 * every run, through the same pipeline as a camera. A debug and benchmark source; `:app` offers it in debug builds only.
 *
 * - The file is read with `MediaExtractor` and decoded by `MediaCodec` in `COLOR_FormatYUV420Flexible`, in ByteBuffer
 *   mode. Each decoded image is copied into an [I420FramePool] buffer and its codec buffer released at once; when the
 *   pool is empty the frame is skipped (ADR-010).
 * - Frames are paced in real time from the presentation timestamps ([PlaybackSchedule]), at the file's own rate, not as
 *   fast as the decoder runs. The pipeline's latest-frame-wins policy (ADR-002) then decides what is analysed, as with
 *   a camera.
 * - The file loops forever. The loop is seamless: the session, the frame ids and the pacing continue across it.
 * - A session starts at the first frame after [open]: `Connected` carries the decoded size. Session ids keep
 *   increasing across every source sharing the [SessionIdSequence].
 * - The decoder runs on its own thread, created by [open] and ended by [close]. A missing or undecodable file gives
 *   `Disconnected(OPEN_FAILED)` before the first frame and `Disconnected(STREAM_ERROR)` after it. [open] first ends the
 *   previous stream, so the pipeline's reconnection can call it without [close] in between.
 * - Frames are stamped on arrival, [TimestampSource.ARRIVAL]: like a capture card, the file has no sensor clock. The
 *   rotation flag of the file is ignored; a portrait file is shown as it is coded.
 * - Events that are never collected are released: the channel closes their buffers.
 *
 * Assumes the decoder's valid picture starts at the top left corner (padding at the right and bottom only), which is
 * what H.264 decoders produce; anything else ends the stream with `STREAM_ERROR`.
 */
class VideoFileFrameSource(
    private val file: File,
    private val clock: Clock,
    private val tracer: Tracer,
    private val sessionIds: SessionIdSequence,
) : FrameSource {
    private val channel =
        Channel<SourceEvent>(Channel.UNLIMITED) { event ->
            if (event is SourceEvent.FrameAvailable) event.frame.image.close()
        }
    override val events: Flow<SourceEvent> = channel.receiveAsFlow()

    private var worker: Worker? = null

    override suspend fun open() =
        withContext(Dispatchers.IO) {
            stopWorker()
            val next = Worker(sessionIds.next())
            worker = next
            next.thread.start()
        }

    override suspend fun close() =
        withContext(Dispatchers.IO) {
            stopWorker()
            // Frames still queued were never delivered: release them now (ADR-010).
            while (true) {
                val event = channel.tryReceive().getOrNull() ?: break
                if (event is SourceEvent.FrameAvailable) event.frame.image.close()
            }
        }

    private fun stopWorker() {
        val current = worker ?: return
        worker = null
        current.stop()
    }

    /** One stream: a decoder thread and everything it touches, which no other thread reads. */
    private inner class Worker(
        private val session: SessionId,
    ) {
        @Volatile private var stopped = false
        val thread = Thread({ run() }, THREAD_NAME)

        private val frameIds = FrameIdSequence()
        private val schedule = PlaybackSchedule()
        private var pool: I420FramePool? = null
        private var connected = false
        private var width = 0
        private var height = 0

        fun stop() {
            stopped = true
            thread.interrupt()
            // The thread releases the codec and the extractor in its own finally; wait for it, but not for ever.
            thread.join(JOIN_TIMEOUT_MILLIS)
            if (thread.isAlive) Log.w(TAG, "The decoder thread did not end within $JOIN_TIMEOUT_MILLIS ms")
        }

        private fun run() {
            val extractor = MediaExtractor()
            var codec: MediaCodec? = null
            try {
                extractor.setDataSource(file.path)
                val track = videoTrack(extractor)
                extractor.selectTrack(track)
                val format = extractor.getTrackFormat(track)
                val mime = checkNotNull(format.getString(MediaFormat.KEY_MIME))
                format.setInteger(
                    MediaFormat.KEY_COLOR_FORMAT,
                    MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible,
                )
                codec = MediaCodec.createDecoderByType(mime)
                codec.configure(format, null, null, 0)
                codec.start()
                width = format.getInteger(MediaFormat.KEY_WIDTH)
                height = format.getInteger(MediaFormat.KEY_HEIGHT)
                decode(extractor, codec)
            } catch (e: InterruptedException) {
                Thread.currentThread().interrupt()
            } catch (e: Exception) {
                if (!stopped) fail(e)
            } finally {
                runCatching { codec?.stop() }
                runCatching { codec?.release() }
                extractor.release()
            }
        }

        private fun fail(error: Exception) {
            Log.e(TAG, "Cannot play ${file.name}", error)
            val reason = if (connected) DisconnectReason.STREAM_ERROR else DisconnectReason.OPEN_FAILED
            channel.trySend(SourceEvent.Disconnected(reason))
        }

        private fun videoTrack(extractor: MediaExtractor): Int {
            for (index in 0 until extractor.trackCount) {
                val mime = extractor.getTrackFormat(index).getString(MediaFormat.KEY_MIME)
                if (mime != null && mime.startsWith(VIDEO_MIME_PREFIX)) return index
            }
            throw IllegalArgumentException("No video track in ${file.name}")
        }

        /** Feeds the codec and delivers its output until [stopped]; restarts the file at its end. */
        private fun decode(
            extractor: MediaExtractor,
            codec: MediaCodec,
        ) {
            val info = MediaCodec.BufferInfo()
            var inputDone = false
            while (!stopped) {
                if (!inputDone) inputDone = feed(extractor, codec)
                val index = codec.dequeueOutputBuffer(info, DEQUEUE_TIMEOUT_MICROS)
                when {
                    index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        readOutputSize(codec.outputFormat)
                    }

                    index >= 0 -> {
                        val ended = info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
                        if (info.size > 0 && !ended) {
                            deliver(codec, index, info.presentationTimeUs)
                        } else {
                            codec.releaseOutputBuffer(index, false)
                        }
                        if (ended) {
                            // Seamless loop: flush the decoder, seek to the first sample, keep the session going.
                            codec.flush()
                            extractor.seekTo(0, MediaExtractor.SEEK_TO_CLOSEST_SYNC)
                            schedule.looped()
                            inputDone = false
                        }
                    }
                }
            }
        }

        /** Queues the next sample, or the end-of-stream marker; true once the end was queued. */
        private fun feed(
            extractor: MediaExtractor,
            codec: MediaCodec,
        ): Boolean {
            val index = codec.dequeueInputBuffer(DEQUEUE_TIMEOUT_MICROS)
            if (index < 0) return false
            val buffer = checkNotNull(codec.getInputBuffer(index))
            val size = extractor.readSampleData(buffer, 0)
            if (size < 0) {
                codec.queueInputBuffer(index, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                return true
            }
            codec.queueInputBuffer(index, 0, size, extractor.sampleTime, 0)
            extractor.advance()
            return false
        }

        /** The picture size is the crop rectangle of the output format: the codec's buffers may be padded. */
        private fun readOutputSize(format: MediaFormat) {
            val left = format.optionalInt(KEY_CROP_LEFT) ?: 0
            val top = format.optionalInt(KEY_CROP_TOP) ?: 0
            check(left == 0 && top == 0) { "Cropped decoder output (left $left, top $top) is not supported" }
            val right = format.optionalInt(KEY_CROP_RIGHT) ?: (format.getInteger(MediaFormat.KEY_WIDTH) - 1)
            val bottom = format.optionalInt(KEY_CROP_BOTTOM) ?: (format.getInteger(MediaFormat.KEY_HEIGHT) - 1)
            width = right + 1
            height = bottom + 1
        }

        private fun MediaFormat.optionalInt(key: String): Int? = if (containsKey(key)) getInteger(key) else null

        /** Waits for the frame's time, copies it into a pooled buffer, releases the codec's and emits the frame. */
        private fun deliver(
            codec: MediaCodec,
            index: Int,
            presentationUs: Long,
        ) {
            val wait = schedule.waitNanos(presentationUs, clock.nowNanos())
            if (wait > 0) TimeUnit.NANOSECONDS.sleep(wait)
            val image = checkNotNull(codec.getOutputImage(index)) { "The decoder gave no image" }
            val arrival = clock.nowNanos()
            // Allocates only when the size changed: never per frame in steady state.
            val evenWidth = width and 1.inv()
            val evenHeight = height and 1.inv()
            pool = poolFor(pool, evenWidth, evenHeight, POOL_CAPACITY)
            val buffer =
                try {
                    tracer.trace(TraceSection.CAPTURE) { copy(image) }
                } finally {
                    image.close()
                    codec.releaseOutputBuffer(index, false)
                }
            if (buffer == null) return
            if (!connected) startSession(evenWidth, evenHeight)
            val frame = Frame(frameIds.next(), session, arrival, evenWidth, evenHeight, buffer)
            if (channel.trySend(SourceEvent.FrameAvailable(frame)).isFailure) buffer.close()
        }

        private fun copy(image: Image) =
            image.planes.let { planes ->
                pool?.copy(
                    SourcePlane(planes[0].buffer, planes[0].rowStride, planes[0].pixelStride),
                    SourcePlane(planes[1].buffer, planes[1].rowStride, planes[1].pixelStride),
                    SourcePlane(planes[2].buffer, planes[2].rowStride, planes[2].pixelStride),
                )
            }

        private fun startSession(
            frameWidth: Int,
            frameHeight: Int,
        ) {
            frameIds.restart()
            connected = true
            channel.trySend(
                SourceEvent.Connected(
                    StreamSpec(frameWidth, frameHeight, ImageFormat.YUV_420_888, TimestampSource.ARRIVAL),
                    session,
                ),
            )
        }
    }

    private companion object {
        const val TAG = "VideoFileSource"
        const val THREAD_NAME = "file-decode"
        const val VIDEO_MIME_PREFIX = "video/"
        const val DEQUEUE_TIMEOUT_MICROS = 10_000L
        const val JOIN_TIMEOUT_MILLIS = 2_000L
        const val KEY_CROP_LEFT = "crop-left"
        const val KEY_CROP_RIGHT = "crop-right"
        const val KEY_CROP_TOP = "crop-top"
        const val KEY_CROP_BOTTOM = "crop-bottom"

        /** Pending, inferring, displayed, plus one being filled (ADR-010). */
        const val POOL_CAPACITY = 4
    }
}
