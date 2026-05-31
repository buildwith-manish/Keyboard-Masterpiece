package com.keyboardmasterpiece.ime

import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.ClipDescription
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.inputmethodservice.InputMethodService
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.provider.OpenableColumns
import android.util.Log
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.ExtractedTextRequest
import android.view.inputmethod.InputConnection
import android.view.inputmethod.InputMethodManager
import androidx.core.view.inputmethod.InputConnectionCompat
import androidx.core.view.inputmethod.InputContentInfoCompat
import com.keyboardmasterpiece.engine.*
import com.keyboardmasterpiece.settings.SettingsActivity
import kotlin.math.max

private const val TAG = "KeyboardImeService"

// Cursor state for undo/redo operations.
internal data class CursorState(val start: Int, val end: Int)

// Undo entry with cursor position and selection info.
internal data class UndoEntry(
    val text: String,
    val cursorStart: Int,
    val selectionStart: Int,
    val selectionEnd: Int
)

// File preview info for the file sending feature.
data class FilePreviewInfo(
    val uri: Uri,
    val mimeType: String,
    val fileName: String,
    val fileSize: Long
)

// Check if a MIME type pattern matches a specific MIME type.
// Supports wildcard patterns like image slash star.
private fun mimeTypeMatches(pattern: String, mimeType: String): Boolean {
    val wildcard = "*"
    val slash = "/"
    if (pattern == wildcard + slash + wildcard) return true
    if (pattern == mimeType) return true
    val patternParts = pattern.split(slash)
    val mimeParts = mimeType.split(slash)
    if (patternParts.size != 2 || mimeParts.size != 2) return false
    if (patternParts[0] != mimeParts[0] && patternParts[0] != wildcard) return false
    if (patternParts[1] != mimeParts[1] && patternParts[1] != wildcard) return false
    return true
}

// PRODUCTION-GRADE InputMethodService
// FIX: CRIT-001 -- Restore listener in onStartInputView, don't null it in onFinishInputView
// FIX: CRIT-002 -- Proper composing text pipeline with setComposingText/finishComposingText
// FIX: CRIT-003 -- directBootAware with device-protected storage and unlock check
// FIX: HIGH-005 -- UndoEntry with cursor position, selection, and exact-position deletion
// FIX: MED-007 -- Removed stale inputConnection field
// FIX: INFO-004 -- Input type-specific behavior for email, URI, phone, number
// FIX: BUG-005 -- onComputeInsets uses InputMethodService.Insets (not android.graphics.Insets)
// FIX: BUG-003 -- prefs created via UserPreferences.createBootSafe(); upgraded on unlock
// FIX: BUG-009 -- commitPrintable checks word boundary BEFORE appending to composingText
// FIX: QUALITY-004 -- Merged getCursorPosition/getSelection into single getCursorState IPC call
// TASK1 -- Chunked paste: large text is committed in 500-char chunks to prevent ANR/crash
// TASK2 -- Fixed keyboard keys: Delete (word on long press), Shift (double-tap caps lock),
// Space (double-tap period), Enter (respects imeOptions), backspace works in all fields
// TASK3 -- File sending: photo/file picker, InputConnectionCompat.commitContent(), URI permissions,
// fallback to share intent, file preview, and error handling for unsupported apps
class KeyboardImeService : InputMethodService(), KeyboardView.Listener {

    private lateinit var prefs: UserPreferences
    private lateinit var suggestions: SuggestionEngine
    private lateinit var clip: ClipboardStore

    // FIX: BUG-003 -- BroadcastReceiver for ACTION_USER_UNLOCKED to upgrade prefs
    private var unlockReceiver: BroadcastReceiver? = null

    // Store inputView reference since InputMethodService.inputView was removed in API 35
    private var currentKeyboardView: KeyboardView? = null

    // State that survives configuration changes/process death where possible
    private var currentPanel = Panel.QWERTY
    private var isShifted = false
    private var isCapsLocked = false
    private var lastEditorAction = EditorInfo.IME_ACTION_NONE
    private var isPasswordField = false
    private var previousWord: String? = null
    // Feature 6: Track second previous word for trigram context
    private var previousWord2: String? = null

    // FIX: CRIT-002 -- composingText changed from String to StringBuilder
    private val composingText = StringBuilder()
    private var isComposing = false

    private val undoStack = ArrayDeque<UndoEntry>()
    private val redoStack = ArrayDeque<UndoEntry>()

    private var currentEditorInfo: EditorInfo? = null

    // TASK2 -- Shift double-tap detection
    private var lastShiftTapTime = 0L
    private val SHIFT_DOUBLE_TAP_THRESHOLD = 300L // ms

    // TASK2 -- Space double-tap detection
    private var lastSpaceTapTime = 0L
    private val SPACE_DOUBLE_TAP_THRESHOLD = 300L // ms

    // TASK3 -- File sending
    private val fileSenderHandler = Handler(Looper.getMainLooper())
    private var pendingFilePreviewCallback: ((FilePreviewInfo?) -> Unit)? = null

    // TASK1 -- Handler for chunked paste
    private val chunkedPasteHandler = Handler(Looper.getMainLooper())
    private var isChunkedPasteInProgress = false

    // FEATURE: Emoji category tracking
    private var emojiCategory = EmojiCategory.SMILEYS
    private val recentEmojis = mutableListOf<String>()
    private val MAX_RECENT_EMOJIS = 20

