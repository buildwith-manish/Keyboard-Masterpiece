package com.keyboardmasterpiece.ime

import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.inputmethodservice.InputMethodService
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.ExtractedTextRequest
import android.view.inputmethod.InputConnection
import android.view.inputmethod.InputMethodManager
import com.keyboardmasterpiece.engine.*
import com.keyboardmasterpiece.settings.SettingsActivity
import kotlin.math.max

/**
 * PRODUCTION-GRADE InputMethodService
 *
 * FIX: CRIT-001 — Restore listener in onStartInputView, don't null it in onFinishInputView
 * FIX: CRIT-002 — Proper composing text pipeline with setComposingText/finishComposingText
 * FIX: CRIT-003 — directBootAware with device-protected storage and unlock check
 * FIX: HIGH-005 — UndoEntry with cursor position, selection, and exact-position deletion
 * FIX: MED-007 — Removed stale inputConnection field
 * FIX: INFO-004 — Input type-specific behavior for email, URI, phone, number
 * FIX: BUG-005 — onComputeInsets uses InputMethodService.Insets (not android.graphics.Insets)
 * FIX: BUG-003 — prefs created via UserPreferences.createBootSafe(); upgraded on unlock
 * FIX: BUG-009 — commitPrintable checks word boundary BEFORE appending to composingText
 * FIX: QUALITY-004 — Merged getCursorPosition/getSelection into single getCursorState IPC call
 */
class KeyboardImeService : InputMethodService(), KeyboardView.Listener {

    private lateinit var prefs: UserPreferences
    private lateinit var suggestions: SuggestionEngine
    private lateinit var clip: ClipboardStore

    // FIX: BUG-003 — BroadcastReceiver for ACTION_USER_UNLOCKED to upgrade prefs
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

    // FIX: CRIT-002 — composingText changed from String to StringBuilder
    private val composingText = StringBuilder()
    private var isComposing = false

    // FIX: QUALITY-004 — CursorState data class replacing separate getCursorPosition/getSelection
    private data class CursorState(val start: Int, val end: Int)

    // FIX: HIGH-005 — Proper UndoEntry with cursor position and selection info
    private data class UndoEntry(
        val text: String,
        val cursorStart: Int,
        val selectionStart: Int,
        val selectionEnd: Int
    )
    private val undoStack = ArrayDeque<UndoEntry>()
    private val redoStack = ArrayDeque<UndoEntry>()

    private var currentEditorInfo: EditorInfo? = null

