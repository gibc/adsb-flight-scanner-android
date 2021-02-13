package com.flightaware.android.flightfeeder.fragments

import android.os.Bundle
import android.support.v7.app.AlertDialog
import android.support.v7.preference.Preference
import android.support.v7.preference.PreferenceFragmentCompat
import android.webkit.WebView
import com.flightaware.android.flightfeeder.App
import com.flightaware.android.flightfeeder.R
import com.flightaware.android.flightfeeder.analyzers.AvrFormatExporter
import com.flightaware.android.flightfeeder.analyzers.BeastFormatExporter
import com.flightaware.android.flightfeeder.fragments.AntennaLocationDialogFragment.OnLocationModeChangeListener
import com.flightaware.android.flightfeeder.services.LocationService

class SettingsFragment : PreferenceFragmentCompat(), Preference.OnPreferenceClickListener, Preference.OnPreferenceChangeListener, OnLocationModeChangeListener {
    private var mLocation: Preference? = null
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        addPreferencesFromResource(R.xml.preferences)
        var pref = findPreference("pref_scan_mode")
        pref.onPreferenceChangeListener = this
        pref.isEnabled = App.Companion.sPrefs!!.getBoolean("in_us", false)
        setScanModeSummary(pref, App.Companion.sPrefs!!.getString("pref_scan_mode", "ADSB"))
        pref = findPreference("pref_avr")
        pref.onPreferenceChangeListener = this
        pref = findPreference("pref_beast")
        pref.onPreferenceChangeListener = this
        pref = findPreference("pref_licenses")
        pref.onPreferenceClickListener = this
        pref = findPreference("pref_version")
        pref.setSummary(App.Companion.sVersion)
        mLocation = findPreference("pref_location")
        mLocation!!.setOnPreferenceClickListener(this)
        onLocatonModeChange(!App.Companion.sPrefs!!.getBoolean("override_location", false))
    }

    override fun onCreatePreferences(bundle: Bundle, key: String) {}
    override fun onLocatonModeChange(auto: Boolean) {
        if (auto) mLocation!!.setSummary(R.string.text_auto) else if (LocationService.Companion.sLocation != null) {
            val prompt = getString(R.string.text_manually_set)
            mLocation!!.summary = String.format("%s: %.6f, %.6f", prompt,
                    LocationService.Companion.sLocation!!.latitude,
                    LocationService.Companion.sLocation!!.longitude)
        } else mLocation!!.summary = "Unknown"
    }

    override fun onPreferenceChange(pref: Preference, newValue: Any): Boolean {
        val key = pref.key
        if (key == "pref_scan_mode") setScanModeSummary(pref, newValue as String) else if (key == "pref_avr") {
            val start = newValue as Boolean
            if (start) AvrFormatExporter.start() else AvrFormatExporter.stop()
        } else if (key == "pref_beast") {
            val start = newValue as Boolean
            if (start) BeastFormatExporter.start() else BeastFormatExporter.stop()
        }
        return true
    }

    override fun onPreferenceClick(pref: Preference): Boolean {
        val key = pref.key
        if (key == "pref_licenses") {
            val webview = WebView(activity)
            webview.loadUrl("file:///android_asset/licenses.html")
            val alert = AlertDialog.Builder(activity,
                    R.style.Theme_AppCompat_Light_Dialog_Alert)
            alert.setView(webview)
            alert.setPositiveButton(android.R.string.ok, null)
            alert.show()
        } else if (key == "pref_location") {
            val fragment = AntennaLocationDialogFragment()
            fragment.setLocationModeChangeListener(this)
            fragment.show(fragmentManager, null)
        }
        return true
    }

    private fun setScanModeSummary(pref: Preference, scanMode: String) {
        var summary: String? = null
        summary = if (scanMode == "ADSB") getString(R.string.prefs_text_1090) else if (scanMode == "UAT") getString(R.string.prefs_text_978_mhz) else getString(R.string.prefs_text_both)
        pref.summary = summary
    }
}