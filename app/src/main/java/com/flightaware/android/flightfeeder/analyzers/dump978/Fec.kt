package com.flightaware.android.flightfeeder.analyzers.dump978

object Fec {
    external fun init()
    external fun correctAdsbFrame(to: IntArray?, retvals: IntArray?)
    external fun correctUplinkFrame(from: IntArray?, to: IntArray?,
                                    retvals: IntArray?)

    init {
        System.loadLibrary("fec")
    }
}