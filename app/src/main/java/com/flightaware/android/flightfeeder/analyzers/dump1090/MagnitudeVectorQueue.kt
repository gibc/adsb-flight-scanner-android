package com.flightaware.android.flightfeeder.analyzers.dump1090

import java.util.concurrent.LinkedBlockingQueue

internal object MagnitudeVectorQueue {
    private val sVectorQueue = LinkedBlockingQueue<IntArray>(
            100)

    fun offer(vector: IntArray) {
        while (!sVectorQueue.offer(vector)) sVectorQueue.poll()
    }

    fun take(): IntArray? {
        return try {
            sVectorQueue.take()
        } catch (e: Exception) {
            // swallow
            null
        }
    }

    fun size(): Int {
        return sVectorQueue.size
    }
}