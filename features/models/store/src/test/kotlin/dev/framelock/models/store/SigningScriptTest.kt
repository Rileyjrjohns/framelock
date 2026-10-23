package dev.framelock.models.store

import dev.framelock.kernel.model.ModelVersion
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * `scripts/sign-model.sh` (bash, openssl, zip) writes what the store reads: a DER ECDSA signature that
 * `SHA256withECDSA` accepts, a manifest the parser accepts, a zip that follows the rules. Skipped when a tool is missing.
 */
class SigningScriptTest {
    @TempDir
    lateinit var dir: File

    private val script = File("../../../scripts/sign-model.sh").absoluteFile

    private fun run(vararg args: String): Int {
        val process =
            ProcessBuilder("bash", script.path, *args)
                .redirectErrorStream(true)
                .redirectOutput(File(dir, "script.log"))
                .start()
        check(process.waitFor(60, TimeUnit.SECONDS)) { "script timed out" }
        return process.exitValue()
    }

    private fun toolsAvailable(): Boolean =
        script.isFile &&
            listOf("openssl", "zip").all { tool ->
                runCatching {
                    ProcessBuilder(
                        tool,
                        "-h",
                    ).redirectErrorStream(true).start().also { it.inputStream.readBytes() }
                }.isSuccess
            }

    @Test
    fun `a package signed by the script imports with the public key the script wrote`() =
        runTest {
            assumeTrue(toolsAvailable(), "needs bash, openssl and zip")
            val keys = File(dir, "keys")
            assertEquals(0, run("--new-key", keys.path, "--name", "test"))
            val model = File(dir, "m.tflite").apply { writeBytes(TestPackages.MODEL_BYTES) }
            val labels = File(dir, "labels.txt").apply { writeText(TestPackages.LABELS_TEXT) }
            val out = File(dir, "out/m.flmodel")

            val status =
                run(
                    "--model",
                    model.path,
                    "--version",
                    "script-1",
                    "--input",
                    "1x320x320x3:UINT8",
                    "--outputs",
                    "1x25x4:FLOAT32;1:FLOAT32",
                    "--key",
                    File(keys, "test.pem").path,
                    "--labels",
                    labels.path,
                    "--out",
                    out.path,
                )

            assertEquals(0, status, File(dir, "script.log").readText())
            val repository =
                FileModelRepository(
                    File(dir, "files"),
                    listOf(TrustedKeys.parsePem(File(keys, "test.pub.pem").readText())),
                )
            val manifest = repository.import(TestPackages.source(out.readBytes())).getOrThrow()
            assertEquals(ModelVersion("script-1"), manifest.version)
            assertEquals(TestPackages.INPUT, manifest.inputSpec)
            assertEquals(TestPackages.OUTPUTS, manifest.outputSpec)
            assertEquals(listOf("person", "bicycle", "car"), repository.labels(manifest.version))
        }

    @Test
    fun `a key that was not used to sign is refused`() =
        runTest {
            assumeTrue(toolsAvailable(), "needs bash, openssl and zip")
            val keys = File(dir, "keys")
            run("--new-key", keys.path, "--name", "signer")
            run("--new-key", keys.path, "--name", "other")
            val model = File(dir, "m.tflite").apply { writeBytes(TestPackages.MODEL_BYTES) }
            val out = File(dir, "m.flmodel")
            run(
                "--model",
                model.path,
                "--version",
                "v1",
                "--input",
                "1x2:UINT8",
                "--outputs",
                "1:FLOAT32",
                "--key",
                File(keys, "signer.pem").path,
                "--out",
                out.path,
            )
            val repository =
                FileModelRepository(
                    File(dir, "files"),
                    listOf(TrustedKeys.parsePem(File(keys, "other.pub.pem").readText())),
                )

            val result = repository.import(TestPackages.source(out.readBytes()))

            assertTrue(result.isFailure)
        }

    @Test
    fun `the script never overwrites a key`() {
        assumeTrue(toolsAvailable(), "needs bash, openssl and zip")
        val keys = File(dir, "keys")
        assertEquals(0, run("--new-key", keys.path, "--name", "k"))

        assertEquals(1, run("--new-key", keys.path, "--name", "k"))
    }
}
