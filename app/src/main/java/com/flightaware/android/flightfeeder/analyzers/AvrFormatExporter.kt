package com.flightaware.android.flightfeeder.analyzers

import android.text.TextUtils
import java.net.ServerSocket
import java.util.*
import javax.net.ServerSocketFactory

object AvrFormatExporter {
    private val sClientList = Vector<Client>()
    private var sConnectThread: Thread? = null

    @Volatile
    var sIsEnabled = false
    private var sSendThread: Thread? = null

    @Volatile
    private var sServerSocket: ServerSocket? = null
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
                                .createServerSocket(30002)
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
                        val message = AvrFormatMessageQueue.take()
                        if (message == null || message.bytes == null) continue
                        var modeS = message.bytesAsString
                        if (!TextUtils.isEmpty(modeS)) {
                            modeS = "*$modeS;\n"
                            val it = sClientList.iterator()
                            while (it.hasNext()) {
                                val client = it.next()
                                if (client.isConnected) client.send(modeS.toByteArray()) else {
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