package com.tapnix.keyboard.viewmodel

import android.content.Context
import android.view.inputmethod.EditorInfo
import androidx.lifecycle.*
import com.tapnix.keyboard.data.*
import com.tapnix.keyboard.database.TapNixDatabase
import com.tapnix.keyboard.engine.ClipboardEngine
import com.tapnix.keyboard.engine.EmojiEngine
import com.tapnix.keyboard.engine.LongPressConfig
import com.tapnix.keyboard.engine.LongPressEngine
import com.tapnix.keyboard.engine.SuggestionEngine
import com.tapnix.keyboard.settings.SettingsRepository
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

/**
 * KeyboardViewModel
 *
 * Single source of truth for all keyboard UI state.
 * Exposes StateFlows consumed via collectAsStateWithLifecycle().
 *
 * Coordinates:
 *  - SuggestionEngine (word suggestions)
 *  - EmojiEngine      (categories, recents, search)
 *  - ClipboardEngine  (history, pin, paste)
 *  - LongPressEngine  (emoji spam / char repeat)
 *  - SettingsRepository
 */
class KeyboardViewModel(
    private val suggestionEngine: SuggestionEngine,
    val emojiEngine: EmojiEngine,
    val clipboardEngine: ClipboardEngine,
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    // ── Panel State ──────────────────────────────────────────────────────────
    private val _activePanel = MutableStateFlow(KeyboardPanel.QWERTY)
    val activePanel: StateFlow<KeyboardPanel> = _activePanel.asStateFlow()

    // ── Suggestions ──────────────────────────────────────────────────────────
    private val _suggestions = MutableStateFlow<List<Suggestion>>(emptyList())
    val suggestions: StateFlow<List<Suggestion>> = _suggestions.asStateFlow()

    // ── Emoji ────────────────────────────────────────────────────────────────
    val emojiCategories: StateFlow<List<EmojiCategory>> =
        emojiEngine.categoriesFlow.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val recentEmojis: StateFlow<List<Emoji>> =
        emojiEngine.recentFlow.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val _emojiSearchQuery = MutableStateFlow("")
    val emojiSearchQuery: StateFlow<String> = _emojiSearchQuery.asStateFlow()

    val emojiSearchResults: StateFlow<List<Emoji>> =
        _emojiSearchQuery
            .debounce(150)
            .flatMapLatest { q -> emojiEngine.search(q) }
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    // ── Clipboard ────────────────────────────────────────────────────────────
    val clipboardEntries: StateFlow<List<ClipboardEntry>> =
        clipboardEngine.entriesFlow.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val _clipboardSearchQuery = MutableStateFlow("")
    val clipboardSearchQuery: StateFlow<String> = _clipboardSearchQuery.asStateFlow()

    val clipboardSearchResults: StateFlow<List<ClipboardEntry>> =
        _clipboardSearchQuery
            .debounce(200)
            .flatMapLatest { q ->
                if (q.isBlank()) clipboardEngine.entriesFlow
                else clipboardEngine.searchEntries(q)
            }
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    // ── Settings + Theme ─────────────────────────────────────────────────────
    val settings: StateFlow<KeyboardSettings> =
        settingsRepository.settingsFlow
            .stateIn(viewModelScope, SharingStarted.Eagerly, KeyboardSettings.Default)

    val currentTheme: StateFlow<KeyboardTheme> = settings
        .map { s -> KeyboardTheme.allThemes.find { it.id == s.themeId } ?: KeyboardTheme.Default }
        .stateIn(viewModelScope, SharingStarted.Eagerly, KeyboardTheme.Default)

    // ── Modifier Keys ────────────────────────────────────────────────────────
    private val _shiftState = MutableStateFlow(ShiftState.OFF)
    val shiftState: StateFlow<ShiftState> = _shiftState.asStateFlow()

    private val _isPasswordMode = MutableStateFlow(false)
    val isPasswordMode: StateFlow<Boolean> = _isPasswordMode.asStateFlow()

    // ── Long Press Engine ────────────────────────────────────────────────────
    private val longPressEngine = LongPressEngine(viewModelScope)

    // ── Current composing word (for suggestions) ─────────────────────────────
    private var currentWord = StringBuilder()

    // ────────────────────────────────────────────────────────────────────────
    // Public API
    // ────────────────────────────────────────────────────────────────────────

    fun switchPanel(panel: KeyboardPanel) {
        _activePanel.value = panel
    }

    fun setPasswordMode(isPassword: Boolean) {
        _isPasswordMode.value = isPassword
    }

    // ── Shift ────────────────────────────────────────────────────────────────
    fun toggleShift() {
        _shiftState.value = when (_shiftState.value) {
            ShiftState.OFF       -> ShiftState.ON
            ShiftState.ON        -> ShiftState.CAPS_LOCK
            ShiftState.CAPS_LOCK -> ShiftState.OFF
        }
    }

    fun onCharCommitted(char: String) {
        if (_shiftState.value == ShiftState.ON) {
            _shiftState.value = ShiftState.OFF
        }

        // Update composing word for suggestions
        if (char.firstOrNull()?.isLetter() == true) {
            currentWord.append(char)
            updateSuggestions(currentWord.toString())
        } else {
            if (currentWord.isNotEmpty()) {
                suggestionEngine.recordWord(currentWord.toString())
            }
            currentWord.clear()
            _suggestions.value = emptyList()
        }
    }

    fun onBackspaceCommitted() {
        if (currentWord.isNotEmpty()) {
            currentWord.deleteCharAt(currentWord.length - 1)
            updateSuggestions(currentWord.toString())
        }
    }

    fun onSuggestionChosen(text: String) {
        suggestionEngine.recordWord(text)
        currentWord.clear()
        _suggestions.value = emptyList()
    }

    private fun updateSuggestions(prefix: String) {
        val settings = settings.value
        if (!settings.showSuggestions || isPasswordMode.value) return

        viewModelScope.launch(Dispatchers.IO) {
            val results = suggestionEngine.getSuggestions(prefix)
            _suggestions.value = results
        }
    }

    // ── Emoji ────────────────────────────────────────────────────────────────
    fun onEmojiSearchChanged(query: String) {
        _emojiSearchQuery.value = query
    }

    fun recordEmojiUse(emoji: Emoji) {
        emojiEngine.recordUse(emoji)
    }

    // ── Clipboard ────────────────────────────────────────────────────────────
    fun onClipboardSearchChanged(query: String) {
        _clipboardSearchQuery.value = query
    }

    fun toggleClipboardPin(entryId: Long) {
        viewModelScope.launch { clipboardEngine.togglePin(entryId) }
    }

    fun deleteClipboardEntry(entryId: Long) {
        viewModelScope.launch { clipboardEngine.delete(entryId) }
    }

    fun clearClipboardHistory() {
        viewModelScope.launch { clipboardEngine.deleteAllUnpinned() }
    }

    // ── Long Press (emoji spam + char repeat) ────────────────────────────────
    fun startLongPress(
        pointerId: Int = 0,
        payload: String,
        onEmit: suspend (String) -> Unit,
    ) {
        val cfg = settings.value
        longPressEngine.start(
            pointerId = pointerId,
            payload = payload,
            onEmit = onEmit,
        )
    }

    fun cancelLongPress(pointerId: Int = 0) {
        longPressEngine.cancel(pointerId)
    }

    fun cancelAllLongPress() {
        longPressEngine.cancelAll()
    }

    // ── Settings ─────────────────────────────────────────────────────────────
    fun setTheme(themeId: String) {
        viewModelScope.launch { settingsRepository.updateTheme(themeId) }
    }

    fun setHapticFeedback(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.updateHapticFeedback(enabled) }
    }

    fun setSoundFeedback(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.updateSoundFeedback(enabled) }
    }

    fun setAutoCapitalize(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.updateAutoCapitalize(enabled) }
    }

    fun setDoubleSpacePeriod(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.updateDoubleSpacePeriod(enabled) }
    }

    fun setIncognitoMode(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.updateIncognitoMode(enabled) }
    }

    fun setShowSuggestions(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.updateShowSuggestions(enabled) }
    }

    // ── IME Lifecycle ────────────────────────────────────────────────────────
    fun onStartInput(info: EditorInfo?, restarting: Boolean) {
        if (!restarting) {
            currentWord.clear()
            _suggestions.value = emptyList()
            _emojiSearchQuery.value = ""
        }
    }

    fun onFinishInput() {
        cancelAllLongPress()
        if (currentWord.isNotEmpty()) {
            suggestionEngine.recordWord(currentWord.toString())
            currentWord.clear()
        }
    }

    fun onTrimMemory(level: Int) {
        emojiEngine.trimMemory(level)
        suggestionEngine.trimMemory(level)
    }

    override fun onCleared() {
        super.onCleared()
        cancelAllLongPress()
    }

    // ── Factory ──────────────────────────────────────────────────────────────
    class Factory(private val context: Context) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            val appContext = context.applicationContext
            val db = TapNixDatabase.getInstance(appContext)
            val settingsRepo = SettingsRepository(appContext)
            val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
            return KeyboardViewModel(
                suggestionEngine = SuggestionEngine(db.wordDao(), ioScope),
                emojiEngine = EmojiEngine(db.emojiDao(), ioScope),
                clipboardEngine = ClipboardEngine(appContext, db.clipboardDao()),
                settingsRepository = settingsRepo,
            ) as T
        }
    }
}
