package com.github.antohaby.loadbalancer

import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import mu.KotlinLogging

/**
 * Load Balancer of some list of [Provider] which is controlled by [registry]
 * @param iterationStrategy decides how to iterate over list of Providers (eg. round robin, random, etc...)
 * @param heartbeatController sets Heartbeat Controller that excludes/includes certain [Provider] based on its status
 * @param callLimiter provides [CallLimiter] that controls amount of incoming calls
 *
 * It is mandatory to explicitly [start] Balancer, and [stop] at the end
 *
 */
class Balancer(
    val registry: ProviderRegistry,
    iterationStrategy: IterationStrategy,
    private val heartbeatController: HeartbeatController,
    private val callLimiter: () -> CallLimiter<String>
) {
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Default)

    private val logger = KotlinLogging.logger {  }

    sealed class Error(msg: String) : Exception(msg) {
        class NoProvidersAvailable(msg: String): Error(msg)
        class CapacityLimit(msg: String): Error(msg)
        class ProviderFailure(msg: String): Error(msg)
    }

    sealed class Response {
        data class Success(val data: String): Response()
        data class Fail(val error: Error): Response()
    }

    private var registrySubscription: ProviderRegistry.Subscription? = null
    private val iterator: LoopIterator<Provider> = iterationStrategy.iterator(listOf())

    private val heartbeats: MutableMap<String, Job> = mutableMapOf()
    private val limiters: MutableMap<Provider, CallLimiter<String>> = mutableMapOf()

    private val iteratorMutex = Mutex()

    /**
     * Starts Balancer
     *
     * TODO: Protect from double call
     */
    suspend fun start() {
        val subscription = registry.subscribe(this::onRegistryEvent)
        for ((id, provider) in subscription.initial) {
            addToBalancer(id, provider)
        }
        registrySubscription = subscription
    }

    /**
     * Stops Balancer
     *
     * TODO: Protect from double call
     */
    suspend fun stop() {
        scope.cancel()
        registrySubscription?.cancel?.invoke()
    }

    /**
     * The entrypoint
     */
    suspend fun getAsync(): Deferred<Response> {
        val deferred = CompletableDeferred<Response>()
        val provider = iteratorMutex.withLock {
            if (!iterator.hasNext()) {
                return@withLock null
            }

            iterator.next()
        } ?: return deferred
            .also { it.complete(Response.Fail(Error.NoProvidersAvailable("No providers available"))) }

        val limiter = limiters[provider] ?: error("Critical error, limiter must be available")
        val limiterResponse = with(limiter) {
            scope.withLimit {
                provider.get()
            }
        }

        when (limiterResponse) {
            is CallLimiterResponse.Admit -> scope.launch {
                try {
                    val result = limiterResponse.result.await()
                    deferred.complete(Response.Success(result))
                } catch (e: Exception) {
                    deferred.complete(Response.Fail(Error.ProviderFailure(e.toString())))
                }
            }
            is CallLimiterResponse.Reject -> {
                scope.launch { onOverCapacity(provider, limiterResponse.unblocked) }
                deferred.complete(Response.Fail(Error.CapacityLimit("Out Of Limit")))
            }
        }

        return deferred
    }

    private suspend fun onOverCapacity(provider: Provider, unblocked: Deferred<Unit>) = iteratorMutex.withLock {
        logger.info { "Over Capacity for ${provider.hashCode()}, excluding" }
        iterator.exclude(provider)
        try {
            unblocked.await()
        } finally {
            iterator.include(provider)
        }
        logger.info { "Over Capacity is over ${provider.hashCode()}, including" }
    }

    private suspend fun onRegistryEvent(event: ProviderRegistry.Event) {
        logger.info { "Provider: ${event.id} is ${event.javaClass}" }
        when (event) {
            is ProviderRegistry.Event.Added -> addToBalancer(event.id, event.provider)
            is ProviderRegistry.Event.Removed -> removeFromBalancer(event.id, event.provider)
        }
    }

    private suspend fun addToBalancer(id: String, provider: Provider) =  iteratorMutex.withLock {
        iterator.include(provider)
        heartbeats[id] = heartbeatWatch(provider)
        limiters[provider] = callLimiter()
    }

    private suspend fun removeFromBalancer(id: String, provider: Provider) =  iteratorMutex.withLock {
        iterator.exclude(provider)
        heartbeats[id]?.cancelAndJoin()
        limiters.remove(provider)
    }

    private fun heartbeatWatch(provider: Provider) = with(heartbeatController) {
        scope.watch(provider::check) { status ->
            iteratorMutex.withLock {
                when (status) {
                    HeartbeatController.Status.Alive -> iterator.include(provider)
                    HeartbeatController.Status.Dead ->  iterator.exclude(provider)
                }
            }
        }
    }
}