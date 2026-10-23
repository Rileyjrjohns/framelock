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
     * True when Camera2 exposes an external camera (`LENS_FACING_EXTERNAL`), which a device with
     * `android.hardware.camera.external` does for a UVC capture card. `LENS_FACING_EXTERNAL` is behind CameraX's
     * `ExperimentalLensFacing` opt-in.
     */
    @androidx.annotation.OptIn(ExperimentalLensFacing::class)
    suspend fun hasExternal(context: Context): Boolean {
        val provider = ProcessCameraProvider.awaitInstance(context)
        val external = CameraSelector.Builder().requireLensFacing(CameraSelector.LENS_FACING_EXTERNAL).build()
        return try {
            provider.hasCamera(external)
        } catch (e: CameraInfoUnavailableException) {
            false
        }
    }
}
