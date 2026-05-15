package com.tapnix.keyboard.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import com.tapnix.keyboard.data.KeyboardSettings
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.runBlocking

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "tapnix_settings")

/**
 * SettingsRepository
 *
 * Persists user preferences using Jetpack DataStore (Preferences).
 * Exposes a Flow<KeyboardSettings> consumed by ViewModel StateFlow.
 * All writes are async — never blocks the main thread.
 */
class SettingsRepository(private val context: Context) {

    private val dataStore = context.dataStore

    val settingsFlow: Flow<KeyboardSettings> = dataStore.data
        .map { prefs -> prefs.toSettings() }
        .catch { emit(KeyboardSettings.Default) }
        .distinctUntilChanged()

    suspend fun updateTheme(themeId: String) {
        dataStore.edit { it[Keys.THEME_ID] = themeId }
    }

    suspend fun updateHapticFeedback(enabled: Boolean) {
        dataStore.edit { it[Keys.HAPTIC_FEEDBACK] = enabled }
    }

    suspend fun updateSoundFeedback(enabled: Boolean) {
        dataStore.edit { it[Keys.SOUND_FEEDBACK] = enabled }
    }

    suspend fun updateAutoCapitalize(enabled: Boolean) {
        dataStore.edit { it[Keys.AUTO_CAPITALIZE] = enabled }
    }

    suspend fun updateDoubleSpacePeriod(enabled: Boolean) {
        dataStore.edit { it[Keys.DOUBLE_SPACE_PERIOD] = enabled }
    }

    suspend fun updateIncognitoMode(enabled: Boolean) {
        dataStore.edit { it[Keys.INCOGNITO_MODE] = enabled }
    }

    suspend fun updateEmojiRepeatSpeed(initialDelay: Long, minInterval: Long) {
        dataStore.edit {
            it[Keys.EMOJI_REPEAT_INITIAL_DELAY] = initialDelay
            it[Keys.EMOJI_REPEAT_MIN_INTERVAL] = minInterval
        }
    }

    suspend fun updateClipboardEnabled(enabled: Boolean) {
        dataStore.edit { it[Keys.CLIPBOARD_ENABLED] = enabled }
    }

    suspend fun updateShowSuggestions(enabled: Boolean) {
        dataStore.edit { it[Keys.SHOW_SUGGESTIONS] = enabled }
    }

    fun getSettingsSync(): KeyboardSettings = runBlocking {
        settingsFlow.first()
    }

    private fun Preferences.toSettings() = KeyboardSettings(
        themeId = this[Keys.THEME_ID] ?: "default",
        hapticFeedback = this[Keys.HAPTIC_FEEDBACK] ?: true,
        soundFeedback = this[Keys.SOUND_FEEDBACK] ?: false,
        autoCapitalize = this[Keys.AUTO_CAPITALIZE] ?: true,
        doubleSpacePeriod = this[Keys.DOUBLE_SPACE_PERIOD] ?: true,
        incognitoMode = this[Keys.INCOGNITO_MODE] ?: false,
        emojiRepeatInitialDelayMs = this[Keys.EMOJI_REPEAT_INITIAL_DELAY] ?: 500L,
        emojiRepeatMinIntervalMs = this[Keys.EMOJI_REPEAT_MIN_INTERVAL] ?: 28L,
        emojiRepeatStartIntervalMs = this[Keys.EMOJI_REPEAT_START_INTERVAL] ?: 110L,
        clipboardEnabled = this[Keys.CLIPBOARD_ENABLED] ?: true,
        showSuggestions = this[Keys.SHOW_SUGGESTIONS] ?: true,
    )

    private object Keys {
        val THEME_ID = stringPreferencesKey("theme_id")
        val HAPTIC_FEEDBACK = booleanPreferencesKey("haptic_feedback")
        val SOUND_FEEDBACK = booleanPreferencesKey("sound_feedback")
        val AUTO_CAPITALIZE = booleanPreferencesKey("auto_capitalize")
        val DOUBLE_SPACE_PERIOD = booleanPreferencesKey("double_space_period")
        val INCOGNITO_MODE = booleanPreferencesKey("incognito_mode")
        val EMOJI_REPEAT_INITIAL_DELAY = longPreferencesKey("emoji_repeat_initial_delay")
        val EMOJI_REPEAT_MIN_INTERVAL = longPreferencesKey("emoji_repeat_min_interval")
        val EMOJI_REPEAT_START_INTERVAL = longPreferencesKey("emoji_repeat_start_interval")
        val CLIPBOARD_ENABLED = booleanPreferencesKey("clipboard_enabled")
        val SHOW_SUGGESTIONS = booleanPreferencesKey("show_suggestions")
    }
}
