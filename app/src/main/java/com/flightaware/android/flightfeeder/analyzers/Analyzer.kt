package com.flightaware.android.flightfeeder.analyzers

import android.location.Location
import android.text.TextUtils
import com.flightaware.android.flightfeeder.services.LocationService

open class Analyzer {
    companion object {
        private const val DEFAULT_USBFS_PATH = "/dev/bus/usb"
        var sDecodeThread: Thread? = null

        @Volatile
        var sFrameCount = 0

        @Volatile
        var sRange = 0f
        var sReadThread: Thread? = null
        fun getDeviceName(deviceName: String): String {
            var deviceName = deviceName
            deviceName = deviceName.trim { it <= ' ' }
            if (TextUtils.isEmpty(deviceName)) return DEFAULT_USBFS_PATH
            val paths = deviceName.split("/").toTypedArray()
            val sb = StringBuilder()
            for (i in 0 until paths.size - 2) if (i == 0) sb.append(paths[i]) else sb.append("/" + paths[i])
            val stripped_name = sb.toString().trim { it <= ' ' }
            return if (stripped_name.isEmpty()) DEFAULT_USBFS_PATH else stripped_name
        }

        fun computeRange(aircraft: Aircraft) {
            if (LocationService.Companion.sLocation == null) return
            val results = FloatArray(1)
            aircraft.latitude?.let {
                aircraft.longitude?.let { it1 ->
                    Location.distanceBetween(LocationService.Companion.sLocation!!.latitude,
                            LocationService.Companion.sLocation!!.longitude, it,
                            it1, results)
                }
            }

            // convert to nautical miles
            val tempRange = results[0] / 1852
            if (tempRange > sRange && tempRange < 300) sRange = tempRange
        }
    }
}