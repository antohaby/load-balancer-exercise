package com.github.antohaby.loadbalancer

import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

interface ProviderRegistry {
    open class RegistrationError(msg: String) : Exception(msg)

    sealed class Event {
        data class Added(
            val id: String,
            val provider: Provider
        ) : Event()

        data class Removed(
            val id: String,
            val provider: Provider
        ) : Event()
    }

    /**
     * Subscription structure for subscribing of changes from Registry
     *
     * Client must call [cancel] to cancel subscription
     */
    data class Subscription(
        val initial: Map<String, Provider>,
        val cancel: suspend () -> Unit
    )

    /**
     * Registers a [provider] with given [id]
     * Returns null if registration is succeed
     * Returns an instance of [RegistrationError] if it wasn't possible
     *
     * This method is thread unsafe
     */
    suspend fun register(id: String, provider: Provider): RegistrationError?

    /**
     * Unregisters previously registered Provider by its [id]
     * Returns true in case of success or false
     * if no provider were registered with given [id]
     *
     * This method is thread unsafe
     */
    suspend fun unregister(id: String): Boolean

    /**
     * Returns a flow to track Provider Registry Events
     */
    fun subscribe(onEvent: suspend (Event) -> Unit): Subscription
}


/**
 * Provider registry that is limited by [maxProviders]
 */
class SimpleLimitedProviderList(
    val maxProviders: Int
) : ProviderRegistry {

    class OutOfLimit(maxProviders: Int) : ProviderRegistry.RegistrationError(
        "Out of limit, max allowed: $maxProviders"
    )

    class AlreadyRegistered(id: String) : ProviderRegistry.RegistrationError(
        "A provider with given $id was already registered"
    )

    private val registry: MutableMap<String, Provider> = mutableMapOf()
    private val subscribers: MutableList<suspend (ProviderRegistry.Event) -> Unit> = mutableListOf()

    private suspend fun emit(event: ProviderRegistry.Event) = coroutineScope {
        for (subscriber in subscribers) launch {
            subscriber(event)
        }
    }

    override suspend fun register(id: String, provider: Provider): ProviderRegistry.RegistrationError? {
        if (id in registry) return AlreadyRegistered(id)
        if (registry.size >= maxProviders) return OutOfLimit(maxProviders)

        registry[id] = provider
        emit(ProviderRegistry.Event.Added(id, provider))
        return null
    }

    override suspend fun unregister(id: String): Boolean {
        val provider = registry.remove(id)

        return if (provider != null) {
            emit(ProviderRegistry.Event.Removed(id, provider))
            true
        } else {
            false
        }
    }

    override fun subscribe(onEvent: suspend (ProviderRegistry.Event) -> Unit): ProviderRegistry.Subscription {
        subscribers += onEvent

        return ProviderRegistry.Subscription(
            initial = registry.toMap(),
            cancel = { subscribers -= onEvent }
        )
    }
}