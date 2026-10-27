package dev.framelock.app

import android.util.Log
import dev.framelock.models.store.FileModelRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/** Applies debug-only launch options without changing normal startup. */
internal class DebugLaunchHandler(
    private val environment: AppEnvironment,
    private val scope: CoroutineScope,
    private val backendOverride: BackendOverride,
    private val sources: SourceWiring,
    private val modelRepository: FileModelRepository,
) {
    /**
     * Applies the extras of a debug launch (`DebugLaunch`): the backend, the source, then, once a model is active,
     * the requested start callback. Does nothing in a release build. Called once per launch, from the activity.
     */
    fun apply(
        launch: DebugLaunch,
        start: () -> Unit,
    ) {
        if (!environment.debuggable || !launch.isActive) return
        scope.launch(Dispatchers.IO) {
            launch.backend?.let { backendOverride.preference = it }
            launch.sourceId?.let { id ->
                sources.selection.options()
                sources.selection.select(id)
                if (sources.selection.current.value.id != id) Log.w(TAG, "Debug launch: source '$id' is not available")
            }
            if (launch.autostart) {
                val ready = withTimeoutOrNull(MODEL_WAIT) { while (modelRepository.active() == null) delay(MODEL_POLL) }
                if (ready == null) {
                    Log.w(TAG, "Debug launch: no active model after $MODEL_WAIT, not starting")
                } else {
                    withContext(Dispatchers.Main) { start() }
                }
            }
        }
    }

    private companion object {
        const val TAG = "DebugLaunch"
        val MODEL_WAIT = 60.seconds
        val MODEL_POLL = 200.milliseconds
    }
}
