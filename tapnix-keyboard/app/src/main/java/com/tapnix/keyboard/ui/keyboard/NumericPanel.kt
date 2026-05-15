package com.tapnix.keyboard.ui.keyboard

import android.view.KeyEvent
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.tapnix.keyboard.data.KeyboardLayout
import com.tapnix.keyboard.data.KeyboardPanel
import com.tapnix.keyboard.ime.TapNixIMEService
import com.tapnix.keyboard.viewmodel.KeyboardViewModel

/**
 * NumericPanel
 *
 * Symbols/numbers keyboard panel.
 * Shows numbers, punctuation, and special characters.
 */
@Composable
fun NumericPanel(
    viewModel: KeyboardViewModel,
    imeService: TapNixIMEService,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 3.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        KeyboardLayout.NUMERIC_ROWS.take(3).forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                row.forEach { keyDef ->
                    KeyButton(
                        keyDef = keyDef,
                        shifted = false,
                        modifier = Modifier.weight(1f),
                        onTap = {
                            imeService.commitText(keyDef.char.toString())
                            viewModel.onCharCommitted(keyDef.char.toString())
                        },
                    )
                }
            }
        }

        // Bottom row with ABC back
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FunctionKeyButton(
                label = "ABC",
                onClick = { viewModel.switchPanel(KeyboardPanel.QWERTY) },
                modifier = Modifier.weight(1.5f),
            )
            KeyboardLayout.NUMERIC_ROWS.last().take(7).forEach { keyDef ->
                KeyButton(
                    keyDef = keyDef,
                    shifted = false,
                    modifier = Modifier.weight(1f),
                    onTap = {
                        imeService.commitText(keyDef.char.toString())
                        viewModel.onCharCommitted(keyDef.char.toString())
                    },
                )
            }
            FunctionKeyButton(
                label = "⌫",
                onClick = { imeService.deleteBackward() },
                onLongClick = {
                    viewModel.startLongPress(payload = "⌫") {
                        imeService.deleteBackward()
                    }
                },
                modifier = Modifier.weight(1.5f),
            )
        }

        // Space + Enter
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            FunctionKeyButton(
                label = "space",
                onClick = { imeService.commitText(" ") },
                modifier = Modifier.weight(4f),
            )
            FunctionKeyButton(
                label = "↵",
                onClick = { imeService.sendKeyEvent(KeyEvent.KEYCODE_ENTER) },
                modifier = Modifier.weight(1.5f),
            )
        }
    }
}
