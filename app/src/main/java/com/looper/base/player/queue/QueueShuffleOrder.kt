package com.looper.base.player.queue

import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.source.ShuffleOrder
import kotlin.random.Random

@OptIn(UnstableApi::class)
class QueueShuffleOrder private constructor(
    private val shuffled: IntArray,
    private val random: Random,
    private val anchor: () -> Int,
) : ShuffleOrder {

    private val positions = IntArray(shuffled.size).also { array ->
        shuffled.forEachIndexed { position, index ->
            array[index] = position
        }
    }

    constructor(shuffledIndices: IntArray, anchor: () -> Int) : this(
        shuffled = shuffledIndices.copyOf(),
        random = Random.Default,
        anchor = anchor,
    )

    companion object {
        fun startingWith(firstIndex: Int, length: Int, anchor: () -> Int): QueueShuffleOrder {
            val rest = (0 until length)
                .filter {
                    it != firstIndex
                }
                .shuffled()
            val order = if (firstIndex in 0 until length) {
                listOf(firstIndex) + rest
            } else {
                rest
            }
            return QueueShuffleOrder(
                shuffled = order.toIntArray(),
                random = Random.Default,
                anchor = anchor
            )
        }
    }

    override fun getLength(): Int = shuffled.size

    override fun getNextIndex(index: Int): Int {
        val position = positions[index] + 1
        return if (position < shuffled.size) {
            shuffled[position]
        } else {
            C.INDEX_UNSET
        }
    }

    override fun getPreviousIndex(index: Int): Int {
        val position = positions[index] - 1
        return if (position >= 0) {
            shuffled[position]
        } else {
            C.INDEX_UNSET
        }
    }

    override fun getLastIndex(): Int = shuffled.lastOrNull() ?: C.INDEX_UNSET

    override fun getFirstIndex(): Int = shuffled.firstOrNull() ?: C.INDEX_UNSET

    override fun cloneAndInsert(insertionIndex: Int, insertionCount: Int): ShuffleOrder {
        val shiftedOrder = shuffled.map {
            if (it >= insertionIndex) {
                it + insertionCount
            } else {
                it
            }
        }.toMutableList()
        val inserted = (insertionIndex until insertionIndex + insertionCount).toList()
        val anchorIndex = anchor().takeIf {
            it in shuffled.indices
        }
        val anchorPosition = anchorIndex?.let {
            positions[it]
        }
        when {
            anchorPosition == null -> inserted.forEach {
                shiftedOrder.add(
                    index = random.nextInt(from = 0, until = shiftedOrder.size + 1),
                    element = it
                )
            }

            insertionIndex == anchorIndex + 1 -> shiftedOrder.addAll(
                index = anchorPosition + 1,
                elements = inserted
            )

            else -> inserted.forEach {
                shiftedOrder.add(
                    index = random.nextInt(
                        from = anchorPosition + 1,
                        until = shiftedOrder.size + 1
                    ), element = it
                )
            }
        }
        return QueueShuffleOrder(
            shuffled = shiftedOrder.toIntArray(),
            random = random,
            anchor = anchor
        )
    }

    override fun cloneAndRemove(indexFrom: Int, indexToExclusive: Int): ShuffleOrder {
        val removed = indexToExclusive - indexFrom
        val remaining = shuffled
            .filter {
                it < indexFrom || it >= indexToExclusive
            }
            .map {
                if (it >= indexToExclusive) {
                    it - removed
                } else {
                    it
                }
            }
        return QueueShuffleOrder(
            shuffled = remaining.toIntArray(),
            random = random,
            anchor = anchor
        )
    }

    override fun cloneAndClear(): ShuffleOrder =
        QueueShuffleOrder(shuffled = IntArray(size = 0), random = random, anchor = anchor)

    override fun cloneAndSet(insertionCount: Int, startIndex: Int): ShuffleOrder =
        startingWith(firstIndex = startIndex, length = insertionCount, anchor = anchor)

    override fun cloneAndMove(
        indexFrom: Int,
        indexToExclusive: Int,
        newIndexFrom: Int
    ): ShuffleOrder {
        val count = indexToExclusive - indexFrom
        val moved = shuffled.map { index ->
            when {
                index in indexFrom until indexToExclusive -> newIndexFrom + (index - indexFrom)
                newIndexFrom < indexFrom && index in newIndexFrom until indexFrom -> index + count
                newIndexFrom > indexFrom && index in indexToExclusive until newIndexFrom + count -> index - count
                else -> index
            }
        }
        return QueueShuffleOrder(shuffled = moved.toIntArray(), random = random, anchor = anchor)
    }
}
