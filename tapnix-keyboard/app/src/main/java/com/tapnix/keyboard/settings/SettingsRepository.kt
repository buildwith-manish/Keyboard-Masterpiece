package com.tapnix.keyboard.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import com.tapnix.keyboard.data.KeyboardSettings
import com.tapnix.keyboard.data.OneHandedMode
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

    // ── Existing settings ─────────────────────────────────────────────────────

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

    // ── Premium feature settings ──────────────────────────────────────────────

    suspend fun updateSwipeTypingEnabled(enabled: Boolean) {
        dataStore.edit { it[Keys.SWIPE_TYPING] = enabled }
    }

    suspend fun updateAutoCorrectEnabled(enabled: Boolean) {
        dataStore.edit { it[Keys.AUTO_CORRECT] = enabled }
    }

    suspend fun updateShowGrammarHints(enabled: Boolean) {
        dataStore.edit { it[Keys.GRAMMAR_HINTS] = enabled }
    }

    suspend fun updateOneHandedMode(mode: OneHandedMode) {
        dataStore.edit { it[Keys.ONE_HANDED_MODE] = mode.name }
    }

    suspend fun updateKeyboardHeightMultiplier(multiplier: Float) {
        dataStore.edit { it[Keys.KEYBOARD_HEIGHT_MULTIPLIER] = multiplier }
    }

    suspend fun updateGestureDeleteEnabled(enabled: Boolean) {
        dataStore.edit { it[Keys.GESTURE_DELETE] = enabled }
    }

    suspend fun updateAdaptiveLearningEnabled(enabled: Boolean) {
        dataStore.edit { it[Keys.ADAPTIVE_LEARNING] = enabled }
    }

    suspend fun updateLanguage(languageCode: String) {
        dataStore.edit { it[Keys.LANGUAGE] = languageCode }
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
        swipeTypingEnabled = this[Keys.SWIPE_TYPING] ?: true,
        autoCorrectEnabled = this[Keys.AUTO_CORRECT] ?: true,
        showGrammarHints = this[Keys.GRAMMAR_HINTS] ?: true,
        oneHandedMode = OneHandedMode.entries.find { it.name == this[Keys.ONE_HANDED_MODE] }
            ?: OneHandedMode.OFF,
        keyboardHeightMultiplier = this[Keys.KEYBOARD_HEIGHT_MULTIPLIER] ?: 1.0f,
        gestureDeleteEnabled = this[Keys.GESTURE_DELETE] ?: true,
        adaptiveLearningEnabled = this[Keys.ADAPTIVE_LEARNING] ?: true,
        language = this[Keys.LANGUAGE] ?: "en",
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
        val SWIPE_TYPING = booleanPreferencesKey("swipe_typing")
        val AUTO_CORRECT = booleanPreferencesKey("auto_correct")
        val GRAMMAR_HINTS = booleanPreferencesKey("grammar_hints")
        val ONE_HANDED_MODE = stringPreferencesKey("one_handed_mode")
        val KEYBOARD_HEIGHT_MULTIPLIER = floatPreferencesKey("keyboard_height_multiplier")
        val GESTURE_DELETE = booleanPreferencesKey("gesture_delete")
        val ADAPTIVE_LEARNING = booleanPreferencesKey("adaptive_learning")
        val LANGUAGE = stringPreferencesKey("language")
    }
}
