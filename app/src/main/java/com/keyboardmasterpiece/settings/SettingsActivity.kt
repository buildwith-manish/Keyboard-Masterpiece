package com.keyboardmasterpiece.settings

import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import com.keyboardmasterpiece.R
import com.keyboardmasterpiece.engine.ThemePalette
import com.keyboardmasterpiece.engine.UserPreferences

// Professional settings screen for Keyboard Masterpiece.
// Features:
// - Theme picker with visual color swatches for all 10 themes
// - Dark/light quick toggle
// - All keyboard settings with clear section headers
// - Feature list showing available keyboard capabilities
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

        // ── Theme Picker ──
        buildThemePicker()

        // ── Toggles ──
        bindSwitch(R.id.darkTheme, prefs.darkTheme) { checked ->
            prefs.darkTheme = checked
            updateThemeLabel()
        }
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

    // Build a horizontal row of theme color swatches.
// Each swatch shows the theme's primary background + accent colors.
// Tapping a swatch selects that theme.
    private fun buildThemePicker() {
        val container = findViewById<LinearLayout>(R.id.theme_picker_container)
        if (container == null) return
        container.removeAllViews()

        val size = resources.displayMetrics.density.let { (48 * it).toInt() }
        val margin = resources.displayMetrics.density.let { (4 * it).toInt() }
        val cornerRadiusPx = resources.displayMetrics.density.let { 8 * it }

        ThemePalette.THEMES.forEachIndexed { index, theme ->
            val swatch = View(this).apply {
                layoutParams = LinearLayout.LayoutParams(size, size).apply {
                    setMargins(margin, margin, margin, margin)
                }

                // Draw a rounded rectangle with theme background and accent border
                val drawable = GradientDrawable().apply {
                    setColor(theme.keyColor)
                    this.cornerRadius = cornerRadiusPx
                    // Show accent color as a 3dp stroke
                    setStroke((3 * resources.displayMetrics.density).toInt(), theme.accentColor)
                }
                background = drawable

                // Highlight current theme with a checkmark-like indicator
                if (index == prefs.themeIndex) {
                    val selectedDrawable = GradientDrawable().apply {
                        setColor(theme.keyColor)
                        this.cornerRadius = cornerRadiusPx
                        setStroke((4 * resources.displayMetrics.density).toInt(), theme.accentColor)
                    }
                    background = selectedDrawable
                }

                setOnClickListener {
                    prefs.themeIndex = index
                    updateThemeLabel()
                    buildThemePicker() // Rebuild to update selection highlight
                }

                contentDescription = theme.name
            }
            container.addView(swatch)
        }

        updateThemeLabel()
    }

    private fun updateThemeLabel() {
        val label = findViewById<TextView>(R.id.theme_name_label) ?: return
        label.text = "Current: ${ThemePalette.current(prefs).name}"
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
            setTheme(R.style.AppTheme_Dark)
        } else {
            setTheme(R.style.AppTheme)
        }
    }
}
