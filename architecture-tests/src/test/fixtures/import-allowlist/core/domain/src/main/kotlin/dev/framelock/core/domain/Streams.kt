package dev.framelock.core.domain

import java.io.InputStream
import java.io.InputStreamReader

fun reader(stream: InputStream) = InputStreamReader(stream)
