package com.flightaware.android.flightfeeder.analyzers

import java.net.Socket

class Client(private val mSocket: Socket?) {
    @Volatile
    var isConnected = true
        private set

    fun close() {
        if (mSocket != null) {
            try {
                mSocket.close()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun send(bytes: ByteArray?) {
        object : Thread() {
            override fun run() {
                try {
                    val out = mSocket!!.getOutputStream()
                    out.write(bytes)
                    out.flush()
                } catch (e: Exception) {
                    e.printStackTrace()
                    isConnected = false
                }
            }
        }.start()
    }

}