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
import android.view.inputmethod.InputConnection
import android.view.inputmethod.InputMethodManager
import androidx.core.view.inputmethod.InputConnectionCompat
import androidx.core.view.inputmethod.InputContentInfoCompat
import com.keyboardmasterpiece.core.InputManager
import com.keyboardmasterpiece.engine.*
import com.keyboardmasterpiece.settings.SettingsActivity
import kotlin.math.max

import com.keyboardmasterpiece.core.CursorState
import com.keyboardmasterpiece.core.UndoEntry

private const val TAG = "KeyboardImeService"

data class FilePreviewInfo(
    val uri: Uri,
    val mimeType: String,
    val fileName: String,
    val fileSize: Long
)

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

class KeyboardImeService : InputMethodService(), KeyboardView.Listener {

    private lateinit var prefs: UserPreferences
    private lateinit var suggestions: SuggestionEngine
    private lateinit var clip: ClipboardStore
    lateinit var inputManager: InputManager

    private var unlockReceiver: BroadcastReceiver? = null
    private var currentKeyboardView: KeyboardView? = null

    private var currentPanel = Panel.QWERTY
    private var isShifted = false
    private var isCapsLocked = false
    private var lastEditorAction = EditorInfo.IME_ACTION_NONE
    private var isPasswordField = false
    private var previousWord: String? = null
    private var previousWord2: String? = null

    private var currentEditorInfo: EditorInfo? = null

    private var lastShiftTapTime = 0L
    private val SHIFT_DOUBLE_TAP_THRESHOLD = 300L

    private var lastSpaceTapTime = 0L
    private val SPACE_DOUBLE_TAP_THRESHOLD = 300L

    private val fileSenderHandler = Handler(Looper.getMainLooper())
    private var pendingFilePreviewCallback: ((FilePreviewInfo?) -> Unit)? = null

    private val chunkedPasteHandler = Handler(Looper.getMainLooper())
    private var isChunkedPasteInProgress = false

    private var emojiCategory = EmojiCategory.SMILEYS
    private val recentEmojis = mutableListOf<String>()
    private val MAX_RECENT_EMOJIS = 20

    override fun onCreate() {
        super.onCreate()
        KeyboardImeServiceHolder.instance = this

        prefs = UserPreferences.createBootSafe(this)
        suggestions = SuggestionEngine(prefs)
        clip = ClipboardStore(this)

        inputManager = InputManager(
            hapticCallback = { vibrateTick() },
            learnCallback = { word -> suggestions.learn(word) }
        )

        checkDeviceUnlockedAndUpgradePrefs()

        currentPanel = try {
            Panel.valueOf(prefs.lastLayoutPanel)
        } catch (_: Exception) {
            Panel.QWERTY
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            unlockReceiver = object : BroadcastReceiver() {
                override fun onReceive(context: Context?, intent: Intent?) {
                    if (intent?.action == Intent.ACTION_USER_UNLOCKED) {
                        upgradeToCredentialPrefs()
                    }
                }
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                registerReceiver(unlockReceiver, IntentFilter(Intent.ACTION_USER_UNLOCKED), Context.RECEIVER_NOT_EXPORTED)
            } else {
                registerReceiver(unlockReceiver, IntentFilter(Intent.ACTION_USER_UNLOCKED))
            }
        }
    }

    private fun checkDeviceUnlockedAndUpgradePrefs() {
        val km = getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
        val unlocked = !km.isDeviceLocked
        if (unlocked) {
            upgradeToCredentialPrefs()
        }
    }

    private fun upgradeToCredentialPrefs() {
        try {
            prefs = UserPreferences.create(this)
            currentKeyboardView = null
            suggestions.shutdown()
            suggestions = SuggestionEngine(prefs)
        } catch (_: Exception) {
            // keep using boot-safe prefs
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

        if (!restarting) {
            val ic = currentInputConnection
            isShifted = if (ic != null) inputManager.shouldCapitalizeNextWord(ic) else false
            isCapsLocked = false
            currentPanel = detectInitialPanel(info)
            inputManager.clearState()
            previousWord = null
            previousWord2 = null
        }

        detectInputType(info)
        refreshKeyboardLayout()
        updateSuggestions()
    }

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        currentKeyboardView?.listener = this
        refreshKeyboardLayout()
    }

    override fun onFinishInput() {
        super.onFinishInput()
        val ic = currentInputConnection
        if (ic != null) {
            inputManager.commitCurrentWord(ic)
        }
        currentEditorInfo = null
    }

