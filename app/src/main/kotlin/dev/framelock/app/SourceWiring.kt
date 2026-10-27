package dev.framelock.app

import android.content.Context
import dev.framelock.kernel.port.Clock
import dev.framelock.kernel.port.Tracer
import dev.framelock.live.capture.camerax.CameraFacing
import dev.framelock.live.capture.camerax.CameraXCameras
import dev.framelock.live.capture.camerax.CameraXFrameSource
import dev.framelock.live.capture.file.VideoFileFrameSource
import dev.framelock.live.capture.uvc.UvcDevices
import dev.framelock.live.capture.uvc.UvcFrameSource
import dev.framelock.live.domain.frame.SessionIdSequence
import kotlinx.coroutines.CoroutineScope

/** All capture sources share one session sequence and one current selection. */
internal class SourceWiring(
    context: Context,
    environment: AppEnvironment,
    clock: Clock,
    tracer: Tracer,
    scope: CoroutineScope,
) {
    private val appContext = context.applicationContext
    private val debuggable = environment.debuggable
    private val testVideo = environment.testVideo

    // One sequence for every source: the pipeline refuses a session id that does not increase (ADR-016).
    private val sessionIds = SessionIdSequence()
    private val labels =
        SourceLabels(
            auto = context.getString(R.string.source_auto),
            back = context.getString(R.string.source_back),
            front = context.getString(R.string.source_front),
            external = context.getString(R.string.source_external),
        )

    // Listed afresh each time the picker opens: devices come and go.
    val selection =
        SourceSelection(labels) {
            buildSourceOptions(
                labels,
                hasBack = CameraXCameras.has(appContext, CameraFacing.BACK),
                hasFront = CameraXCameras.has(appContext, CameraFacing.FRONT),
                hasExternal = CameraXCameras.has(appContext, CameraFacing.EXTERNAL),
                uvcDevices = UvcDevices.list(appContext),
                hasTestVideo = debuggable && testVideo.isFile,
            )
        }
    private val uvcSource =
        UvcFrameSource(
            appContext,
            clock,
            tracer,
            sessionIds,
            selectedDeviceId = { (selection.choice as? SourceChoice.Uvc)?.deviceId },
        )
    val source =
        SelectingFrameSource(
            external = CameraXFrameSource(appContext, clock, tracer, sessionIds, facing = CameraFacing.EXTERNAL),
            uvc = uvcSource,
            back = CameraXFrameSource(appContext, clock, tracer, sessionIds),
            front = CameraXFrameSource(appContext, clock, tracer, sessionIds, facing = CameraFacing.FRONT),
            file = if (debuggable) VideoFileFrameSource(testVideo, clock, tracer, sessionIds) else null,
            choice = { selection.choice },
            hasExternalCamera = { CameraXCameras.has(appContext, CameraFacing.EXTERNAL) },
            hasUvcDevice = { UvcDevices.isAttached(appContext) },
            labels = labels,
            uvcDescription = uvcSource.description,
            scope = scope,
        )
}
