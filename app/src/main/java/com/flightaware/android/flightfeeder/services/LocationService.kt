package com.flightaware.android.flightfeeder.services

import android.Manifest
import android.annotation.SuppressLint
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences.Editor
import android.content.pm.PackageManager
import android.location.Location
import android.os.Bundle
import android.os.IBinder
import android.os.PowerManager
import android.os.PowerManager.WakeLock
import android.os.SystemClock
import android.support.v4.content.ContextCompat
import com.flightaware.android.flightfeeder.App
import com.flightaware.android.flightfeeder.BuildConfig
import com.flightaware.android.flightfeeder.R
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.api.GoogleApiClient
import com.google.android.gms.common.api.GoogleApiClient.ConnectionCallbacks
import com.google.android.gms.common.api.GoogleApiClient.OnConnectionFailedListener
import com.google.android.gms.location.LocationListener
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.model.LatLng

class LocationService : Service(), ConnectionCallbacks, OnConnectionFailedListener, LocationListener {
    private var mGoogleApiClient: GoogleApiClient? = null
    private var mWakeLock: WakeLock? = null
    private var mRetryThread: Thread? = null
    override fun onBind(intent: Intent): IBinder? {
        return null
    }

    override fun onConnected(bundle: Bundle?) {
        if (ContextCompat.checkSelfPermission(this,
                        Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            val location = LocationServices.FusedLocationApi
                    .getLastLocation(mGoogleApiClient)
            onLocationChanged(location)
            val locationRequest = LocationRequest.create()

            // Use high accuracy
            locationRequest.priority = LocationRequest.PRIORITY_HIGH_ACCURACY

            // Set the update interval to 5 minutes
            locationRequest.interval = 5 * 60 * 1000.toLong()
            LocationServices.FusedLocationApi.requestLocationUpdates(
                    mGoogleApiClient, locationRequest, this)
        } else stopSelf()
    }

    override fun onConnectionFailed(result: ConnectionResult) {
        if (mRetryThread != null) {
            mRetryThread!!.interrupt()
            mRetryThread = null
        }
        mRetryThread = object : Thread() {
            override fun run() {
                SystemClock.sleep(30000)
                if (mGoogleApiClient != null) mGoogleApiClient!!.connect()
                mRetryThread = null
            }
        }
        mRetryThread!!.start()
    }

    override fun onConnectionSuspended(cause: Int) {
        /*
		 * Remove location updates for a listener. The current service is the
		 * listener, so the argument is "this".
		 */
        LocationServices.FusedLocationApi.removeLocationUpdates(
                mGoogleApiClient, this)
    }

    @SuppressLint("InlinedApi")
    override fun onCreate() {
        super.onCreate()
        onLocationChanged(null)
        val appName = getString(R.string.app_name)
        val mgr = getSystemService(Context.POWER_SERVICE) as PowerManager
        mWakeLock = mgr.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, appName
                + " Location Service")
        mWakeLock!!.acquire()
        mGoogleApiClient = GoogleApiClient.Builder(this)
                .addApi(LocationServices.API).addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this).build()
        mGoogleApiClient!!.connect()
        if (BuildConfig.DEBUG) println("Location service started")
    }

    override fun onDestroy() {
        if (mRetryThread != null) mRetryThread!!.interrupt()
        if (mGoogleApiClient != null) {
            mGoogleApiClient!!.disconnect()
            mGoogleApiClient = null
        }
        if (mWakeLock != null && mWakeLock!!.isHeld) {
            mWakeLock!!.release()
            mWakeLock = null
        }
        if (BuildConfig.DEBUG) println("Location service stopped")
        super.onDestroy()
    }

    override fun onLocationChanged(location: Location?) {
        if (location == null) {
            val lat: Double = App.Companion.sPrefs!!.getFloat("latitude", 0f).toDouble()
            val lon: Double = App.Companion.sPrefs!!.getFloat("longitude", 0f).toDouble()
            if (lat == lon && lat == 0.0) return
            sLocation = LatLng(lat, lon)
        } else {
            sLocation = LatLng(location.latitude,
                    location.longitude)
            val editor: Editor = App.Companion.sPrefs!!.edit()
            editor.putFloat("latitude", sLocation!!.latitude.toFloat())
            editor.putFloat("longitude", sLocation!!.longitude.toFloat())
            editor.commit()
        }
    }

    override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    companion object {
        @Volatile
        var sLocation: LatLng? = null
    }
}