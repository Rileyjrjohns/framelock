package dev.framelock.live.ui

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import dev.framelock.kernel.telemetry.ElementId

/**
 * Reports a completed tap on this element to [onTrack] (`TrackUiInteraction`, wired in `:app`, ADR-004). It only
 * observes the gesture, on the initial pass, so the element's own click handling is unchanged. Tracking a new
 * element means adding it to [ElementId]: a reviewed change.
 */
fun Modifier.track(
    element: ElementId,
    onTrack: (ElementId) -> Unit,
): Modifier =
    pointerInput(element, onTrack) {
        awaitEachGesture {
            awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            if (waitForUpOrCancellation(PointerEventPass.Initial) != null) onTrack(element)
        }
    }
