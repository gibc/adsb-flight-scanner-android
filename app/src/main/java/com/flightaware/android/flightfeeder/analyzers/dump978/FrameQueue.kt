package com.flightaware.android.flightfeeder.analyzers.dump978

import java.util.concurrent.LinkedBlockingQueue

internal object FrameQueue {
    private val sFrameQueue = LinkedBlockingQueue<IntArray>(
            1000)

    fun offer(data: IntArray) {
        while (!sFrameQueue.offer(data)) sFrameQueue.poll()
    }

    fun take(): IntArray? {
        return try {
            sFrameQueue.take()
        } catch (e: Exception) {
            // swallow
            null
        }
    }

    fun size(): Int {
        return sFrameQueue.size
    }
}