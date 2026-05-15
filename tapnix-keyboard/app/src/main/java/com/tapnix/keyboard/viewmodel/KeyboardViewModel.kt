package com.tapnix.keyboard.viewmodel

import android.content.Context
import android.view.inputmethod.EditorInfo
import androidx.compose.ui.geometry.Offset
import androidx.lifecycle.*
import com.tapnix.keyboard.data.*
import com.tapnix.keyboard.database.TapNixDatabase
import com.tapnix.keyboard.engine.*
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
 *  - SuggestionEngine  (word suggestions + bigram next-word prediction)
 *  - GrammarEngine     (autocorrect, grammar hints)
 *  - SwipeTypingEngine (swipe gesture word candidates)
 *  - EmojiEngine       (categories, recents, search)
 *  - ClipboardEngine   (history, pin, paste)
 *  - LongPressEngine   (emoji spam / char repeat)
 *  - SettingsRepository
 */
class KeyboardViewModel(
    private val suggestionEngine: SuggestionEngine,
    val emojiEngine: EmojiEngine,
    val clipboardEngine: ClipboardEngine,
    private val settingsRepository: SettingsRepository,
    private val grammarEngine: GrammarEngine = GrammarEngine(),
    private val swipeEngine: SwipeTypingEngine = SwipeTypingEngine(),
) : ViewModel() {

    // ── Panel State ──────────────────────────────────────────────────────────
    private val _activePanel = MutableStateFlow(KeyboardPanel.QWERTY)
    val activePanel: StateFlow<KeyboardPanel> = _activePanel.asStateFlow()

    // ── Suggestions ──────────────────────────────────────────────────────────
    private val _suggestions = MutableStateFlow<List<Suggestion>>(emptyList())
    val suggestions: StateFlow<List<Suggestion>> = _suggestions.asStateFlow()

    // ── Swipe typing state ───────────────────────────────────────────────────
    private val _swipePath = MutableStateFlow<List<Offset>>(emptyList())
    val swipePath: StateFlow<List<Offset>> = _swipePath.asStateFlow()

    private val _isSwipingActive = MutableStateFlow(false)
    val isSwipingActive: StateFlow<Boolean> = _isSwipingActive.asStateFlow()

    private val _swipeCandidates = MutableStateFlow<List<String>>(emptyList())
    val swipeCandidates: StateFlow<List<String>> = _swipeCandidates.asStateFlow()

    // ── Grammar / autocorrect ────────────────────────────────────────────────
    private val _pendingAutocorrect = MutableStateFlow<GrammarEngine.Correction?>(null)
    val pendingAutocorrect: StateFlow<GrammarEngine.Correction?> = _pendingAutocorrect.asStateFlow()

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

    // ── Composing word tracking ──────────────────────────────────────────────
    private var currentWord = StringBuilder()
    private var previousWord = ""

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

        if (char.firstOrNull()?.isLetter() == true) {
            currentWord.append(char)
            updateSuggestions(currentWord.toString())
        } else {
            if (currentWord.isNotEmpty()) {
                val committed = currentWord.toString()
                val settings = settings.value
                if (settings.adaptiveLearningEnabled && !isPasswordMode.value) {
                    suggestionEngine.recordWord(committed, previousWord.ifEmpty { null })
                }
                previousWord = committed
            }
            currentWord.clear()
            // After a space, show next-word suggestions if available
            if (char == " " && previousWord.isNotEmpty()) {
                updateNextWordSuggestions(previousWord)
            } else {
                _suggestions.value = emptyList()
            }
        }
    }

    fun onBackspaceCommitted() {
        if (currentWord.isNotEmpty()) {
            currentWord.deleteCharAt(currentWord.length - 1)
            updateSuggestions(currentWord.toString())
        }
    }

    fun onSuggestionChosen(text: String) {
        val settings = settings.value
        if (settings.adaptiveLearningEnabled && !isPasswordMode.value) {
            suggestionEngine.recordWord(text, previousWord.ifEmpty { null })
        }
        previousWord = text
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

    private fun updateNextWordSuggestions(previousWord: String) {
        val settings = settings.value
        if (!settings.showSuggestions || isPasswordMode.value) return

        viewModelScope.launch(Dispatchers.IO) {
            val results = suggestionEngine.getNextWordSuggestions(previousWord)
            if (results.isNotEmpty()) _suggestions.value = results
        }
    }

    // ── Swipe typing ─────────────────────────────────────────────────────────

    fun onSwipeStart(startPoint: Offset) {
        val settings = settings.value
        if (!settings.swipeTypingEnabled || isPasswordMode.value) return
        _swipePath.value = listOf(startPoint)
        _isSwipingActive.value = true
        _swipeCandidates.value = emptyList()
    }

    fun onSwipeMove(point: Offset) {
        if (!_isSwipingActive.value) return
        _swipePath.value = _swipePath.value + point
    }

    fun onSwipeEnd(
        keyboardWidth: Float,
        keyboardRowHeight: Float,
    ) {
        if (!_isSwipingActive.value) return
        val path = _swipePath.value
        _isSwipingActive.value = false

        if (path.size < 3) {
            clearSwipeState()
            return
        }

        viewModelScope.launch(Dispatchers.Default) {
            val userWords = _suggestions.value.map { it.text }
            val candidates = swipeEngine.getSwipeCandidates(
                path = path,
                keyboardWidth = keyboardWidth,
                keyboardRowHeight = keyboardRowHeight,
                userWords = userWords,
            )
            _swipeCandidates.value = candidates
            if (candidates.isNotEmpty()) {
                _suggestions.value = candidates.map { Suggestion(it, 1f) }
            }
            // Keep path briefly for the fade-out animation, then clear
            delay(400)
            clearSwipeState()
        }
    }

    fun commitSwipeWord(word: String) {
        val settings = settings.value
        if (settings.adaptiveLearningEnabled && !isPasswordMode.value) {
            suggestionEngine.recordWord(word, previousWord.ifEmpty { null })
        }
        previousWord = word
        currentWord.clear()
        clearSwipeState()
        _suggestions.value = emptyList()
    }

    private fun clearSwipeState() {
        _swipePath.value = emptyList()
        _swipeCandidates.value = emptyList()
    }

    // ── Grammar / autocorrect ─────────────────────────────────────────────────

    fun checkAutocorrect(word: String) {
        val settings = settings.value
        if (!settings.autoCorrectEnabled || isPasswordMode.value || word.length < 3) return

        viewModelScope.launch(Dispatchers.Default) {
            val correction = grammarEngine.getCorrection(word)
            _pendingAutocorrect.value = correction
        }
    }

    fun applyAutocorrect(correction: GrammarEngine.Correction) {
        _pendingAutocorrect.value = null
    }

    fun dismissAutocorrect() {
        _pendingAutocorrect.value = null
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

    fun setSwipeTypingEnabled(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.updateSwipeTypingEnabled(enabled) }
    }

    fun setAutoCorrectEnabled(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.updateAutoCorrectEnabled(enabled) }
    }

    fun setShowGrammarHints(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.updateShowGrammarHints(enabled) }
    }

    fun setOneHandedMode(mode: OneHandedMode) {
        viewModelScope.launch { settingsRepository.updateOneHandedMode(mode) }
    }

    fun setKeyboardHeightMultiplier(multiplier: Float) {
        viewModelScope.launch { settingsRepository.updateKeyboardHeightMultiplier(multiplier) }
    }

    fun setGestureDeleteEnabled(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.updateGestureDeleteEnabled(enabled) }
    }

    fun setAdaptiveLearningEnabled(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.updateAdaptiveLearningEnabled(enabled) }
    }

    fun setLanguage(languageCode: String) {
        viewModelScope.launch { settingsRepository.updateLanguage(languageCode) }
    }

    fun setClipboardEnabled(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.updateClipboardEnabled(enabled) }
    }

    // ── IME Lifecycle ────────────────────────────────────────────────────────
    fun onStartInput(info: EditorInfo?, restarting: Boolean) {
        if (!restarting) {
            currentWord.clear()
            previousWord = ""
            _suggestions.value = emptyList()
            _emojiSearchQuery.value = ""
            clearSwipeState()
            _pendingAutocorrect.value = null
        }
    }

    fun onFinishInput() {
        cancelAllLongPress()
        clearSwipeState()
        _pendingAutocorrect.value = null
        if (currentWord.isNotEmpty()) {
            val committed = currentWord.toString()
            suggestionEngine.recordWord(committed, previousWord.ifEmpty { null })
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
                suggestionEngine = SuggestionEngine(
                    dao = db.wordDao(),
                    bigramDao = db.bigramDao(),
                    ioScope = ioScope,
                ),
                emojiEngine = EmojiEngine(db.emojiDao(), ioScope),
                clipboardEngine = ClipboardEngine(appContext, db.clipboardDao()),
                settingsRepository = settingsRepo,
                grammarEngine = GrammarEngine(),
                swipeEngine = SwipeTypingEngine(),
            ) as T
        }
    }
}
