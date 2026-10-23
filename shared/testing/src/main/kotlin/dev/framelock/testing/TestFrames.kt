package dev.framelock.testing

import dev.framelock.live.domain.frame.Frame
import dev.framelock.live.domain.frame.FrameId
import dev.framelock.live.domain.frame.ImageFormat
import dev.framelock.live.domain.frame.SessionId
import dev.framelock.live.domain.frame.StreamSpec
import dev.framelock.live.domain.frame.TimestampSource

/** Builds frames whose buffers come from a [FakeImageBufferPool]. */
object TestFrames {
    private const val WIDTH = 640
    private const val HEIGHT = 480

    /** About 30 fps. */
    private const val FRAME_INTERVAL_NANOS = 33_000_000L

    val SPEC = StreamSpec(WIDTH, HEIGHT, ImageFormat.YUV_420_888, TimestampSource.REALTIME)

    /**
     * A frame with a buffer labelled `s<session>/f<id>`. The caller owns the buffer and must close it (ADR-010).
     * The capture time defaults to 30 fps from the start of the session.
     */
    @Suppress("LongParameterList") // A test-data builder: every parameter but the first two has a default.
    fun frame(
        pool: FakeImageBufferPool,
        id: Long,
        session: Long = 1,
        captureNanos: Long = id * FRAME_INTERVAL_NANOS,
        width: Int = WIDTH,
        height: Int = HEIGHT,
    ): Frame {
        val buffer = pool.acquire("s$session/f$id")
        var built = false
        try {
            return Frame(FrameId(id), SessionId(session), captureNanos, width, height, buffer).also { built = true }
        } finally {
            if (!built) buffer.close()
        }
    }
}