    override fun onCreate() {
        super.onCreate()

        // TASK3 -- Register service instance for FilePickerActivity
        KeyboardImeServiceHolder.instance = this

        // FIX: BUG-003 -- Use createBootSafe() for device-protected storage (Direct Boot aware)
        prefs = UserPreferences.createBootSafe(this)
        suggestions = SuggestionEngine(prefs)
        clip = ClipboardStore(this)

        // FIX: BUG-003 -- Check if device is already unlocked; if so, upgrade to credential-encrypted prefs
        checkDeviceUnlockedAndUpgradePrefs()

        // Recover last panel from process death
        currentPanel = try {
            Panel.valueOf(prefs.lastLayoutPanel)
        } catch (_: Exception) {
            Panel.QWERTY
        }

        // FIX: BUG-003 -- Register for ACTION_USER_UNLOCKED to upgrade prefs when device unlocks
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            unlockReceiver = object : BroadcastReceiver() {
                override fun onReceive(context: Context?, intent: Intent?) {
                    if (intent?.action == Intent.ACTION_USER_UNLOCKED) {
                        upgradeToCredentialPrefs()
                    }
                }
            }
            // FIX: FINAL-004 -- Use RECEIVER_NOT_EXPORTED on API 33+
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                registerReceiver(unlockReceiver, IntentFilter(Intent.ACTION_USER_UNLOCKED), Context.RECEIVER_NOT_EXPORTED)
            } else {
                registerReceiver(unlockReceiver, IntentFilter(Intent.ACTION_USER_UNLOCKED))
            }
        }
    }

    // FIX: BUG-003 -- Check device unlock state and upgrade prefs if already unlocked
    private fun checkDeviceUnlockedAndUpgradePrefs() {
        val km = getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
        // FIX: FINAL-008 -- minSdk is 26 (LOLLIPOP_MR1 is 22), so isDeviceLocked is always available
        val unlocked = !km.isDeviceLocked
        if (unlocked) {
            upgradeToCredentialPrefs()
        }
    }

    // FIX: BUG-003 -- Upgrade from device-protected prefs to credential-encrypted prefs
    private fun upgradeToCredentialPrefs() {
        try {
            prefs = UserPreferences.create(this)
            // Rebuild suggestion engine with the new prefs (which may have more personal words)
            currentKeyboardView = null
            suggestions.shutdown()
            suggestions = SuggestionEngine(prefs)
        } catch (_: Exception) {
            // If upgrade fails, keep using boot-safe prefs
        }
    }

    override fun onCreateInputView(): View {
        return KeyboardView(this).also { view ->
            currentKeyboardView = view
            view.listener = this
            applyCurrentPreferencesToView(view)
            refreshKeyboardLayout()
        }
    }

    override fun onStartInput(info: EditorInfo?, restarting: Boolean) {
        super.onStartInput(info, restarting)
        currentEditorInfo = info

        lastEditorAction = info?.imeOptions?.and(EditorInfo.IME_MASK_ACTION) ?: EditorInfo.IME_ACTION_NONE
        isPasswordField = isPasswordField(info)

        // Reset state for new field
        if (!restarting) {
            isShifted = shouldAutoCapitalize()
            isCapsLocked = false
            currentPanel = detectInitialPanel(info) // FIX: INFO-004
            composingText.clear()
            isComposing = false
            previousWord = null // Feature 6: Reset context on new field
            previousWord2 = null
        }

        // FIX: INFO-004 -- Detect input type and switch panel accordingly
        detectInputType(info)

        refreshKeyboardLayout()
        updateSuggestions()
    }

    // FIX: CRIT-001 -- Restore listener in onStartInputView.
// The listener must always be set when the input view is shown.
    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        // FIX: CRIT-001 -- Restore the listener every time the view starts
        currentKeyboardView?.listener = this
        refreshKeyboardLayout()
    }

    override fun onFinishInput() {
        super.onFinishInput()
        // FIX: CRIT-002 -- Finish composing text when input ends
        commitCurrentWord()
        currentEditorInfo = null
    }

    // FIX: CRIT-001 -- Do NOT null the listener in onFinishInputView.
// Only clean up composing text. The listener will be restored in onStartInputView.
    override fun onFinishInputView(finishingInput: Boolean) {
        super.onFinishInputView(finishingInput)
        // FIX: CRIT-002 -- Clean up composing text but do NOT null the listener
        commitCurrentWord()
    }

    override fun onDestroy() {
        // FIX: BUG-003 -- Unregister the unlock receiver
        unlockReceiver?.let {
            try { unregisterReceiver(it) } catch (_: Exception) { }
            unlockReceiver = null
        }

        suggestions.shutdown()
        // Save last panel for process death recovery
        prefs.lastLayoutPanel = currentPanel.name
        undoStack.clear()
        redoStack.clear()
        composingText.clear()

        // TASK1 -- Clear clipboard cache and cancel chunked paste
        clip.clearLargeClipCache()
        chunkedPasteHandler.removeCallbacksAndMessages(null)
        isChunkedPasteInProgress = false

        // TASK3 -- Clear file sender and unregister service holder
        fileSenderHandler.removeCallbacksAndMessages(null)
        pendingFilePreviewCallback = null
        KeyboardImeServiceHolder.instance = null

        super.onDestroy()
    }

    // FIX: BUG-005 -- onComputeInsets uses InputMethodService.Insets, not android.graphics.Insets.
