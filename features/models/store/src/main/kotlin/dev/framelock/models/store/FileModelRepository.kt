package dev.framelock.models.store

import dev.framelock.kernel.model.ModelArtifact
import dev.framelock.kernel.model.ModelManifest
import dev.framelock.kernel.model.ModelVersion
import dev.framelock.models.domain.ModelRejectedException
import dev.framelock.models.domain.ModelRejectionReason
import dev.framelock.models.domain.ModelSource
import dev.framelock.models.domain.port.ModelRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.security.PublicKey
import java.util.Base64
import java.util.UUID

/**
 * Models on disk (ADR-003, amendment M5), all under [filesDir]:
 * - `models/<version>/{model.tflite, manifest.properties, manifest.sig, labels.txt?}`;
 * - `model-state/{active, previous}`: pointer files holding a version, replaced by an atomic rename;
 * - `model-staging/`: where an import is unpacked and checked before it is moved into `models/`. The three live in
 *   `filesDir`, so the move is a rename within one file system. They are separate directories because every token of
 *   [ModelVersion] is a valid directory name, including `active`.
 *
 * Import order: unpack with the zip rules, verify the signature of `manifest.properties` against [trustedKeys], parse
 * it, check the SHA-256 of the model and labels, then move the directory. Anything that fails leaves `models/` as it
 * was. Importing a version that is already installed is idempotent when `manifest.properties` is byte for byte the
 * same (nothing is written); with different content it is refused as `BAD_HASH`: a version names one model.
 *
 * `MALFORMED_MANIFEST` also covers a package that breaks the zip rules or lacks a file; a package without
 * `manifest.sig` is `BAD_SIGNATURE`. Reading a stored version does not check its signature again: the import is the
 * trust boundary and `filesDir` is private to the app.
 */
