package com.flightaware.android.flightfeeder.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.flightaware.android.flightfeeder.services.ControllerService

class StopServiceReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        context.stopService(Intent(context, ControllerService::class.java))
    }
}