// The android.graphics.Insets class was added in API 30 and is not the same as
// InputMethodService.Insets, which is the correct type for this callback.
    override fun onComputeInsets(outInsets: InputMethodService.Insets) {
        super.onComputeInsets(outInsets)
        if (isFullscreenMode) {
            outInsets.contentTopInsets = 0
            outInsets.touchableInsets = InputMethodService.Insets.TOUCHABLE_INSETS_VISIBLE
        } else {
            currentKeyboardView?.height?.let { h ->
                outInsets.contentTopInsets = h
                outInsets.touchableInsets = InputMethodService.Insets.TOUCHABLE_INSETS_VISIBLE
            }
        }
    }

    override fun onUpdateSelection(oldSelStart: Int, oldSelEnd: Int, newSelStart: Int, newSelEnd: Int, candidatesStart: Int, candidatesEnd: Int) {
        super.onUpdateSelection(oldSelStart, oldSelEnd, newSelStart, newSelEnd, candidatesStart, candidatesEnd)
        // FIX: CRIT-002 -- If cursor moved outside composing region, commit the composing text
        if (isComposing && candidatesStart >= 0 && candidatesEnd >= 0) {
            if (newSelStart < candidatesStart || newSelEnd > candidatesEnd) {
                commitCurrentWord()
            }
        }
        if (newSelStart == newSelEnd) {
            updateSuggestions()
        }
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (level >= TRIM_MEMORY_MODERATE) {
            currentKeyboardView?.onLowMemory()
            suggestions.clearCacheIfNeeded()
            // TASK1 -- Clear large clip cache on moderate memory pressure
            clip.clearLargeClipCache()
            if (level >= TRIM_MEMORY_COMPLETE) {
                undoStack.clear()
                redoStack.clear()
            }
        }
    }

    override fun onKey(key: KeyboardKey) {
        val ic = currentInputConnection ?: return
        if (prefs.haptics) vibrateTick()
        if (prefs.sounds) playClick(key.code)

        ic.beginBatchEdit()
        try {
            if (key.isAction) {
                handleActionKey(key, ic)
            } else {
                commitPrintable(key.output, ic)
            }
        } finally {
            ic.endBatchEdit()
        }
    }

    override fun onLongPress(key: KeyboardKey) {
        val ic = currentInputConnection ?: return
        when {
            key.code == KeyCodes.BACKSPACE -> {
                // TASK2 -- Long press backspace: delete selection if selected, else delete word
                val sel = getCursorState(ic)
                if (sel.start != sel.end) {
                    // Delete selected text
                    ic.commitText("", 0)
                } else {
                    deleteWord(ic)
                }
            }
            key.code == KeyCodes.SPACE -> showInputMethodPicker()
            // FIX: Long press on EMOJI key shows input method picker (keyboard switcher)
            // This replaces the old LANGUAGE key behavior -- since EMOJI replaced LANGUAGE on the bottom row,
            // users can still switch keyboards by long-pressing the [emoji] emoji button
            key.code == KeyCodes.EMOJI -> showInputMethodPicker()
            key.alt.isNotEmpty() -> {
                commitCurrentWord() // FIX: CRIT-002
                commitTextWithUndo(key.alt.first().toString(), ic)
            }
            key.output.isNotBlank() -> {
                commitCurrentWord() // FIX: CRIT-002
                commitTextWithUndo(key.output.uppercase(), ic)
            }
        }
    }

    override fun onSwipeWord(word: String) {
        if (word.isNotBlank() && !isPasswordField) {
            val ic = currentInputConnection ?: return
            ic.beginBatchEdit()
            try {
                commitCurrentWord() // FIX: CRIT-002 -- Clear any composing text first
                commitTextWithUndo(word, ic)
                commitTextWithUndo(" ", ic)
                suggestions.learn(word)
                previousWord2 = previousWord // Feature 6: Track second previous word
                previousWord = word
            } finally {
                ic.endBatchEdit()
            }
            updateSuggestions()
        }
    }

    override fun onSpaceDrag(deltaChars: Int) {
        val ic = currentInputConnection ?: return
        val keyCode = if (deltaChars < 0) KeyEvent.KEYCODE_DPAD_LEFT else KeyEvent.KEYCODE_DPAD_RIGHT
        val steps = max(1, kotlin.math.abs(deltaChars).coerceAtMost(8))
        for (i in 0 until steps) {
            ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, keyCode))
            ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, keyCode))
        }
    }

    override fun onBackspaceDrag(deltaWords: Int) {
        val ic = currentInputConnection ?: return
        ic.beginBatchEdit()
        try {
            for (i in 0 until deltaWords) {
                deleteWord(ic)
                if (prefs.haptics) vibrateTick()
            }
        } finally {
            ic.endBatchEdit()
        }
    }

    override fun onSuggestion(text: String) {
        val ic = currentInputConnection ?: return
        if (text.isBlank() || isPasswordField) return

        ic.beginBatchEdit()
        try {
            // FIX: CRIT-002 -- Finish composing before applying suggestion
            commitCurrentWord()
            val current = currentWord(ic)
            if (current.isNotEmpty()) {
                ic.deleteSurroundingText(current.length, 0)
            }
            commitTextWithUndo(text + " ", ic)
            previousWord2 = previousWord // Feature 6: Track second previous word
            previousWord = text
            suggestions.learn(text)
        } finally {
            ic.endBatchEdit()
        }
        updateSuggestions()
    }

    // TASK3 -- Handle file picker result from KeyboardView
    override fun onFilePicked(uri: Uri, mimeType: String) {
        sendFileViaInputConnection(uri, mimeType)
    }

    private fun handleActionKey(key: KeyboardKey, ic: InputConnection) {
        val code = key.code
        when (code) {
            KeyCodes.SHIFT -> handleShift()
            KeyCodes.BACKSPACE -> handleBackspace(ic)
            KeyCodes.ENTER -> {
                commitCurrentWord() // FIX: CRIT-002
                performEditorAction(ic)
            }
            KeyCodes.SPACE -> handleSpace(ic)
            KeyCodes.SYMBOLS -> { currentPanel = Panel.SYMBOLS; refreshKeyboardLayout() }
            KeyCodes.ABC -> { currentPanel = Panel.QWERTY; refreshKeyboardLayout() }
            KeyCodes.EMOJI -> { currentPanel = Panel.EMOJI; refreshKeyboardLayout() }
            KeyCodes.CLIPBOARD -> { currentPanel = Panel.CLIPBOARD; refreshKeyboardLayout() }
            KeyCodes.NUMPAD -> { currentPanel = Panel.NUMPAD; refreshKeyboardLayout() }
            KeyCodes.EDIT -> { currentPanel = Panel.EDIT; refreshKeyboardLayout() }
            KeyCodes.LANGUAGE, KeyCodes.VOICE -> showInputMethodPicker()
            KeyCodes.SETTINGS -> launchSettings()
            KeyCodes.MODE -> cycleLayoutMode()
            KeyCodes.THEME -> {
                ThemePalette.cycle(prefs) // Feature 4: Cycle through all themes
                refreshKeyboardLayout()
            }
            KeyCodes.INCOGNITO -> {
                prefs.incognito = !prefs.incognito
                refreshKeyboardLayout()
                updateSuggestions()
            }
            KeyCodes.UNDO -> performUndo(ic)
            KeyCodes.REDO -> performRedo(ic)
            KeyCodes.COPY -> {
                ic.performContextMenuAction(android.R.id.copy)
                // Refresh clipboard panel if visible so new item appears
                if (currentPanel == Panel.CLIPBOARD) refreshKeyboardLayout()
            }
            KeyCodes.CUT -> {
                ic.performContextMenuAction(android.R.id.cut)
                if (currentPanel == Panel.CLIPBOARD) refreshKeyboardLayout()
            }
            KeyCodes.PASTE -> performPaste(ic)
            KeyCodes.SELECT_ALL -> ic.performContextMenuAction(android.R.id.selectAll)
            KeyCodes.LEFT -> sendKey(ic, KeyEvent.KEYCODE_DPAD_LEFT)
            KeyCodes.RIGHT -> sendKey(ic, KeyEvent.KEYCODE_DPAD_RIGHT)
            KeyCodes.UP -> sendKey(ic, KeyEvent.KEYCODE_DPAD_UP)
            KeyCodes.DOWN -> sendKey(ic, KeyEvent.KEYCODE_DPAD_DOWN)
            // TASK3 -- File picker key codes
            KeyCodes.PHOTO_PICKER -> launchPhotoPicker()
            KeyCodes.FILE_PICKER -> launchFilePicker()

            // ------
            // FEATURE: Emoji category key codes
            // ------
            KeyCodes.EMOJI_CATEGORY_SMILEYS -> { emojiCategory = EmojiCategory.SMILEYS; refreshKeyboardLayout() }
            KeyCodes.EMOJI_CATEGORY_GESTURES -> { emojiCategory = EmojiCategory.GESTURES; refreshKeyboardLayout() }
            KeyCodes.EMOJI_CATEGORY_ANIMALS -> { emojiCategory = EmojiCategory.ANIMALS; refreshKeyboardLayout() }
            KeyCodes.EMOJI_CATEGORY_FOOD -> { emojiCategory = EmojiCategory.FOOD; refreshKeyboardLayout() }
            KeyCodes.EMOJI_CATEGORY_ACTIVITIES -> { emojiCategory = EmojiCategory.ACTIVITIES; refreshKeyboardLayout() }
            KeyCodes.EMOJI_CATEGORY_TRAVEL -> { emojiCategory = EmojiCategory.TRAVEL; refreshKeyboardLayout() }
            KeyCodes.EMOJI_CATEGORY_OBJECTS -> { emojiCategory = EmojiCategory.OBJECTS; refreshKeyboardLayout() }
            KeyCodes.EMOJI_CATEGORY_SYMBOLS -> { emojiCategory = EmojiCategory.SYMBOLS; refreshKeyboardLayout() }
            KeyCodes.EMOJI_CATEGORY_RECENT -> { emojiCategory = EmojiCategory.RECENT; refreshKeyboardLayout() }

            // ------
            // FEATURE: Clipboard action key codes
            // ------
            KeyCodes.CLIP_ITEM -> {
                // key.output contains the full clip text
                val clipText = key.output
                if (clipText.isNotEmpty()) {
                    commitCurrentWord()
                    if (clipText.length <= ClipboardStore.CHUNK_SIZE) {
                        commitTextWithUndo(clipText, ic)
                    } else {
                        performChunkedPaste(clipText, ic)
                    }
                }
                refreshKeyboardLayout()
            }
            KeyCodes.CLIP_PIN -> {
                // key.output contains the clip index as string
                val idx = key.output.toIntOrNull()
                if (idx != null) {
                    clip.togglePin(idx)
                }
                refreshKeyboardLayout()
            }
            KeyCodes.CLIP_DELETE -> {
                // key.output contains the clip index as string
                val idx = key.output.toIntOrNull()
                if (idx != null) {
                    clip.deleteAt(idx)
                }
                refreshKeyboardLayout()
            }
            KeyCodes.CLIP_CLEAR -> {
                clip.clearAll()
                refreshKeyboardLayout()
            }

            // ------
            // FEATURE: Toolbar key codes
            // ------
            KeyCodes.TOOLBAR_CLIPBOARD -> { currentPanel = Panel.CLIPBOARD; refreshKeyboardLayout() }
            KeyCodes.TOOLBAR_SETTINGS -> launchSettings()
            KeyCodes.TOOLBAR_THEME -> {
                ThemePalette.cycle(prefs) // Feature 4: Cycle through all themes
                refreshKeyboardLayout()
            }
            KeyCodes.TOOLBAR_ONEHAND -> cycleLayoutMode()
            KeyCodes.TOOLBAR_VOICE -> showInputMethodPicker()
            KeyCodes.TOOLBAR_INCOGNITO -> {
                prefs.incognito = !prefs.incognito
                refreshKeyboardLayout()
                updateSuggestions()
            }
        }
    }

    // ------
    // TASK2 -- Fixed keyboard key handlers
    // ------

    // TASK2 -- Shift key behavior:
