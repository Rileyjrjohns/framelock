package dev.framelock.live.ui.previews

import dev.framelock.live.ui.ControlsActions
import dev.framelock.live.ui.FrameCallbacks
import dev.framelock.live.ui.PipelineActions
import dev.framelock.live.ui.SourceActions

internal val PreviewActions = ControlsActions(PipelineActions({}, {}, {}), SourceActions({}, {}, {}, {}), {}, {}, {})
internal val PreviewFrames = FrameCallbacks({ _, _, _ -> null }, { it.value.toString() }, {})
