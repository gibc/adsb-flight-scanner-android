package com.flightaware.android.flightfeeder.analyzers

import com.flightaware.android.flightfeeder.analyzers.dump1090.ModeSMessage
import java.net.ServerSocket
import java.nio.ByteBuffer
import java.util.*
import javax.net.ServerSocketFactory

object BeastFormatExporter {
    private val sClientList = Vector<Client>()
    private var sConnectThread: Thread? = null

    @Volatile
    var sIsEnabled = false
    private var sSendThread: Thread? = null

    @Volatile
    private var sServerSocket: ServerSocket? = null
    private fun prepareBeastOutput(message: ModeSMessage): ByteArray? {
        var length = message.bytes!!.size
        val buffer = ByteBuffer.allocate(2 + 2 * (7 + length))
        buffer.put(0x1a.toByte())
        if (length == 7) buffer.put('2'.toByte()) else if (length == 14) buffer.put('3'.toByte()) else return null

        // timestamp
        val timestamp = message.clockCount
        var bite = (timestamp shr 40).toByte()
        buffer.put(bite)
        if (bite.toInt() == 0x1A) buffer.put(bite)
        bite = (timestamp shr 32).toByte()
        buffer.put(bite)
        if (bite.toInt() == 0x1A) buffer.put(bite)
        bite = (timestamp shr 24).toByte()
        buffer.put(bite)
        if (bite.toInt() == 0x1A) buffer.put(bite)
        bite = (timestamp shr 16).toByte()
        buffer.put(bite)
        if (bite.toInt() == 0x1A) buffer.put(bite)
        bite = (timestamp shr 8).toByte()
        buffer.put(bite)
        if (bite.toInt() == 0x1A) buffer.put(bite)
        bite = timestamp.toByte()
        buffer.put(bite)
        if (bite.toInt() == 0x1A) buffer.put(bite)

        // *p++ = ch = (char) round(sqrt(mm->signalLevel) * 255);
        // if (0x1A == ch) {*p++ = ch; }

        // signal level is not currently implemented
        // this is just a placeholder for now.
        buffer.put(200.toByte())
        for (i in 0 until length) {
            bite = message.bytes!![i] as Byte
            buffer.put(bite)
            if (bite.toInt() == 0x1A) buffer.put(bite)
        }
        length = buffer.position()
        val bytes = ByteArray(length)
        buffer.position(0)
        buffer[bytes, 0, length]
        buffer.clear()
        return bytes
    }

    fun start() {
        if (sIsEnabled) return
        sIsEnabled = true
        BeastFormatMessageQueue.clear()
        if (sConnectThread == null) {
            sConnectThread = object : Thread() {
                override fun interrupt() {
                    if (sServerSocket != null) {
                        try {
                            sServerSocket!!.close()
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                    super.interrupt()
                }

                override fun run() {
                    try {
                        sServerSocket = ServerSocketFactory.getDefault()
                                .createServerSocket(30005)
                        val client = Client(sServerSocket!!.accept())
                        sClientList.add(client)
                    } catch (e: Exception) {
                        e.printStackTrace()
                        interrupt()
                    }
                }
            }
            sConnectThread!!.start()
        }
        if (sSendThread == null) {
            sSendThread = object : Thread() {
                override fun interrupt() {
                    val it = sClientList.iterator()
                    while (it.hasNext()) {
                        val client = it.next()
                        client.close()
                        it.remove()
                    }
                    super.interrupt()
                }

                override fun run() {
                    while (sIsEnabled) {
                        val message = BeastFormatMessageQueue.take()
                        if (message == null || message.bytes == null) continue
                        val bytes = prepareBeastOutput(message)
                        if (bytes != null) {
                            val it = sClientList.iterator()
                            while (it.hasNext()) {
                                val client = it.next()
                                if (client.isConnected) client.send(bytes) else {
                                    client.close()
                                    it.remove()
                                }
                            }
                        }
                    }
                }
            }
            sSendThread!!.start()
        }
    }

    fun stop() {
        if (!sIsEnabled) return
        sIsEnabled = false
        if (sConnectThread != null) {
            sConnectThread!!.interrupt()
            sConnectThread = null
        }
        if (sSendThread != null) {
            sSendThread!!.interrupt()
            sSendThread = null
        }
    }
}