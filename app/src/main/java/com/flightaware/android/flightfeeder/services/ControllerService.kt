package com.flightaware.android.flightfeeder.services

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.SharedPreferences.OnSharedPreferenceChangeListener
import android.graphics.BitmapFactory
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.net.wifi.WifiManager
import android.net.wifi.WifiManager.WifiLock
import android.os.Binder
import android.os.IBinder
import android.os.PowerManager
import android.os.PowerManager.WakeLock
import android.os.SystemClock
import android.support.v4.app.NotificationCompat
import com.flightaware.android.flightfeeder.App
import com.flightaware.android.flightfeeder.BuildConfig
import com.flightaware.android.flightfeeder.R
import com.flightaware.android.flightfeeder.activities.MainActivity
import com.flightaware.android.flightfeeder.analyzers.Analyzer
import com.flightaware.android.flightfeeder.analyzers.AvrFormatExporter
import com.flightaware.android.flightfeeder.analyzers.BeastFormatExporter
import com.flightaware.android.flightfeeder.analyzers.dump1090.Dump1090
import com.flightaware.android.flightfeeder.analyzers.dump978.Dump978
import com.flightaware.android.flightfeeder.services.ControllerService
import com.flightaware.android.flightfeeder.util.MovingAverage

class ControllerService : Service(), OnSharedPreferenceChangeListener {
    /**
     * Class for clients to access. Because we know this service always runs in
     * the same process as its clients, we don't need to deal with IPC.
     */
    inner class LocalBinder : Binder() {
        val service: ControllerService
            get() = this@ControllerService
    }

    private var mBinder: IBinder? = null
    private var mDevice: UsbDevice? = null

    @Volatile
    var isUat = false
        private set
    var isScanning = false
        private set
    private var mScanTimer: Thread? = null
    private var mUsbManager: UsbManager? = null
    private var mWakeLock: WakeLock? = null
    private var mWifiLock: WifiLock? = null
    private fun changeModes() {
        if (!Dump1090.sExit) Dump1090.stop()
        if (!Dump978.sExit) Dump978.stop()
        isUat = !isUat
        startScanning()
        App.Companion.sBroadcastManager!!.sendBroadcast(Intent(
                MainActivity.Companion.ACTION_MODE_CHANGE))
    }

    override fun onBind(intent: Intent): IBinder {
        return mBinder!!
    }

    @SuppressLint("InlinedApi")
    override fun onCreate() {
        super.onCreate()
        mBinder = LocalBinder()
        val appName = getString(R.string.app_name)
        val mgr = getSystemService(Context.POWER_SERVICE) as PowerManager
        mWakeLock = mgr.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, appName
                + " Controller Service")
        mWakeLock!!.acquire()
        val wifiMan = getSystemService(Context.WIFI_SERVICE) as WifiManager
        mWifiLock = wifiMan.createWifiLock(
                WifiManager.WIFI_MODE_FULL_HIGH_PERF, appName
                + " Controller Service")
        mWifiLock!!.acquire()
        mUsbManager = getSystemService(Context.USB_SERVICE) as UsbManager
        App.Companion.sPrefs!!.registerOnSharedPreferenceChangeListener(this)
        if (App.Companion.sPrefs!!.getBoolean("pref_beast", false)) BeastFormatExporter.start()
        if (App.Companion.sPrefs!!.getBoolean("pref_avr", false)) AvrFormatExporter.start()
        if (BuildConfig.DEBUG) println("Controller service started")
    }

    override fun onDestroy() {
        App.Companion.sPrefs!!.unregisterOnSharedPreferenceChangeListener(this)
        if (mWakeLock != null && mWakeLock!!.isHeld) {
            mWakeLock!!.release()
            mWakeLock = null
        }
        if (mWifiLock != null && mWifiLock!!.isHeld) {
            mWifiLock!!.release()
            mWifiLock = null
        }
        stopScanning(false)
        BeastFormatExporter.stop()
        AvrFormatExporter.stop()
        if (BuildConfig.DEBUG) println("Controller service stopped")
        super.onDestroy()
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences,
                                           key: String) {
        if (key == "pref_scan_mode") {
            if (mScanTimer != null) {
                mScanTimer!!.interrupt()
                mScanTimer = null
            }
            changeModes()
        } else if (key == "pref_mlat") {
            stopScanning(false)
            startScanning()
        }
    }

    override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {
        if (!isScanning && intent != null && intent.hasExtra(UsbManager.EXTRA_DEVICE)) {
            val device = intent
                    .getParcelableExtra<UsbDevice>(UsbManager.EXTRA_DEVICE)
            if (device != null) {
                mDevice = device
                startScanning()
                showNotification()
            }
        }
        return START_STICKY
    }

    fun setUsbDevice(device: UsbDevice?) {
        mDevice = device
    }

    fun showNotification() {
        val resultIntent = Intent(this, MainActivity::class.java)
        val resultPendingIntent = PendingIntent.getActivity(this, 0,
                resultIntent, PendingIntent.FLAG_UPDATE_CURRENT)
        val stopIntent = Intent(
                "com.flightaware.android.flightfeeder.STOP")
        val pendingStopIntent = PendingIntent.getBroadcast(this, 0,
                stopIntent, PendingIntent.FLAG_UPDATE_CURRENT)
        val title = getString(R.string.app_name)
        val notice = getString(R.string.text_running_in_background)
        val style = NotificationCompat.BigTextStyle()
        style.setBigContentTitle(title).bigText(notice)
        val builder = NotificationCompat.Builder(
                this)
                .setContentTitle(title)
                .setContentText(notice)
                .setStyle(NotificationCompat.BigTextStyle())
                .setTicker(notice)
                .setSmallIcon(R.drawable.ic_stat_notify)
                .setLargeIcon(
                        BitmapFactory.decodeResource(resources,
                                R.drawable.ic_launcher))
                .setContentIntent(resultPendingIntent)
                .setOngoing(true)
                .setWhen(0)
                .setStyle(style)
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .addAction(R.drawable.ic_delete, getString(R.string.text_stop),
                        pendingStopIntent)
        startForeground(R.string.app_name, builder.build())
    }

    fun startScanning() {
        Analyzer.Companion.sFrameCount = 0
        MovingAverage.reset()
        //val scanMode: String = App.Companion.sPrefs!!.getString("pref_scan_mode", "ADSB") gib - put back
        val scanMode = "UAT"
        try {
            if (scanMode == "ADSB") {
                isUat = false
                Dump1090.start(mUsbManager, mDevice)
            } else if (scanMode == "UAT") {
                isUat = true
                Dump978.start(mUsbManager, mDevice)
            } else {
                if (isUat) Dump978.start(mUsbManager, mDevice) else {
                    Dump1090.start(mUsbManager, mDevice)
                }
                mScanTimer = object : Thread() {
                    override fun run() {
                        if (isUat) SystemClock.sleep(20000) // 20 seconds
                        else SystemClock.sleep(40000) // 40 seconds
                        changeModes()
                    }
                }
                mScanTimer!!.start()
            }
            isScanning = true
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun stopScanning(allowAutoRestart: Boolean) {
        if (mScanTimer != null) {
            mScanTimer!!.interrupt()
            mScanTimer = null
        }
        if (!Dump1090.sExit) Dump1090.stop()
        if (!Dump978.sExit) Dump978.stop()
        isScanning = allowAutoRestart
    }
}