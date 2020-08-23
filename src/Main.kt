package com.github.antohaby.loadbalancer

import kotlinx.coroutines.runBlocking
import mu.KotlinLogging
import kotlin.random.Random

val random = Random(42) // For determined results
val logger = KotlinLogging.logger { }

fun main(): Unit = runBlocking {
    val max = 10
    val providerRegistry = SimpleLimitedProviderList(max)
    repeat(max) {
        val err = providerRegistry.register(
            "ID-$it", SimpleProvider(
                id = "ID-$it",
                slownessRange = 100L..200L,
                random = random
            )
        )
        if (err != null) throw err
    }

    // Try add more
    val err = providerRegistry.register(
        "ID-999", SimpleProvider(
            id = "ID-999",
            slownessRange = 100L..200L,
            random = random
        )
    )
    if (err != null) {
        logger.error(err) { "Cant add more" }
    }


    //Unregister one
    val success = providerRegistry.unregister("ID-5")
    if (!success) {
        logger.error { "Cant remove ID-5" }
    }

    //Try again
    val err2 = providerRegistry.register(
        "ID-999", SimpleProvider(
            id = "ID-999",
            slownessRange = 100L..200L,
            random = random
        )
    )
    if (err2 != null) {
        logger.error(err) { "Cant add more" }
    }
}