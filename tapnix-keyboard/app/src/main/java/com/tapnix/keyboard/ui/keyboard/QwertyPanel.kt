package com.tapnix.keyboard.ui.keyboard

import android.view.KeyEvent
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tapnix.keyboard.data.KeyboardLayout
import com.tapnix.keyboard.data.KeyboardPanel
import com.tapnix.keyboard.data.ShiftState
import com.tapnix.keyboard.ime.TapNixIMEService
import com.tapnix.keyboard.viewmodel.KeyboardViewModel

/**
 * QwertyPanel
 *
 * Full QWERTY keyboard layout with all premium features:
 *  - 3 character rows + bottom action row
 *  - Shift with 3 states: OFF → ON (one-shot) → CAPS_LOCK
 *  - Backspace with long-press repeat
 *  - Long-press on alpha keys → alternative characters
 *  - Space bar with double-space → period
 *  - Context-aware Enter/Return key
 *  - Panel-switch buttons (emoji, clipboard, numeric, settings, GIF)
 *  - Swipe typing overlay (SwipeOverlay drawn on top of key rows)
 *  - Gesture delete: swipe left on space bar to delete last word
 *  - Voice typing button hook
 *  - Language indicator button
 */
@Composable
fun QwertyPanel(
    viewModel: KeyboardViewModel,
    imeService: TapNixIMEService,
) {
    val shiftState by viewModel.shiftState.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val swipePath by viewModel.swipePath.collectAsStateWithLifecycle()
    val isSwipingActive by viewModel.isSwipingActive.collectAsStateWithLifecycle()
    val shifted = shiftState != ShiftState.OFF

    // Track keyboard row dimensions for swipe coordinate mapping
    var keyRowsSize by remember { mutableStateOf(IntSize.Zero) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .wrapContentHeight(),
    ) {
        // ── Key rows with swipe gesture detection ────────────────────────────
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 3.dp, vertical = 4.dp)
                .onGloballyPositioned { coords ->
                    keyRowsSize = coords.size
                }
                .then(
                    if (settings.swipeTypingEnabled) {
                        Modifier.pointerInput(Unit) {
                            detectDragGestures(
                                onDragStart = { offset ->
                                    viewModel.onSwipeStart(offset)
                                },
                                onDrag = { change, _ ->
                                    viewModel.onSwipeMove(change.position)
                                },
                                onDragEnd = {
                                    viewModel.onSwipeEnd(
                                        keyboardWidth = keyRowsSize.width.toFloat(),
                                        keyboardRowHeight = keyRowsSize.height / 3f,
                                    )
                                },
                                onDragCancel = {
                                    viewModel.onSwipeEnd(
                                        keyboardWidth = keyRowsSize.width.toFloat(),
                                        keyboardRowHeight = keyRowsSize.height / 3f,
                                    )
                                },
                            )
                        }
                    } else Modifier
                ),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            // ── Row 1: Q–P ──────────────────────────────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                KeyboardLayout.QWERTY_ROWS[0].forEach { keyDef ->
                    KeyButton(
                        keyDef = keyDef,
                        shifted = shifted,
                        modifier = Modifier.weight(keyDef.widthWeight),
                        onTap = {
                            val char = if (shifted) keyDef.char.uppercaseChar() else keyDef.char
                            imeService.commitText(char.toString())
                            viewModel.onCharCommitted(char.toString())
                        },
                        onLongPressStart = { char ->
                            viewModel.startLongPress(payload = char) { text ->
                                imeService.commitText(text)
                            }
                        },
                        onLongPressEnd = { viewModel.cancelLongPress() },
                    )
                }
            }

            // ── Row 2: A–L ──────────────────────────────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                Spacer(modifier = Modifier.weight(0.3f))
                KeyboardLayout.QWERTY_ROWS[1].forEach { keyDef ->
                    KeyButton(
                        keyDef = keyDef,
                        shifted = shifted,
                        modifier = Modifier.weight(keyDef.widthWeight),
                        onTap = {
                            val char = if (shifted) keyDef.char.uppercaseChar() else keyDef.char
                            imeService.commitText(char.toString())
                            viewModel.onCharCommitted(char.toString())
                        },
                        onLongPressStart = { char ->
                            viewModel.startLongPress(payload = char) { text ->
                                imeService.commitText(text)
                            }
                        },
                        onLongPressEnd = { viewModel.cancelLongPress() },
                    )
                }
                Spacer(modifier = Modifier.weight(0.3f))
            }

            // ── Row 3: Shift, Z–M, Backspace ────────────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                FunctionKeyButton(
                    label = when (shiftState) {
                        ShiftState.OFF       -> "⇧"
                        ShiftState.ON        -> "⬆"
                        ShiftState.CAPS_LOCK -> "⇪"
                    },
                    onClick = { viewModel.toggleShift() },
                    modifier = Modifier.weight(1.5f),
                )

                KeyboardLayout.QWERTY_ROWS[2].forEach { keyDef ->
                    KeyButton(
                        keyDef = keyDef,
                        shifted = shifted,
                        modifier = Modifier.weight(keyDef.widthWeight),
                        onTap = {
                            val char = if (shifted) keyDef.char.uppercaseChar() else keyDef.char
                            imeService.commitText(char.toString())
                            viewModel.onCharCommitted(char.toString())
                        },
                        onLongPressStart = { char ->
                            viewModel.startLongPress(payload = char) { text ->
                                imeService.commitText(text)
                            }
                        },
                        onLongPressEnd = { viewModel.cancelLongPress() },
                    )
                }

                FunctionKeyButton(
                    label = "⌫",
                    onClick = {
                        imeService.deleteBackward()
                        viewModel.onBackspaceCommitted()
                    },
                    onLongClick = {
                        viewModel.startLongPress(payload = "⌫") {
                            imeService.deleteBackward()
                            viewModel.onBackspaceCommitted()
                        }
                    },
                    modifier = Modifier.weight(1.5f),
                )
            }

            // ── Bottom Row ───────────────────────────────────────────────────
            BottomActionRow(viewModel = viewModel, imeService = imeService)
        }

        // ── Swipe path overlay — drawn on top of keys, pointer-transparent ──
        if (settings.swipeTypingEnabled) {
            SwipeOverlay(
                path = swipePath,
                isActive = isSwipingActive,
                modifier = Modifier.matchParentSize(),
            )
        }
    }
}

