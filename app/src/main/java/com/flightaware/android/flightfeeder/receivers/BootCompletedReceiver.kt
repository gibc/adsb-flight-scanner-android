package com.flightaware.android.flightfeeder.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.hardware.usb.UsbManager
import android.os.SystemClock
import com.flightaware.android.flightfeeder.App
import com.flightaware.android.flightfeeder.services.ControllerService
import com.flightaware.android.flightfeeder.services.LocationService
import com.flightaware.android.flightfeeder.util.UsbDvbDetector
import com.google.android.gms.maps.model.LatLng

class BootCompletedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (App.Companion.sPrefs!!.getBoolean("pref_auto_start_boot", true)) {
            val device = UsbDvbDetector.isValidDeviceConnected(context)
            if (device != null) {
                val usbManager = context
                        .getSystemService(Context.USB_SERVICE) as UsbManager
                if (usbManager.hasPermission(device)
                        || App.Companion.sPrefs!!.getBoolean("usb_permission_granted",
                                false)) {
                    object : Thread() {
                        override fun run() {
                            SystemClock.sleep(300000)
                            val service = Intent(context, ControllerService::class.java)
                            service.putExtra(UsbManager.EXTRA_DEVICE, device)
                            if (!App.Companion.sPrefs!!.getBoolean("override_location",
                                            false)) context.startService(Intent(context,
                                    LocationService::class.java)) else if (LocationService.Companion.sLocation == null && App.Companion.sPrefs!!.contains("latitude")
                                    && App.Companion.sPrefs!!.contains("longitude")) {
                                val lat: Float = App.Companion.sPrefs!!.getFloat("latitude", 0f)
                                val lon: Float = App.Companion.sPrefs!!.getFloat("longitude", 0f)
                                LocationService.Companion.sLocation = LatLng(lat.toDouble(), lon.toDouble())
                            }
                            context.startService(service)
                        }
                    }.start()
                }
            }
        }
    }
}