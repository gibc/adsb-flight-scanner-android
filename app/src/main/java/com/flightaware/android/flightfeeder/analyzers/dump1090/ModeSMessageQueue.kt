package com.flightaware.android.flightfeeder.analyzers.dump1090

import java.util.concurrent.LinkedBlockingQueue

internal object ModeSMessageQueue {
    private val sMessageQueue = LinkedBlockingQueue<ModeSMessage>(
            1000)

    fun offer(message: ModeSMessage) {
        while (!sMessageQueue.offer(message)) sMessageQueue.poll()
    }

    fun take(): ModeSMessage? {
        return try {
            sMessageQueue.take()
        } catch (e: Exception) {
            // swallow
            null
        }
    }

    fun size(): Int {
        return sMessageQueue.size
    }
}