package dev.framelock.core.testing

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/**
 * The current value of a monitor, as a flow that can be made to fail. Like a `StateFlow`, a collector gets the
 * current value first, then each change, and may miss intermediate values. After [fail], the collectors that are
 * running and the ones that start later get [error] thrown; the next [set] makes the flow work again.
 */
internal class FailableState<T>(
    initial: T,
) {
    private sealed interface State<out T> {
        data class Value<T>(
            val value: T,
        ) : State<T>

        data class Failed(
            val error: Throwable,
        ) : State<Nothing>
    }

    private val state = MutableStateFlow<State<T>>(State.Value(initial))

    val flow: Flow<T> =
        state.map {
            when (it) {
                is State.Value -> it.value
                is State.Failed -> throw it.error
            }
        }

    fun set(value: T) {
        state.value = State.Value(value)
    }

    fun fail(error: Throwable) {
        state.value = State.Failed(error)
    }
}
