package com.github.antohaby.loadbalancer

import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import mu.KotlinLogging
import kotlin.random.Random

private val random = Random(42) // For determined results
private val logger = KotlinLogging.logger { }

fun main(): Unit = runBlocking {
    val max = 10
    val providerRegistry = SimpleLimitedProviderList(max)
    repeat(3) {
        val err = providerRegistry.register(
            "ID-$it", SimpleProvider(
                id = "ID-$it",
                slownessRange = 100L..200L,
                failureChance = 0.1f,
                random = random
            )
        )
        if (err != null) throw err
    }

    val heartbeatController = SimpleHeartbeat(
        interval = 1000L
    )

    val balancer = Balancer(
        registry = providerRegistry,
        iterationStrategy = RoundRobin,
        heartbeatController = heartbeatController
    )

    coroutineScope {
        // Launch 3 concurrent workers
        repeat(3) { workerNo ->

            // Each of them would call 10k times balancer's get method
            launch {
                repeat(100) {
                    try {
                        val res = balancer.get()
                        logger.info { "Worker[$workerNo]: $res" }
                    } catch (e: Balancer.Error) {
                        logger.error(e) { "Worker[$workerNo]: ${e.message}" }
                        delay(100L)
                    }
                }
            }
        }

        // Slowly add/remove providers
        launch {
            repeat(7) { providerNo ->
                delay(1_000) // 1 s
                logger.info { "Add New: $providerNo" }
                providerRegistry.register(
                    "NEW-$providerNo", SimpleProvider(
                        id = "NEW-$providerNo",
                        slownessRange = 100L..1000L,
                        failureChance = 0.2f,
                        random = random
                    )
                )
            }

            // Now slowly remove
            repeat(7) {
                delay(500) // 0.5 s
                logger.info { "Remove New: $it" }
                providerRegistry.unregister("NEW-$it")
            }
        }
    }

    balancer.stop()
}