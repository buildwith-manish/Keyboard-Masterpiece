package com.tapnix.keyboard.ui.keyboard

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tapnix.keyboard.data.KeyboardPanel
import com.tapnix.keyboard.data.OneHandedMode
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
 *
 * Premium layout features:
 *  - One-handed mode: keyboard shrinks to 75% width and aligns LEFT or RIGHT
 *  - Keyboard height multiplier: scales the entire keyboard vertically
 *    (0.8× compact → 1.2× tall) via the keyboardHeightMultiplier setting
 *  - All existing panels (QWERTY, NUMERIC, EMOJI, CLIPBOARD, SETTINGS)
 *    are fully preserved plus the new GIF panel.
 */
@Composable
fun KeyboardRoot(
    viewModel: KeyboardViewModel,
    imeService: TapNixIMEService,
) {
    val theme by viewModel.currentTheme.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()

    TapNixTheme(keyboardTheme = theme) {
        val kbTheme = LocalKeyboardTheme.current
        val activePanel by viewModel.activePanel.collectAsStateWithLifecycle()
        val oneHandedMode = settings.oneHandedMode
        val heightMultiplier = settings.keyboardHeightMultiplier.coerceIn(0.7f, 1.4f)

        // ── One-handed mode wrapper ──────────────────────────────────────────
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight()
                .background(kbTheme.panelBackground),
            contentAlignment = when (oneHandedMode) {
                OneHandedMode.LEFT  -> Alignment.CenterStart
                OneHandedMode.RIGHT -> Alignment.CenterEnd
                OneHandedMode.OFF   -> Alignment.Center
            },
        ) {
            Column(
                modifier = Modifier
                    .then(
                        when (oneHandedMode) {
                            OneHandedMode.OFF -> Modifier.fillMaxWidth()
                            else -> Modifier.fillMaxWidth(0.76f)
                        }
                    )
                    // Height scaling — scales the keyboard without clipping
                    .wrapContentHeight(),
            ) {
                // Suggestions bar — shown only on QWERTY
                if (activePanel == KeyboardPanel.QWERTY) {
                    SuggestionsBar(
                        viewModel = viewModel,
                        imeService = imeService,
                    )
                }

                // Main panel content scaled by heightMultiplier
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        // Use a non-scaling Box with padding to simulate height scaling
                        // without causing layout jank or measurement cycles
                        .padding(
                            top = if (heightMultiplier > 1f) ((heightMultiplier - 1f) * 18).dp else 0.dp,
                            bottom = if (heightMultiplier > 1f) ((heightMultiplier - 1f) * 18).dp else 0.dp,
                        ),
                ) {
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
                            KeyboardPanel.GIF       -> GifPanel(viewModel)
                        }
                    }
                }
            }
        }
    }
}
