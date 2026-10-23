package dev.framelock.models.store

import java.security.GeneralSecurityException
import java.security.KeyFactory
import java.security.PublicKey
import java.security.Signature
import java.security.spec.X509EncodedKeySpec
import java.util.Base64

/** Public keys the app trusts (ADR-003): ECDSA P-256, X.509 SubjectPublicKeyInfo, as PEM. */
object TrustedKeys {
    /** @throws IllegalArgumentException when [pem] is not a PEM-encoded EC public key. */
    fun parsePem(pem: String): PublicKey {
        val body =
            pem
                .lineSequence()
                .map(String::trim)
                .filter { it.isNotEmpty() && !it.startsWith("-----") }
                .joinToString("")
        return try {
            val der = Base64.getDecoder().decode(body)
            KeyFactory.getInstance("EC").generatePublic(X509EncodedKeySpec(der))
        } catch (e: GeneralSecurityException) {
            throw IllegalArgumentException("Not an EC public key in PEM form", e)
        }
    }
}

/** `SHA256withECDSA` over exact bytes, accepted when any trusted key verifies it. DER signatures, as openssl writes. */
internal class SignatureVerifier(
    private val keys: List<PublicKey>,
) {
    fun verify(
        data: ByteArray,
        signature: ByteArray,
    ): Boolean =
        keys.any { key ->
            try {
                Signature.getInstance(ALGORITHM).run {
                    initVerify(key)
                    update(data)
                    verify(signature)
                }
            } catch (
                // A malformed signature or a key of another type is a failed check, not an error.
                @Suppress("SwallowedException") e: GeneralSecurityException,
            ) {
                false
            }
        }

    private companion object {
        const val ALGORITHM = "SHA256withECDSA"
    }
}
