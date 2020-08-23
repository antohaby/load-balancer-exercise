package com.github.antohaby.loadbalancer

import kotlinx.coroutines.runBlocking
import mu.KotlinLogging
import kotlin.random.Random

val random = Random(42) // For determined results
val logger  = KotlinLogging.logger {  }

fun main(): Unit = runBlocking {
    val provider = SimpleProvider(
        id = "A",
        slownessRange = 100L..200L,
        random = random
    )

    val result = provider.get()
    logger.info { "Provider Says: $result" }
}