package com.github.antohaby.loadbalancer

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
    fun <T> iterator(list: List<T>): LoopIterator<T>
}

/**
 * Specific iterator that meant to infinitely iterate over some finite set of items
 * certain items can be exclude/include
 */
interface LoopIterator<T> : Iterator<T> {
    /**
     * Include previously excluded item
     *
     * returns true in case of success
     */
    fun include(item: T): Boolean

    /**
     * Exclude item in iterator
     */
    fun exclude(item: T): Boolean
}

abstract class ListLoopIterator<T>(
    items: List<T>,
): LoopIterator<T> {
    protected val activeItems: MutableSet<T> = items.toMutableSet()

    override fun include(item: T): Boolean = activeItems.add(item).also { onActiveItemsChange() }

    override fun exclude(item: T) = activeItems.remove(item).also { onActiveItemsChange() }

    override fun hasNext(): Boolean = activeItems.isNotEmpty()

    abstract fun onActiveItemsChange()
}


/**
 * Iterate infinitely a list using random
 */
class RandomIteration(
    private val random: Random
): IterationStrategy {
    override fun <T> iterator(list: List<T>): LoopIterator<T> = object : ListLoopIterator<T>(list) {
        override fun next(): T = activeItems.random(random)
        override fun onActiveItemsChange() = Unit // Not necessary
    }
}

/**
 * Round Robin Iteration Strategy
 */
object RoundRobin : IterationStrategy {
    override fun <T> iterator(list: List<T>): ListLoopIterator<T> {
        // According to Interface contract Iterator should not be thread safe
        // So it is fine to use plain counter variable
        var counter = 0
        var activeItemsList = list.toList()

        return object : ListLoopIterator<T>(list) {
            override fun next(): T = activeItemsList[counter % activeItemsList.size].also {
                if (counter == activeItemsList.lastIndex) {
                    counter = 0
                } else {
                    counter++
                }
            }

            override fun onActiveItemsChange() {
                counter = 0 // reset counter
                activeItemsList = activeItems.toList()
            }
        }
    }
}