class FileModelRepository(
    private val filesDir: File,
    trustedKeys: List<PublicKey>,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) : ModelRepository {
    private val verifier = SignatureVerifier(trustedKeys)
    private val modelsDir get() = File(filesDir, "models")
    private val stagingDir get() = File(filesDir, "model-staging")
    private val stateDir get() = File(filesDir, "model-state")

    @Synchronized
    override fun active(): ModelArtifact? = readPointer(ACTIVE)?.let(::loadArtifact)

    @Synchronized
    override fun previous(): ModelArtifact? =
        readPointer(PREVIOUS)?.takeIf { it != readPointer(ACTIVE) }?.let(::loadArtifact)

    @Synchronized
    override fun artifact(version: ModelVersion): ModelArtifact? = loadArtifact(version)

    @Synchronized
    override fun list(): List<ModelManifest> =
        modelsDir
            .listFiles { file -> file.isDirectory }
            .orEmpty()
            .sortedBy { it.name }
            .mapNotNull { dir -> versionOrNull(dir.name)?.let(::loadArtifact)?.manifest }

    override suspend fun import(source: ModelSource): Result<ModelManifest> = attempt { importPackage(source) }

    override suspend fun activate(version: ModelVersion): Result<Unit> = attempt { switchTo(version) }

    override suspend fun rollback(): Result<Unit> = attempt { rollBackToPrevious() }

    /** The labels of [version]: `labels.txt` when the package has one, else those embedded in the model. */
    @Synchronized
    fun labels(version: ModelVersion): List<String> {
        val dir = File(modelsDir, version.value)
        val file = File(dir, PackageFiles.LABELS)
        return if (file.isFile) {
            file.readLines().filter { it.isNotBlank() }
        } else {
            EmbeddedLabels.read(File(dir, PackageFiles.MODEL))
        }
    }

    private suspend fun <T> attempt(block: () -> T): Result<T> =
        withContext(io) {
            try {
                Result.success(block())
            } catch (e: ModelRejectedException) {
                Result.failure(e)
            } catch (e: IOException) {
                Result.failure(e)
            }
        }

    @Synchronized
    private fun importPackage(source: ModelSource): ModelManifest {
        stagingDir.deleteRecursively()
        val staging = File(stagingDir, UUID.randomUUID().toString())
        check(staging.mkdirs()) { "Cannot create $staging" }
        try {
            source.open().use { PackageExtractor.extract(it, staging) }
            val manifestBytes = fileOrNull(staging, PackageFiles.MANIFEST) ?: throw rejected(MALFORMED)
            val signature =
                fileOrNull(staging, PackageFiles.SIGNATURE) ?: throw rejected(ModelRejectionReason.BAD_SIGNATURE)
            if (!verifier.verify(manifestBytes, signature)) throw rejected(ModelRejectionReason.BAD_SIGNATURE)
            val parsed = ManifestFile.parse(manifestBytes)
            checkHashes(staging, parsed)
            val manifest = toManifest(parsed, signature)
            val target = File(modelsDir, parsed.version.value)
            if (isInstalled(parsed.version)) {
                if (!manifestBytes.contentEquals(fileOrNull(target, PackageFiles.MANIFEST))) {
                    throw rejected(ModelRejectionReason.BAD_HASH)
                }
                return loadArtifact(parsed.version)?.manifest ?: manifest
            }
            target.deleteRecursively()
            modelsDir.mkdirs()
            Files.move(staging.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE)
            return manifest
        } finally {
            stagingDir.deleteRecursively()
        }
    }

    private fun checkHashes(
        dir: File,
        parsed: PackageManifest,
    ) {
        val model = File(dir, PackageFiles.MODEL)
        val labels = File(dir, PackageFiles.LABELS)
        if (!model.isFile || labels.isFile != (parsed.labelsSha256 != null)) throw rejected(MALFORMED)
        if (sha256(model) != parsed.sha256) throw rejected(ModelRejectionReason.BAD_HASH)
        if (parsed.labelsSha256 != null && sha256(labels) != parsed.labelsSha256) {
            throw rejected(ModelRejectionReason.BAD_HASH)
        }
    }

    @Synchronized
    private fun switchTo(version: ModelVersion) {
        if (loadArtifact(version) == null) throw rejected(ModelRejectionReason.UNKNOWN_VERSION)
        val current = readPointer(ACTIVE)
        if (current == version) return
        // `previous` first: if we stop in between, previous equals active, which previous() reports as none.
        current?.let { writePointer(PREVIOUS, it) }
        writePointer(ACTIVE, version)
    }

    @Synchronized
    private fun rollBackToPrevious() {
        val current = readPointer(ACTIVE)
        val target =
            readPointer(PREVIOUS)?.takeIf { it != current && loadArtifact(it) != null }
                ?: throw rejected(ModelRejectionReason.NO_PREVIOUS_VERSION)
        current?.let { writePointer(PREVIOUS, it) }
        writePointer(ACTIVE, target)
    }

    private fun isInstalled(version: ModelVersion): Boolean = loadArtifact(version) != null

    /** The artifact of a version directory that holds a model, a parsable manifest for that version and a signature. */
    private fun loadArtifact(version: ModelVersion): ModelArtifact? {
        val dir = File(modelsDir, version.value)
        val model = File(dir, PackageFiles.MODEL)
        val manifestBytes = fileOrNull(dir, PackageFiles.MANIFEST)
        val signature = fileOrNull(dir, PackageFiles.SIGNATURE)
        if (!model.isFile || manifestBytes == null || signature == null) return null
        return try {
            val parsed = ManifestFile.parse(manifestBytes)
            if (parsed.version != version) null else ModelArtifact(toManifest(parsed, signature), model.absolutePath)
        } catch (
            @Suppress("SwallowedException") e: ModelRejectedException,
        ) {
            null
        }
    }

    private fun toManifest(
        parsed: PackageManifest,
        signature: ByteArray,
    ): ModelManifest =
        try {
            ModelManifest(
                version = parsed.version,
                sha256 = parsed.sha256,
                signature = Base64.getEncoder().encodeToString(signature),
                inputSpec = parsed.input,
                outputSpec = parsed.outputs,
            )
        } catch (
            @Suppress("SwallowedException") e: IllegalArgumentException,
        ) {
            throw rejected(MALFORMED)
        }

    private fun readPointer(name: String): ModelVersion? =
        fileOrNull(stateDir, name)?.let { versionOrNull(String(it, Charsets.US_ASCII).trim()) }

    /** Writes next to the pointer, flushes to disk, then renames over it: a reader sees the old or the new value. */
    private fun writePointer(
        name: String,
        version: ModelVersion,
    ) {
        stateDir.mkdirs()
        val temp = File(stateDir, "$name.tmp")
        FileOutputStream(temp).use { out ->
            out.write(version.value.toByteArray(Charsets.US_ASCII))
            out.fd.sync()
        }
        Files.move(temp.toPath(), File(stateDir, name).toPath(), StandardCopyOption.ATOMIC_MOVE)
    }

    private fun versionOrNull(text: String): ModelVersion? =
        try {
            ModelVersion(text)
        } catch (
            @Suppress("SwallowedException") e: IllegalArgumentException,
        ) {
            null
        }

    private fun fileOrNull(
        dir: File,
        name: String,
    ): ByteArray? = File(dir, name).takeIf { it.isFile }?.readBytes()

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(BUFFER_SIZE)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun rejected(reason: ModelRejectionReason) = ModelRejectedException(reason)

    private companion object {
        const val ACTIVE = "active"
        const val PREVIOUS = "previous"
        val MALFORMED = ModelRejectionReason.MALFORMED_MANIFEST
        const val BUFFER_SIZE = 64 * 1024
    }
}
