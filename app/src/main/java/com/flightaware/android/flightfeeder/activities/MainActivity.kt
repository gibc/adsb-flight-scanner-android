package com.flightaware.android.flightfeeder.activities

import android.Manifest
import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.*
import android.content.SharedPreferences.Editor
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.net.Uri
import android.os.Bundle
import android.os.IBinder
import android.os.Parcelable
import android.os.SystemClock
import android.support.design.widget.NavigationView
import android.support.design.widget.NavigationView.OnNavigationItemSelectedListener
import android.support.v4.app.ActivityCompat
import android.support.v4.content.ContextCompat
import android.support.v4.widget.DrawerLayout
import android.support.v7.app.ActionBarDrawerToggle
import android.support.v7.app.AlertDialog
import android.support.v7.app.AppCompatActivity
import android.support.v7.widget.Toolbar
import android.text.TextUtils
import android.view.MenuItem
import android.view.View
import android.widget.*
import android.widget.AdapterView.OnItemClickListener
import com.flightaware.android.flightfeeder.App
import com.flightaware.android.flightfeeder.R
import com.flightaware.android.flightfeeder.adapters.PlaneAdapter
import com.flightaware.android.flightfeeder.analyzers.Aircraft
import com.flightaware.android.flightfeeder.analyzers.Analyzer
import com.flightaware.android.flightfeeder.analyzers.RecentAircraftCache
import com.flightaware.android.flightfeeder.services.ControllerService
import com.flightaware.android.flightfeeder.services.ControllerService.LocalBinder
import com.flightaware.android.flightfeeder.services.LocationService
import com.flightaware.android.flightfeeder.util.MovingAverage
import com.flightaware.android.flightfeeder.util.UsbDvbDetector
import com.google.android.gms.maps.model.LatLng
import java.util.*
// gethub comment
class MainActivity() : AppCompatActivity(), OnNavigationItemSelectedListener, OnItemClickListener, ServiceConnection {
    private var m1HertzUpdater: Thread? = null
    private var mAlert: AlertDialog? = null
    private var mBackPressedTime: Long = 0
    private var mDrawerLayout: DrawerLayout? = null
    private var mDrawerToggle: ActionBarDrawerToggle? = null
    private var mFilter: IntentFilter? = null
    private var mNavView: NavigationView? = null
    private var mOffDelay = 50
    private var mPermissionReceiver: BroadcastReceiver? = null
    private var mPlaneAdapter: PlaneAdapter? = null
    private var mPlaneList: ListView? = null
    private val mPlanes = ArrayList<Aircraft>()
    private var mPressBackToast: Toast? = null
    private var mRange: TextView? = null
    private var mRate: TextView? = null
    private var mReceiver: BroadcastReceiver? = null
    private var mRx: ImageView? = null
    private var mTx: ImageView? = null
    private var mService: ControllerService? = null
    private val mUsernameView: TextView? = null
    override fun onBackPressed() {
        if (mDrawerLayout!!.isDrawerVisible(mNavView)) {
            mDrawerLayout!!.closeDrawers()
            return
        }
        val currentTime = System.currentTimeMillis()
        if (currentTime - mBackPressedTime > 2000 /* Toast.LENGTH_LONG  */) {
            mPressBackToast!!.show()
            mBackPressedTime = currentTime
        } else {
            mPressBackToast!!.cancel()
            super.onBackPressed()
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        mDrawerToggle!!.onConfigurationChanged(newConfig)
    }

    @SuppressLint("ShowToast")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        startService(Intent(this, ControllerService::class.java))
        if (!App.Companion.sPrefs!!.getBoolean("override_location", false)) if (ContextCompat.checkSelfPermission(this,
                        Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            startService(Intent(this, LocationService::class.java))
        } else {
            ActivityCompat
                    .requestPermissions(
                            this, arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                            LOCATION_REQUEST_CODE)
        } else if (((LocationService.Companion.sLocation == null
                        ) && App.Companion.sPrefs!!.contains("latitude")
                        && App.Companion.sPrefs!!.contains("longitude"))) {
            val lat: Float = App.Companion.sPrefs!!.getFloat("latitude", 0f)
            val lon: Float = App.Companion.sPrefs!!.getFloat("longitude", 0f)
            LocationService.Companion.sLocation = LatLng(lat.toDouble(), lon.toDouble())
        }
        val toolbar = findViewById(R.id.toolbar) as Toolbar
        setSupportActionBar(toolbar)
        mNavView = findViewById(R.id.navigation_view) as NavigationView
        mNavView!!.setNavigationItemSelectedListener(this)
        mDrawerLayout = findViewById(R.id.drawer_layout) as DrawerLayout
        mDrawerToggle = ActionBarDrawerToggle(this, mDrawerLayout, toolbar,
                R.string.app_name, R.string.app_name)
        mDrawerLayout!!.setDrawerListener(mDrawerToggle)
        mRx = findViewById(R.id.rx) as ImageView
        mTx = findViewById(R.id.tx) as ImageView
        mRate = findViewById(R.id.rate) as TextView
        mRange = findViewById(R.id.range) as TextView
        mPlaneList = findViewById(R.id.output) as ListView
        mPressBackToast = Toast.makeText(this, R.string.text_press_again,
                Toast.LENGTH_LONG)
        mPlaneAdapter = PlaneAdapter(this, mPlanes)
        mPlaneList!!.adapter = mPlaneAdapter
        mPlaneList!!.onItemClickListener = this
        val rxOff: Runnable = Runnable { mRx!!.setImageResource(R.drawable.data_off) }
        val txOff: Runnable = object : Runnable {
            override fun run() {
                mTx!!.setImageResource(R.drawable.data_off)
            }
        }
        mFilter = IntentFilter()
        mFilter!!.addAction(ACTION_UPDATE_RX)
        mFilter!!.addAction(ACTION_UPDATE_TX)
        mFilter!!.addAction(ACTION_UPDATE_1HERTZ)
        mFilter!!.addAction(ACTION_MODE_CHANGE)
        mFilter!!.addAction(ACTION_LOGIN)
        mReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                val action = intent.action
                if (!TextUtils.isEmpty(action)) {
                    if ((action == ACTION_UPDATE_RX)) {
                        mRx!!.removeCallbacks(rxOff)
                        mRx!!.setImageResource(R.drawable.data_on)
                        mRx!!.postDelayed(rxOff, mOffDelay.toLong())
                    } else if ((action == ACTION_UPDATE_TX)) {
                        mTx!!.removeCallbacks(txOff)
                        mTx!!.setImageResource(R.drawable.data_on)
                        mTx!!.postDelayed(txOff, 200)
                    } else if ((action == ACTION_UPDATE_1HERTZ)) {
                        mRange!!.text = String.format("%.1f", Analyzer.Companion.sRange)
                        val rate = MovingAverage.currentAverage
                        val delay = (1000 / rate).toInt()
                        mOffDelay = Math.min(delay, 200)
                        mRate!!.text = String.format("%.1f", rate)
                        mPlanes.clear()
                        mPlanes.addAll(RecentAircraftCache.getActiveAircraftList(true))
                        mPlaneAdapter!!.notifyDataSetChanged()
                    } else if ((action == ACTION_MODE_CHANGE)) {
                        mRate!!.text = "0.0"
                        mRange!!.text = "0.0"
                        if (mService != null) setTitle(mService!!.isUat)
                    }
                }
            }
        }
        mPermissionReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                synchronized(this) {
                    val device: UsbDevice? = intent
                            .getParcelableExtra<Parcelable>(UsbManager.EXTRA_DEVICE) as UsbDevice?
                    if (intent.getBooleanExtra(
                                    UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
                        if (device != null) {
                            val editor: Editor = App.Companion.sPrefs!!.edit()
                            editor.putBoolean("usb_permission_granted", true)
                            editor.commit()
                            startListening(device)
                        } else {
                            val editor: Editor = App.Companion.sPrefs!!.edit()
                            editor.putBoolean("usb_permission_granted", false)
                            editor.commit()
                        }
                    } else {
                        val editor: Editor = App.Companion.sPrefs!!.edit()
                        editor.putBoolean("usb_permission_granted", false)
                        editor.commit()
                        finish()
                    }
                }
            }
        }
        registerReceiver(mPermissionReceiver, IntentFilter(
                ACTION_USB_PERMISSION))
        bindService(Intent(this, ControllerService::class.java), this,
                Context.BIND_IMPORTANT)
    }

    public override fun onDestroy() {
        if (mAlert != null) mAlert!!.dismiss()
        if (!App.Companion.sPrefs!!.getBoolean("pref_background", true) || (mService == null
                        ) || !mService!!.isScanning) stopService(Intent(this, ControllerService::class.java)) else mService!!.showNotification()
        unregisterReceiver(mPermissionReceiver)
        unbindService(this)
        super.onDestroy()
    }

    override fun onItemClick(listView: AdapterView<*>, view: View, position: Int,
                             id: Long) {
        val aircraft: Aircraft = listView.getItemAtPosition(position) as Aircraft ?: return
        val intent = Intent(Intent.ACTION_VIEW)
        intent.data = Uri.parse(("https://flightaware.com/live/flight/"
                + aircraft.identity))
        startActivity(intent)
    }

    override fun onNavigationItemSelected(menuItem: MenuItem): Boolean {
        mDrawerLayout!!.closeDrawers()
        val id = menuItem.itemId
        if (id == R.id.drawer_map) startActivity(Intent(this, MapActivity::class.java)) else if (id == R.id.drawer_settings) startActivity(Intent(this, SettingsActivity::class.java))
        return true
    }

    public override fun onNewIntent(intent: Intent) {
        if (intent != null) {
            val action = intent.action
            if (TextUtils.isEmpty(action)) return
            var device: UsbDevice? = null
            if ((action
                            == "android.hardware.usb.action.USB_DEVICE_ATTACHED")) {
                synchronized(this) {
                    device = intent
                            .getParcelableExtra<Parcelable>(UsbManager.EXTRA_DEVICE) as UsbDevice?
                    if (device != null) startListening(device!!) else if (mAlert == null || !mAlert!!.isShowing()) showNoDongle()
                }
            } else if ((action
                            == "android.hardware.usb.action.USB_DEVICE_DETACHED")) {
                if (mAlert == null || !mAlert!!.isShowing) showNoDongle()
                if (mService != null) mService!!.stopScanning(false)
            } else {
                device = UsbDvbDetector.isValidDeviceConnected(this); // gib - commended in from initial code
                if (device != null) {
                    val usbManager = getSystemService(Context.USB_SERVICE) as UsbManager
                    if ((usbManager.hasPermission(device)))  // gib - fix from java version
                                    //|| App.Companion.sPrefs!!.getBoolean("usb_permission_granted",
                                    //false)))
                        startListening(device!!)

                    else {
                        val permission = PendingIntent.getBroadcast(
                                this, 0, Intent(ACTION_USB_PERMISSION), 0)
                        usbManager.requestPermission(device, permission)
                    }
                } else if (mAlert == null || !mAlert!!.isShowing) showNoDongle()
            }
        }
    }

    override fun onPostCreate(savedInstanceState: Bundle?) {
        super.onPostCreate(savedInstanceState)
        mDrawerToggle!!.syncState()
    }

    override fun onRequestPermissionsResult(requestCode: Int,
                                            permissions: Array<String>, grantResults: IntArray) {
        if ((requestCode == LOCATION_REQUEST_CODE) && (grantResults.size > 0
                        ) && (grantResults[0] == PackageManager.PERMISSION_GRANTED)) {
            startService(Intent(this, LocationService::class.java))
        }
    }

    public override fun onResume() {
        super.onResume()
        val scanMode: String = App.Companion.sPrefs!!.getString("pref_scan_mode", "ADSB")
        if ((scanMode == "ADSB")) setTitle(false) else if ((scanMode == "UAT")) setTitle(true) else if (mService != null) setTitle(mService!!.isUat)
        mPlaneList!!.keepScreenOn = App.Companion.sPrefs!!.getBoolean("pref_keep_screen_on",
                false)
        if (mService != null) mService!!.stopForeground(true)
        App.Companion.sBroadcastManager!!.registerReceiver(mReceiver, mFilter)
        if (m1HertzUpdater == null) {
            m1HertzUpdater = object : Thread() {
                override fun run() {
                    while (true) {
                        App.Companion.sBroadcastManager!!.sendBroadcast(Intent(
                                ACTION_UPDATE_1HERTZ))
                        SystemClock.sleep(1000)
                    }
                }
            }
            (m1HertzUpdater as Thread).start()
        }
    }

    public override fun onStop() {
        if (m1HertzUpdater != null) {
            m1HertzUpdater!!.interrupt()
            m1HertzUpdater = null
        }
        App.Companion.sBroadcastManager!!.unregisterReceiver(mReceiver)
        super.onStop()
    }

    private fun setTitle(uatMode: Boolean) {
        var title = getString(R.string.app_name)
        if (uatMode) title += " - " + getString(R.string.text_978_mhz) else title += " - " + getString(R.string.text_1090_mhz)
        supportActionBar!!.title = title
    }

    private fun showNoDongle() {
        val builder = AlertDialog.Builder(this,
                R.style.Theme_AppCompat_Light_Dialog_Alert)
        builder.setTitle(R.string.dialog_no_usb_device_title)
        builder.setMessage(R.string.dialog_no_usb_device_msg)
        builder.setPositiveButton(android.R.string.ok, null)
        mAlert = builder.create()
        mAlert!!.show()
    }

    private fun startListening(device: UsbDevice) {
        println("1")
        if (mService != null) {
            mService!!.setUsbDevice(device)
            mService!!.startScanning()
        }
        if (mAlert != null && mAlert!!.isShowing) mAlert!!.dismiss()
    }

    override fun onServiceConnected(name: ComponentName, service: IBinder) {
        mService = (service as LocalBinder).service
        mService!!.stopForeground(true)
        onNewIntent(intent)
    }

    override fun onServiceDisconnected(name: ComponentName) {}

    companion object {
        val ACTION_LOGIN = "com.flightaware.android.flightfeeder.LOGIN"
        val ACTION_MODE_CHANGE = "com.flightaware.android.flightfeeder.MODE_CHANGE"
        val ACTION_UPDATE_1HERTZ = "com.flightaware.android.flightfeeder.UPDATE_1HERTZ"
        val ACTION_UPDATE_RX = "com.flightaware.android.flightfeeder.UPDATE_RX"
        val ACTION_UPDATE_TX = "com.flightaware.android.flightfeeder.UPDATE_TX"
        private val ACTION_USB_PERMISSION = "com.flightaware.android.flightfeeder.USB_PERMISSION"
        private val LOCATION_REQUEST_CODE = 100
    }
}