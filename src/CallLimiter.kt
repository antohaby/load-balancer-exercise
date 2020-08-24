package com.github.antohaby.loadbalancer

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * Interface that allow to protect certain suspend function
 * from extensive amount of calls
 */
fun interface CallLimiter<R> {
    fun CoroutineScope.withLimit(
        code: suspend () -> R
    ): CallLimiterResponse<R>
}

sealed class CallLimiterResponse<R> {
    /**
     * Admit incoming call, and return Deferred for result
     */
    data class Admit<R>(val result: Deferred<R>) : CallLimiterResponse<R>()

    /**
     * Reject incoming call with a [reason] and special [unblocked] Deferred to notify caller when
     * given resource is unblocked
     */
    data class Reject<R>(
        val reason: String,
        val unblocked: Deferred<Unit>
    ) : CallLimiterResponse<R>()
}

/**
 * Simple Max call limit implementation, it will notify about unblocked queue immediately after last call
 */
fun <R> maxCallLimit(maxCalls: Int): CallLimiter<R> {
    val counter = AtomicInteger()
    val isFull = AtomicBoolean()
    var unblocked = CompletableDeferred<Unit>()

    return CallLimiter { code ->
        if (isFull.get()) {
            CallLimiterResponse.Reject(
                reason = "Max Limit Reached",
                unblocked = unblocked
            )
        } else {
            val reachedLimit = counter.incrementAndGet() >= maxCalls
            if (reachedLimit) {
                isFull.set(true)
                // TODO: What if previous unblocked was not complete :thinking:
                unblocked = CompletableDeferred<Unit>()
            }

            CallLimiterResponse.Admit(
                async {
                    try {
                        code()
                    } finally {
                        unblocked.complete(Unit)
                    }
                }
            )
        }
    }
}