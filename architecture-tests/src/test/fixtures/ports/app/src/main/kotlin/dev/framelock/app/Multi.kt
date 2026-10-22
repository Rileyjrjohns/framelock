package dev.framelock.app

import dev.framelock.core.domain.port.Clock

val multi =
    object : Runnable, Clock {
        override fun run() = Unit
    }
