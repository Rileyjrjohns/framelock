package dev.framelock.models.store

import dev.framelock.kernel.model.ModelVersion
import dev.framelock.kernel.model.TensorDataType
import dev.framelock.kernel.model.TensorSpec
import dev.framelock.models.domain.ModelSource
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.Signature
import java.security.spec.ECGenParameterSpec
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** Builds `.flmodel` packages for tests, signed with keys generated here. */
internal object TestPackages {
    val INPUT = TensorSpec(listOf(1, 320, 320, 3), TensorDataType.UINT8)
    val OUTPUTS =
        listOf(TensorSpec(listOf(1, 25, 4), TensorDataType.FLOAT32), TensorSpec(listOf(1), TensorDataType.FLOAT32))
    val MODEL_BYTES = ByteArray(2_048) { (it % 251).toByte() }
    val LABELS_TEXT = "person\nbicycle\n\ncar\n"

    fun newKeyPair(): KeyPair =
        KeyPairGenerator.getInstance("EC").apply { initialize(ECGenParameterSpec("secp256r1")) }.generateKeyPair()

    fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    fun sign(
        key: KeyPair,
        data: ByteArray,
    ): ByteArray =
        Signature.getInstance("SHA256withECDSA").run {
            initSign(key.private)
            update(data)
            sign()
        }

    fun manifest(
        version: String = "v1",
        model: ByteArray = MODEL_BYTES,
        labels: String? = null,
    ) = PackageManifest(
        ModelVersion(version),
        sha256(model),
        INPUT,
        OUTPUTS,
        labels?.let { sha256(it.toByteArray()) },
    )

    /** A package whose parts can each be replaced to break it. */
    fun build(
        key: KeyPair,
        version: String = "v1",
        model: ByteArray = MODEL_BYTES,
        labels: String? = null,
        manifestBytes: ByteArray = ManifestFile.serialize(manifest(version, model, labels)),
        signature: ByteArray? = sign(key, manifestBytes),
        modelInPackage: ByteArray? = model,
        labelsInPackage: String? = labels,
        extra: List<Pair<String, ByteArray>> = emptyList(),
    ): ByteArray {
        val entries =
            buildList {
                modelInPackage?.let { add(PackageFiles.MODEL to it) }
                add(PackageFiles.MANIFEST to manifestBytes)
                signature?.let { add(PackageFiles.SIGNATURE to it) }
                labelsInPackage?.let { add(PackageFiles.LABELS to it.toByteArray()) }
                addAll(extra)
            }
        return zip(entries)
    }

    fun zip(entries: List<Pair<String, ByteArray>>): ByteArray {
        val bytes = ByteArrayOutputStream()
        ZipOutputStream(bytes).use { out ->
            entries.forEach { (name, content) ->
                out.putNextEntry(ZipEntry(name))
                out.write(content)
                out.closeEntry()
            }
        }
        return bytes.toByteArray()
    }

    fun source(
        bytes: ByteArray,
        name: String = "test.flmodel",
    ): ModelSource =
        object : ModelSource {
            override val displayName = name

            override fun open(): InputStream = ByteArrayInputStream(bytes)
        }
}
