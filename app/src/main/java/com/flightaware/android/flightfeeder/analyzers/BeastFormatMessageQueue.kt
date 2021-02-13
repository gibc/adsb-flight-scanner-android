package com.flightaware.android.flightfeeder.analyzers

import com.flightaware.android.flightfeeder.analyzers.dump1090.ModeSMessage
import java.util.concurrent.LinkedBlockingQueue

object BeastFormatMessageQueue {
    private val sMessageQueue = LinkedBlockingQueue<ModeSMessage>(
            100)

    fun offer(message: ModeSMessage?) {
        if (message != null) while (!sMessageQueue.offer(message)) sMessageQueue.poll()
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

    fun clear() {
        sMessageQueue.clear()
    }
}