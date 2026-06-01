package com.keyboardmasterpiece.core

import android.view.KeyEvent
import android.view.inputmethod.ExtractedTextRequest
import android.view.inputmethod.InputConnection
import com.keyboardmasterpiece.engine.ClipboardStore
import kotlin.collections.ArrayDeque

// Core Cursor state structure
data class CursorState(val start: Int, val end: Int)

// Core Undo/Redo entry structure
data class UndoEntry(
    val text: String,
    val actualTextLength: Int,
    val cursorStart: Int,
    val selectionStart: Int,
    val selectionEnd: Int
)

class InputManager(
    private val hapticCallback: () -> Unit,
    private val learnCallback: (String) -> Unit
) {
    val composingText = StringBuilder()
    var isComposing = false
        private set

    private val undoStack = ArrayDeque<UndoEntry>()
    private val redoStack = ArrayDeque<UndoEntry>()

    var hapticsEnabled: Boolean = true
    var incognitoEnabled: Boolean = false

    companion object {
        private const val UNDO_LIMIT = 40
        private val SENTENCE_ENDERS = setOf('.', '!', '?')
        private val WORD_BOUNDARY_CHARS = setOf(
            ' ', '.', ',', '!', '?', ':', ';', '@', '#', '$', '%', '&', '*',
            '(', ')', '-', '_', '=', '+', '[', ']', '{', '}', '|', '\\',
            '/', '<', '>', '\'', '"', '~', '^', '`', '\n', '\t', '€', '£',
            '¥', '₹', '₩', '₱', '₫', '₿'
        )
    }

    fun clearState() {
        composingText.clear()
        isComposing = false
        undoStack.clear()
        redoStack.clear()
    }

    fun getCursorState(ic: InputConnection): CursorState {
        return try {
            val request = ExtractedTextRequest().apply { token = 0 }
            val extracted = ic.getExtractedText(request, 0)
            CursorState(extracted?.selectionStart ?: 0, extracted?.selectionEnd ?: 0)
        } catch (e: Exception) {
            CursorState(0, 0)
        }
    }

    fun getTextBeforeCursor(ic: InputConnection, n: Int): String {
        return try {
            ic.getTextBeforeCursor(n, 0)?.toString() ?: ""
        } catch (e: Exception) {
            ""
        }
    }

    fun currentWord(ic: InputConnection): String {
        val before = getTextBeforeCursor(ic, 64)
        return before.takeLastWhile { it.isLetter() || it == '\'' || it == '-' }
    }

    fun safeCommitText(ic: InputConnection, text: String, finishComposing: Boolean = true) {
        try {
            if (finishComposing && isComposing) {
                ic.finishComposingText()
                isComposing = false
            }
            ic.commitText(text, 1)
        } catch (e: Exception) {
            for (char in text) {
                sendKeyEvent(ic, KeyEvent.ACTION_DOWN, char.code)
                sendKeyEvent(ic, KeyEvent.ACTION_UP, char.code)
            }
        }
    }

    fun updateComposingText(ic: InputConnection) {
        try {
            if (composingText.isNotEmpty()) {
                ic.setComposingText(composingText, 1)
                isComposing = true
            } else {
                ic.finishComposingText()
                isComposing = false
            }
        } catch (e: Exception) {
            isComposing = false
        }
    }

    fun commitCurrentWord(ic: InputConnection) {
        if (!isComposing || composingText.isEmpty()) return
        try {
            ic.finishComposingText()
        } catch (_: Exception) {}
        val word = composingText.toString()
        composingText.clear()
        isComposing = false
        if (word.length > 2 && !incognitoEnabled) {
            learnCallback(word)
        }
    }

    fun robustDelete(ic: InputConnection): Boolean {
        return try {
            if (isComposing && composingText.isNotEmpty()) {
                composingText.deleteCharAt(composingText.length - 1)
                updateComposingText(ic)
                true
            } else {
                ic.finishComposingText()
                isComposing = false
                val deleted = ic.deleteSurroundingText(1, 0)
                if (!deleted) {
                    sendKeyEvent(ic, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DEL)
                    sendKeyEvent(ic, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DEL)
                }
                true
            }
        } catch (e: Exception) {
            sendKeyEvent(ic, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DEL)
            sendKeyEvent(ic, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DEL)
            false
        }
    }

    fun deleteWord(ic: InputConnection) {
        if (isComposing) {
            commitCurrentWord(ic)
        }
        val before = getTextBeforeCursor(ic, 80)
        val wordLength = before.takeLastWhile { !it.isWhitespace() && it != '\n' }.length.coerceAtLeast(1)
        try {
            ic.deleteSurroundingText(wordLength, 0)
        } catch (e: Exception) {
            for (i in 0 until wordLength) {
                sendKeyEvent(ic, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DEL)
                sendKeyEvent(ic, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DEL)
            }
        }
    }

    fun commitTextWithUndo(ic: InputConnection, text: String) {
        if (text.isEmpty()) return
        val cursor = getCursorState(ic)
        safeCommitText(ic, text, finishComposing = true)

        val undoText = if (text.length > ClipboardStore.CHUNK_SIZE * 4) {
            text.substring(0, ClipboardStore.CHUNK_SIZE * 4) + "...[truncated]"
        } else {
            text
        }

        val entry = UndoEntry(
            text = undoText,
            actualTextLength = text.length,
            cursorStart = cursor.start,
            selectionStart = cursor.start,
            selectionEnd = cursor.end
        )
        pushUndo(entry)
    }

    fun pushUndo(entry: UndoEntry) {
        undoStack.addLast(entry)
        if (undoStack.size > UNDO_LIMIT) undoStack.removeFirst()
        redoStack.clear()
    }

    fun performUndo(ic: InputConnection) {
        val entry = undoStack.removeLastOrNull() ?: return
        val textLength = entry.actualTextLength
        val deletePosition = entry.cursorStart
        val currentCursorState = getCursorState(ic)
        val currentCursor = currentCursorState.start

        try {
            if (currentCursor >= deletePosition + textLength) {
                ic.setSelection(deletePosition + textLength, deletePosition + textLength)
                ic.deleteSurroundingText(textLength, 0)
            } else {
                ic.setSelection(deletePosition, deletePosition + textLength)
                ic.commitText("", 0)
            }
            ic.setSelection(entry.selectionStart.coerceAtLeast(0), entry.selectionEnd.coerceAtLeast(0))
        } catch (_: Exception) {}
        redoStack.addLast(entry)
    }

    fun performRedo(ic: InputConnection) {
        val entry = redoStack.removeLastOrNull() ?: return
        try {
            ic.setSelection(entry.cursorStart, entry.cursorStart)
            ic.commitText(entry.text, 1)
        } catch (_: Exception) {}
        undoStack.addLast(entry)
    }

    fun sendKeyEvent(ic: InputConnection, action: Int, keyCode: Int) {
        try {
            ic.sendKeyEvent(KeyEvent(action, keyCode))
        } catch (_: Exception) {}
    }

    fun shouldCapitalizeNextWord(ic: InputConnection): Boolean {
        val before = getTextBeforeCursor(ic, 8).trimEnd()
        if (before.isEmpty()) return true
        val lastChar = before.last()
        return lastChar in SENTENCE_ENDERS || lastChar == '\n'
    }

    fun handleAutoCloseCharacters(ic: InputConnection, output: String): Boolean {
        if (output.length != 1) return false
        val char = output[0]
        val closing = when (char) {
            '"' -> '"'
            '\'' -> '\''
            '(' -> ')'
            '[' -> ']'
            '{' -> '}'
            '<' -> '>'
            else -> return false
        }
        
        ic.beginBatchEdit()
        try {
            ic.commitText(output + closing, 1)
            val state = getCursorState(ic)
            ic.setSelection(state.start - 1, state.start - 1)
        } finally {
            ic.endBatchEdit()
        }
        return true
    }

    fun commitPrintable(ic: InputConnection, output: String, isShifted: Boolean, isCapsLocked: Boolean, onLayoutRefresh: () -> Unit) {
        if (output.isEmpty()) return

        if (handleAutoCloseCharacters(ic, output)) {
            onLayoutRefresh()
            return
        }

        val shouldUpper = (isShifted || isCapsLocked) && output.length == 1 && output[0].isLetter()
        val textToType = if (shouldUpper) output.uppercase() else output

        val isWordBoundary = textToType.any { it.isWhitespace() || it in WORD_BOUNDARY_CHARS }

        if (isWordBoundary) {
            commitCurrentWord(ic)
            safeCommitText(ic, textToType, finishComposing = false)
        } else {
            composingText.append(textToType)
            isComposing = true
            updateComposingText(ic)
        }
        onLayoutRefresh()
    }
}
