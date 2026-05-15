package com.keyboardmasterpiece.ime

import android.inputmethodservice.InputMethodService
import android.media.AudioManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import com.keyboardmasterpiece.engine.*

class KeyboardImeService : InputMethodService(), KeyboardView.Listener {
    private lateinit var prefs: UserPreferences
    private lateinit var suggestions: SuggestionEngine
    private lateinit var clip: ClipboardStore
    private var keyboardView: KeyboardView? = null
    private var panel = Panel.QWERTY
    private var shifted = false
    private var capsLock = false
    private var previousWord: String? = null
    private var undoStack = ArrayDeque<String>()
    private var redoStack = ArrayDeque<String>()
    private var enterAction = EditorInfo.IME_ACTION_NONE
    private var passwordMode = false

    override fun onCreate() {
        super.onCreate()
        prefs = UserPreferences(this)
        suggestions = SuggestionEngine(prefs)
        clip = ClipboardStore(this)
    }

    override fun onCreateInputView(): View {
        return KeyboardView(this).also {
            it.listener = this
            keyboardView = it
            refreshLayout()
        }
    }

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        enterAction = info?.imeOptions?.and(EditorInfo.IME_MASK_ACTION) ?: EditorInfo.IME_ACTION_NONE
        passwordMode = isPassword(info)
        panel = Panel.QWERTY
        shifted = shouldAutoCapitalize()
        refreshLayout()
    }

    override fun onDestroy() {
        suggestions.shutdown()
        keyboardView?.listener = null
        keyboardView = null
        super.onDestroy()
    }

    override fun onKey(key: KeyboardKey) {
        if (prefs.haptics) vibrateTick()
        if (prefs.sounds) playClick(key.code)
        if (key.isAction) handleAction(key.code) else commitPrintable(key.output)
    }

    override fun onLongPress(key: KeyboardKey) {
        when {
            key.code == KeyCodes.BACKSPACE -> deleteWord()
            key.code == KeyCodes.SPACE -> showInputPicker()
            key.alt.isNotEmpty() -> commitPrintable(key.alt.first().toString())
            key.output.isNotBlank() -> commitPrintable(key.output.uppercase())
        }
    }

    override fun onSwipeWord(word: String) {
        if (word.isNotBlank() && !passwordMode) {
            commitTextWithUndo(word)
            commitTextWithUndo(" ")
            suggestions.learn(word)
            updateSuggestions()
        }
    }

    override fun onSpaceDrag(deltaChars: Int) {
        val keyCode = if (deltaChars < 0) KeyEvent.KEYCODE_DPAD_LEFT else KeyEvent.KEYCODE_DPAD_RIGHT
        repeat(kotlin.math.abs(deltaChars).coerceAtMost(12)) { currentInputConnection?.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, keyCode)) }
    }

    override fun onSuggestion(text: String) {
        val word = currentWord()
        if (word.isNotEmpty()) currentInputConnection?.deleteSurroundingText(word.length, 0)
        commitTextWithUndo(text + " ")
        previousWord = text
        suggestions.learn(text)
        updateSuggestions()
    }

    private fun handleAction(code: Int) {
        when (code) {
            KeyCodes.SHIFT -> { if (shifted) capsLock = !capsLock; shifted = !shifted || capsLock; refreshLayout() }
            KeyCodes.BACKSPACE -> { currentInputConnection?.deleteSurroundingText(1, 0); updateSuggestions() }
            KeyCodes.ENTER -> enter()
            KeyCodes.SPACE -> space()
            KeyCodes.SYMBOLS -> { panel = Panel.SYMBOLS; refreshLayout() }
            KeyCodes.ABC -> { panel = Panel.QWERTY; refreshLayout() }
            KeyCodes.EMOJI -> { panel = Panel.EMOJI; refreshLayout() }
            KeyCodes.CLIPBOARD -> { panel = Panel.CLIPBOARD; refreshLayout() }
            KeyCodes.NUMPAD -> { panel = Panel.NUMPAD; refreshLayout() }
            KeyCodes.EDIT -> { panel = Panel.EDIT; refreshLayout() }
            KeyCodes.LANGUAGE -> showInputPicker()
            KeyCodes.VOICE -> showInputPicker()
            KeyCodes.SETTINGS -> showSettings()
            KeyCodes.MODE -> cycleLayoutMode()
            KeyCodes.THEME -> { prefs.darkTheme = !prefs.darkTheme; refreshLayout() }
            KeyCodes.INCOGNITO -> { prefs.incognito = !prefs.incognito; refreshLayout() }
            KeyCodes.UNDO -> undo()
            KeyCodes.REDO -> redo()
            KeyCodes.COPY -> copySelection(false)
            KeyCodes.CUT -> copySelection(true)
            KeyCodes.PASTE -> paste()
            KeyCodes.SELECT_ALL -> currentInputConnection?.performContextMenuAction(android.R.id.selectAll)
            KeyCodes.LEFT -> sendDpad(KeyEvent.KEYCODE_DPAD_LEFT)
            KeyCodes.RIGHT -> sendDpad(KeyEvent.KEYCODE_DPAD_RIGHT)
            KeyCodes.UP -> sendDpad(KeyEvent.KEYCODE_DPAD_UP)
            KeyCodes.DOWN -> sendDpad(KeyEvent.KEYCODE_DPAD_DOWN)
        }
    }

    private fun commitPrintable(text: String) {
        if (text.isEmpty()) return
        val out = if (text.length == 1 && text[0].isLetter() && (shifted || capsLock)) text.uppercase() else text
        commitTextWithUndo(out)
        if (shifted && !capsLock) shifted = false
        if (out.any { it.isWhitespace() || it in ".,!?" }) {
            val before = getBefore(64).trim().split(Regex("\\s+")).lastOrNull().orEmpty()
            if (before.length > 2) { previousWord = before; suggestions.learn(before) }
        }
        updateSuggestions()
        refreshLayout()
    }

    private fun space() {
        val before = getBefore(2)
        val word = currentWord()
        val correction = if (!passwordMode) suggestions.autocorrect(word) else null
        if (correction != null) {
            currentInputConnection?.deleteSurroundingText(word.length, 0)
            commitTextWithUndo(correction)
        }
        if (before == "  ") return
        if (before.endsWith(" ") && shouldDoubleSpacePeriod()) {
            currentInputConnection?.deleteSurroundingText(1, 0)
            commitTextWithUndo(". ")
        } else commitTextWithUndo(" ")
        shifted = shouldAutoCapitalize()
        updateSuggestions()
    }

    private fun enter() {
        if (enterAction != EditorInfo.IME_ACTION_NONE) currentInputConnection?.performEditorAction(enterAction)
        else currentInputConnection?.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER))
    }

    private fun deleteWord() {
        val before = getBefore(80)
        val n = before.takeLastWhile { !it.isWhitespace() }.length.coerceAtLeast(1)
        currentInputConnection?.deleteSurroundingText(n, 0)
        updateSuggestions()
    }

    private fun updateSuggestions() {
        if (passwordMode || prefs.incognito) { keyboardView?.setSuggestions(emptyList()); return }
        suggestions.suggestAsync(currentWord(), previousWord) { list -> keyboardView?.post { keyboardView?.setSuggestions(list) } }
    }

    private fun refreshLayout() {
        keyboardView?.apply {
            preferences = prefs
            layoutMode = LayoutMode.entries[prefs.layoutModeOrdinal.coerceIn(0, LayoutMode.entries.lastIndex)]
            setKeys(KeyboardLayoutFactory.layout(panel, shifted, capsLock, prefs.numberRow), panel, shifted, capsLock, prefs.incognito)
        }
        updateSuggestions()
    }

    private fun commitTextWithUndo(text: String) { currentInputConnection?.commitText(text, 1); undoStack.addLast(text); if (undoStack.size > 64) undoStack.removeFirst(); redoStack.clear(); if (!passwordMode) clip.remember(text) }
    private fun undo() { val s = undoStack.removeLastOrNull() ?: return; currentInputConnection?.deleteSurroundingText(s.length, 0); redoStack.addLast(s) }
    private fun redo() { val s = redoStack.removeLastOrNull() ?: return; currentInputConnection?.commitText(s, 1); undoStack.addLast(s) }
    private fun copySelection(cut: Boolean) { currentInputConnection?.performContextMenuAction(if (cut) android.R.id.cut else android.R.id.copy) }
    private fun paste() { val t = clip.pasteText(); if (t.isNotEmpty()) commitTextWithUndo(t) else currentInputConnection?.performContextMenuAction(android.R.id.paste) }
    private fun sendDpad(key: Int) = currentInputConnection?.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, key))
    private fun currentWord(): String = getBefore(64).takeLastWhile { it.isLetter() || it == '\'' }
    private fun getBefore(n: Int): String = currentInputConnection?.getTextBeforeCursor(n, 0)?.toString().orEmpty()
    private fun shouldAutoCapitalize(): Boolean { val t = getBefore(80).trimEnd(); return t.isEmpty() || t.endsWith('.') || t.endsWith('!') || t.endsWith('?') }
    private fun shouldDoubleSpacePeriod(): Boolean = getBefore(3).matches(Regex(".*[A-Za-z] "))
    private fun cycleLayoutMode() { prefs.layoutModeOrdinal = (prefs.layoutModeOrdinal + 1) % LayoutMode.entries.size; refreshLayout() }
    private fun showInputPicker() { (getSystemService(INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager).showInputMethodPicker() }
    private fun showSettings() { val i = packageManager.getLaunchIntentForPackage(packageName); i?.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK); if (i != null) startActivity(i) }
    private fun isPassword(info: EditorInfo?): Boolean { val v = (info?.inputType ?: 0) and android.text.InputType.TYPE_MASK_VARIATION; return v in setOf(android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD, android.text.InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD, android.text.InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD, android.text.InputType.TYPE_NUMBER_VARIATION_PASSWORD) }
    private fun vibrateTick() { val vib = getSystemService(VIBRATOR_SERVICE) as Vibrator; if (Build.VERSION.SDK_INT >= 26) vib.vibrate(VibrationEffect.createOneShot(8, VibrationEffect.DEFAULT_AMPLITUDE)) else @Suppress("DEPRECATION") vib.vibrate(8) }
    private fun playClick(code: Int) { (getSystemService(AUDIO_SERVICE) as AudioManager).playSoundEffect(if (code == KeyCodes.SPACE) AudioManager.FX_KEYPRESS_SPACEBAR else AudioManager.FX_KEYPRESS_STANDARD, .25f) }
}
