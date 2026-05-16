package com.keyboardmasterpiece.engine

import android.content.Context
import android.content.SharedPreferences
import android.content.res.Configuration

/**
 * FIX: BUG-003 — UserPreferences now uses a private constructor with factory methods.
 *   - createBootSafe() uses device-protected storage (safe before device unlock).
 *   - create() uses regular credential-encrypted storage.
 * FIX: QUALITY-003 — All one-liner properties expanded to multi-line with named constants.
 */
class UserPreferences private constructor(
    private val sp: SharedPreferences
) {

    companion object {
        /** FIX: MED-006 — Single source of truth for max personal words. */
        const val MAX_PERSONAL_WORDS = 1500

        // Property key constants — FIX: QUALITY-003
        private const val KEY_DARK_THEME = "dark"
        private const val KEY_NUMBER_ROW = "number_row"
        private const val KEY_HAPTICS = "haptics"
        private const val KEY_SOUNDS = "sounds"
        private const val KEY_INCOGNITO = "incognito"
        private const val KEY_FONT_SIZE_DELTA = "font_delta"
        private const val KEY_HEIGHT_DELTA = "height_delta"
        private const val KEY_LAYOUT_MODE = "layout_mode"
        private const val KEY_IS_RTL = "is_rtl"
        private const val KEY_LAST_LAYOUT_PANEL = "last_layout_panel"
        private const val KEY_PERSONAL_WORDS = "personal_words"

        // Default values
        private const val DEFAULT_DARK_THEME = false
        private const val DEFAULT_NUMBER_ROW = true
        private const val DEFAULT_HAPTICS = true
        private const val DEFAULT_SOUNDS = false
        private const val DEFAULT_INCOGNITO = false
        private const val DEFAULT_FONT_SIZE_DELTA = 4
        private const val DEFAULT_HEIGHT_DELTA = 8
        private const val DEFAULT_LAYOUT_MODE = 0
        private const val DEFAULT_IS_RTL = false
        private const val DEFAULT_LAST_LAYOUT_PANEL = Panel.QWERTY.name

        /**
         * FIX: BUG-003 — Create UserPreferences using device-protected storage.
         * Safe to use before the device is unlocked (Direct Boot aware).
         */
        fun createBootSafe(context: Context): UserPreferences {
            val protectedContext = context.createDeviceProtectedStorageContext()
            val sp = protectedContext.getSharedPreferences("keyboard_prefs", Context.MODE_PRIVATE)
            return UserPreferences(sp)
        }

        /**
         * FIX: BUG-003 — Create UserPreferences using regular credential-encrypted storage.
         * Only safe to use after the device is unlocked.
         */
        fun create(context: Context): UserPreferences {
            val sp = context.getSharedPreferences("keyboard_prefs", Context.MODE_PRIVATE)
            return UserPreferences(sp)
        }
    }

    // FIX: QUALITY-003 — All properties expanded to multi-line with named constants

    var darkTheme: Boolean
        get() = sp.getBoolean(KEY_DARK_THEME, DEFAULT_DARK_THEME)
        set(value) = sp.edit().putBoolean(KEY_DARK_THEME, value).apply()

    var numberRow: Boolean
        get() = sp.getBoolean(KEY_NUMBER_ROW, DEFAULT_NUMBER_ROW)
        set(value) = sp.edit().putBoolean(KEY_NUMBER_ROW, value).apply()

    var haptics: Boolean
        get() = sp.getBoolean(KEY_HAPTICS, DEFAULT_HAPTICS)
        set(value) = sp.edit().putBoolean(KEY_HAPTICS, value).apply()

    var sounds: Boolean
        get() = sp.getBoolean(KEY_SOUNDS, DEFAULT_SOUNDS)
        set(value) = sp.edit().putBoolean(KEY_SOUNDS, value).apply()

    var incognito: Boolean
        get() = sp.getBoolean(KEY_INCOGNITO, DEFAULT_INCOGNITO)
        set(value) = sp.edit().putBoolean(KEY_INCOGNITO, value).apply()

    var fontSizeDelta: Int
        get() = sp.getInt(KEY_FONT_SIZE_DELTA, DEFAULT_FONT_SIZE_DELTA)
        set(value) = sp.edit().putInt(KEY_FONT_SIZE_DELTA, value).apply()

    var keyHeightDelta: Int
        get() = sp.getInt(KEY_HEIGHT_DELTA, DEFAULT_HEIGHT_DELTA)
        set(value) = sp.edit().putInt(KEY_HEIGHT_DELTA, value).apply()

    var layoutModeOrdinal: Int
        get() = sp.getInt(KEY_LAYOUT_MODE, DEFAULT_LAYOUT_MODE)
        set(value) = sp.edit().putInt(KEY_LAYOUT_MODE, value).apply()

    /** FIX: MED-006 / FILE 7 — Single constant for personal word limits. */
    var isRtl: Boolean
        get() = sp.getBoolean(KEY_IS_RTL, DEFAULT_IS_RTL)
        set(value) = sp.edit().putBoolean(KEY_IS_RTL, value).apply()

    /** FIX: FILE 7 — Persist last panel across process death. */
    var lastLayoutPanel: String
        get() = sp.getString(KEY_LAST_LAYOUT_PANEL, DEFAULT_LAST_LAYOUT_PANEL) ?: DEFAULT_LAST_LAYOUT_PANEL
        set(value) = sp.edit().putString(KEY_LAST_LAYOUT_PANEL, value).apply()

    fun personalWords(): MutableSet<String> =
        sp.getStringSet(KEY_PERSONAL_WORDS, emptySet())?.toMutableSet() ?: mutableSetOf()

    /** FIX: MED-006 — Consistent personal word limit. */
    fun savePersonalWords(words: Set<String>) {
        sp.edit().putStringSet(KEY_PERSONAL_WORDS, words.take(MAX_PERSONAL_WORDS).toSet()).apply()
    }

    /** Detect landscape orientation from context configuration. */
    fun isLandscape(context: Context): Boolean =
        context.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
}
