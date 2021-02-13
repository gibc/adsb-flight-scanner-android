package com.flightaware.android.flightfeeder.analyzers.dump978

import com.flightaware.android.flightfeeder.BuildConfig
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.util.*

class ReadTestDataFileThread : Thread() {
    override fun run() {
        if (BuildConfig.DEBUG) println("Started reading test data file")
        var stream: BufferedInputStream? = null
        try {
            val file = File("/mnt/sdcard", "sample1843.bin")
            val fis = FileInputStream(file)
            stream = BufferedInputStream(fis)
            val buffer = ByteArray(BUFFER_SIZE)
            var length = 0
            while (!Dump978.sExit) { // && length != BUFFER_SIZE) {
                length = stream.read(buffer)
                if (length == -1) break
                RtlSdrDataQueue.offer(Arrays.copyOf(buffer, length))
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            if (stream != null) {
                try {
                    stream.close()
                } catch (e: Exception) {
                    // swallow
                }
            }
        }
        if (BuildConfig.DEBUG) println("Stopped reading test data file")
    }

    companion object {
        const val BUFFER_SIZE = 128 * 1024
    }

    init {
        name = "ReadTestDataFileThread"
    }
}