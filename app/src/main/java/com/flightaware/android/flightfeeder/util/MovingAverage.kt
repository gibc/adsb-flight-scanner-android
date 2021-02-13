package com.flightaware.android.flightfeeder.util

import java.util.*

object MovingAverage {
    // Vectors or synchronized
    private val sSamples = Vector<Double>()

    @Volatile
    private var sPeriod = 10

    @Volatile
    private var sSum = 0.0
    fun setPeriod(period: Int) {
        sPeriod = period
    }

    fun addSample(sample: Double) {
        sSum += sample
        sSamples.add(sample)
        if (sSamples.size > sPeriod) {
            val old = sSamples.firstElement()
            sSum -= old
            sSamples.remove(old)
        }
    }

    // technically the average is undefined
    val currentAverage: Double
        get() = if (sSamples.isEmpty()) 0.0 else sSum / sPeriod // technically the average is undefined

    fun reset() {
        sSamples.clear()
        sSum = 0.0
    }
}