package com.flightaware.android.flightfeeder.analyzers

import android.text.TextUtils
import android.util.LruCache
import com.flightaware.android.flightfeeder.analyzers.dump1090.ModeSMessage
import java.util.*

class Aircraft {
    var altitude: Int? = null
        private set
    var isAltitudeHoldEnabled = false
    var altitudeSource: String? = null
    private var mAltitudeTimestamp: Long = 0
    var isAutoPilotEngaged = false
    var baroSetting: Float? = null
    var category: Int? = null
    var evenMessage: ModeSMessage? = null
    var evenPosition: RawPosition? = null
    var heading: Int? = null
        private set
    var headingDelta = 0
        private set
    private var mHeadingTimestamp: Long = 0
    var icao: String? = null
    var identity: String? = null
    var latitude: Double? = null
    var longitude: Double? = null
    var messageCount = 0
    var oddMessage: ModeSMessage? = null
    var oddPosition: RawPosition? = null
    var isOnApproach = false
    var isOnGround = false
    private var mReady = false
    var seen: Long = 0
    var seenLatLon: Long = 0
    var selectedAltitude: Int? = null
    var selectedHeading: Int? = null
    private val mSignalStrengthCache = LruCache<String, Double>(
            8)
    var squawk: Int? = null
    var status: Int? = null
    var isTcasEnabled = false
    var trackAngle: Int? = null
    var isUat = false
    var velocity: Int? = null
        private set
    private var mVelocityTimestamp: Long = 0
    var isVerticalNavEnabled = false
    var verticalRate: Int? = null

    constructor() {}
    constructor(addr: Int) {
        icao = Integer.toHexString(addr).toUpperCase(Locale.US).trim { it <= ' ' }
    }

    fun addRawPosition(rawPosition: RawPosition?, odd: Boolean) {
        if (odd) oddPosition = rawPosition else evenPosition = rawPosition
    }

    fun addSignalStrength(signalStrength: Double) {
        mSignalStrengthCache.put(UUID.randomUUID().toString(), signalStrength)
    }

    val averageSignalStrength: Double
        get() {
            val snapshot = mSignalStrengthCache.snapshot()
            var total = 0.0
            for (value in snapshot.values) {
                total += value
            }
            total += 1e-5
            return 10 * Math.log10(total / 8)
        }

    fun isReady(timestamp: Long): Boolean {
        if (TextUtils.isEmpty(icao)) return false
        if (altitude == null || timestamp - mAltitudeTimestamp > 30000) return false
        if (heading == null || timestamp - mHeadingTimestamp > 30000) return false
        return if (velocity == null || timestamp - mVelocityTimestamp > 30000) false else mReady
    }

    fun setAltitude(altitude: Int?, timestamp: Long) {
        this.altitude = altitude
        mAltitudeTimestamp = timestamp
    }

    fun setCategory(category: Int) {
        this.category = category
    }

    fun setHeading(heading: Int?, timestamp: Long) {
        if (heading != null && this.heading != null) headingDelta = Math.abs(heading - this.heading!!)
        this.heading = heading
        mHeadingTimestamp = timestamp
    }

    fun setReady(ready: Boolean) {
        mReady = ready
    }

    fun setVelocity(velocity: Int?, timestamp: Long) {
        this.velocity = velocity
        mVelocityTimestamp = timestamp
    }

}