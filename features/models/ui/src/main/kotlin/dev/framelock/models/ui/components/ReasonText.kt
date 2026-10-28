package dev.framelock.models.ui.components

import dev.framelock.models.domain.ModelRejectionReason
import dev.framelock.models.ui.R

internal fun reasonText(reason: ModelRejectionReason): Int =
    when (reason) {
        ModelRejectionReason.BAD_HASH -> R.string.models_main_reason_bad_hash
        ModelRejectionReason.BAD_SIGNATURE -> R.string.models_main_reason_bad_signature
        ModelRejectionReason.MALFORMED_MANIFEST -> R.string.models_main_reason_malformed_manifest
        ModelRejectionReason.IO_ERROR -> R.string.models_main_reason_io_error
        ModelRejectionReason.UNKNOWN_VERSION -> R.string.models_main_reason_unknown_version
        ModelRejectionReason.NO_PREVIOUS_VERSION -> R.string.models_main_reason_no_previous_version
        ModelRejectionReason.SWAP_REFUSED -> R.string.models_main_reason_swap_refused
        ModelRejectionReason.LOAD_FAILED -> R.string.models_main_reason_load_failed
    }
