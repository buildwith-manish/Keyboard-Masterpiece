package com.tapnix.keyboard.ui.keyboard

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tapnix.keyboard.data.KeyboardPanel
import com.tapnix.keyboard.ime.TapNixIMEService
import com.tapnix.keyboard.ui.clipboard.ClipboardPanel
import com.tapnix.keyboard.ui.emoji.EmojiPanel
import com.tapnix.keyboard.ui.settings.SettingsPanel
import com.tapnix.keyboard.ui.theme.LocalKeyboardTheme
import com.tapnix.keyboard.ui.theme.TapNixTheme
import com.tapnix.keyboard.viewmodel.KeyboardViewModel

/**
 * KeyboardRoot
 *
 * Top-level Compose entry point for the keyboard.
 * Applies the active theme, then routes to the correct panel.
 */
@Composable
fun KeyboardRoot(
    viewModel: KeyboardViewModel,
    imeService: TapNixIMEService,
) {
    val theme by viewModel.currentTheme.collectAsStateWithLifecycle()

    TapNixTheme(keyboardTheme = theme) {
        val kbTheme = LocalKeyboardTheme.current
        val activePanel by viewModel.activePanel.collectAsStateWithLifecycle()

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight()
                .background(kbTheme.panelBackground)
        ) {
            // Suggestions bar — shown only on QWERTY
            if (activePanel == KeyboardPanel.QWERTY) {
                SuggestionsBar(
                    viewModel = viewModel,
                    imeService = imeService,
                )
            }

            // Main panel — animated crossfade between panels
            Crossfade(
                targetState = activePanel,
                animationSpec = tween(durationMillis = 160),
                label = "KeyboardPanelSwitch",
            ) { panel ->
                when (panel) {
                    KeyboardPanel.QWERTY    -> QwertyPanel(viewModel, imeService)
                    KeyboardPanel.NUMERIC   -> NumericPanel(viewModel, imeService)
                    KeyboardPanel.EMOJI     -> EmojiPanel(viewModel, imeService)
                    KeyboardPanel.CLIPBOARD -> ClipboardPanel(viewModel, imeService)
                    KeyboardPanel.SETTINGS  -> SettingsPanel(viewModel)
                }
            }
        }
    }
}
