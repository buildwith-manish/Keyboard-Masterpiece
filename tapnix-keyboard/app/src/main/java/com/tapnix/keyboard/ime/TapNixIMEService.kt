package com.tapnix.keyboard.ime

import android.inputmethodservice.InputMethodService
import android.text.InputType
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.*
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.savedstate.*
import com.tapnix.keyboard.ui.keyboard.KeyboardRoot
import com.tapnix.keyboard.viewmodel.KeyboardViewModel
import kotlinx.coroutines.*

/**
 * TapNixIMEService
 *
 * Production-grade InputMethodService with full Compose lifecycle ownership.
 *
 * Implements:
 *  - LifecycleOwner       → correct Compose lifecycle signals
 *  - ViewModelStoreOwner  → ViewModel scoped to the IME service
 *  - SavedStateRegistryOwner → state restoration after process kill
 *
 * Handles:
 *  - Compose ViewTree owners set on both decor and input view
 *  - Memory-safe teardown (scope cancel, viewModelStore.clear)
 *  - Password field detection (skips clipboard, hides suggestions)
 *  - Orientation changes (input view is re-created by the system)
 *  - Android 12–15 compatibility via standard API surface
 *
 * Extended helpers:
 *  - deleteBackward(count)  → delete multiple characters (for autocorrect)
 *  - deleteWordBackward()   → delete last word (for gesture delete)
 *  - getSurroundingText()   → read preceding text (for grammar analysis)
 */
