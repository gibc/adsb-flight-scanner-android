package com.flightaware.android.flightfeeder.analyzers

import android.content.Context
import android.os.SystemClock
import android.text.TextUtils
import com.flightaware.android.flightfeeder.App
import com.flightaware.android.flightfeeder.services.LocationService
import fi.iki.elonen.NanoHTTPD
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.*
import java.util.*
import kotlin.jvm.Throws

class NanoWebServer @JvmOverloads constructor(private val mContext: Context, ipAddress: String? = null) : NanoHTTPD(ipAddress, 8080) {
    private val mCacheDir: File
    private var mHistoryThread: Thread? = null
    private fun buildJsonArray(aircraftList: ArrayList<Aircraft>): JSONArray {
        val array = JSONArray()
        for (aircraft in aircraftList!!) {
            if (aircraft!!.messageCount < 2) continue
            val icao = aircraft!!.icao
            if (TextUtils.isEmpty(icao) || icao!!.length != 6) continue
            val now = System.currentTimeMillis()
            val seen = (now - aircraft.seen) / 1000
            val plane = JSONObject()
            try {
                plane.put("hex", icao!!.toLowerCase(Locale.US))
                val squawk = aircraft.squawk
                if (squawk != null) plane.put("squawk", String.format("%04x", aircraft.squawk))
                val ident = aircraft.identity
                if (!TextUtils.isEmpty(ident)) plane.put("flight", ident)
                val lat = aircraft.latitude
                val lon = aircraft.longitude
                if (lat != null && lon != null) {
                    plane.put("lat", lat)
                    plane.put("lon", lon)
                    plane.put("seen_pos",
                            (now - aircraft.seen) / 1000)
                }
                if (aircraft!!.isOnGround) plane.put("altitude", "ground") else plane.putOpt("altitude", aircraft.altitude)
                plane.putOpt("vert_rate", aircraft.verticalRate)
                plane.putOpt("track", aircraft.heading)
                plane.putOpt("speed", aircraft.velocity)
                val category = aircraft.category
                if (category != null) plane.put("category", String.format("%02X", category))
                plane.putOpt("messages", aircraft.messageCount)
                plane.putOpt("seen", seen)
                plane.putOpt("rssi", aircraft.averageSignalStrength)
                array.put(plane)
            } catch (e: JSONException) {
                e.printStackTrace()
            }
        }
        return array
    }

    private fun generateAircraftJson(): JSONObject? {
        try {
            val aircraft = JSONObject()
            aircraft.put("now", System.currentTimeMillis() / 1000)
            aircraft.put("messages", Analyzer.Companion.sFrameCount)
            val aircraftList = RecentAircraftCache.getActiveAircraftList(true)
            aircraft.put("aircraft", buildJsonArray(aircraftList))
            return aircraft
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return null
    }

    override fun serve(uri: String?, method: Method?,
                       headers: Map<String?, String?>?, parms: Map<String, String?>?,
                       files: Map<String, String>?): Response? {
        var uri = uri
        uri = sRoot + uri
        var `is`: InputStream? = null
        try {
            if (uri.contains("/data/aircraft.json")) {
                val aircraft = generateAircraftJson()
                if (aircraft != null) {
                    try {
                        `is` = ByteArrayInputStream(aircraft.toString(2)
                                .toByteArray())
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            } else if (uri.contains("/data/history_")) {
                val elements = uri.split("_").toTypedArray()
                val idx = elements[elements.size - 1].replace(".json", "")
                var index = -1
                index = try {
                    idx.toInt()
                } catch (ex: Exception) {
                    ex.printStackTrace()
                    return null
                }
                if (index < 0 || index >= 120) return null
                val file = File(mCacheDir, "history_$index.json")
                val fis = FileInputStream(file)
                `is` = BufferedInputStream(fis)
            } else if (uri.contains("/data/receiver.json")) {
                val receiver = JSONObject()
                if (LocationService.Companion.sLocation != null) {
                    try {
                        receiver.put("lat", LocationService.Companion.sLocation!!.latitude)
                        receiver.put("lon", LocationService.Companion.sLocation!!.longitude)
                        receiver.put("version", App.Companion.sVersion)
                        receiver.put("refresh", 1000)
                        receiver.put("history", 120)
                        `is` = ByteArrayInputStream(receiver.toString()
                                .toByteArray())
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            } else {
                if (uri.equals(sRoot, ignoreCase = true)
                        || uri.equals("$sRoot/", ignoreCase = true)
                        || uri.contains("..")) uri = uri + "gmap.html"
                `is` = BufferedInputStream(mContext.assets.open(uri))
            }
        } catch (e: IOException) {
            e.printStackTrace()
        }
        if (`is` != null) {
            var mimeType: String? = null
            if (uri!!.endsWith(".html") || uri.endsWith("htm")) mimeType = MODES_CONTENT_TYPE_HTML else if (uri.endsWith(".css")) mimeType = MODES_CONTENT_TYPE_CSS else if (uri.endsWith(".js")) mimeType = MODES_CONTENT_TYPE_JS else if (uri.endsWith(".json")) mimeType = MODES_CONTENT_TYPE_JSON else if (uri.endsWith(".ico")) mimeType = MODES_CONTENT_TYPE_ICON
            if (TextUtils.isEmpty(mimeType)) mimeType = MODES_CONTENT_TYPE_PLAIN
            return Response(Response.Status.OK, mimeType, `is`)
        }
        return super.serve(uri, method, headers, parms, files)
    }

    @Throws(IOException::class)
    override fun start() {
        super.start()
        if (mHistoryThread == null) {
            mHistoryThread = object : Thread() {
                override fun run() {
                    var i = 0
                    while (true) {
                        SystemClock.sleep(30000)
                        val aircraft = generateAircraftJson()
                        if (aircraft != null) {
                            try {
                                val file = File(mCacheDir, "history_" + i
                                        + ".json")
                                val fos = FileOutputStream(
                                        file)
                                val bos = BufferedOutputStream(
                                        fos)
                                bos.write(aircraft.toString(2).toByteArray())
                                bos.flush()
                                bos.close()
                                if (++i == 120) i = 0
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        }
                    }
                }
            }
            mHistoryThread!!.setName("History JSON Writer Thread")
            mHistoryThread!!.start()
        }
    }

    override fun stop() {
        super.stop()
        if (mHistoryThread != null) {
            mHistoryThread!!.interrupt()
            mHistoryThread = null
        }
    }

    companion object {
        private const val MODES_CONTENT_TYPE_CSS = "text/css;charset=utf-8"
        private const val MODES_CONTENT_TYPE_HTML = "text/html;charset=utf-8"
        private const val MODES_CONTENT_TYPE_ICON = "image/x-icon"
        private const val MODES_CONTENT_TYPE_JS = "application/javascript;charset=utf-8"
        private const val MODES_CONTENT_TYPE_JSON = "application/json;charset=utf-8"
        private const val MODES_CONTENT_TYPE_PLAIN = "text/plain;charset=utf-8"
        private const val sRoot = "public_html"
    }

    init {
        mCacheDir = mContext.cacheDir
    }
}