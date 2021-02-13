package com.flightaware.android.flightfeeder.fragments

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences.Editor
import android.graphics.Rect
import android.os.Bundle
import android.support.v4.app.DialogFragment
import android.support.v7.app.AppCompatDialog
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.Toast
import com.flightaware.android.flightfeeder.App
import com.flightaware.android.flightfeeder.R
import com.flightaware.android.flightfeeder.services.LocationService
import com.google.android.gms.maps.model.LatLng

class AntennaLocationDialogFragment : DialogFragment(), View.OnClickListener {
    interface OnLocationModeChangeListener {
        fun onLocatonModeChange(auto: Boolean)
    }

    private var mLatitude: EditText? = null
    private var mListener: OnLocationModeChangeListener? = null
    private var mLongitude: EditText? = null
    override fun onClick(view: View) {
        val id = view.id
        if (id == R.id.buttonCancel) dismiss() else if (id == R.id.buttonAuto) {
            val editor: Editor = App.Companion.sPrefs!!.edit()
            editor.putBoolean("override_location", false)
            editor.commit()
            val context: Context = activity
            context.startService(Intent(context, LocationService::class.java))
            mListener!!.onLocatonModeChange(true)
            dismiss()
        } else if (id == R.id.buttonSet) {
            var lat: Float? = null
            var lon: Float? = null
            var input = mLatitude!!.text.toString().trim { it <= ' ' }
            try {
                lat = input.toFloat()
                if (lat > 90 || lat < -90) {
                    showError()
                    return
                }
            } catch (ex: Exception) {
                showError()
                return
            }
            input = mLongitude!!.text.toString().trim { it <= ' ' }
            try {
                lon = input.toFloat()
                if (lon > 360) {
                    showError()
                    return
                }
                if (lon == 360f) lon = 0f
                if (lon > 180) lon = 180f - lon
            } catch (ex: Exception) {
                showError()
                return
            }
            if (lat == null || lon == null) {
                showError()
                return
            }
            mLatitude!!.setText(String.format("%.6f", lat))
            mLongitude!!.setText(String.format("%.6f", lon))
            val context: Context = activity
            context.stopService(Intent(context, LocationService::class.java))
            LocationService.Companion.sLocation = LatLng(lat.toDouble(), lon.toDouble())
            val editor: Editor = App.Companion.sPrefs!!.edit()
            editor.putFloat("latitude", lat)
            editor.putFloat("longitude", lon)
            editor.putBoolean("override_location", true)
            editor.commit()
            mListener!!.onLocatonModeChange(false)
            dismiss()
        }
    }

    override fun onCreateDialog(savedInstanceState: Bundle): AppCompatDialog {
        val dialog = AppCompatDialog(activity, theme)
        dialog.setTitle(R.string.prefs_antenna_location_title)
        dialog.setCanceledOnTouchOutside(false)
        return dialog
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                              savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_dialog_antenna_location,
                container, false)
    }

    override fun onStart() {
        super.onStart()
        if (dialog != null) {
            val displayRectangle = Rect()
            val window = activity.window
            window.decorView
                    .getWindowVisibleDisplayFrame(displayRectangle)
            dialog.window.setLayout(
                    (displayRectangle.width() * 0.9f).toInt(),
                    ViewGroup.LayoutParams.WRAP_CONTENT)
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        mLatitude = view.findViewById(R.id.latitude) as EditText
        if (LocationService.Companion.sLocation != null) mLatitude!!.setText(String.format("%.6f",
                LocationService.Companion.sLocation!!.latitude))
        mLongitude = view.findViewById(R.id.longitude) as EditText
        if (LocationService.Companion.sLocation != null) mLongitude!!.setText(String.format("%.6f",
                LocationService.Companion.sLocation!!.longitude))
        view.findViewById(R.id.buttonCancel).setOnClickListener(this)
        view.findViewById(R.id.buttonAuto).setOnClickListener(this)
        view.findViewById(R.id.buttonSet).setOnClickListener(this)
    }

    fun setLocationModeChangeListener(
            listener: OnLocationModeChangeListener?) {
        mListener = listener
    }

    private fun showError() {
        Toast.makeText(activity, R.string.text_invalid_location,
                Toast.LENGTH_LONG).show()
    }
}