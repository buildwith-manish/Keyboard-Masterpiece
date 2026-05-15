package com.keyboardmasterpiece.settings

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.inputmethod.InputMethodManager
import android.widget.*
import com.keyboardmasterpiece.R
import com.keyboardmasterpiece.engine.UserPreferences

class SettingsActivity : Activity() {
    private lateinit var prefs: UserPreferences
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        prefs = UserPreferences(this)
        findViewById<Button>(R.id.enableKeyboard).setOnClickListener { startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS)) }
        findViewById<Button>(R.id.pickKeyboard).setOnClickListener { (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager).showInputMethodPicker() }
        bindSwitch(R.id.darkTheme, prefs.darkTheme) { prefs.darkTheme = it }
        bindSwitch(R.id.numberRow, prefs.numberRow) { prefs.numberRow = it }
        bindSwitch(R.id.haptics, prefs.haptics) { prefs.haptics = it }
        bindSwitch(R.id.sounds, prefs.sounds) { prefs.sounds = it }
        bindSwitch(R.id.incognito, prefs.incognito) { prefs.incognito = it }
        findViewById<SeekBar>(R.id.fontSize).apply { progress = prefs.fontSizeDelta; setOnSeekBarChangeListener(simple { prefs.fontSizeDelta = it }) }
        findViewById<SeekBar>(R.id.keyHeight).apply { progress = prefs.keyHeightDelta; setOnSeekBarChangeListener(simple { prefs.keyHeightDelta = it }) }
    }
    private fun bindSwitch(id: Int, checked: Boolean, set: (Boolean) -> Unit) { findViewById<Switch>(id).apply { isChecked = checked; setOnCheckedChangeListener { _, b -> set(b) } } }
    private fun simple(set: (Int) -> Unit) = object : SeekBar.OnSeekBarChangeListener { override fun onProgressChanged(s: SeekBar, p: Int, f: Boolean) = set(p); override fun onStartTrackingTouch(s: SeekBar?) {}; override fun onStopTrackingTouch(s: SeekBar?) {} }
}
