package com.flightaware.android.flightfeeder.analyzers.dump1090

import android.content.Intent
import android.os.SystemClock
import com.flightaware.android.flightfeeder.App
import com.flightaware.android.flightfeeder.BuildConfig
import com.flightaware.android.flightfeeder.activities.MainActivity
import com.flightaware.android.flightfeeder.analyzers.Analyzer
import com.flightaware.android.flightfeeder.util.MovingAverage

class DecodeFramesThread : Thread() {
    override fun run() {
        if (BuildConfig.DEBUG) println("Started decoding mode-s messages.")

        // Start a child thread to compute the frame rate at 1 Hertz
        val thread: Thread = object : Thread() {
            override fun run() {
                while (!Dump1090.sExit) {
                    MovingAverage.addSample(Analyzer.Companion.sFrameCount.toDouble())
                    Analyzer.Companion.sFrameCount = 0
                    SystemClock.sleep(1000)
                }
            }
        }
        thread.name = "MovingAverage"
        thread.start()
        val intent = Intent(MainActivity.Companion.ACTION_UPDATE_RX)
        while (!Dump1090.sExit) {
            val message = ModeSMessageQueue.take() ?: continue
            val aircraft = Decoder.decodeModeS(message) ?: continue
            Analyzer.Companion.sFrameCount++
            App.Companion.sBroadcastManager!!.sendBroadcast(intent)
            val now = SystemClock.uptimeMillis()
            if (!aircraft.isReady(now)) continue
            Analyzer.Companion.computeRange(aircraft)
            val altitude = aircraft.altitude
            val vertRate = aircraft.verticalRate
            val headingDelta = aircraft.headingDelta
        }
        if (BuildConfig.DEBUG) println("Stopped decoding mode-s messages.")
    }

    init {
        name = "DecodeAdsbFramesThread"
    }
}