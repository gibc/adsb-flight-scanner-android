package com.flightaware.android.flightfeeder.analyzers.dump978

import com.flightaware.android.flightfeeder.BuildConfig
import kotlin.experimental.and

// gib - Phi is the angle of the signal in polar coordinate form, angle of the vector where A is magnitude of the vector
class ConvertToPhiThread : Thread() {
    companion object {
        private val sPhiLut = Array(256) { IntArray(256) }
        private fun buildPhiLookUpTable() {
            var d_i = 0.0
            var d_q = 0.0
            var ang = 0.0
            var scaledAng = 0.0
            for (i in 0..255) {
                for (q in 0..255) {
                    d_i = i - 127.5
                    d_q = q - 127.5
                    ang = Math.atan2(d_q, d_i) + Math.PI // atan2 returns
                    // [-pi..pi],
                    // normalize to
                    // [0..2*pi]
                    scaledAng = Math.round(32767.5 * ang / Math.PI).toDouble()

                    // bound the value between 0 and 65535
                    scaledAng = Math.max(0.0, scaledAng)
                    scaledAng = Math.min(65535.0, scaledAng)
                    sPhiLut[i][q] = scaledAng.toChar().toInt()
                }
            }
        }

        init {
            buildPhiLookUpTable()
        }
    }

    private fun convertToPhi(data: ByteArray) {
        val phi = IntArray(data.size / 2)
        var i = 0
        var q = 0
        var j = 0
        while (j < data.size) {
            i = (data[j] and 0xFF.toByte()).toInt()
            q = (data[j + 1] and 0xFF.toByte()).toInt()
            phi[j / 2] = sPhiLut[i][q]
            j += 2
        }
        PhiQueue.offer(phi)
    }

    override fun run() {
        if (BuildConfig.DEBUG) println("Started computing phi")
        while (!Dump978.sExit) {
            val data = RtlSdrDataQueue.take() ?: continue
            convertToPhi(data)
        }
        if (BuildConfig.DEBUG) println("Stopped computing phi")
    }

    init {
        name = "ConvertToPhiThread"
    }
}