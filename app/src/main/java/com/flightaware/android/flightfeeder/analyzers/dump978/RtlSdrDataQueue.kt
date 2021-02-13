package com.flightaware.android.flightfeeder.analyzers.dump978

import java.util.concurrent.LinkedBlockingQueue

internal object RtlSdrDataQueue {
    private val sDataQueue = LinkedBlockingQueue<ByteArray>(
            100)

    fun offer(data: ByteArray) {
        while (!sDataQueue.offer(data)) sDataQueue.poll()
    }

    fun take(): ByteArray? {
        return try {
            sDataQueue.take()
        } catch (e: Exception) {
            // swallow
            null
        }
    }

    fun size(): Int {
        return sDataQueue.size
    }
}