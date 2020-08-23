package com.github.antohaby.loadbalancer

import java.util.concurrent.atomic.AtomicInteger
import kotlin.random.Random

/**
 * Defines an finite or infinite iteration strategy over a list some objects
 */
interface IterationStrategy {
    /**
     * Create an Iterator for given [list]
     *
     * The returned Iterator is not thread safe
     */
    fun <T> iterator(list: List<T>): Iterator<T>
}

/**
 * Iterate infinitely a list using random
 */
class RandomIteration(
    private val random: Random
): IterationStrategy {
    override fun <T> iterator(list: List<T>): Iterator<T> = object : Iterator<T> {
        override fun hasNext(): Boolean = list.isNotEmpty()
        override fun next(): T = list.random(random)
    }
}

/**
 * Round Robin Iteration Strategy
 */
object RoundRobin : IterationStrategy {
    override fun <T> iterator(list: List<T>): Iterator<T> {
        // According to Interface contract Iterator should not be thread safe
        // So it is fine to use plain counter variable
        var counter = 0

        return object : Iterator<T> {
            override fun hasNext(): Boolean = list.isNotEmpty()
            override fun next(): T = list[counter % list.size].also {
                if (counter == list.lastIndex) {
                    counter = 0
                } else {
                    counter++
                }
            }
        }
    }
}