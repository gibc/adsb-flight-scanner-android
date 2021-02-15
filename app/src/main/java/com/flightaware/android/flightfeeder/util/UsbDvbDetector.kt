package com.flightaware.android.flightfeeder.util

import android.content.Context
import android.content.res.XmlResourceParser
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import com.flightaware.android.flightfeeder.R
import org.xmlpull.v1.XmlPullParser
import java.util.*

object UsbDvbDetector {
    private val sDevices = HashSet<String>()
    fun isValidDeviceConnected(context: Context): UsbDevice? {
        if (sDevices.size == 0) {
            var xrp: XmlResourceParser? = null
            try {
                xrp = context.resources.getXml(R.xml.device_filter)
                xrp.next()
                var eventType = xrp.eventType
                while (eventType != XmlPullParser.END_DOCUMENT) {
                    if (eventType == XmlPullParser.START_TAG
                            && xrp.name.equals("usb-device", ignoreCase = true)) {
                        val ident = (xrp.getAttributeIntValue(0, -1).toString() + "-"
                                + xrp.getAttributeIntValue(1, -1))
                        if (!ident.contains("-1")) sDevices.add(ident)
                    }
                    eventType = xrp.next()
                }
            } catch (ex: Exception) {
                // swallow
            } finally {
                xrp?.close()
            }
        }
        val usbManager = context
                .getSystemService(Context.USB_SERVICE) as UsbManager
        val deviceList = usbManager.deviceList
        val deviceIterator: Iterator<UsbDevice> = deviceList.values.iterator()
        while (deviceIterator.hasNext()) {
            val device = deviceIterator.next()
            return device  // gib - can't make device ids work in java version
            val ident = device.vendorId.toString() + "-" + device.productId
            if (sDevices.contains(ident)) return device
        }
        return null
    }
}