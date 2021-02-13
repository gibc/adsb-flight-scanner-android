package com.flightaware.android.flightfeeder.analyzers.dump978

import android.text.TextUtils
import com.flightaware.android.flightfeeder.analyzers.Aircraft
import com.flightaware.android.flightfeeder.analyzers.RecentAircraftCache

object Decoder {
    private val sBase40Alphabet = charArrayOf('0', '1', '2',
            '3', '4', '5', '6', '7', '8', '9', 'A', 'B', 'C', 'D', 'E', 'F',
            'G', 'H', 'I', 'J', 'K', 'L', 'M', 'N', 'O', 'P', 'Q', 'R', 'S',
            'T', 'U', 'V', 'W', 'X', 'Y', 'Z', ' ', ' ', '.', '.')

    private fun decodeAuxSv(frame: IntArray, aircraft: Aircraft) {
        val raw_alt = frame[29] shl 4 or (frame[30] and 0xf0 shr 4)
        if (raw_alt != 0) {
            val sec_altitude = (raw_alt - 1) * 25 - 1000
            if (aircraft.altitude == 0) aircraft.setAltitude(sec_altitude, sNow)
        }
    }

    private var sNow: Long = 0
    private fun decodeMs(frame: IntArray, aircraft: Aircraft) {
        val callSign = CharArray(9)
        var v = frame[17] shl 8 or frame[18]
        callSign[0] = sBase40Alphabet[v / 40 % 40]
        callSign[1] = sBase40Alphabet[v % 40]
        v = frame[19] shl 8 or frame[20]
        callSign[2] = sBase40Alphabet[v / 1600 % 40]
        callSign[3] = sBase40Alphabet[v / 40 % 40]
        callSign[4] = sBase40Alphabet[v % 40]
        v = frame[21] shl 8 or frame[22]
        callSign[5] = sBase40Alphabet[v / 1600 % 40]
        callSign[6] = sBase40Alphabet[v / 40 % 40]
        callSign[7] = sBase40Alphabet[v % 40]
        callSign[8] = 0.toChar()
        if (callSign[0] > 0.toChar()) {
            val text = String(callSign).trim { it <= ' ' }
            if (TextUtils.isEmpty(text)) return
            if (frame[26] and 0x02 == 0x02) aircraft.identity = text else if (TextUtils.isDigitsOnly(text)) aircraft.squawk = text.toInt(16)
        }
    }

