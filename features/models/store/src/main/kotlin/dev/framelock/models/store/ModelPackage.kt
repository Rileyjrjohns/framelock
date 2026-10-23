package dev.framelock.models.store

import dev.framelock.kernel.model.ModelVersion
import dev.framelock.kernel.model.TensorDataType
import dev.framelock.kernel.model.TensorSpec
import dev.framelock.models.domain.ModelRejectedException
import dev.framelock.models.domain.ModelRejectionReason
import java.io.ByteArrayInputStream
import java.io.EOFException
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.util.Properties
import java.util.zip.ZipException
import java.util.zip.ZipInputStream

/** The files of a `.flmodel` package, and of a version directory on disk (ADR-003, amendment M5). */
internal object PackageFiles {
    const val MODEL = "model.tflite"
    const val MANIFEST = "manifest.properties"
    const val SIGNATURE = "manifest.sig"
    const val LABELS = "labels.txt"

    /** Size limits per entry, measured on the bytes actually read: a zip bomb declares nothing we trust. */
    val LIMITS =
        mapOf(
            MODEL to 256L * 1024 * 1024,
            MANIFEST to 16L * 1024,
            SIGNATURE to 256L,
            LABELS to 1024L * 1024,
        )
}

/** Text form of a [TensorSpec]: dimensions joined by `x`, a colon, the data type, e.g. `1x320x320x3:UINT8`. */
internal object TensorSpecCodec {
    fun encode(spec: TensorSpec): String = spec.shape.joinToString("x") + ":" + spec.dataType.name

    /** @throws IllegalArgumentException when [text] is not a valid spec. */
    fun decode(text: String): TensorSpec {
        val parts = text.split(':')
        require(parts.size == 2) { "Tensor spec must be <shape>:<type>" }
        return TensorSpec(parts[0].split('x').map { it.toInt() }, TensorDataType.valueOf(parts[1]))
    }
}

/** What `manifest.properties` says. The signature covers these bytes, hence the version, specs and, by hash, the files. */
internal data class PackageManifest(
    val version: ModelVersion,
    val sha256: String,
    val input: TensorSpec,
    val outputs: List<TensorSpec>,
    val labelsSha256: String?,
)

/**
 * `manifest.properties` is a `java.util.Properties` file (ISO-8859-1, values are ASCII) with `version`, `sha256`
 * (of `model.tflite`), `input`, `outputs` (specs separated by `;`) and, when `labels.txt` is present, `labels.sha256`.
 * Unknown keys are ignored: they are signed like the rest.
 */
internal object ManifestFile {
    private val SHA256 = Regex("[0-9a-f]{64}")

    /** @throws ModelRejectedException with `MALFORMED_MANIFEST` for any missing, invalid or inconsistent field. */
    fun parse(bytes: ByteArray): PackageManifest {
        val properties = Properties()
        try {
            properties.load(ByteArrayInputStream(bytes))
            val sha256 = required(properties, "sha256")
            val labelsSha256 = properties.getProperty("labels.sha256")
            require(SHA256.matches(sha256)) { "sha256" }
            require(labelsSha256 == null || SHA256.matches(labelsSha256)) { "labels.sha256" }
            val outputs = required(properties, "outputs").split(';').map(TensorSpecCodec::decode)
            return PackageManifest(
                version = ModelVersion(required(properties, "version")),
                sha256 = sha256,
                input = TensorSpecCodec.decode(required(properties, "input")),
                outputs = outputs,
                labelsSha256 = labelsSha256,
            )
        } catch (
            // Properties.load, require, ModelVersion, TensorSpec and valueOf all signal bad input this way.
            @Suppress("SwallowedException") e: IllegalArgumentException,
        ) {
            throw ModelRejectedException(ModelRejectionReason.MALFORMED_MANIFEST)
        } catch (
            @Suppress("SwallowedException") e: IOException,
        ) {
            throw ModelRejectedException(ModelRejectionReason.MALFORMED_MANIFEST)
        }
    }

    /** Deterministic text, for tests and tools; the signing script writes the same keys. */
    fun serialize(manifest: PackageManifest): ByteArray {
        val lines =
            buildList {
                add("version=${manifest.version.value}")
                add("sha256=${manifest.sha256}")
                add("input=${TensorSpecCodec.encode(manifest.input)}")
                add("outputs=${manifest.outputs.joinToString(";", transform = TensorSpecCodec::encode)}")
                manifest.labelsSha256?.let { add("labels.sha256=$it") }
            }
        return (lines.joinToString("\n") + "\n").toByteArray(Charsets.ISO_8859_1)
    }

    private fun required(
        properties: Properties,
        key: String,
    ): String = requireNotNull(properties.getProperty(key)?.trim()?.takeIf { it.isNotEmpty() }) { key }
}

/**
 * Streams a package into a directory, applying the zip rules: only the four known names, each at most once, no
 * directory entries, no path separators (so nothing can leave [into]), and a size limit per entry counted on the
 * bytes read.
 */
internal object PackageExtractor {
    private const val BUFFER = 64 * 1024

    /** @return the names written. @throws ModelRejectedException `MALFORMED_MANIFEST` when a rule is broken. */
    fun extract(
        input: InputStream,
        into: File,
    ): Set<String> {
        val written = mutableSetOf<String>()
        try {
            ZipInputStream(input).use { zip ->
                val buffer = ByteArray(BUFFER)
                while (true) {
                    val entry = zip.nextEntry ?: break
                    val limit = PackageFiles.LIMITS[entry.name]
                    if (limit == null || !written.add(entry.name)) throw malformed()
                    File(into, entry.name).outputStream().use { out -> copyLimited(zip, out, limit, buffer) }
                }
            }
        } catch (
            @Suppress("SwallowedException") e: ZipException,
        ) {
            throw malformed()
        } catch (
            // A truncated archive.
            @Suppress("SwallowedException") e: EOFException,
        ) {
            throw malformed()
        } catch (
            // An entry name that is not valid UTF-8.
            @Suppress("SwallowedException") e: IllegalArgumentException,
        ) {
            throw malformed()
        }
        return written
    }

    private fun copyLimited(
        from: InputStream,
        to: java.io.OutputStream,
        limit: Long,
        buffer: ByteArray,
    ) {
        var total = 0L
        while (true) {
            val read = from.read(buffer)
            if (read < 0) return
            total += read
            if (total > limit) throw malformed()
            to.write(buffer, 0, read)
        }
    }

    private fun malformed() = ModelRejectedException(ModelRejectionReason.MALFORMED_MANIFEST)
}
