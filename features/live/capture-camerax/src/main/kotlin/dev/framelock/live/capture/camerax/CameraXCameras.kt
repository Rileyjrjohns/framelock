package dev.framelock.live.capture.camerax

import android.content.Context
import androidx.camera.core.CameraInfoUnavailableException
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalLensFacing
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.lifecycle.awaitInstance

/** Questions about the cameras CameraX can see, for the composition root to choose a source (ADR-016). */
object CameraXCameras {
    /**
     * True when CameraX has a camera facing [facing]. For [CameraFacing.EXTERNAL] that means Camera2 exposes an external
     * camera (`LENS_FACING_EXTERNAL`), which a device with `android.hardware.camera.external` does for a UVC capture
     * card; `LENS_FACING_EXTERNAL` is behind CameraX's `ExperimentalLensFacing` opt-in.
     */
    @androidx.annotation.OptIn(ExperimentalLensFacing::class)
    suspend fun has(
        context: Context,
        facing: CameraFacing,
    ): Boolean {
        val provider = ProcessCameraProvider.awaitInstance(context)
        val selector =
            when (facing) {
                CameraFacing.BACK -> {
                    CameraSelector.DEFAULT_BACK_CAMERA
                }

                CameraFacing.FRONT -> {
                    CameraSelector.DEFAULT_FRONT_CAMERA
                }

                CameraFacing.EXTERNAL -> {
                    CameraSelector.Builder().requireLensFacing(CameraSelector.LENS_FACING_EXTERNAL).build()
                }
            }
        return try {
            provider.hasCamera(selector)
        } catch (e: CameraInfoUnavailableException) {
            false
        }
    }
}
