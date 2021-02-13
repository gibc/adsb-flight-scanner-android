package com.flightaware.android.flightfeeder.analyzers

import android.os.SystemClock

class RawPosition(var rawLatitude: Int, var rawLongitude: Int, var nucp: Int) {
    var timestamp: Long

    init {
        timestamp = SystemClock.uptimeMillis()
    }
}