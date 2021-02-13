package com.flightaware.android.flightfeeder.receivers

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.usb.UsbManager
import com.flightaware.android.flightfeeder.App
import com.flightaware.android.flightfeeder.analyzers.NanoWebServer
import com.flightaware.android.flightfeeder.services.ControllerService
import com.flightaware.android.flightfeeder.services.LocationService
import com.flightaware.android.flightfeeder.util.UsbDvbDetector
import com.google.android.gms.maps.model.LatLng

class ConnectivityChangedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        App.Companion.isInternetAvailable
        if (App.Companion.sPrefs!!.getBoolean("pref_broadcast", true) && App.Companion.sOnAccessPoint) {
            if (App.Companion.sWebServer != null) {
                App.Companion.sWebServer!!.stop()
                App.Companion.sWebServer = null
            }
            App.Companion.sWebServer = NanoWebServer(context)
            try {
                App.Companion.sWebServer!!.start()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        } else if (App.Companion.sWebServer != null) {
            App.Companion.sWebServer!!.stop()
            App.Companion.sWebServer = null
        }
        val device = UsbDvbDetector.isValidDeviceConnected(context)
        if (device != null) {
            val usbManager = context
                    .getSystemService(Context.USB_SERVICE) as UsbManager
            if (usbManager.hasPermission(device)
                    || App.Companion.sPrefs!!.getBoolean("usb_permission_granted", false)) {
                val service = Intent(context, ControllerService::class.java)
                service.putExtra(UsbManager.EXTRA_DEVICE, device)
                if (!App.Companion.sPrefs!!.getBoolean("override_location", false)) context.startService(Intent(context,
                        LocationService::class.java)) else if (LocationService.Companion.sLocation == null && App.Companion.sPrefs!!.contains("latitude")
                        && App.Companion.sPrefs!!.contains("longitude")) {
                    val lat: Float = App.Companion.sPrefs!!.getFloat("latitude", 0f)
                    val lon: Float = App.Companion.sPrefs!!.getFloat("longitude", 0f)
                    LocationService.Companion.sLocation = LatLng(lat.toDouble(), lon.toDouble())
                }
                context.startService(service)
            }
        }

        // if there is a connection disable this component
        if (App.Companion.sOnAccessPoint) {
            context.packageManager.setComponentEnabledSetting(
                    ComponentName(context,
                            ConnectivityChangedReceiver::class.java),
                    PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                    PackageManager.DONT_KILL_APP)
        }
    }
}