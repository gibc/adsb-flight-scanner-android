package com.flightaware.android.flightfeeder.analyzers

import android.text.TextUtils
import android.util.LruCache
import java.util.*

object RecentAircraftCache {
    private val sRecentAircraft = LruCache<Int, Aircraft>(
            300)

    fun add(icao: Int, aircraft: Aircraft) {
        val strIcao = aircraft.icao
        if (TextUtils.isEmpty(strIcao) || strIcao!!.length != 6) return
        sRecentAircraft.put(icao, aircraft)
    }

    fun clear() {
        sRecentAircraft.evictAll()
    }

    operator fun get(icao: Int): Aircraft {
        return sRecentAircraft[icao]
    }

    fun getActiveAircraftList(sort: Boolean): ArrayList<Aircraft> {
        val aircraftList = ArrayList<Aircraft>()
        val planes = sRecentAircraft.snapshot()
        val now = System.currentTimeMillis()
        for (aircraft in planes.values) {
            val seen = (now - aircraft.seen) / 1000
            if (seen <= 60) aircraftList.add(aircraft)
        }
        if (sort && aircraftList.size > 1) {
            aircraftList.sortWith(Comparator { lhs, rhs -> lhs.icao!!.compareTo(rhs.icao!!) })
        }
        return aircraftList
    }
}