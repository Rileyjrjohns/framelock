package dev.framelock.models.ui.components

import dev.framelock.models.domain.ModelRejectionReason
import dev.framelock.models.ui.R

internal fun reasonText(reason: ModelRejectionReason): Int =
    when (reason) {
        ModelRejectionReason.BAD_HASH -> R.string.reason_bad_hash
        ModelRejectionReason.BAD_SIGNATURE -> R.string.reason_bad_signature
        ModelRejectionReason.MALFORMED_MANIFEST -> R.string.reason_malformed_manifest
        ModelRejectionReason.IO_ERROR -> R.string.reason_io_error
        ModelRejectionReason.UNKNOWN_VERSION -> R.string.reason_unknown_version
        ModelRejectionReason.NO_PREVIOUS_VERSION -> R.string.reason_no_previous_version
        ModelRejectionReason.SWAP_REFUSED -> R.string.reason_swap_refused
        ModelRejectionReason.LOAD_FAILED -> R.string.reason_load_failed
    }
