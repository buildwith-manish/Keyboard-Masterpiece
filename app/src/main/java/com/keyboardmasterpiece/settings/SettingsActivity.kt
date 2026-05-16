package com.keyboardmasterpiece.settings

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.SeekBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import com.keyboardmasterpiece.R
import com.keyboardmasterpiece.engine.UserPreferences

/**
 * Settings screen for Keyboard Masterpiece.
 *
 * Uses AppCompatActivity for proper theme support and SwitchCompat
 * instead of deprecated android.widget.Switch.
 */
class SettingsActivity : AppCompatActivity() {
    private lateinit var prefs: UserPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        prefs = UserPreferences.create(this)
        applyTheme()

        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        // ── Buttons ──
        findViewById<Button>(R.id.btn_enable_ime).setOnClickListener {
            startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
        }
        findViewById<Button>(R.id.btn_set_default).setOnClickListener {
            (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager).showInputMethodPicker()
        }

        // ── Toggles ──
        bindSwitch(R.id.darkTheme, prefs.darkTheme) { prefs.darkTheme = it }
        bindSwitch(R.id.numberRow, prefs.numberRow) { prefs.numberRow = it }
        bindSwitch(R.id.haptics, prefs.haptics) { prefs.haptics = it }
        bindSwitch(R.id.sounds, prefs.sounds) { prefs.sounds = it }
        bindSwitch(R.id.incognito, prefs.incognito) { prefs.incognito = it }
        bindSwitch(R.id.switch_rtl, prefs.isRtl) { prefs.isRtl = it }

        // ── SeekBars ──
        findViewById<SeekBar>(R.id.fontSize).apply {
            progress = prefs.fontSizeDelta
            setOnSeekBarChangeListener(simple { prefs.fontSizeDelta = it })
        }
        findViewById<SeekBar>(R.id.keyHeight).apply {
            progress = prefs.keyHeightDelta
            setOnSeekBarChangeListener(simple { prefs.keyHeightDelta = it })
        }
    }

    private fun bindSwitch(id: Int, checked: Boolean, set: (Boolean) -> Unit) {
        findViewById<SwitchCompat>(id).apply {
            isChecked = checked
            setOnCheckedChangeListener { _, b -> set(b) }
        }
    }

    private fun simple(set: (Int) -> Unit) = object : SeekBar.OnSeekBarChangeListener {
        override fun onProgressChanged(s: SeekBar, p: Int, f: Boolean) = set(p)
        override fun onStartTrackingTouch(s: SeekBar?) {}
        override fun onStopTrackingTouch(s: SeekBar?) {}
    }

    private fun applyTheme() {
        if (prefs.darkTheme) {
            setTheme(android.R.style.Theme_DeviceDefault)
        } else {
            setTheme(android.R.style.Theme_DeviceDefault_Light)
        }
    }
}
