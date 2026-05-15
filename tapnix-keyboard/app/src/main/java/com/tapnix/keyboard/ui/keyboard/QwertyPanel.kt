package com.tapnix.keyboard.ui.keyboard

import android.view.KeyEvent
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tapnix.keyboard.data.KeyboardLayout
import com.tapnix.keyboard.data.KeyboardPanel
import com.tapnix.keyboard.data.ShiftState
import com.tapnix.keyboard.ime.TapNixIMEService
import com.tapnix.keyboard.ui.theme.LocalKeyboardTheme
import com.tapnix.keyboard.viewmodel.KeyboardViewModel

/**
 * QwertyPanel
 *
 * Full QWERTY keyboard layout with:
 *  - 3 character rows + bottom action row
 *  - Shift with 3 states: OFF → ON (one-shot) → CAPS_LOCK
 *  - Backspace with long-press repeat
 *  - Long-press on alpha keys → alternative characters
 *  - Space bar with double-space → period
 *  - Context-aware Enter/Return key
 *  - Panel-switch buttons (emoji, clipboard, numeric, settings)
 */
@Composable
fun QwertyPanel(
    viewModel: KeyboardViewModel,
    imeService: TapNixIMEService,
) {
    val shiftState by viewModel.shiftState.collectAsStateWithLifecycle()
    val theme = LocalKeyboardTheme.current
    val shifted = shiftState != ShiftState.OFF

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 3.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        // ── Row 1: Q–P ──────────────────────────────────────────────────────
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

        // ── Row 2: A–L ──────────────────────────────────────────────────────
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

        // ── Row 3: Shift, Z–M, Backspace ────────────────────────────────────
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Shift key
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

            // Backspace key with long-press repeat
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

        // ── Bottom Row: 123, Emoji, Space, Enter ──────────────────────────
        BottomActionRow(viewModel = viewModel, imeService = imeService)
    }
}

@Composable
private fun BottomActionRow(
    viewModel: KeyboardViewModel,
    imeService: TapNixIMEService,
) {
    val theme = LocalKeyboardTheme.current
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

        // Space bar
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
