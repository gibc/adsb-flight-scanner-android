package com.flightaware.android.flightfeeder.analyzers.dump1090

internal class ErrorInfo {
    var bitCount // Number of bit positions to fix
            : Byte = 0
    var bitPositions = intArrayOf(-1, -1) // Bit positions

    // corrected by this
    // syndrome
    var syndrome =0// CRC syndrome = 0

    override fun equals(o: Any?): Boolean {
        if (this === o) return true
        if (o !is ErrorInfo) return false
        return o.syndrome == syndrome
    }

    override fun hashCode(): Int {
        return syndrome
    }

}