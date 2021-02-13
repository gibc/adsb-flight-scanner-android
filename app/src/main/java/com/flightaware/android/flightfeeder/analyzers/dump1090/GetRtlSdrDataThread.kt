package com.flightaware.android.flightfeeder.analyzers.dump1090

import android.os.SystemClock
import com.flightaware.android.flightfeeder.BuildConfig
import java.net.Socket
import java.util.*
import javax.net.SocketFactory

class GetRtlSdrDataThread : Thread() {
    override fun run() {
        if (BuildConfig.DEBUG) println("Started reading RTLSDR data socket")
        var socket: Socket? = null
        val maxWait: Long = 3000
        var waited: Long = 0
        try {
            while (socket == null && waited < maxWait) {
                SystemClock.sleep(100)
                try {
                    socket = SocketFactory.getDefault().createSocket(
                            "127.0.0.1", 1234)
                } catch (e: Exception) {
                    // swallow
                    if (socket != null) {
                        socket.close()
                        socket = null
                    }
                } finally {
                    waited += 100
                }
            }
            val stream = socket!!.getInputStream()
            val buffer = ByteArray(BUFFER_SIZE)
            while (!Dump1090.sExit) {
                RtlSdrDataQueue.offer(Arrays.copyOf(buffer, stream.read(buffer)))
            }
        } catch (e: Exception) {
            // swallow;
        } finally {
            if (socket != null) {
                try {
                    socket.close()
                } catch (e: Exception) {
                    // swallow
                }
            }
        }
        if (BuildConfig.DEBUG) println("Stopped reading RTLSDR data socket")
    }

    companion object {
        const val BUFFER_SIZE = 256 * 1024
    }

    init {
        name = "GetRtlSdrDataThread"
    }
}