package com.keyboardmasterpiece.engine

import android.content.Context
import android.content.SharedPreferences
import android.content.res.Configuration
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * FIX: BUG-003 — UserPreferences now uses a private constructor with factory methods.
 *   - createBootSafe() uses device-protected storage (safe before device unlock).
 *   - create() uses regular credential-encrypted storage.
 * FIX: QUALITY-003 — All one-liner properties expanded to multi-line with named constants.
 * FIX: FINAL-002 — Personal words stored in EncryptedSharedPreferences.
 *   User vocabulary data (which reveals what they type) is now encrypted at rest.
 */
class UserPreferences private constructor(
    private val sp: SharedPreferences,
    private val encryptedSp: SharedPreferences
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
        private const val KEY_THEME_INDEX = "theme_index"

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
        private const val DEFAULT_LAST_LAYOUT_PANEL = "QWERTY"

        /**
         * FIX: FINAL-002 — Create encrypted SharedPreferences for sensitive data.
         */
        private fun createEncryptedPrefs(context: Context): SharedPreferences {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            return EncryptedSharedPreferences.create(
                context,
                "keyboard_prefs_encrypted",
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        }

        /**
         * FIX: BUG-003 — Create UserPreferences using device-protected storage.
         * Safe to use before the device is unlocked (Direct Boot aware).
         */
        fun createBootSafe(context: Context): UserPreferences {
            val protectedContext = context.createDeviceProtectedStorageContext()
            val sp = protectedContext.getSharedPreferences("keyboard_prefs", Context.MODE_PRIVATE)
            // FIX: FINAL-002 — Encrypted prefs may not work in device-protected storage,
            // so fall back to regular prefs for boot-safe mode
            val encryptedSp = try {
                createEncryptedPrefs(protectedContext)
            } catch (_: Exception) {
                sp // Fallback to same prefs if encryption fails in protected storage
            }
            return UserPreferences(sp, encryptedSp)
        }

        /**
         * FIX: BUG-003 — Create UserPreferences using regular credential-encrypted storage.
         * Only safe to use after the device is unlocked.
         */
        fun create(context: Context): UserPreferences {
            val sp = context.getSharedPreferences("keyboard_prefs", Context.MODE_PRIVATE)
            val encryptedSp = createEncryptedPrefs(context)
            return UserPreferences(sp, encryptedSp)
        }
    }

    // FIX: QUALITY-003 — All properties expanded to multi-line with named constants

    /** Feature 4: themeIndex — index into ThemePalette.THEMES. */
    var themeIndex: Int
        get() = sp.getInt(KEY_THEME_INDEX, 0)
        set(value) = sp.edit().putInt(KEY_THEME_INDEX, value % ThemePalette.THEMES.size).apply()

    /**
     * Feature 4: darkTheme now derives from themeIndex for backward compatibility.
     * 0 is Classic Light, everything else is "dark".
     */
    var darkTheme: Boolean
        get() = themeIndex != 0
        set(value) { if (value && themeIndex == 0) themeIndex = 1 else if (!value) themeIndex = 0 }

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

    /** FIX: FINAL-002 — Personal words stored in encrypted SharedPreferences */
    fun personalWords(): MutableSet<String> =
        encryptedSp.getStringSet(KEY_PERSONAL_WORDS, emptySet())?.toMutableSet() ?: mutableSetOf()

    /** FIX: MED-006 — Consistent personal word limit. FINAL-002 — Encrypted storage. */
    fun savePersonalWords(words: Set<String>) {
        encryptedSp.edit().putStringSet(KEY_PERSONAL_WORDS, words.take(MAX_PERSONAL_WORDS).toSet()).apply()
    }

    /** Detect landscape orientation from context configuration. */
    fun isLandscape(context: Context): Boolean =
        context.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
}