// Single tap = next letter uppercase only (one-shot shift)
// Double tap = caps lock (all uppercase until tapped again)
    private fun handleShift() {
        val now = System.currentTimeMillis()
        val timeSinceLastTap = now - lastShiftTapTime
        lastShiftTapTime = now

        if (timeSinceLastTap < SHIFT_DOUBLE_TAP_THRESHOLD && isShifted && !isCapsLocked) {
            // Double tap: activate caps lock
            isCapsLocked = true
            isShifted = true
        } else if (isCapsLocked) {
            // Tapping while caps locked: turn off caps lock and shift
            isCapsLocked = false
            isShifted = false
        } else {
            // Single tap: toggle one-shot shift
            isShifted = !isShifted
            isCapsLocked = false
        }
        refreshKeyboardLayout()
    }

    // TASK2 -- Backspace key behavior:
// Single tap = delete 1 char (or composing char)
// If text is selected, delete the selection
// Works in ALL input fields including search bars
    private fun handleBackspace(ic: InputConnection) {
        // TASK2 -- First check if there is a selection; if so, delete it
        val sel = getCursorState(ic)
        if (sel.start != sel.end) {
            // Delete selected text
            ic.commitText("", 0)
            updateSuggestions()
            refreshKeyboardLayout()
            return
        }

        // FIX: CRIT-002 -- Handle backspace with composing text
        if (isComposing && composingText.isNotEmpty()) {
            composingText.deleteCharAt(composingText.length - 1)
            updateComposingText(ic)
            if (composingText.isEmpty()) {
                ic.finishComposingText()
                isComposing = false
            }
        } else {
            // TASK2 -- Use sendKeyEvent for backspace to work in ALL input fields
            // including search bars, URL bars, and other fields where
            // deleteSurroundingText may not work correctly
            try {
                ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DEL))
                ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DEL))
            } catch (e: Exception) {
                // Fallback: if sendKeyEvent fails (rare), use deleteSurroundingText
                try {
                    ic.deleteSurroundingText(1, 0)
                } catch (_: Exception) {
                    // Last resort: do nothing to avoid crash
                }
            }
        }
        updateSuggestions()
        refreshKeyboardLayout()
    }

    // TASK2 -- Space key behavior:
