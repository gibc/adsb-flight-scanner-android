package com.flightaware.android.flightfeeder.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.flightaware.android.flightfeeder.activities.MainActivity

class UsbSdrDeviceDetachedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        intent.setClass(context, MainActivity::class.java)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }
}