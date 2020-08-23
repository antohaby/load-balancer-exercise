package com.github.antohaby.loadbalancer

import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import mu.KotlinLogging

class Balancer(
    registry: ProviderRegistry,
    private val iterationStrategy: IterationStrategy,
    private val heartbeatController: HeartbeatController
) {
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Default)

    private val logger = KotlinLogging.logger {  }

    sealed class Error(msg: String) : Exception(msg) {
        class NoProvidersAvailable(msg: String): Error(msg)
    }

    private data class ProviderWrap(
        val provider: Provider,
        val isAlive: Boolean
    )

    private val registrySubscription: ProviderRegistry.Subscription
    private val providers: MutableMap<String,ProviderWrap>
    private var iterator: Iterator<ProviderWrap>
    private val providersMutex = Mutex()

    private val heartbeatJobs = mutableMapOf<String, Job>()

    init {
        registrySubscription = registry.subscribe { event ->
            providersMutex.withLock {
                onRegistryEvent(event)
                restartIterator()
            }
        }

        providers = registrySubscription.initial
            .mapValues { providerWrap(it.key, it.value) }
            .toMutableMap()

        iterator = iterationStrategy.iterator(providers.values.toList())

        for ( (id, providerWrap) in providers) {
            heartbeatJobs[id] = watchProvider(id, providerWrap.provider)
        }
    }

    private suspend fun onRegistryEvent(event: ProviderRegistry.Event) {
        when (event) {
            is ProviderRegistry.Event.Added -> {
                providers[event.id] = providerWrap(event.id, event.provider)
                heartbeatJobs[event.id] = watchProvider(event.id, event.provider)
            }
            is ProviderRegistry.Event.Removed -> {
                providers.remove(event.id)
                heartbeatJobs[event.id]?.cancelAndJoin()
            }
        }
    }

    private fun providerWrap(id: String, provider: Provider): ProviderWrap {
        return ProviderWrap(
            provider = provider,
            isAlive = true
        )
    }

    private fun watchProvider(id: String, provider: Provider): Job {
        return with(heartbeatController) {
            scope.watch(provider::check) { status ->
                onHeartbeatChange(id, status)
            }
        }
    }

    private fun restartIterator() {
        val healthyProviders = providers
            .values
            .filter { it.isAlive }

        logger.info { "Healthy Providers: ${providers.filter { it.value.isAlive }.keys}" }
        iterator = iterationStrategy.iterator(healthyProviders)
    }

    private suspend fun onHeartbeatChange(id: String, status: HeartbeatController.Status) {
        logger.info { "Provider #$id is $status" }
        val isAlive = when(status) {
            HeartbeatController.Status.Alive -> true
            HeartbeatController.Status.Dead -> false
        }

        providersMutex.withLock {
            val old = providers[id] ?: error("Provider not found")
            providers[id] = old.copy(isAlive = isAlive)
            restartIterator()
        }
    }

    suspend fun get(): String {
        val entry = providersMutex.withLock {
            if (!iterator.hasNext()) {
                throw Error.NoProvidersAvailable("No active providers available to handle request")
            }

            iterator.next()
        }

        return entry.provider.get()
    }

    suspend fun stop() {
        scope.cancel()
        registrySubscription.cancel()
    }
}