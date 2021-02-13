package com.flightaware.android.flightfeeder.analyzers.dump978

import java.util.concurrent.LinkedBlockingQueue

internal object PhiQueue {
    private val sPhiQueue = LinkedBlockingQueue<IntArray>(
            100)

    fun offer(vector: IntArray) {
        while (!sPhiQueue.offer(vector)) sPhiQueue.poll()
    }

    fun take(): IntArray? {
        return try {
            sPhiQueue.take()
        } catch (e: Exception) {
            // swallow
            null
        }
    }

    fun size(): Int {
        return sPhiQueue.size
    }
}