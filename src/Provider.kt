package com.github.antohaby.loadbalancer

import kotlinx.coroutines.delay
import mu.KotlinLogging
import kotlin.random.Random

interface Provider {
    /**
     * Return unique Provider Identifier
     */
    suspend fun get(): String
}

/**
 * SimpleProvider returns its [id] with some delay randomly selected by [random] from [slownessRange]
 */
class SimpleProvider(
    val id: String,
    private val slownessRange: LongRange,
    private val random: Random = Random
) : Provider {
    private val logger = KotlinLogging.logger {  }

    override suspend fun get(): String {
        val delayTime = slownessRange.random(random)
        logger.info { "Provider #$id will report in: $delayTime ms" }
        delay(delayTime)
        return id
    }
}