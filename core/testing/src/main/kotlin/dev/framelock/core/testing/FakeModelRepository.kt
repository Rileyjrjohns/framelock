package dev.framelock.core.testing

import dev.framelock.core.domain.model.ModelArtifact
import dev.framelock.core.domain.model.ModelManifest
import dev.framelock.core.domain.model.ModelRejectionReason
import dev.framelock.core.domain.model.ModelSource
import dev.framelock.core.domain.model.ModelVersion
import dev.framelock.core.domain.port.ModelRepository

/**
 * An in-memory [ModelRepository]. Nothing is read from a [ModelSource], hashed or verified.
 *
 * Activating a version pushes the active one on a stack of previous versions; [rollback] pops it. A failed operation
 * (an injected failure, an unknown version, an empty stack) is returned as `Result.failure` and leaves the state as
 * it was. Thread-safe.
 */
class FakeModelRepository(
    initial: List<ModelArtifact> = emptyList(),
    active: ModelVersion? = null,
) : ModelRepository {
    enum class Operation { IMPORT, ACTIVATE, ROLLBACK }

    init {
        require(active == null || initial.any { it.manifest.version == active }) {
            "The active version $active is not among the initial models"
        }
    }

    private val lock = Any()
    private val artifacts = initial.associateByTo(LinkedHashMap()) { it.manifest.version }
    private var activeVersion: ModelVersion? = active
    private val previousVersions = ArrayDeque<ModelVersion>()
    private val pendingFailures = Operation.entries.associateWith { ArrayDeque<Exception>() }
    private val activations = mutableListOf<ModelVersion>()
    private var importCallCount = 0
    private var rollbackCallCount = 0

    /**
     * What [import] does with a source. By default it succeeds with a valid manifest whose version is the source's
     * display name, with characters outside `A-Za-z0-9._-` replaced by `_`. A manifest it returns is added to the
     * repository; a failure leaves the repository unchanged.
     */
    @Volatile
    var importResult: (ModelSource) -> Result<ModelManifest> = { source ->
        Result.success(TestModels.artifact(versionNameOf(source.displayName)).manifest)
    }

    /** Every version passed to [activate], including calls that failed. */
    val activateCalls: List<ModelVersion> get() = synchronized(lock) { activations.toList() }

    val importCalls: Int get() = synchronized(lock) { importCallCount }

    val rollbackCalls: Int get() = synchronized(lock) { rollbackCallCount }

    /** Makes the next call of [operation] return [error] as a failure. Calls queue: one failure per call. */
    fun failNext(
        operation: Operation,
        error: Exception,
    ) {
        synchronized(lock) { pendingFailures.getValue(operation).addLast(error) }
    }

    override fun active(): ModelArtifact? = synchronized(lock) { activeVersion?.let(artifacts::get) }

    override fun previous(): ModelArtifact? = synchronized(lock) { previousVersions.lastOrNull()?.let(artifacts::get) }

    override fun artifact(version: ModelVersion): ModelArtifact? = synchronized(lock) { artifacts[version] }

    override fun list(): List<ModelManifest> = synchronized(lock) { artifacts.values.map { it.manifest } }

    override suspend fun import(source: ModelSource): Result<ModelManifest> {
        synchronized(lock) {
            importCallCount++
            pendingFailures.getValue(Operation.IMPORT).removeFirstOrNull()
        }?.let { return Result.failure(it) }
        val result = importResult(source)
        result.onSuccess { manifest ->
            synchronized(lock) { artifacts[manifest.version] = ModelArtifact(manifest, locationOf(manifest.version)) }
        }
        return result
    }

    override suspend fun activate(version: ModelVersion): Result<Unit> =
        synchronized(lock) {
            activations += version
            val injected = pendingFailures.getValue(Operation.ACTIVATE).removeFirstOrNull()
            when {
                injected != null -> {
                    Result.failure(injected)
                }

                version !in artifacts -> {
                    Result.failure(rejection(ModelRejectionReason.UNKNOWN_VERSION, "$version"))
                }

                version == activeVersion -> {
                    Result.success(Unit)
                }

                else -> {
                    activeVersion?.let(previousVersions::addLast)
                    activeVersion = version
                    Result.success(Unit)
                }
            }
        }

    override suspend fun rollback(): Result<Unit> =
        synchronized(lock) {
            rollbackCallCount++
            val injected = pendingFailures.getValue(Operation.ROLLBACK).removeFirstOrNull()
            when {
                injected != null -> {
                    Result.failure(injected)
                }

                previousVersions.isEmpty() -> {
                    Result.failure(rejection(ModelRejectionReason.NO_PREVIOUS_VERSION))
                }

                else -> {
                    activeVersion = previousVersions.removeLast()
                    Result.success(Unit)
                }
            }
        }

    private fun rejection(
        reason: ModelRejectionReason,
        detail: String = "",
    ) = IllegalStateException(listOf(reason.name, detail).filter { it.isNotEmpty() }.joinToString(": "))

    private companion object {
        val INVALID_VERSION_CHARS = Regex("[^A-Za-z0-9._-]")
        const val MAX_VERSION_LENGTH = 64
        const val FALLBACK_VERSION = "imported"

        fun versionNameOf(displayName: String): String =
            displayName.replace(INVALID_VERSION_CHARS, "_").take(MAX_VERSION_LENGTH).ifEmpty { FALLBACK_VERSION }

        fun locationOf(version: ModelVersion) = "/fake/models/${version.value}/model.tflite"
    }
}