class TapNixIMEService :
    InputMethodService(),
    LifecycleOwner,
    ViewModelStoreOwner,
    SavedStateRegistryOwner {

    // ── Lifecycle ─────────────────────────────────────────────────────────────
    private val lifecycleRegistry by lazy { LifecycleRegistry(this) }
    override val lifecycle: Lifecycle get() = lifecycleRegistry

    // ── ViewModel Store ───────────────────────────────────────────────────────
    private val _viewModelStore = ViewModelStore()
    override val viewModelStore: ViewModelStore get() = _viewModelStore

    // ── SavedState Registry ───────────────────────────────────────────────────
    private val savedStateRegistryController = SavedStateRegistryController.create(this)
    override val savedStateRegistry: SavedStateRegistry
        get() = savedStateRegistryController.savedStateRegistry

    // ── Service Scope — tied to service lifetime ──────────────────────────────
    private val serviceScope = CoroutineScope(
        SupervisorJob() + Dispatchers.Main.immediate
    )

    // ── ViewModel ─────────────────────────────────────────────────────────────
    private val viewModel: KeyboardViewModel by lazy {
        ViewModelProvider(
            owner = this,
            factory = KeyboardViewModel.Factory(applicationContext),
        )[KeyboardViewModel::class.java]
    }

    // ── Input view reference ──────────────────────────────────────────────────
    private var inputView: ComposeView? = null

    // ─────────────────────────────────────────────────────────────────────────
    // Lifecycle: onCreate
    // ─────────────────────────────────────────────────────────────────────────
    override fun onCreate() {
        super.onCreate()
        savedStateRegistryController.performRestore(null)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Lifecycle: onCreateInputView
    //   Called each time the keyboard is shown (or on orientation change).
    //   Must wire ViewTree owners so Compose can resolve LifecycleOwner etc.
    // ─────────────────────────────────────────────────────────────────────────
    override fun onCreateInputView(): View {
        val composeView = ComposeView(this).apply {
            setContent {
                KeyboardRoot(
                    viewModel = viewModel,
                    imeService = this@TapNixIMEService,
                )
            }
        }

        // Wire the Compose tree owners
        window?.window?.decorView?.let { decor ->
            decor.setViewTreeLifecycleOwner(this)
            decor.setViewTreeViewModelStoreOwner(this)
            decor.setViewTreeSavedStateRegistryOwner(this)
        }
        composeView.setViewTreeLifecycleOwner(this)
        composeView.setViewTreeViewModelStoreOwner(this)
        composeView.setViewTreeSavedStateRegistryOwner(this)

        inputView = composeView
        return composeView
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Lifecycle: onStartInputView — keyboard becomes visible
    // ─────────────────────────────────────────────────────────────────────────
    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
        viewModel.onStartInput(info, restarting)
        detectAndApplyPasswordMode(info)
        if (!viewModel.isPasswordMode.value) {
            viewModel.clipboardEngine.registerClipboardListener()
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Lifecycle: onFinishInputView — keyboard hidden
    // ─────────────────────────────────────────────────────────────────────────
    override fun onFinishInputView(finishingInput: Boolean) {
        super.onFinishInputView(finishingInput)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
        viewModel.onFinishInput()
        viewModel.clipboardEngine.unregisterClipboardListener()
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Lifecycle: onDestroy
    // ─────────────────────────────────────────────────────────────────────────
    override fun onDestroy() {
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        serviceScope.cancel()
        _viewModelStore.clear()
        viewModel.clipboardEngine.dispose()
        inputView = null
        super.onDestroy()
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Input Connection helpers — called from Compose UI on the main thread
    // ─────────────────────────────────────────────────────────────────────────

    /** Commit a text string at the current cursor position. */
    fun commitText(text: String) {
        currentInputConnection?.commitText(text, 1)
    }

    /** Delete one character before the cursor. */
    fun deleteBackward() {
        currentInputConnection?.deleteSurroundingText(1, 0)
    }

    /**
     * Delete [count] characters before the cursor.
     * Used by autocorrect to replace a misspelled word.
     */
    fun deleteBackward(count: Int) {
        if (count <= 0) return
        currentInputConnection?.deleteSurroundingText(count, 0)
    }

    /**
     * Delete the entire word before the cursor (gesture-delete feature).
     * Reads up to 50 chars of preceding text, finds the last word boundary,
     * and removes everything from there to the cursor.
     */
    fun deleteWordBackward() {
        val ic = currentInputConnection ?: return
        val before = ic.getTextBeforeCursor(50, 0)?.toString() ?: return
        if (before.isEmpty()) return

        // Trim trailing spaces first
        val trimmed = before.trimEnd()
        val toDelete = if (trimmed.isEmpty()) {
            before.length
        } else {
            // Find the start of the last word
            val lastSpaceIdx = trimmed.lastIndexOf(' ')
            if (lastSpaceIdx < 0) trimmed.length else trimmed.length - lastSpaceIdx - 1
        }
        if (toDelete > 0) ic.deleteSurroundingText(toDelete, 0)
    }

    /**
     * Read [maxChars] of text before the cursor.
     * Used for grammar analysis and auto-capitalisation context.
     */
    fun getTextBeforeCursor(maxChars: Int = 100): String {
        return currentInputConnection?.getTextBeforeCursor(maxChars, 0)?.toString() ?: ""
    }

    fun sendKeyEvent(keyCode: Int, action: Int = KeyEvent.ACTION_DOWN) {
        currentInputConnection?.sendKeyEvent(KeyEvent(action, keyCode))
    }

    fun performEditorAction(actionId: Int) {
        currentInputConnection?.performEditorAction(actionId)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Memory pressure — release caches on low-memory signal
    // ─────────────────────────────────────────────────────────────────────────
    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (level >= TRIM_MEMORY_MODERATE) {
            viewModel.onTrimMemory(level)
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Password Field Detection
    // ─────────────────────────────────────────────────────────────────────────
    private fun detectAndApplyPasswordMode(info: EditorInfo?) {
        val inputType = info?.inputType ?: 0
        val variation = inputType and InputType.TYPE_MASK_VARIATION
        val isPassword = variation == InputType.TYPE_TEXT_VARIATION_PASSWORD ||
                variation == InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD ||
                variation == InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD ||
                variation == InputType.TYPE_NUMBER_VARIATION_PASSWORD
        viewModel.setPasswordMode(isPassword)
    }
}
