package dev.framelock.models.domain

import java.io.InputStream
import java.io.InputStreamReader

fun reader(stream: InputStream) = InputStreamReader(stream)
