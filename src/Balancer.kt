package com.github.antohaby.loadbalancer

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class Balancer(
    registry: ProviderRegistry,
    private val iterationStrategy: IterationStrategy
) {
    sealed class Error(msg: String) : Exception(msg) {
        class NoProvidersAvailable(msg: String): Error(msg)
    }

    private val subscription = registry.subscribe { event ->
        when (event) {
            is ProviderRegistry.Event.Added -> providers[event.id] = event.provider
            is ProviderRegistry.Event.Removed -> providers -= event.id
        }
        restartIterator()
    }
    private val providers: MutableMap<String,Provider> = subscription.initial.toMutableMap()

    private var iterator: Iterator<Provider> = iterationStrategy.iterator(providers.values.toList())
    private val iteratorMutex = Mutex()

    private suspend fun restartIterator() = iteratorMutex.withLock {
        iterator = iterationStrategy.iterator(providers.values.toList())
    }

    suspend fun get(): String {
        val provider = iteratorMutex.withLock {
            if (!iterator.hasNext()) {
                throw Error.NoProvidersAvailable("No active providers available to handle request")
            }

            iterator.next()
        }

        return provider.get()
    }

    suspend fun stop() {
        subscription.cancel()
    }
}