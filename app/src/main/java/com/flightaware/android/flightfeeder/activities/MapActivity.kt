package com.flightaware.android.flightfeeder.activities

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.net.wifi.WifiManager
import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import android.text.TextUtils
import android.util.Patterns
import android.webkit.WebView
import android.webkit.WebViewClient
import com.flightaware.android.flightfeeder.App
import com.flightaware.android.flightfeeder.R
import com.flightaware.android.flightfeeder.analyzers.NanoWebServer
import java.util.*

class MapActivity : AppCompatActivity() {
    private var mWebView: WebView? = null
    private var mLocalWebServer: NanoWebServer? = null

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (App.Companion.sWebServer == null || !App.Companion.sWebServer!!.isAlive
                || !App.Companion.sOnAccessPoint) {
            mLocalWebServer = NanoWebServer(this, "127.0.0.1")
            try {
                mLocalWebServer!!.start()
            } catch (e: Exception) {
                // swallow
            }
        }
        mWebView = WebView(this)
        mWebView!!.setWebViewClient(WebClient())
        mWebView!!.settings.javaScriptEnabled = true
        mWebView!!.settings.domStorageEnabled = true
        setContentView(mWebView)
        mWebView!!.loadUrl("http://127.0.0.1:8080")
        var ipStr = "127.0.0.1"
        if (App.Companion.sPrefs!!.getBoolean("pref_broadcast", true) && App.Companion.sOnAccessPoint) {
            val wifiMan = getSystemService(Context.WIFI_SERVICE) as WifiManager
            val info = wifiMan.connectionInfo
            if (info != null) {
                val ipAddress = info.ipAddress
                ipStr = String.format(Locale.US, "%d.%d.%d.%d",
                        ipAddress and 0xff, ipAddress shr 8 and 0xff,
                        ipAddress shr 16 and 0xff, ipAddress shr 24 and 0xff)
                if (ipStr == "0.0.0.0") ipStr = "127.0.0.1"
            }
        }
        val actionBar = supportActionBar
        val title = getString(R.string.text_map) + " - " + ipStr + ":8080"
        actionBar!!.setTitle(title)
    }

    public override fun onDestroy() {
        if (mLocalWebServer != null) mLocalWebServer!!.stop()
        super.onDestroy()
    }

    public override fun onResume() {
        super.onResume()
        mWebView!!.keepScreenOn = App.Companion.sPrefs!!.getBoolean("pref_keep_screen_on",
                false)
    }

    private inner class WebClient : WebViewClient() {
        override fun shouldOverrideUrlLoading(view: WebView, url: String): Boolean {
            if (App.Companion.sIsConnected && !TextUtils.isEmpty(url)
                    && Patterns.WEB_URL.matcher(url).matches()) {
                val uri = Uri.parse(url)
                val intent = Intent(Intent.ACTION_VIEW, uri)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                try {
                    startActivity(intent)
                } catch (ex: Exception) {
                    ex.printStackTrace()
                }
            }
            return true
        }
    }
}