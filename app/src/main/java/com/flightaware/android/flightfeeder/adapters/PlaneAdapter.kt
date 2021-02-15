package com.flightaware.android.flightfeeder.adapters

import android.content.Context
import android.support.v4.content.ContextCompat
import android.text.Html
import android.text.TextUtils
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.ImageView
import android.widget.TextView
import com.flightaware.android.flightfeeder.App
import com.flightaware.android.flightfeeder.R
import com.flightaware.android.flightfeeder.analyzers.Aircraft
import java.util.*
import java.util.regex.Pattern

class PlaneAdapter2(context: Context?, planeList: ArrayList<Aircraft>) : BaseAdapter() {
    private class ViewHolder {
        var icao: TextView? = null
        var ident: TextView? = null
        var squawk: TextView? = null
        var latitude: TextView? = null
        var longitude: TextView? = null
        var altitude: TextView? = null
        var speed: TextView? = null
        var heading: TextView? = null
        var uat: ImageView? = null
    }

    private val mPlaneList: ArrayList<Aircraft>
    private val mInflater: LayoutInflater
    private val mWebLinkColor: Int
    private val mNormalColor: Int
    private var mBoth: Boolean
    override fun getCount(): Int {
        return mPlaneList.size
    }

    override fun getItem(position: Int): Aircraft? {
        return if (position < mPlaneList.size) mPlaneList[position] else null
    }

    override fun getItemId(position: Int): Long {
        return position.toLong()
    }

    override fun getView(position: Int, view: View, parent: ViewGroup): View {
        // gib - call to this funtion blows null ptr execption
        Log.d("TAG1","get View position: " + position.toString() )
        var view = view
        if (view == null) view = mInflater.inflate(R.layout.item_active_plane, parent, false)
        var holder: ViewHolder? = null
        if (view.tag == null) {
            holder = ViewHolder()
            Log.d("TAG","get View got holder"  )
            holder.icao = view.findViewById(R.id.icao) as TextView
            holder.ident = view.findViewById(R.id.ident) as TextView
            holder.squawk = view.findViewById(R.id.squawk) as TextView
            holder.latitude = view.findViewById(R.id.latitude) as TextView
            holder.longitude = view.findViewById(R.id.longitude) as TextView
            holder.altitude = view.findViewById(R.id.altitude) as TextView
            holder.speed = view.findViewById(R.id.speed) as TextView
            holder.heading = view.findViewById(R.id.heading) as TextView
            holder.uat = view.findViewById(R.id.uat_flag) as ImageView
        } else holder = view.tag as ViewHolder
        val aircraft = getItem(position)
        Log.d("TAG2","get View getting Aircarft" )
        if (mBoth) {
            if (aircraft!!.isUat) holder.uat!!.visibility = View.VISIBLE else holder.uat!!.visibility = View.GONE
        } else holder.uat!!.visibility = View.GONE
        holder.icao!!.setText(aircraft!!.icao)
        var ident = aircraft.identity
        if (isEnabled(position)) {
            holder.ident!!.setTextColor(mWebLinkColor)
            ident = "<u>$ident</u>"
            holder.ident!!.text = Html.fromHtml(ident)
        } else {
            holder.ident!!.setTextColor(mNormalColor)
            holder.ident!!.text = ident
        }
        if (aircraft.squawk == null) holder.squawk!!.text = null else holder.squawk!!.text = String.format("%4x", aircraft.squawk)
        if (aircraft.latitude == null) holder.latitude!!.text = null else holder.latitude!!.text = String.format("%.3f",
                aircraft.latitude)
        if (aircraft.longitude == null) holder.longitude!!.text = null else holder.longitude!!.text = String.format("%.3f",
                aircraft.longitude)
        if (aircraft.altitude == null) holder.altitude!!.text = null else holder.altitude!!.setText(aircraft.altitude.toString())
        if (aircraft.velocity == null) holder.speed!!.text = null else holder.speed!!.setText(aircraft.velocity.toString())
        if (aircraft.heading == null) holder.heading!!.text = null else holder.heading!!.setText(aircraft.heading.toString())
        if (position % 2 == 0) view.setBackgroundResource(android.R.color.transparent) else view.setBackgroundResource(R.color.primary_light)
        Log.d("ATAG","get View returning view" )
        return view
    }

    override fun isEnabled(position: Int): Boolean {
        val aircraft = getItem(position) ?: return false
        val ident = aircraft.identity
        return (!TextUtils.isEmpty(ident)
                && sIdentPattern.matcher(ident).matches())
    }

    override fun notifyDataSetChanged() {
        mBoth = App.Companion.sPrefs!!.getString("pref_scan_mode", "ADSB") == "BOTH"
        super.notifyDataSetChanged()
    }

    companion object {
        // regex for 1 letter AND 1 number for ident
        private val sIdentPattern = Pattern
                .compile("^(?=.*[0-9])(?=.*[a-zA-Z])([a-zA-Z0-9]+)$")
    }

    init {
        mInflater = LayoutInflater.from(context)
        mPlaneList = planeList
        mWebLinkColor = ContextCompat.getColor(context, R.color.web_link)
        mNormalColor = ContextCompat.getColor(context,
                android.R.color.primary_text_light)
        mBoth = App.Companion.sPrefs!!.getString("pref_scan_mode", "ADSB") == "BOTH"
    }
}