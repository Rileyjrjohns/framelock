package dev.framelock.models.ui

import android.content.ContentResolver
import android.net.Uri
import android.provider.OpenableColumns
import dev.framelock.models.domain.ModelSource
import java.io.IOException
import java.io.InputStream

/** A model the user picked with the system file picker. The name is only shown; the repository verifies the content. */
class UriModelSource(
    private val resolver: ContentResolver,
    private val uri: Uri,
) : ModelSource {
    // Lazy: the picker callback builds this source on the main thread; the query runs where the name is first read.
    override val displayName: String by lazy {
        runCatching {
            resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            }
        }.getOrNull() ?: uri.lastPathSegment.orEmpty()
    }

    override fun open(): InputStream = resolver.openInputStream(uri) ?: throw IOException("Cannot open $uri")
}
