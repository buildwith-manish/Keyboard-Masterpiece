package com.keyboardmasterpiece.engine

import android.content.Context
import android.content.SharedPreferences

class UserPreferences(context: Context) {
    private val sp: SharedPreferences = context.getSharedPreferences("keyboard_prefs", Context.MODE_PRIVATE)
    var darkTheme: Boolean get() = sp.getBoolean("dark", false); set(v) = sp.edit().putBoolean("dark", v).apply()
    var numberRow: Boolean get() = sp.getBoolean("number_row", true); set(v) = sp.edit().putBoolean("number_row", v).apply()
    var haptics: Boolean get() = sp.getBoolean("haptics", true); set(v) = sp.edit().putBoolean("haptics", v).apply()
    var sounds: Boolean get() = sp.getBoolean("sounds", false); set(v) = sp.edit().putBoolean("sounds", v).apply()
    var incognito: Boolean get() = sp.getBoolean("incognito", false); set(v) = sp.edit().putBoolean("incognito", v).apply()
    var fontSizeDelta: Int get() = sp.getInt("font_delta", 4); set(v) = sp.edit().putInt("font_delta", v).apply()
    var keyHeightDelta: Int get() = sp.getInt("height_delta", 8); set(v) = sp.edit().putInt("height_delta", v).apply()
    var layoutModeOrdinal: Int get() = sp.getInt("layout_mode", 0); set(v) = sp.edit().putInt("layout_mode", v).apply()
    fun personalWords(): MutableSet<String> = sp.getStringSet("personal_words", emptySet())?.toMutableSet() ?: mutableSetOf()
    fun savePersonalWords(words: Set<String>) { sp.edit().putStringSet("personal_words", words.take(2048).toSet()).apply() }
}
