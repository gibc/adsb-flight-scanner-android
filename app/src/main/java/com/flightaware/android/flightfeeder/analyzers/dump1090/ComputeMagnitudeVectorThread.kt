package com.flightaware.android.flightfeeder.analyzers.dump1090

import com.flightaware.android.flightfeeder.BuildConfig
import kotlin.experimental.and

class ComputeMagnitudeVectorThread : Thread() {
    companion object {
        private val sMagLut = Array(256) { IntArray(256) }
        private fun buildMagnitudeLookUpTable() {
            // I rewrote this method to use a more natural 2-d array instead of
            // a 1-d array with a weird index.
            var mag_i = 0f
            var mag_q = 0f
            for (i in 0..255) {
                mag_i = i * 2 - 255.toFloat()
                for (q in 0..255) {
                    mag_q = q * 2 - 255.toFloat()
                    sMagLut[i][q] = Math.round(Math.sqrt((mag_i * mag_i
                            + mag_q * mag_q).toDouble()) * 258.433254 - 365.4798).toInt()
                }
            }
        }

        init {
            buildMagnitudeLookUpTable()
        }
    }
// get hum comment
    private fun computeMagnitudeVector(data: ByteArray) {
        // gib - flow control problem, loop below never completes so
        // nothing added to MagnitudeVectorQueue???
        val vector = IntArray(data.size / 2)
        var i = 0
        var q = 0
        try {
            var j = 0
            while (j < data.size) {

                // In the original C file, the 'data' array here is an unsigned
                // char
                // data type, which is an 8-bit integer. Java has no equivalent
                // data
                // type. The closest thing is a byte, which is also 8 bits, but
                // signed.
                //
                // To get back to the original functionality, we must do a
                // bitwise
                // AND with 255 and store the result in an integer, and then
                // continue with the algorithm.
                i = (data[j] and 0xFF.toByte()).toInt()
                q = (data[j + 1] and 0xFF.toByte()).toInt()
                vector[j / 2] = sMagLut[i][q]
                j += 2
            }
            MagnitudeVectorQueue.offer(vector)
        } catch (ex: Exception) {
            ex.printStackTrace()
        }
    }

    override fun run() {
        if (BuildConfig.DEBUG) println("Started generating vectors")
        while (!Dump1090.sExit) {
            val data = RtlSdrDataQueue.take() ?: continue
            computeMagnitudeVector(data)
        }
        if (BuildConfig.DEBUG) println("Stopped generating vectors")
    }

    init {
        name = "ComputeMagnitudeVectorThread"
    }
}