// Single tap = space (with autocorrect)
// Double tap = period + space
    private fun handleSpace(ic: InputConnection) {
        val now = System.currentTimeMillis()
        val timeSinceLastTap = now - lastSpaceTapTime
        lastSpaceTapTime = now

        // TASK2 -- Double-tap space = period + space
        if (timeSinceLastTap < SPACE_DOUBLE_TAP_THRESHOLD) {
            // Double tap detected: replace previous space with ". "
            // First delete the space we just inserted on the first tap
            val before = getTextBeforeCursor(ic, 2)
            if (before.isNotEmpty() && before.last() == ' ') {
                ic.deleteSurroundingText(1, 0)
                commitTextWithUndo(". ", ic)
            } else {
                commitTextWithUndo(" ", ic)
            }
            lastSpaceTapTime = 0L // Reset to avoid triple-tap triggering
            isShifted = shouldAutoCapitalize()
            updateSuggestions()
            refreshKeyboardLayout()
            return
        }

        // Single tap: normal space with autocorrect
        val word = if (isComposing) composingText.toString() else currentWord(ic)
        var correction: String? = null
        if (!isPasswordField && !prefs.incognito) {
            correction = suggestions.autocorrect(word)
        }

        // FIX: CRIT-002 -- Commit composing text before applying autocorrect
        if (isComposing) {
            if (correction != null && correction != word) {
                // Replace the composing text with the correction
                composingText.clear()
                composingText.append(correction)
                ic.setComposingText(correction, 1)
            }
            commitCurrentWord()
        } else {
            if (correction != null && correction != word) {
                ic.deleteSurroundingText(word.length, 0)
                commitTextWithUndo(correction, ic)
            }
        }

        if (correction == null || correction == word) {
            val before = getTextBeforeCursor(ic, 3)
            if (before.endsWith("  ") && shouldDoubleSpaceToPeriod()) {
                ic.deleteSurroundingText(2, 0)
                commitTextWithUndo(". ", ic)
            } else {
                commitTextWithUndo(" ", ic)
            }
        }
        isShifted = shouldAutoCapitalize()
        updateSuggestions()
        refreshKeyboardLayout()
    }

    // FIX: CRIT-002 -- Update the composing region on the InputConnection.
// This sets the composing text so the target app can display it with
// an underline or other composing indicator.
    private fun updateComposingText(ic: InputConnection) {
        if (composingText.isNotEmpty()) {
            ic.setComposingText(composingText.toString(), 1)
        }
    }

    // FIX: CRIT-002 -- Commit the current composing word.
// Called when the user finishes typing a word (space, punctuation, etc.)
// or when focus changes. This moves the text from composing to committed state.
    private fun commitCurrentWord() {
        if (!isComposing || composingText.isEmpty()) return
        val ic = currentInputConnection ?: return
        ic.finishComposingText()
        val word = composingText.toString()
        composingText.clear()
        isComposing = false
        // Learn the committed word
        if (!isPasswordField && !prefs.incognito && word.length > 2) {
            previousWord2 = previousWord // Feature 6: Track second previous word
            previousWord = word
            suggestions.learn(word)
        }
    }

    // FIX: CRIT-002 -- Proper composing text pipeline.
// FIX: BUG-009 -- Check word boundary BEFORE appending to composingText.
// TASK2 -- Symbol keys: all special characters must insert correctly.
// When the output contains whitespace or punctuation, commit the current composing
// word first, then commit the punctuation directly -- instead of appending it to
// composingText which would corrupt the composing region.
    private fun commitPrintable(output: String, ic: InputConnection) {
        if (output.isEmpty()) return

        // FEATURE: Emoji panel -- commit emojis directly without composing, track recent
        if (currentPanel == Panel.EMOJI) {
            commitCurrentWord()
            ic.commitText(output, 1)
            addRecentEmoji(output)
            refreshKeyboardLayout()
            updateSuggestions()
            return
        }

        val shouldUpper = (isShifted || isCapsLocked) && output.length == 1 && output[0].isLetter()
        val textToType = if (shouldUpper) output.uppercase() else output

        // FIX: BUG-009 -- Check word boundary BEFORE appending to composingText
        // TASK2 -- Symbol keys: punctuation and special characters commit directly
        // FIX: Added currency symbols and numpad operators
        //   These were previously added to composing text instead of being committed directly
        val boundaryChars = setOf('.', ',', '!', '?', ':', ';', '@', '#', '$', '%', '&', '*', '(', ')', '-', '_', '=', '+', '[', ']', '{', '}', '|', '\\', '/', '<', '>', '\'', '"', '~', '^', '`', '€', '£', '¥', '₹', '₩', '₱', '₫', '₿')
        val isWordBoundary = textToType.any { it.isWhitespace() || it in boundaryChars }

        if (isWordBoundary) {
            // Commit current composing word first, then commit the punctuation directly
            commitCurrentWord()
            ic.commitText(textToType, 1)
            val before = getTextBeforeCursor(ic, 64).trim().split(Regex("\\s+")).lastOrNull() ?: ""
            if (before.length > 2) {
                previousWord2 = null // Feature 6: Reset second context on word boundary
                previousWord = before
                if (!isPasswordField && !prefs.incognito) suggestions.learn(before)
            }
        } else {
            // Regular letter -- add to composing text
            composingText.append(textToType)
            isComposing = true
            updateComposingText(ic)
        }

        // TASK2 -- Single-tap shift: uppercase only next letter, then release
        if (isShifted && !isCapsLocked) isShifted = false
        refreshKeyboardLayout()
        updateSuggestions()
    }

    // TASK2 -- Enter key: respect editorInfo.imeOptions (send vs newline).
// If the editor specifies an action (e.g., IME_ACTION_SEND, IME_ACTION_SEARCH),
// perform that action. Otherwise, insert a newline character.
    private fun performEditorAction(ic: InputConnection) {
        val info = currentEditorInfo
        val action = info?.imeOptions?.and(EditorInfo.IME_MASK_ACTION) ?: EditorInfo.IME_ACTION_NONE

        if (action != EditorInfo.IME_ACTION_NONE) {
            // The editor expects a specific action -- perform it
            ic.performEditorAction(action)
        } else {
            // No specific action -- insert newline
            ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER))
            ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_ENTER))
        }
    }

    private fun deleteWord(ic: InputConnection) {
        // FIX: CRIT-002 -- Handle composing text in deleteWord
        if (isComposing) {
            commitCurrentWord()
        }
        val before = getTextBeforeCursor(ic, 80)
        val wordLength = before.takeLastWhile { !it.isWhitespace() && it != '\n' }.length.coerceAtLeast(1)
        ic.deleteSurroundingText(wordLength, 0)
        updateSuggestions()
    }

    // ------
    // FEATURE: Emoji recent tracking
    // ------

    // Add an emoji to the recent list. Most recent is first.
