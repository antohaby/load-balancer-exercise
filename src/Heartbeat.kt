package com.github.antohaby.loadbalancer

import kotlinx.coroutines.*

/**
 * Heartbeat controller
 */
interface HeartbeatController {
    enum class Status { Alive, Dead }

    /**
     * Client can request to watch some resource by delegating [check] method
     * and it will be informed about status changes via [onStatusChange]
     *
     * It returns watching job that can be cancelled
     */
    fun CoroutineScope.watch(check: suspend () -> Boolean, onStatusChange: suspend (Status) -> Unit): Job
}

/**
 * Simple heartbeat implementation that just simply checks every [interval] ms
 * and delegates sequence of statuses true/false to [deadDetectStrategy] to decide about liveness status
 */
class SimpleHeartbeat(
    val interval: Long,
    val deadDetectStrategy: () -> (Boolean) -> HeartbeatController.Status
) : HeartbeatController {
    override fun CoroutineScope.watch(
        check: suspend () -> Boolean,
        onStatusChange: suspend (HeartbeatController.Status) -> Unit
    ): Job {
        val deadDetector = deadDetectStrategy()
        var lastStatus = HeartbeatController.Status.Alive
        return launch {
            while (isActive) {
                val newStatus = deadDetector(check())
                if (newStatus != lastStatus) {
                    onStatusChange(newStatus)
                    lastStatus = newStatus
                }
                delay(interval)
            }
        }
    }
}

fun makeAliveAfterXRounds(minAliveRounds: Int): (Boolean) -> HeartbeatController.Status {
    var currentStatus = true
    var aliveRounds = 0

    return { check ->
        when {
            currentStatus && check -> HeartbeatController.Status.Alive
            currentStatus && !check -> {
                aliveRounds = 0
                currentStatus = false
                HeartbeatController.Status.Dead
            }
            !currentStatus && check -> {
                aliveRounds++
                if (aliveRounds >= minAliveRounds) {
                    currentStatus = true
                    HeartbeatController.Status.Alive
                } else {
                    HeartbeatController.Status.Dead
                }
            }
            !currentStatus && !check -> {
                aliveRounds = 0
                HeartbeatController.Status.Dead
            }
            else -> error("Invalid state")
        }
    }
}