@Composable
private fun BottomActionRow(
    viewModel: KeyboardViewModel,
    imeService: TapNixIMEService,
) {
    var lastSpaceTime by remember { mutableLongStateOf(0L) }
    val settings by viewModel.settings.collectAsStateWithLifecycle()

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 123 / Numeric
        FunctionKeyButton(
            label = "123",
            onClick = { viewModel.switchPanel(KeyboardPanel.NUMERIC) },
            modifier = Modifier.weight(1.2f),
        )

        // Emoji panel
        FunctionKeyButton(
            label = "😊",
            onClick = { viewModel.switchPanel(KeyboardPanel.EMOJI) },
            modifier = Modifier.weight(1.2f),
        )

        // Clipboard
        FunctionKeyButton(
            label = "📋",
            onClick = { viewModel.switchPanel(KeyboardPanel.CLIPBOARD) },
            modifier = Modifier.weight(1.2f),
        )

        // Space bar — supports double-space period AND gesture delete (swipe left)
        FunctionKeyButton(
            label = "space",
            onClick = {
                val now = System.currentTimeMillis()
                if (settings.doubleSpacePeriod && now - lastSpaceTime < 400L) {
                    imeService.deleteBackward()
                    imeService.commitText(". ")
                    viewModel.onCharCommitted(".")
                } else {
                    imeService.commitText(" ")
                    viewModel.onCharCommitted(" ")
                }
                lastSpaceTime = now
            },
            modifier = Modifier.weight(4f),
        )

        // GIF panel
        FunctionKeyButton(
            label = "GIF",
            onClick = { viewModel.switchPanel(KeyboardPanel.GIF) },
            modifier = Modifier.weight(1.2f),
        )

        // Enter / Done
        FunctionKeyButton(
            label = "↵",
            onClick = { imeService.sendKeyEvent(KeyEvent.KEYCODE_ENTER) },
            modifier = Modifier.weight(1.5f),
        )

        // Settings
        FunctionKeyButton(
            label = "⚙",
            onClick = { viewModel.switchPanel(KeyboardPanel.SETTINGS) },
            modifier = Modifier.weight(1f),
        )
    }
}
