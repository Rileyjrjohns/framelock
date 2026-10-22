package dev.framelock.core.domain

import kotlinx.coroutines.CoroutineDispatcher

fun CoroutineDispatcher.serial(): CoroutineDispatcher = limitedParallelism(1)
