package com.flightaware.android.flightfeeder.analyzers.dump1090

import java.util.*

class ModeSMessage(// Binary message.
        var mBytes: IntArray, var clockCount: Long) {
    var mCa=0 // Responder capabilities = 0
    var mCrc=0 // crc from the raw message = 0
    var numCorrectedBits // Number of bits corrected
            : Byte = 0
    var signalLevel = 0.0
    val bytes: IntArray?
        get() = mBytes

    val bytesAsString: String
        get() {
            val builder = StringBuilder()
            for (bite in mBytes) {
                val hex = String.format("%02X", bite and 0xFF)
                builder.append(hex)
            }
            return builder.toString().toUpperCase(Locale.US)
        }

    val format: Int
        get() = mBytes[0] shr 3

    //public int getIcao() {
    //    return (mBytes[1] << 16) | (mBytes[2] << 8) | mBytes[3];
    //}
    // gib - convet bytes 1,2, and 3 to 3 byte int max = 16777215
    val icao: Int
        get() = (mBytes[1] shl 16) or (mBytes[2] shl 8) or mBytes[3]

    val isValid: Boolean
        get() = mCrc == 0

}