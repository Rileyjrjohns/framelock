package dev.framelock.testing

/** The debug agent of kotlinx.coroutines (on under `-ea`) renames a thread while it runs a coroutine. */
private val COROUTINE_SUFFIX = Regex(""" @.*#\d+$""")

/**
 * The name of the calling thread, without the ` @coroutine#7` suffix that coroutine debugging appends: the same
 * thread keeps the same name whichever coroutine it runs.
 */
internal fun currentThreadName(): String = Thread.currentThread().name.replace(COROUTINE_SUFFIX, "")