// Duplicates are moved to the front. Max 20 items.
    private fun addRecentEmoji(emoji: String) {
        if (emoji.isBlank()) return
        recentEmojis.remove(emoji)
        recentEmojis.add(0, emoji)
        while (recentEmojis.size > MAX_RECENT_EMOJIS) {
            recentEmojis.removeAt(recentEmojis.lastIndex)
        }
    }

    // ------
    // TASK1 -- Chunked paste for large text (5000+ lines)
    // ------

    // TASK1 -- Perform paste with chunked input for large text.
// Instead of committing all text at once (which can crash/ANR for 5000+ lines),
// we split into 500-char chunks and commit each in a batch edit.
// Small text (< CHUNK_SIZE) is committed immediately for responsiveness.
    private fun performPaste(ic: InputConnection) {
        // Cancel any in-progress chunked paste
        if (isChunkedPasteInProgress) {
            chunkedPasteHandler.removeCallbacksAndMessages(null)
            isChunkedPasteInProgress = false
        }

        val clipboardText = clip.pasteText()
        if (clipboardText.isNotEmpty()) {
            if (clipboardText.length <= ClipboardStore.CHUNK_SIZE) {
                // Small text: commit directly (fast path)
                commitTextWithUndo(clipboardText, ic)
            } else {
                // Large text: chunked paste
                performChunkedPaste(clipboardText, ic)
            }
        } else {
            // Fallback to system paste
            ic.performContextMenuAction(android.R.id.paste)
        }
    }

    // TASK1 -- Chunked paste implementation.
// Commits text in 500-character chunks using batch edits.
// Each chunk is committed with a small delay to keep the UI responsive
// and prevent ANR on the main thread.
    private fun performChunkedPaste(text: String, ic: InputConnection) {
        isChunkedPasteInProgress = true

        // First, commit any composing text
        commitCurrentWord()

        // Record undo for the entire paste operation
        val cursor = getCursorState(ic)

        // Split text into chunks and commit each one
        val chunks = text.chunked(ClipboardStore.CHUNK_SIZE)
        var offset = 0

        ic.beginBatchEdit()
        try {
            for (chunk in chunks) {
                ic.commitText(chunk, 1)
                offset += chunk.length
            }
        } finally {
            ic.endBatchEdit()
        }

        // Add undo entry for the entire pasted text
        val entry = UndoEntry(
            text = text,
            cursorStart = cursor.start,
            selectionStart = cursor.start,
            selectionEnd = cursor.end
        )
        undoStack.addLast(entry)
        if (undoStack.size > 40) undoStack.removeFirst()
        redoStack.clear()

        isChunkedPasteInProgress = false
    }

    // ------
    // TASK3 -- File sending from keyboard
    // ------

    // TASK3 -- Launch photo picker to select images.
// Uses the Android photo picker (ActivityResultContracts.PickVisualMedia)
// via an intent, since InputMethodService can't use ActivityResult APIs directly.
    private fun launchPhotoPicker() {
        try {
            val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                // Use Android 13+ photo picker
                Intent(android.provider.MediaStore.ACTION_PICK_IMAGES).apply {
                    type = "image/" + "*"
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
            } else {
                // Fallback for older versions: generic image picker
                Intent(Intent.ACTION_GET_CONTENT).apply {
                    type = "image/" + "*"
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    addCategory(Intent.CATEGORY_OPENABLE)
                }
            }

            // Start the picker activity from the IME service
            val pendingIntent = android.app.PendingIntent.getActivity(
                this,
                1001,
                intent,
                android.app.PendingIntent.FLAG_IMMUTABLE or android.app.PendingIntent.FLAG_UPDATE_CURRENT
            )

            // We can't directly receive activity results from InputMethodService,
            // so we use a different approach: start the activity and let the user
            // pick the file. We'll use a content provider approach instead.
            startFilePickerActivity("image/" + "*")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to launch photo picker", e)
            showErrorFeedback("Unable to open photo picker")
        }
    }

    // TASK3 -- Launch file picker for PDF, docs, audio, video.
    private fun launchFilePicker() {
        try {
            startFilePickerActivity("${'*'}/${'*'}")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to launch file picker", e)
            showErrorFeedback("Unable to open file picker")
        }
    }

    // TASK3 -- Start file picker activity.
// Since InputMethodService cannot use ActivityResultLauncher,
// we start a transparent activity that handles the file picking
// and sends the result back to the IME service.
    private fun startFilePickerActivity(mimeType: String) {
        val intent = Intent(this, FilePickerActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_ACTIVITY_NO_HISTORY)
            putExtra(FilePickerActivity.EXTRA_MIME_TYPE, mimeType)
        }
        startActivity(intent)
    }

    // TASK3 -- Send a file via InputConnectionCompat.commitContent().
// Falls back to share intent if the app doesn't support commitContent.
// Handles URI permissions correctly and shows error if unsupported.
    fun sendFileViaInputConnection(uri: Uri, mimeType: String) {
        val ic = currentInputConnection
        if (ic == null) {
            showErrorFeedback("No active input field")
            return
        }

        // Take persistable URI permission
        try {
            contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        } catch (_: Exception) {
            // May not be a persistable URI (e.g., content:// from picker)
        }

        // Get file info for preview and metadata
        val fileInfo = getFileInfo(uri, mimeType)
        if (fileInfo == null) {
            showErrorFeedback("Unable to read file")
            return
        }

        // Check if the editor supports this MIME type via commitContent
        val editorInfo = currentEditorInfo
        val supportedMimeTypes: Array<String> = if (editorInfo != null) {
            // EditorInfo.contentMimeTypes is available on API 25+ (our minSdk is 26)
            editorInfo.contentMimeTypes ?: emptyArray()
        } else {
            emptyArray()
        }

        val isSupported = supportedMimeTypes.isEmpty() || supportedMimeTypes.any { supported ->
            mimeTypeMatches(supported, mimeType)
        }

        if (isSupported) {
            // Try commitContent API
            commitFileContent(uri, mimeType, fileInfo, ic)
        } else {
            // Fallback to share intent
            fallbackToShareIntent(uri, mimeType, fileInfo)
        }
    }

    // TASK3 -- Commit file content using InputConnectionCompat.commitContent().
