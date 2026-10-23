package dev.framelock.app

import android.content.res.AssetManager
import dev.framelock.models.store.TrustedKeys
import java.security.PublicKey

/**
 * The keys the app trusts to sign models: every `.pub.pem` file in `assets/keys` (ECDSA P-256, X.509 PEM). The debug build adds
 * a development key (`scripts/fetch-model.sh`); a release build trusts none until its key is added to
 * `app/src/main/assets/keys/` (README), and then refuses every import. A file that cannot be parsed is skipped and
 * reported: trusting less is the safe failure.
 */
internal object TrustedKeyAssets {
    private const val DIRECTORY = "keys"
    private const val SUFFIX = ".pub.pem"

    fun load(
        assets: AssetManager,
        onInvalid: (String, Throwable) -> Unit,
    ): List<PublicKey> =
        assets
            .list(DIRECTORY)
            .orEmpty()
            .filter { it.endsWith(SUFFIX) }
            .sorted()
            .mapNotNull { name ->
                runCatching { TrustedKeys.parsePem(assets.open("$DIRECTORY/$name").use { it.reader().readText() }) }
                    .onFailure { onInvalid(name, it) }
                    .getOrNull()
            }
}
