package com.github.antohaby.loadbalancer

import kotlin.math.max

interface ProviderRegistry {
    open class RegistrationError(msg: String) : Exception(msg)

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

    override suspend fun register(id: String, provider: Provider): ProviderRegistry.RegistrationError? {
        if (id in registry) return AlreadyRegistered(id)
        if (registry.size >= maxProviders) return OutOfLimit(maxProviders)

        registry[id] = provider
        return null
    }

    override suspend fun unregister(id: String): Boolean {
        return registry.remove(id) != null
    }
}