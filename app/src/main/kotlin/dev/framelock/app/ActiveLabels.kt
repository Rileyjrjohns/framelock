package dev.framelock.app

import dev.framelock.kernel.model.ModelVersion
import dev.framelock.live.domain.inference.LabelId

/**
 * Class names of the active model for the overlay. [labelOf] is called while drawing, so it never reads a file: it
 * answers from the labels cached by [refresh], which `:app` calls off the main thread at startup and after every swap.
 */
internal class ActiveLabels(
    private val activeVersion: () -> ModelVersion?,
    private val read: (ModelVersion) -> List<String>,
    private val onUnreadable: (Throwable) -> Unit,
) {
    private class Cached(
        val version: ModelVersion?,
        val labels: List<String>,
    )

    @Volatile private var cached = Cached(null, emptyList())

    /** Reloads when the active version is not the cached one. Blocking: do not call it from the main thread. */
    fun refresh() {
        val version = activeVersion()
        if (version == cached.version) return
        val labels =
            version
                ?.let { runCatching { read(it) }.onFailure(onUnreadable).getOrNull() }
                .orEmpty()
        cached = Cached(version, labels)
    }

    fun labelOf(label: LabelId): String = cached.labels.getOrNull(label.value) ?: "#${label.value}"
}