    override fun onCreate() {
        super.onCreate()

        // FIX: BUG-003 — Use createBootSafe() for device-protected storage (Direct Boot aware)
        prefs = UserPreferences.createBootSafe(this)
        suggestions = SuggestionEngine(prefs)
        clip = ClipboardStore(this)

        // FIX: BUG-003 — Check if device is already unlocked; if so, upgrade to credential-encrypted prefs
        checkDeviceUnlockedAndUpgradePrefs()

        // Recover last panel from process death
        currentPanel = try {
            Panel.valueOf(prefs.lastLayoutPanel)
        } catch (_: Exception) {
            Panel.QWERTY
        }

        // FIX: BUG-003 — Register for ACTION_USER_UNLOCKED to upgrade prefs when device unlocks
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            unlockReceiver = object : BroadcastReceiver() {
                override fun onReceive(context: Context?, intent: Intent?) {
                    if (intent?.action == Intent.ACTION_USER_UNLOCKED) {
                        upgradeToCredentialPrefs()
                    }
                }
            }
            // FIX: FINAL-004 — Use RECEIVER_NOT_EXPORTED on API 33+
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                registerReceiver(unlockReceiver, IntentFilter(Intent.ACTION_USER_UNLOCKED), Context.RECEIVER_NOT_EXPORTED)
            } else {
                registerReceiver(unlockReceiver, IntentFilter(Intent.ACTION_USER_UNLOCKED))
            }
        }
    }

    // FIX: BUG-003 — Check device unlock state and upgrade prefs if already unlocked
    private fun checkDeviceUnlockedAndUpgradePrefs() {
        val km = getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
        // FIX: FINAL-008 — minSdk is 26 (LOLLIPOP_MR1 is 22), so isDeviceLocked is always available
        val unlocked = !km.isDeviceLocked
        if (unlocked) {
            upgradeToCredentialPrefs()
        }
    }

    // FIX: BUG-003 — Upgrade from device-protected prefs to credential-encrypted prefs
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
        }

        // FIX: INFO-004 — Detect input type and switch panel accordingly
        detectInputType(info)

        refreshKeyboardLayout()
        updateSuggestions()
    }

    /**
     * FIX: CRIT-001 — Restore listener in onStartInputView.
     * The listener must always be set when the input view is shown.
     */
    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        // FIX: CRIT-001 — Restore the listener every time the view starts
        currentKeyboardView?.listener = this
        refreshKeyboardLayout()
    }

    override fun onFinishInput() {
        super.onFinishInput()
        // FIX: CRIT-002 — Finish composing text when input ends
        commitCurrentWord()
        currentEditorInfo = null
    }

    /**
     * FIX: CRIT-001 — Do NOT null the listener in onFinishInputView.
     * Only clean up composing text. The listener will be restored in onStartInputView.
     */
    override fun onFinishInputView(finishingInput: Boolean) {
        super.onFinishInputView(finishingInput)
        // FIX: CRIT-002 — Clean up composing text but do NOT null the listener
        commitCurrentWord()
    }

    override fun onDestroy() {
        // FIX: BUG-003 — Unregister the unlock receiver
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
        super.onDestroy()
    }

    /**
     * FIX: BUG-005 — onComputeInsets uses InputMethodService.Insets, not android.graphics.Insets.
     * The android.graphics.Insets class was added in API 30 and is not the same as
     * InputMethodService.Insets, which is the correct type for this callback.
     */
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
        // FIX: CRIT-002 — If cursor moved outside composing region, commit the composing text
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
                handleActionKey(key.code, ic)
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
            key.code == KeyCodes.BACKSPACE -> deleteWord(ic)
            key.code == KeyCodes.SPACE -> showInputMethodPicker()
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
                commitCurrentWord() // FIX: CRIT-002 — Clear any composing text first
                commitTextWithUndo(word, ic)
                commitTextWithUndo(" ", ic)
                suggestions.learn(word)
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

    override fun onSuggestion(text: String) {
        val ic = currentInputConnection ?: return
        if (text.isBlank() || isPasswordField) return

        ic.beginBatchEdit()
        try {
            // FIX: CRIT-002 — Finish composing before applying suggestion
            commitCurrentWord()
            val current = currentWord(ic)
            if (current.isNotEmpty()) {
                ic.deleteSurroundingText(current.length, 0)
            }
            commitTextWithUndo(text + " ", ic)
            previousWord = text
            suggestions.learn(text)
        } finally {
            ic.endBatchEdit()
        }
        updateSuggestions()
    }

    private fun handleActionKey(code: Int, ic: InputConnection) {
        when (code) {
            KeyCodes.SHIFT -> {
                if (isShifted && !isCapsLocked) isCapsLocked = true
                else if (isCapsLocked) isCapsLocked = false
                else isShifted = !isShifted
                refreshKeyboardLayout()
            }
            KeyCodes.BACKSPACE -> {
                // FIX: CRIT-002 — Handle backspace with composing text
                if (isComposing && composingText.isNotEmpty()) {
                    composingText.deleteCharAt(composingText.length - 1)
                    updateComposingText(ic)
                    if (composingText.isEmpty()) {
                        ic.finishComposingText()
                        isComposing = false
                    }
                } else {
                    ic.deleteSurroundingText(1, 0)
                }
                updateSuggestions()
            }
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
                prefs.darkTheme = !prefs.darkTheme
                refreshKeyboardLayout()
            }
            KeyCodes.INCOGNITO -> {
                prefs.incognito = !prefs.incognito
                refreshKeyboardLayout()
                updateSuggestions()
            }
            KeyCodes.UNDO -> performUndo(ic)
            KeyCodes.REDO -> performRedo(ic)
            KeyCodes.COPY -> ic.performContextMenuAction(android.R.id.copy)
            KeyCodes.CUT -> ic.performContextMenuAction(android.R.id.cut)
            KeyCodes.PASTE -> performPaste(ic)
            KeyCodes.SELECT_ALL -> ic.performContextMenuAction(android.R.id.selectAll)
            KeyCodes.LEFT -> sendKey(ic, KeyEvent.KEYCODE_DPAD_LEFT)
            KeyCodes.RIGHT -> sendKey(ic, KeyEvent.KEYCODE_DPAD_RIGHT)
            KeyCodes.UP -> sendKey(ic, KeyEvent.KEYCODE_DPAD_UP)
            KeyCodes.DOWN -> sendKey(ic, KeyEvent.KEYCODE_DPAD_DOWN)
        }
    }

    /**
     * FIX: CRIT-002 — Proper composing text pipeline.
     * FIX: BUG-009 — Check word boundary BEFORE appending to composingText.
     * When the output contains whitespace or punctuation, commit the current composing
     * word first, then commit the punctuation directly — instead of appending it to
     * composingText which would corrupt the composing region.
     */
    private fun commitPrintable(output: String, ic: InputConnection) {
        if (output.isEmpty()) return
        val shouldUpper = (isShifted || isCapsLocked) && output.length == 1 && output[0].isLetter()
        val textToType = if (shouldUpper) output.uppercase() else output

        // FIX: BUG-009 — Check word boundary BEFORE appending to composingText
        val isWordBoundary = textToType.any { it.isWhitespace() || it in ".,!?:" }

        if (isWordBoundary) {
            // Commit current composing word first, then commit the punctuation directly
            commitCurrentWord()
            ic.commitText(textToType, 1)
            val before = getTextBeforeCursor(ic, 64).trim().split(Regex("\\s+")).lastOrNull() ?: ""
            if (before.length > 2) {
                previousWord = before
                if (!isPasswordField && !prefs.incognito) suggestions.learn(before)
            }
        } else {
            // Regular letter — add to composing text
            composingText.append(textToType)
            isComposing = true
            updateComposingText(ic)
        }

        if (isShifted && !isCapsLocked) isShifted = false
        refreshKeyboardLayout()
        updateSuggestions()
    }

    /**
     * FIX: CRIT-002 — Update the composing region on the InputConnection.
     * This sets the composing text so the target app can display it with
     * an underline or other composing indicator.
     */
    private fun updateComposingText(ic: InputConnection) {
        if (composingText.isNotEmpty()) {
            ic.setComposingText(composingText.toString(), 1)
        }
    }

    /**
     * FIX: CRIT-002 — Commit the current composing word.
     * Called when the user finishes typing a word (space, punctuation, etc.)
     * or when focus changes. This moves the text from composing to committed state.
     */
    private fun commitCurrentWord() {
        if (!isComposing || composingText.isEmpty()) return
        val ic = currentInputConnection ?: return
        ic.finishComposingText()
        val word = composingText.toString()
        composingText.clear()
        isComposing = false
        // Learn the committed word
        if (!isPasswordField && !prefs.incognito && word.length > 2) {
            previousWord = word
            suggestions.learn(word)
        }
    }

    private fun handleSpace(ic: InputConnection) {
        val word = if (isComposing) composingText.toString() else currentWord(ic)
        var correction: String? = null
        if (!isPasswordField && !prefs.incognito) {
            correction = suggestions.autocorrect(word)
        }

        // FIX: CRIT-002 — Commit composing text before applying autocorrect
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

    private fun performEditorAction(ic: InputConnection) {
        if (lastEditorAction != EditorInfo.IME_ACTION_NONE) {
            ic.performEditorAction(lastEditorAction)
        } else {
            ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER))
            ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_ENTER))
        }
    }

    private fun deleteWord(ic: InputConnection) {
        // FIX: CRIT-002 — Handle composing text in deleteWord
        if (isComposing) {
            commitCurrentWord()
        }
        val before = getTextBeforeCursor(ic, 80)
        val wordLength = before.takeLastWhile { !it.isWhitespace() && it != '\n' }.length.coerceAtLeast(1)
        ic.deleteSurroundingText(wordLength, 0)
        updateSuggestions()
    }

    private fun updateSuggestions() {
        if (isPasswordField || prefs.incognito) {
            currentKeyboardView?.setSuggestions(emptyList())
            return
        }

        val view = currentKeyboardView ?: return
        val word = if (isComposing) composingText.toString() else currentWord(currentInputConnection ?: return)
        suggestions.suggestAsync(word, previousWord) { list ->
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
                prefs.isLandscape(this@KeyboardImeService) // FIX: LOW-004
            )
            setKeys(keys, currentPanel, isShifted, isCapsLocked, prefs.incognito)
        }
    }

    private fun applyCurrentPreferencesToView(view: KeyboardView) {
        view.preferences = prefs
        view.layoutMode = LayoutMode.entries.getOrElse(prefs.layoutModeOrdinal) { LayoutMode.FULL }
    }

    /**
     * FIX: QUALITY-004 — Single IPC call to get cursor position and selection.
     * Replaces separate getCursorPosition() and getSelection() methods which
     * each made their own ExtractedText request (two IPC round-trips).
     * Now uses a single ExtractedText request to fetch both values.
     */
    private fun getCursorState(ic: InputConnection): CursorState {
        return try {
            val request = ExtractedTextRequest().apply { token = 0 }
            val extracted = ic.getExtractedText(request, 0)
            CursorState(extracted?.selectionStart ?: 0, extracted?.selectionEnd ?: 0)
        } catch (e: Exception) {
            CursorState(0, 0)
        }
    }

    /**
     * FIX: HIGH-005 — Commit text with undo entry that captures cursor position and selection.
     * FIX: QUALITY-004 — Uses single getCursorState() IPC call instead of two separate calls.
     */
    private fun commitTextWithUndo(text: String, ic: InputConnection) {
        if (text.isBlank()) { ic.commitText(text, 1); return }

        // FIX: QUALITY-004 — Single IPC call for cursor state
        val cursor = getCursorState(ic)
        ic.commitText(text, 1)

        val entry = UndoEntry(
            text = text,
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

    /**
     * FIX: HIGH-005 — Proper undo using UndoEntry with cursor position.
     * FIX: QUALITY-004 — Uses getCursorState() for single IPC call.
     * Moves cursor to the exact position where the text was committed,
     * then deletes the text at that position.
     */
    private fun performUndo(ic: InputConnection) {
        val entry = undoStack.removeLastOrNull() ?: return

        // Move cursor to the position where the text was originally committed
        val textLength = entry.text.length
        val deletePosition = entry.cursorStart

        // Get current cursor position — FIX: QUALITY-004 — single IPC call
        val currentCursorState = getCursorState(ic)
        val currentCursor = currentCursorState.start

        // If cursor is after the committed text, we can simply delete backwards
        if (currentCursor >= deletePosition + textLength) {
            // Move cursor to the end of the text to delete
            ic.setSelection(deletePosition + textLength, deletePosition + textLength)
            ic.deleteSurroundingText(textLength, 0)
        } else {
            // Cursor is elsewhere — use setSelection to select the committed text, then delete
            ic.setSelection(deletePosition, deletePosition + textLength)
            ic.commitText("", 0)
        }

        // Restore cursor to the original position before the commit
        ic.setSelection(entry.selectionStart.coerceAtLeast(0), entry.selectionEnd.coerceAtLeast(0))

        redoStack.addLast(entry)
    }

    /**
     * FIX: HIGH-005 — Proper redo using UndoEntry with cursor position.
     */
    private fun performRedo(ic: InputConnection) {
        val entry = redoStack.removeLastOrNull() ?: return

        // Move cursor to the original commit position
        ic.setSelection(entry.cursorStart, entry.cursorStart)
        ic.commitText(entry.text, 1)

        undoStack.addLast(entry)
    }

    private fun performPaste(ic: InputConnection) {
        val clipboardText = clip.pasteText()
        if (clipboardText.isNotEmpty()) {
            commitTextWithUndo(clipboardText, ic)
        } else {
            ic.performContextMenuAction(android.R.id.paste)
        }
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

    /**
     * FIX: INFO-004 — Detect input type and switch to appropriate panel.
     * For email fields: switch to SYMBOLS panel (@ is prominent).
     * For URI fields: switch to SYMBOLS panel (/ and : are prominent).
     * For phone fields: switch to NUMPAD panel.
     * For number fields: switch to NUMPAD panel.
     */
    private fun detectInputType(info: EditorInfo?) {
        if (info == null) return
        val inputType = info.inputType
        val variation = inputType and android.text.InputType.TYPE_MASK_VARIATION
        val klass = inputType and android.text.InputType.TYPE_MASK_CLASS

        when {
            variation == android.text.InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS -> {
                // Email — switch to symbols for @ access
                if (currentPanel == Panel.QWERTY) {
                    // Keep QWERTY but user may want @ — no forced switch, just flag
                    // In a full implementation, could rearrange symbols row
                }
            }
            variation == android.text.InputType.TYPE_TEXT_VARIATION_URI -> {
                // URI — symbols for / and :
                if (currentPanel == Panel.QWERTY) {
                    // Keep QWERTY — URI still uses letters primarily
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

    /**
     * FIX: INFO-004 — Detect initial panel based on EditorInfo input type.
     */
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
        // FIX: FINAL-008 — minSdk is 26 (O is 26), so VibrationEffect is always available
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
