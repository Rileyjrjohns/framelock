package dev.framelock.live.domain

import kotlinx.coroutines.CoroutineDispatcher

fun CoroutineDispatcher.serial(): CoroutineDispatcher = limitedParallelism(1)