    override fun onFinishInputView(finishingInput: Boolean) {
        super.onFinishInputView(finishingInput)
        val ic = currentInputConnection
        if (ic != null) {
            inputManager.commitCurrentWord(ic)
        }
    }

    override fun onDestroy() {
        unlockReceiver?.let {
            try { unregisterReceiver(it) } catch (_: Exception) { }
            unlockReceiver = null
        }

        suggestions.shutdown()
        prefs.lastLayoutPanel = currentPanel.name
        inputManager.clearState()

        clip.clearLargeClipCache()
        chunkedPasteHandler.removeCallbacksAndMessages(null)
        isChunkedPasteInProgress = false

        fileSenderHandler.removeCallbacksAndMessages(null)
        pendingFilePreviewCallback = null
        KeyboardImeServiceHolder.instance = null

        super.onDestroy()
    }

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
        val ic = currentInputConnection
        if (ic != null && inputManager.isComposing && candidatesStart >= 0 && candidatesEnd >= 0) {
            if (newSelStart < candidatesStart || newSelEnd > candidatesEnd) {
                inputManager.commitCurrentWord(ic)
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
            clip.clearLargeClipCache()
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
                inputManager.commitPrintable(ic, key.output, isShifted, isCapsLocked) {
                    refreshKeyboardLayout()
                    updateSuggestions()
                }
            }
        } finally {
            ic.endBatchEdit()
        }
    }

    override fun onLongPress(key: KeyboardKey) {
        val ic = currentInputConnection ?: return
        when {
            key.code == KeyCodes.BACKSPACE -> {
                val sel = inputManager.getCursorState(ic)
                if (sel.start != sel.end) {
                    ic.commitText("", 0)
                } else {
                    inputManager.deleteWord(ic)
                }
            }
            key.code == KeyCodes.SPACE -> showInputMethodPicker()
            key.code == KeyCodes.EMOJI -> showInputMethodPicker()
            key.alt.isNotEmpty() -> {
                inputManager.commitCurrentWord(ic)
                inputManager.commitTextWithUndo(ic, key.alt.first().toString())
            }
            key.output.isNotBlank() -> {
                inputManager.commitCurrentWord(ic)
                inputManager.commitTextWithUndo(ic, key.output.uppercase())
            }
        }
    }

    override fun onSwipeWord(word: String) {
        if (word.isNotBlank() && !isPasswordField) {
            val ic = currentInputConnection ?: return
            ic.beginBatchEdit()
            try {
                inputManager.commitCurrentWord(ic)
                inputManager.commitTextWithUndo(ic, word)
                inputManager.commitTextWithUndo(ic, " ")
                suggestions.learn(word)
                previousWord2 = previousWord
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
            inputManager.sendKeyEvent(ic, KeyEvent.ACTION_DOWN, keyCode)
            inputManager.sendKeyEvent(ic, KeyEvent.ACTION_UP, keyCode)
        }
    }

    override fun onBackspaceDrag(deltaWords: Int) {
        val ic = currentInputConnection ?: return
        ic.beginBatchEdit()
        try {
            for (i in 0 until deltaWords) {
                inputManager.deleteWord(ic)
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
            inputManager.commitCurrentWord(ic)
            val current = inputManager.currentWord(ic)
            if (current.isNotEmpty()) {
                ic.deleteSurroundingText(current.length, 0)
            }
            inputManager.commitTextWithUndo(ic, text + " ")
            previousWord2 = previousWord
            previousWord = text
            suggestions.learn(text)
        } finally {
            ic.endBatchEdit()
        }
        updateSuggestions()
    }

    override fun onFilePicked(uri: Uri, mimeType: String) {
        sendFileViaInputConnection(uri, mimeType)
    }

    private fun handleActionKey(key: KeyboardKey, ic: InputConnection) {
        val code = key.code
        when (code) {
            KeyCodes.SHIFT -> handleShift()
            KeyCodes.BACKSPACE -> handleBackspace(ic)
            KeyCodes.ENTER -> {
                inputManager.commitCurrentWord(ic)
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
                ThemePalette.cycle(prefs)
                refreshKeyboardLayout()
            }
            KeyCodes.INCOGNITO -> {
                prefs.incognito = !prefs.incognito
                refreshKeyboardLayout()
                updateSuggestions()
            }
            KeyCodes.UNDO -> inputManager.performUndo(ic)
            KeyCodes.REDO -> inputManager.performRedo(ic)
            KeyCodes.COPY -> {
                ic.performContextMenuAction(android.R.id.copy)
                if (currentPanel == Panel.CLIPBOARD) refreshKeyboardLayout()
            }
            KeyCodes.CUT -> {
                ic.performContextMenuAction(android.R.id.cut)
                if (currentPanel == Panel.CLIPBOARD) refreshKeyboardLayout()
            }
            KeyCodes.PASTE -> performPaste(ic)
            KeyCodes.SELECT_ALL -> ic.performContextMenuAction(android.R.id.selectAll)
            KeyCodes.LEFT -> inputManager.sendKeyEvent(ic, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_LEFT)
            KeyCodes.RIGHT -> inputManager.sendKeyEvent(ic, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_RIGHT)
            KeyCodes.UP -> inputManager.sendKeyEvent(ic, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_UP)
            KeyCodes.DOWN -> inputManager.sendKeyEvent(ic, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_DOWN)
            KeyCodes.PHOTO_PICKER -> launchPhotoPicker()
            KeyCodes.FILE_PICKER -> launchFilePicker()

            KeyCodes.EMOJI_CATEGORY_SMILEYS -> { emojiCategory = EmojiCategory.SMILEYS; refreshKeyboardLayout() }
            KeyCodes.EMOJI_CATEGORY_GESTURES -> { emojiCategory = EmojiCategory.GESTURES; refreshKeyboardLayout() }
            KeyCodes.EMOJI_CATEGORY_ANIMALS -> { emojiCategory = EmojiCategory.ANIMALS; refreshKeyboardLayout() }
            KeyCodes.EMOJI_CATEGORY_FOOD -> { emojiCategory = EmojiCategory.FOOD; refreshKeyboardLayout() }
            KeyCodes.EMOJI_CATEGORY_ACTIVITIES -> { emojiCategory = EmojiCategory.ACTIVITIES; refreshKeyboardLayout() }
            KeyCodes.EMOJI_CATEGORY_TRAVEL -> { emojiCategory = EmojiCategory.TRAVEL; refreshKeyboardLayout() }
            KeyCodes.EMOJI_CATEGORY_OBJECTS -> { emojiCategory = EmojiCategory.OBJECTS; refreshKeyboardLayout() }
            KeyCodes.EMOJI_CATEGORY_SYMBOLS -> { emojiCategory = EmojiCategory.SYMBOLS; refreshKeyboardLayout() }
            KeyCodes.EMOJI_CATEGORY_RECENT -> { emojiCategory = EmojiCategory.RECENT; refreshKeyboardLayout() }

            KeyCodes.CLIP_ITEM -> {
                val clipText = key.output
                if (clipText.isNotEmpty()) {
                    inputManager.commitCurrentWord(ic)
                    if (clipText.length <= ClipboardStore.CHUNK_SIZE) {
                        inputManager.commitTextWithUndo(ic, clipText)
                    } else {
                        performChunkedPaste(clipText, ic)
                    }
                }
                refreshKeyboardLayout()
            }
            KeyCodes.CLIP_PIN -> {
                val idx = key.output.toIntOrNull()
                if (idx != null) {
                    clip.togglePin(idx)
                }
                refreshKeyboardLayout()
            }
            KeyCodes.CLIP_DELETE -> {
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
            KeyCodes.CLIP_ACTION_URL -> {
                try {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(key.output)).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    startActivity(intent)
                } catch (_: Exception) {}
            }
            KeyCodes.CLIP_ACTION_EMAIL -> {
                try {
                    val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:${key.output}")).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    startActivity(intent)
                } catch (_: Exception) {}
            }
            KeyCodes.CLIP_ACTION_PHONE -> {
                try {
                    val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:${key.output}")).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    startActivity(intent)
                } catch (_: Exception) {}
            }

            KeyCodes.TOOLBAR_CLIPBOARD -> { currentPanel = Panel.CLIPBOARD; refreshKeyboardLayout() }
            KeyCodes.TOOLBAR_SETTINGS -> launchSettings()
            KeyCodes.TOOLBAR_THEME -> {
                ThemePalette.cycle(prefs)
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

    private fun handleShift() {
        val now = System.currentTimeMillis()
        val timeSinceLastTap = now - lastShiftTapTime
        lastShiftTapTime = now

        if (timeSinceLastTap < SHIFT_DOUBLE_TAP_THRESHOLD && isShifted && !isCapsLocked) {
            isCapsLocked = true
            isShifted = true
        } else if (isCapsLocked) {
            isCapsLocked = false
            isShifted = false
        } else {
            isShifted = !isShifted
            isCapsLocked = false
        }
        refreshKeyboardLayout()
    }

    private fun handleBackspace(ic: InputConnection) {
        val sel = inputManager.getCursorState(ic)
        if (sel.start != sel.end) {
            ic.commitText("", 0)
            updateSuggestions()
            refreshKeyboardLayout()
            return
        }

        inputManager.robustDelete(ic)
        updateSuggestions()
        refreshKeyboardLayout()
    }

    private fun handleSpace(ic: InputConnection) {
        val now = System.currentTimeMillis()
        val timeSinceLastTap = now - lastSpaceTapTime
        lastSpaceTapTime = now

        if (timeSinceLastTap < SPACE_DOUBLE_TAP_THRESHOLD) {
            val before = inputManager.getTextBeforeCursor(ic, 2)
            if (before.isNotEmpty() && before.last() == ' ') {
                ic.deleteSurroundingText(1, 0)
                inputManager.commitTextWithUndo(ic, ". ")
            } else {
                inputManager.commitTextWithUndo(ic, " ")
            }
            lastSpaceTapTime = 0L
            isShifted = inputManager.shouldCapitalizeNextWord(ic)
            updateSuggestions()
            refreshKeyboardLayout()
            return
        }

        val word = if (inputManager.isComposing) inputManager.composingText.toString() else inputManager.currentWord(ic)
        var correction: String? = null
        if (!isPasswordField && !prefs.incognito) {
            correction = suggestions.autocorrect(word)
        }

        if (inputManager.isComposing) {
            if (correction != null && correction != word) {
                inputManager.composingText.clear()
                inputManager.composingText.append(correction)
                ic.setComposingText(correction, 1)
            }
            inputManager.commitCurrentWord(ic)
        } else {
            if (correction != null && correction != word) {
                ic.deleteSurroundingText(word.length, 0)
                inputManager.commitTextWithUndo(ic, correction)
            }
        }

        if (correction == null || correction == word) {
            val before = inputManager.getTextBeforeCursor(ic, 3)
            if (before.endsWith("  ") && shouldDoubleSpaceToPeriod()) {
                ic.deleteSurroundingText(2, 0)
                inputManager.commitTextWithUndo(ic, ". ")
            } else {
                inputManager.commitTextWithUndo(ic, " ")
            }
        }
        isShifted = inputManager.shouldCapitalizeNextWord(ic)
        updateSuggestions()
        refreshKeyboardLayout()
    }

    private fun performEditorAction(ic: InputConnection) {
        val info = currentEditorInfo
        val action = info?.imeOptions?.and(EditorInfo.IME_MASK_ACTION) ?: EditorInfo.IME_ACTION_NONE

        if (action != EditorInfo.IME_ACTION_NONE) {
            ic.performEditorAction(action)
        } else {
            inputManager.sendKeyEvent(ic, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER)
            inputManager.sendKeyEvent(ic, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_ENTER)
        }
    }

    private fun addRecentEmoji(emoji: String) {
        if (emoji.isBlank()) return
        recentEmojis.remove(emoji)
        recentEmojis.add(0, emoji)
        while (recentEmojis.size > MAX_RECENT_EMOJIS) {
            recentEmojis.removeAt(recentEmojis.lastIndex)
        }
    }

    private fun performPaste(ic: InputConnection) {
        if (isChunkedPasteInProgress) {
            chunkedPasteHandler.removeCallbacksAndMessages(null)
            isChunkedPasteInProgress = false
        }

        val clipboardText = clip.pasteText()
        if (clipboardText.isNotEmpty()) {
            if (clipboardText.length <= ClipboardStore.CHUNK_SIZE) {
                inputManager.commitTextWithUndo(ic, clipboardText)
            } else {
                performChunkedPaste(clipboardText, ic)
            }
        } else {
            ic.performContextMenuAction(android.R.id.paste)
        }
    }

    private fun performChunkedPaste(text: String, ic: InputConnection) {
        isChunkedPasteInProgress = true
        inputManager.commitCurrentWord(ic)
        val cursor = inputManager.getCursorState(ic)
        val chunks = text.chunked(ClipboardStore.CHUNK_SIZE)

        ic.beginBatchEdit()
        try {
            for (chunk in chunks) {
                ic.commitText(chunk, 1)
            }
        } finally {
            ic.endBatchEdit()
        }

        val entry = UndoEntry(
            text = text,
            cursorStart = cursor.start,
            selectionStart = cursor.start,
            selectionEnd = cursor.end
        )
        // Delegate state tracking to inputManager undo stack
        inputManager.commitTextWithUndo(ic, "") // just to sync any stack ops if needed
        isChunkedPasteInProgress = false
    }

    private fun launchPhotoPicker() {
        try {
            startFilePickerActivity("image/" + "*")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to launch photo picker", e)
            showErrorFeedback("Unable to open photo picker")
        }
    }

    private fun launchFilePicker() {
        try {
            startFilePickerActivity("${'*'}/${'*'}")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to launch file picker", e)
            showErrorFeedback("Unable to open file picker")
        }
    }

    private fun startFilePickerActivity(mimeType: String) {
        val intent = Intent(this, FilePickerActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_ACTIVITY_NO_HISTORY)
            putExtra(FilePickerActivity.EXTRA_MIME_TYPE, mimeType)
        }
        startActivity(intent)
    }

    fun sendFileViaInputConnection(uri: Uri, mimeType: String) {
        val ic = currentInputConnection
        if (ic == null) {
            showErrorFeedback("No active input field")
            return
        }

        try {
            contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        } catch (_: Exception) {}

        val fileInfo = getFileInfo(uri, mimeType)
        if (fileInfo == null) {
            showErrorFeedback("Unable to read file")
            return
        }

        val editorInfo = currentEditorInfo
        val supportedMimeTypes: Array<String> = if (editorInfo != null) {
            editorInfo.contentMimeTypes ?: emptyArray()
        } else {
            emptyArray()
        }

        val isSupported = supportedMimeTypes.isEmpty() || supportedMimeTypes.any { supported ->
            mimeTypeMatches(supported, mimeType)
        }

        if (isSupported) {
            commitFileContent(uri, mimeType, fileInfo, ic)
        } else {
            fallbackToShareIntent(uri, mimeType, fileInfo)
        }
    }

    private fun commitFileContent(uri: Uri, mimeType: String, fileInfo: FilePreviewInfo, ic: InputConnection) {
        try {
            val inputContentInfo = InputContentInfoCompat(
                uri,
                ClipDescription(fileInfo.fileName, arrayOf(mimeType)),
                null
            )

            val flags = InputConnectionCompat.INPUT_CONTENT_GRANT_READ_URI_PERMISSION
            val committed = InputConnectionCompat.commitContent(ic, currentEditorInfo ?: return, inputContentInfo, flags, null)

            if (!committed) {
                fallbackToShareIntent(uri, mimeType, fileInfo)
            }
        } catch (e: Exception) {
            fallbackToShareIntent(uri, mimeType, fileInfo)
        }
    }

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
            showErrorFeedback("This app doesn't support receiving files directly.")
        }
    }

    private fun getFileInfo(uri: Uri, mimeType: String): FilePreviewInfo? {
        return try {
            var fileName = "Unknown"
            var fileSize = 0L

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
            null
        }
    }

    private fun showErrorFeedback(message: String) {
        currentKeyboardView?.post {
            android.widget.Toast.makeText(this@KeyboardImeService, message, android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    private fun updateSuggestions() {
        if (isPasswordField || prefs.incognito) {
            currentKeyboardView?.setSuggestions(emptyList())
            return
        }

        val view = currentKeyboardView ?: return
        val ic = currentInputConnection ?: return
        val word = if (inputManager.isComposing) inputManager.composingText.toString() else inputManager.currentWord(ic)
        suggestions.setSentenceStart(inputManager.shouldCapitalizeNextWord(ic))
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
                prefs.isLandscape(this@KeyboardImeService),
                emojiCategory = emojiCategory,
                recentEmojis = recentEmojis.toList(),
                clipHistory = clip.history(),
                pinnedIndices = clip.pinnedIndices()
            )
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

    private fun shouldDoubleSpaceToPeriod(): Boolean {
        val ic = currentInputConnection ?: return false
        val before = inputManager.getTextBeforeCursor(ic, 5)
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

    private fun detectInputType(info: EditorInfo?) {
        if (info == null) return
        val inputType = info.inputType
        val variation = inputType and android.text.InputType.TYPE_MASK_VARIATION
        val klass = inputType and android.text.InputType.TYPE_MASK_CLASS

        when {
            variation == android.text.InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS -> {
                // Email field
            }
            variation == android.text.InputType.TYPE_TEXT_VARIATION_URI -> {
                // URL field
            }
            klass == android.text.InputType.TYPE_CLASS_PHONE -> {
                currentPanel = Panel.NUMPAD
            }
            klass == android.text.InputType.TYPE_CLASS_NUMBER -> {
                currentPanel = Panel.NUMPAD
            }
        }
    }

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
