package dev.framelock.app

import dev.framelock.live.domain.port.FrameSource

/** The one composite the app is allowed to host (ADR-016). */
class SelectingFrameSource : FrameSource

/** Not in the list: caught. */
class OtherSource : FrameSource