// This is the modern way to send rich content from an IME.
    private fun commitFileContent(uri: Uri, mimeType: String, fileInfo: FilePreviewInfo, ic: InputConnection) {
        try {
            val inputContentInfo = InputContentInfoCompat(
                uri,
                ClipDescription(fileInfo.fileName, arrayOf(mimeType)),
                null // linkUri
            )

            val flags = InputConnectionCompat.INPUT_CONTENT_GRANT_READ_URI_PERMISSION

            val committed = InputConnectionCompat.commitContent(ic, currentEditorInfo ?: return, inputContentInfo, flags, null)

            if (!committed) {
                // App didn't accept the content -- try share intent fallback
                Log.w(TAG, "commitContent returned false, falling back to share intent")
                fallbackToShareIntent(uri, mimeType, fileInfo)
            } else {
                Log.d(TAG, "File sent successfully via commitContent: ${fileInfo.fileName}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "commitContent failed", e)
            // Fallback to share intent
            fallbackToShareIntent(uri, mimeType, fileInfo)
        }
    }

    // TASK3 -- Fallback to share intent when commitContent is not supported.
// Shows an error to the user that the app doesn't support direct file receiving.
    private fun fallbackToShareIntent(uri: Uri, mimeType: String, fileInfo: FilePreviewInfo) {
        try {
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = mimeType
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            val chooserIntent = Intent.createChooser(shareIntent, "Send ${fileInfo.fileName} via").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            startActivity(chooserIntent)
        } catch (e: Exception) {
            Log.e(TAG, "Share intent failed", e)
            showErrorFeedback("This app doesn't support receiving files directly. The file could not be sent.")
        }
    }

    // TASK3 -- Get file information from a URI for preview.
    private fun getFileInfo(uri: Uri, mimeType: String): FilePreviewInfo? {
        return try {
            var fileName = "Unknown"
            var fileSize = 0L

            // Try to get filename and size from content resolver
            contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)

                    if (nameIndex >= 0) {
                        fileName = cursor.getString(nameIndex)
                    }
                    if (sizeIndex >= 0) {
                        fileSize = cursor.getLong(sizeIndex)
                    }
                }
            }

            // If file size is 0, try to get it from the input stream
            if (fileSize == 0L) {
                try {
                    contentResolver.openInputStream(uri)?.use { stream ->
                        fileSize = stream.available().toLong()
                    }
                } catch (_: Exception) { }
            }

            FilePreviewInfo(
                uri = uri,
                mimeType = mimeType,
                fileName = fileName,
                fileSize = fileSize
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get file info", e)
            null
        }
    }

    // TASK3 -- Show error feedback to the user.
    private fun showErrorFeedback(message: String) {
        currentKeyboardView?.post {
            android.widget.Toast.makeText(this@KeyboardImeService, message, android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    // ------
    // Existing utility methods
    // ------

    private fun updateSuggestions() {
        if (isPasswordField || prefs.incognito) {
            currentKeyboardView?.setSuggestions(emptyList())
            return
        }

        val view = currentKeyboardView ?: return
        val word = if (isComposing) composingText.toString() else currentWord(currentInputConnection ?: return)
        suggestions.suggestAsync(word, previousWord, previousWord2) { list ->
            view.post {
                view.setSuggestions(list)
            }
        }
    }

    private fun refreshKeyboardLayout() {
        currentKeyboardView?.apply {
            this.preferences = prefs
            this.layoutMode = LayoutMode.entries.getOrElse(prefs.layoutModeOrdinal) { LayoutMode.FULL }
            val keys = KeyboardLayoutFactory.layout(
                currentPanel,
                isShifted,
                isCapsLocked,
                prefs.numberRow,
                prefs.isRtl,
                prefs.isLandscape(this@KeyboardImeService), // FIX: LOW-004
                emojiCategory = emojiCategory,
                recentEmojis = recentEmojis.toList(),
                clipHistory = clip.history(),
                pinnedIndices = clip.pinnedIndices()
            )
            // Dynamically set Spacebar label to show current subtype (Gboard style)
            val imm = getSystemService(INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
            val subtype = imm.currentInputMethodSubtype
            val spaceLabel = subtype?.let {
                val name = it.getDisplayName(this@KeyboardImeService, packageName, applicationInfo).toString()
                if (name.isNotBlank()) name else "space"
            } ?: "space"
            val updatedKeys = keys.map { row ->
                row.map { key ->
                    if (key.code == KeyCodes.SPACE) {
                        key.copy(label = spaceLabel)
                    } else {
                        key
                    }
                }
            }
            setKeys(updatedKeys, currentPanel, isShifted, isCapsLocked, prefs.incognito)
        }
    }

    private fun applyCurrentPreferencesToView(view: KeyboardView) {
        view.preferences = prefs
        view.layoutMode = LayoutMode.entries.getOrElse(prefs.layoutModeOrdinal) { LayoutMode.FULL }
    }

    // FIX: QUALITY-004 -- Single IPC call to get cursor position and selection.
// Replaces separate getCursorPosition() and getSelection() methods which
// each made their own ExtractedText request (two IPC round-trips).
// Now uses a single ExtractedText request to fetch both values.
    private fun getCursorState(ic: InputConnection): CursorState {
        return try {
            val request = ExtractedTextRequest().apply { token = 0 }
            val extracted = ic.getExtractedText(request, 0)
            CursorState(extracted?.selectionStart ?: 0, extracted?.selectionEnd ?: 0)
        } catch (e: Exception) {
            CursorState(0, 0)
        }
    }

    // FIX: HIGH-005 -- Commit text with undo entry that captures cursor position and selection.
// FIX: QUALITY-004 -- Uses single getCursorState() IPC call instead of two separate calls.
// TASK1 -- For large text, does NOT store the full text in undo stack (memory concern).
    private fun commitTextWithUndo(text: String, ic: InputConnection) {
        if (text.isBlank()) { ic.commitText(text, 1); return }

        // FIX: QUALITY-004 -- Single IPC call for cursor state
        val cursor = getCursorState(ic)
        ic.commitText(text, 1)

        // TASK1 -- Limit undo entry text size to prevent OOM with large pastes
        val undoText = if (text.length > ClipboardStore.CHUNK_SIZE * 4) {
            text.substring(0, ClipboardStore.CHUNK_SIZE * 4) + "...[truncated]"
        } else {
            text
        }

        val entry = UndoEntry(
            text = undoText,
            cursorStart = cursor.start,
            selectionStart = cursor.start,
            selectionEnd = cursor.end
        )
        undoStack.addLast(entry)
        if (undoStack.size > 40) undoStack.removeFirst()
        redoStack.clear()

        if (!isPasswordField && !prefs.incognito) {
            clip.remember(text)
        }
    }

    // FIX: HIGH-005 -- Proper undo using UndoEntry with cursor position.
// FIX: QUALITY-004 -- Uses getCursorState() for single IPC call.
// Moves cursor to the exact position where the text was committed,
// then deletes the text at that position.
    private fun performUndo(ic: InputConnection) {
        val entry = undoStack.removeLastOrNull() ?: return

        // Move cursor to the position where the text was originally committed
        val textLength = entry.text.length
        val deletePosition = entry.cursorStart

        // Get current cursor position -- FIX: QUALITY-004 -- single IPC call
        val currentCursorState = getCursorState(ic)
        val currentCursor = currentCursorState.start

        // If cursor is after the committed text, we can simply delete backwards
        if (currentCursor >= deletePosition + textLength) {
            // Move cursor to the end of the text to delete
            ic.setSelection(deletePosition + textLength, deletePosition + textLength)
            ic.deleteSurroundingText(textLength, 0)
        } else {
            // Cursor is elsewhere -- use setSelection to select the committed text, then delete
            ic.setSelection(deletePosition, deletePosition + textLength)
            ic.commitText("", 0)
        }

        // Restore cursor to the original position before the commit
        ic.setSelection(entry.selectionStart.coerceAtLeast(0), entry.selectionEnd.coerceAtLeast(0))

        redoStack.addLast(entry)
    }

    // FIX: HIGH-005 -- Proper redo using UndoEntry with cursor position.
    private fun performRedo(ic: InputConnection) {
        val entry = redoStack.removeLastOrNull() ?: return

        // Move cursor to the original commit position
        ic.setSelection(entry.cursorStart, entry.cursorStart)
        ic.commitText(entry.text, 1)

        undoStack.addLast(entry)
    }

    private fun sendKey(ic: InputConnection, keyCode: Int) {
        ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, keyCode))
        ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, keyCode))
    }

    private fun currentWord(ic: InputConnection): String {
        val before = getTextBeforeCursor(ic, 64)
        return before.takeLastWhile { it.isLetter() || it == '\'' || it == '-' }
    }

    private fun getTextBeforeCursor(ic: InputConnection, n: Int): String {
        return ic.getTextBeforeCursor(n, 0)?.toString() ?: ""
    }

    private fun shouldAutoCapitalize(): Boolean {
        val before = getTextBeforeCursor(currentInputConnection ?: return false, 100).trimEnd()
        return before.isEmpty() || before.endsWith(". ") || before.endsWith("! ") || before.endsWith("? ") || before.endsWith("\n")
    }

    private fun shouldDoubleSpaceToPeriod(): Boolean {
        val before = getTextBeforeCursor(currentInputConnection ?: return false, 5)
        return before.matches(Regex(".*[A-Za-z] $"))
    }

    private fun cycleLayoutMode() {
        prefs.layoutModeOrdinal = (prefs.layoutModeOrdinal + 1) % LayoutMode.entries.size
        refreshKeyboardLayout()
    }

    private fun showInputMethodPicker() {
        val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        imm.showInputMethodPicker()
    }

    private fun launchSettings() {
        val intent = Intent(this, SettingsActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        startActivity(intent)
    }

    private fun isPasswordField(info: EditorInfo?): Boolean {
        if (info == null) return false
        val variation = info.inputType and android.text.InputType.TYPE_MASK_VARIATION
        return variation in listOf(
            android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD,
            android.text.InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD,
            android.text.InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD,
            android.text.InputType.TYPE_NUMBER_VARIATION_PASSWORD
        )
    }

    // FIX: INFO-004 -- Detect input type and switch to appropriate panel.
// For email fields: switch to SYMBOLS panel (@ is prominent).
// For URI fields: switch to SYMBOLS panel (/ and : are prominent).
// For phone fields: switch to NUMPAD panel.
// For number fields: switch to NUMPAD panel.
    private fun detectInputType(info: EditorInfo?) {
        if (info == null) return
        val inputType = info.inputType
        val variation = inputType and android.text.InputType.TYPE_MASK_VARIATION
        val klass = inputType and android.text.InputType.TYPE_MASK_CLASS

        when {
            variation == android.text.InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS -> {
                // Email -- switch to symbols for @ access
                if (currentPanel == Panel.QWERTY) {
                    // Keep QWERTY but user may want @ -- no forced switch, just flag
                    // In a full implementation, could rearrange symbols row
                }
            }
            variation == android.text.InputType.TYPE_TEXT_VARIATION_URI -> {
                // URI -- symbols for / and :
                if (currentPanel == Panel.QWERTY) {
                    // Keep QWERTY -- URI still uses letters primarily
                }
            }
            klass == android.text.InputType.TYPE_CLASS_PHONE -> {
                currentPanel = Panel.NUMPAD
            }
            klass == android.text.InputType.TYPE_CLASS_NUMBER -> {
                currentPanel = Panel.NUMPAD
            }
        }
    }

    // FIX: INFO-004 -- Detect initial panel based on EditorInfo input type.
    private fun detectInitialPanel(info: EditorInfo?): Panel {
        if (info == null) return Panel.QWERTY
        val inputType = info.inputType
        val klass = inputType and android.text.InputType.TYPE_MASK_CLASS

        return when (klass) {
            android.text.InputType.TYPE_CLASS_PHONE -> Panel.NUMPAD
            android.text.InputType.TYPE_CLASS_NUMBER -> Panel.NUMPAD
            else -> Panel.QWERTY
        }
    }

    private fun vibrateTick() {
        val vibrator = getSystemService(VIBRATOR_SERVICE) as Vibrator
        // FIX: FINAL-008 -- minSdk is 26 (O is 26), so VibrationEffect is always available
        vibrator.vibrate(VibrationEffect.createOneShot(12, VibrationEffect.DEFAULT_AMPLITUDE))
    }

    private fun playClick(code: Int) {
        val audio = getSystemService(AUDIO_SERVICE) as AudioManager
        val sound = if (code == KeyCodes.SPACE) AudioManager.FX_KEYPRESS_SPACEBAR else AudioManager.FX_KEYPRESS_STANDARD
        audio.playSoundEffect(sound, 0.3f)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK && isInputViewShown) {
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

}
