package com.flightaware.android.flightfeeder

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.SharedPreferences
import android.content.SharedPreferences.OnSharedPreferenceChangeListener
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.os.SystemClock
import android.preference.PreferenceManager
import android.support.v4.content.LocalBroadcastManager
import com.flightaware.android.flightfeeder.analyzers.NanoWebServer
import com.flightaware.android.flightfeeder.receivers.ConnectivityChangedReceiver

class App : Application(), OnSharedPreferenceChangeListener {
    override fun onCreate() {
        super.onCreate()
        sContext = this
        sPrefs = PreferenceManager.getDefaultSharedPreferences(this)
        sPrefs!!.registerOnSharedPreferenceChangeListener(this)
        sConnectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        sComponentName = ComponentName(this,
                ConnectivityChangedReceiver::class.java)
        sPackageManager = packageManager
        sBroadcastManager = LocalBroadcastManager.getInstance(this)
        sStartTime = SystemClock.uptimeMillis()
        try {
            val packageInfo = sPackageManager!!.getPackageInfo(
                    packageName, 0)
            sVersion = packageInfo.versionName
        } catch (e: Exception) {
            e.printStackTrace()
        }
        isInternetAvailable
        if (sPrefs!!.getBoolean("pref_broadcast", true) && sOnAccessPoint) {
            sWebServer = NanoWebServer(this)
            try {
                sWebServer!!.start()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        object : Thread() {
            override fun run() {
                isInternetAvailable
                SystemClock.sleep(5000)
            }
        }.start()
    }

    override fun onSharedPreferenceChanged(prefs: SharedPreferences, key: String) {
        if (key == "pref_broadcast") {
            isInternetAvailable
            if (sPrefs!!.getBoolean("pref_broadcast", true) && sOnAccessPoint) {
                if (sWebServer != null) {
                    sWebServer!!.stop()
                    sWebServer = null
                }
                sWebServer = NanoWebServer(this)
                try {
                    sWebServer!!.start()
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            } else if (sWebServer != null) {
                sWebServer!!.stop()
                sWebServer = null
            }
        }
    }

    companion object {
        var sBroadcastManager: LocalBroadcastManager? = null
        private var sComponentName: ComponentName? = null
        private var sConnectivityManager: ConnectivityManager? = null
        var sContext: Context? = null

        @Volatile
        var sIsConnected = false

        @Volatile
        private var sLastCheck: Long = 0

        @Volatile
        var sOnAccessPoint = false
        private var sPackageManager: PackageManager? = null
        var sPrefs: SharedPreferences? = null
        var sStartTime: Long = 0
        var sVersion: String? = null
        var sWebServer: NanoWebServer? = null

        // if there is no access point and the component is disabled, enable
        // it
        val isInternetAvailable: Boolean
            get() {
                val now = SystemClock.uptimeMillis()
                if (now - sLastCheck > 2000) {
                    sLastCheck = now
                    val info = sConnectivityManager!!.activeNetworkInfo
                    sIsConnected = (info != null && info.isAvailable
                            && info.isConnected)
                    sOnAccessPoint = (sIsConnected
                            && info != null && (info.type == ConnectivityManager.TYPE_WIFI || info
                            .type == ConnectivityManager.TYPE_ETHERNET))
                    val state = sPackageManager!!
                            .getComponentEnabledSetting(sComponentName)

                    // if there is no access point and the component is disabled, enable
                    // it
                    if (!sOnAccessPoint) {
                        if (sWebServer != null) {
                            sWebServer!!.stop()
                            sWebServer = null
                        }
                        if (state == PackageManager.COMPONENT_ENABLED_STATE_DISABLED) {
                            sPackageManager!!.setComponentEnabledSetting(sComponentName,
                                    PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                                    PackageManager.DONT_KILL_APP)
                        }
                    }
                }
                return sIsConnected
            }
    }
}