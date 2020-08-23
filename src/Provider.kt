package com.github.antohaby.loadbalancer

import kotlinx.coroutines.delay
import mu.KotlinLogging
import kotlin.random.Random

interface Provider {
    /**
     * Return unique Provider Identifier
     */
    suspend fun get(): String

    /**
     * Returns current heartbeat status of provider
     */
    suspend fun check(): Boolean
}

/**
 * SimpleProvider returns its [id] with some delay randomly selected by [random] from [slownessRange]
 * [failureChance] can be tuned to control chance check method to return Failure
 */
class SimpleProvider(
    val id: String,
    private val slownessRange: LongRange,
    private val failureChance: Float = 0.5f,
    private val random: Random = Random
) : Provider {
    private val logger = KotlinLogging.logger {  }

    override suspend fun get(): String {
        val delayTime = slownessRange.random(random)
        logger.info { "Provider #$id will report in: $delayTime ms" }
        delay(delayTime)
        return id
    }

    override suspend fun check(): Boolean = failureChance < Random.nextFloat()
        .also { logger.info { "Provider #$id check: $it" } }
}