package com.flightaware.android.flightfeeder.activities

import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import com.flightaware.android.flightfeeder.R

class SettingsActivity constructor() : AppCompatActivity() {
    public override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        getSupportActionBar()!!.setTitle(R.string.text_settings)
    }
}