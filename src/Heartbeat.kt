package com.github.antohaby.loadbalancer

import kotlinx.coroutines.*

interface HeartbeatController {
    enum class Status { Alive, Dead }
    fun CoroutineScope.watch(check: suspend () -> Boolean, onStatusChange: suspend (Status) -> Unit): Job
}

class SimpleHeartbeat(
    val interval: Long
) : HeartbeatController {
    override fun CoroutineScope.watch(
        check: suspend () -> Boolean,
        onStatusChange: suspend (HeartbeatController.Status) -> Unit
    ): Job = launch {
        while (isActive) {
            // Just mark it forever dead when got false status check
            val checkStatus = check()
            if (!checkStatus) onStatusChange(HeartbeatController.Status.Dead)

            delay(interval)
        }
    }
}