    private fun decodeSv(frame: IntArray, aircraft: Aircraft): Int {
        var pri_alt = 0
        val nic = frame[11] and 15
        val rawLat = (frame[4] shl 15 or (frame[5] shl 7) or (frame[6] shr 1).toLong().toInt()).toLong()
        val rawLon = (frame[6] and 0x01 shl 23 or (frame[7] shl 15)
                or (frame[8] shl 7) or (frame[9] shr 1)).toLong()
        if (nic != 0 || rawLat != 0L || rawLon != 0L) {
            var lat = rawLat * 360.0 / 16777216.0
            if (lat > 90) lat -= 180.0
            var lon = rawLon * 360.0 / 16777216.0
            if (lon > 180) lon -= 360.0
            aircraft.latitude = lat
            aircraft.longitude = lon
            aircraft.setReady(true)
        }
        val rawAlt = (frame[10] shl 4 or (frame[11] and 0xF0 shr 4).toLong().toInt()).toLong()
        if (rawAlt != 0L) {
            pri_alt = ((rawAlt - 1) * 25 - 1000).toInt()
            aircraft.setAltitude(pri_alt, sNow)
        }
        aircraft.isOnGround = frame[12] shr 6 and 0x03 == 2
        if (aircraft.isOnGround) {
            val raw_gs = frame[12] and 0x1f shl 6 or (frame[13] and 0xfc shr 2)
            if (raw_gs != 0) {
                val speed = (raw_gs and 0x3ff) - 1
                aircraft.setVelocity(speed, sNow)
            }
            val raw_heading = (frame[13] and 0x03 shl 9 or (frame[14] shl 1)
                    or (frame[15] and 0x80 shr 7))
            if (raw_heading and 0x0600 shr 9 > 0) {
                val heading = (raw_heading and 0x1ff) * 360 / 512
                aircraft.setHeading(heading, sNow)
            }

            // mdb->position_offset = (frame[15] & 0x04) ? 1 : 0;
        } else {
            var raw_vvel = 0
            var ns_vel = 0
            var ew_vel = 0
            var ew_vel_valid = false
            var ns_vel_valid = false
            val raw_ns = frame[12] and 0x1f shl 6 or (frame[13] and 0xfc shr 2)
            if (raw_ns and 0x3ff != 0) {
                ns_vel_valid = true
                ns_vel = (raw_ns and 0x3ff) - 1
                if (raw_ns and 0x400 == 0x400) ns_vel *= -1
                if (frame[12] shr 6 and 0x03 == 1) ns_vel *= 4
            }
            val raw_ew = (frame[13] and 0x03 shl 9 or (frame[14] shl 1)
                    or (frame[15] and 0x80 shr 7))
            if (raw_ew and 0x3ff != 0) {
                ew_vel_valid = true
                ew_vel = (raw_ew and 0x3ff) - 1
                if (raw_ew and 0x400 == 0x400) ew_vel *= -1
                if (frame[12] shr 6 and 0x03 == 1) ew_vel *= 4
            }
            if (ew_vel_valid && ns_vel_valid) {
                if (ns_vel != 0 || ew_vel != 0) {
                    val heading = (360 + 90 - Math.atan2(ns_vel.toDouble(), ew_vel.toDouble())
                            * 180 / Math.PI).toInt() % 360
                    aircraft.setHeading(heading, sNow)
                }
                val speed = Math.sqrt(ns_vel * ns_vel + ew_vel * ew_vel.toDouble()).toInt()
                aircraft.setVelocity(speed, sNow)
            }
            raw_vvel = frame[15] and 0x7f shl 4 or (frame[16] and 0xf0 shr 4)
            if (raw_vvel and 0x1ff != 0) {
                var vert_rate = ((raw_vvel and 0x1ff) - 1) * 64
                if (raw_vvel and 0x200 == 0x200) vert_rate *= -1
                aircraft.verticalRate = vert_rate
            }
        }
        return pri_alt
    }

    fun decodeUatFrame(frame: IntArray): Aircraft? {
        sNow = System.currentTimeMillis()
        if (frame.size == DetectFramesThread.Companion.UPLINK_FRAME_DATA_BYTES) {
            // decode uplink frame
            // we actually don't care about this frame
            // it does not have any aircraft data in it.

            // TODO - add the code anyway for documentation purposes
        } else {
            val icao = frame[1] shl 16 or (frame[2] shl 8) or frame[3]
            if (icao <= 0) return null
            var aircraft = RecentAircraftCache.get(icao)
            if (aircraft == null) {
                aircraft = Aircraft(icao)
                RecentAircraftCache.add(icao, aircraft)
            }
            aircraft.isUat = true
            aircraft.setReady(false)
            aircraft.messageCount = aircraft.messageCount + 1
            aircraft.seen = System.currentTimeMillis()
            when (frame[0] shr 3 and 0x1F) {
                0, 4, 7, 8, 9, 10 -> decodeSv(frame, aircraft)
                1 -> {
                    decodeMs(frame, aircraft)
                    if (decodeSv(frame, aircraft) == 0) decodeAuxSv(frame, aircraft)
                }
                2, 5, 6 -> if (decodeSv(frame, aircraft) == 0) decodeAuxSv(frame, aircraft)
                3 -> {
                    decodeSv(frame, aircraft)
                    decodeMs(frame, aircraft)
                }
            }
            return aircraft
        }
        return null
    }
}