package com.tapnix.keyboard.ui.settings

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tapnix.keyboard.data.KeyboardLanguage
import com.tapnix.keyboard.data.KeyboardPanel
import com.tapnix.keyboard.data.KeyboardTheme
import com.tapnix.keyboard.data.OneHandedMode
import com.tapnix.keyboard.ui.theme.LocalKeyboardTheme
import com.tapnix.keyboard.viewmodel.KeyboardViewModel

/**
 * SettingsPanel — inline settings accessible from the keyboard.
 *
 * Sections:
 *  Theme picker (all 9 themes), Input toggles, Premium features,
 *  Layout options (one-handed, height), Privacy, System link.
 */
@Composable
fun SettingsPanel(viewModel: KeyboardViewModel) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val currentTheme by viewModel.currentTheme.collectAsStateWithLifecycle()
    val theme = LocalKeyboardTheme.current
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(theme.panelBackground)
            .padding(vertical = 4.dp),
    ) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                "Settings",
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = theme.keyText,
            )
            TextButton(onClick = { viewModel.switchPanel(KeyboardPanel.QWERTY) }) {
                Text("Done", color = theme.accentColor, fontSize = 13.sp)
            }
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .height(290.dp),
            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {

            // ── Theme Picker ───────────────────────────────────────────────
            item {
                Text(
                    "Theme",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = theme.accentColor,
                    modifier = Modifier.padding(bottom = 4.dp),
                )
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(vertical = 4.dp),
                ) {
                    items(KeyboardTheme.allThemes, key = { it.id }) { t ->
                        ThemeChip(
                            themeModel = t,
                            isSelected = t.id == currentTheme.id,
                            onClick = { viewModel.setTheme(t.id) },
                        )
                    }
                }
            }

            // ── Input ──────────────────────────────────────────────────────
            item { SettingDivider(label = "Input", theme = theme) }

            item {
                SettingToggleRow(
                    icon = Icons.Default.Vibration,
                    label = "Haptic feedback",
                    checked = settings.hapticFeedback,
                    onToggle = { viewModel.setHapticFeedback(it) },
                    theme = theme,
                )
            }
            item {
                SettingToggleRow(
                    icon = Icons.Default.VolumeUp,
                    label = "Sound on keypress",
                    checked = settings.soundFeedback,
                    onToggle = { viewModel.setSoundFeedback(it) },
                    theme = theme,
                )
            }
            item {
                SettingToggleRow(
                    icon = Icons.Default.TextFields,
                    label = "Auto capitalise",
                    checked = settings.autoCapitalize,
                    onToggle = { viewModel.setAutoCapitalize(it) },
                    theme = theme,
                )
            }
            item {
                SettingToggleRow(
                    icon = Icons.Default.Speed,
                    label = "Double space → period",
                    checked = settings.doubleSpacePeriod,
                    onToggle = { viewModel.setDoubleSpacePeriod(it) },
                    theme = theme,
                )
            }
            item {
                SettingToggleRow(
                    icon = Icons.Default.Lightbulb,
                    label = "Word suggestions",
                    checked = settings.showSuggestions,
                    onToggle = { viewModel.setShowSuggestions(it) },
                    theme = theme,
                )
            }

            // ── AI & Premium Features ──────────────────────────────────────
            item { SettingDivider(label = "AI & Premium Features", theme = theme) }

            item {
                SettingToggleRow(
                    icon = Icons.Default.Gesture,
                    label = "Swipe typing",
                    checked = settings.swipeTypingEnabled,
                    onToggle = { viewModel.setSwipeTypingEnabled(it) },
                    theme = theme,
                )
            }
            item {
                SettingToggleRow(
                    icon = Icons.Default.AutoFixHigh,
                    label = "Auto-correct",
                    checked = settings.autoCorrectEnabled,
                    onToggle = { viewModel.setAutoCorrectEnabled(it) },
                    theme = theme,
                )
            }
            item {
                SettingToggleRow(
                    icon = Icons.Default.Spellcheck,
                    label = "Grammar hints",
                    checked = settings.showGrammarHints,
                    onToggle = { viewModel.setShowGrammarHints(it) },
                    theme = theme,
                )
            }
            item {
                SettingToggleRow(
                    icon = Icons.Default.Psychology,
                    label = "Adaptive learning",
                    checked = settings.adaptiveLearningEnabled,
                    onToggle = { viewModel.setAdaptiveLearningEnabled(it) },
                    theme = theme,
                )
            }
            item {
                SettingToggleRow(
                    icon = Icons.Default.SwipeLeft,
                    label = "Gesture delete word",
                    checked = settings.gestureDeleteEnabled,
                    onToggle = { viewModel.setGestureDeleteEnabled(it) },
                    theme = theme,
                )
            }

            // ── Language ───────────────────────────────────────────────────
            item { SettingDivider(label = "Language", theme = theme) }

            item {
                LanguagePicker(
                    currentCode = settings.language,
                    onSelect = { viewModel.setLanguage(it) },
                    theme = theme,
                )
            }

            // ── Layout ─────────────────────────────────────────────────────
            item { SettingDivider(label = "Layout", theme = theme) }

            item {
                OneHandedModePicker(
                    current = settings.oneHandedMode,
                    onSelect = { viewModel.setOneHandedMode(it) },
                    theme = theme,
                )
            }

            item {
                HeightSlider(
                    current = settings.keyboardHeightMultiplier,
                    onChange = { viewModel.setKeyboardHeightMultiplier(it) },
                    theme = theme,
                )
            }

            // ── Privacy ────────────────────────────────────────────────────
            item { SettingDivider(label = "Privacy", theme = theme) }

            item {
                SettingToggleRow(
                    icon = Icons.Default.VisibilityOff,
                    label = "Incognito mode",
                    checked = settings.incognitoMode,
                    onToggle = { viewModel.setIncognitoMode(it) },
                    theme = theme,
                )
            }
            item {
                SettingToggleRow(
                    icon = Icons.Default.ContentPaste,
                    label = "Clipboard history",
                    checked = settings.clipboardEnabled,
                    onToggle = { viewModel.setClipboardEnabled(it) },
                    theme = theme,
                )
            }

            // ── System ─────────────────────────────────────────────────────
            item { SettingDivider(label = "System", theme = theme) }

            item {
                TextButton(
                    onClick = {
                        context.startActivity(
                            Intent(Settings.ACTION_INPUT_METHOD_SETTINGS).apply {
                                flags = Intent.FLAG_ACTIVITY_NEW_TASK
                            }
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Default.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Input method settings", color = theme.accentColor, fontSize = 13.sp)
                }
            }
        }
    }
}

// ── Theme Chip ────────────────────────────────────────────────────────────────

@Composable
private fun ThemeChip(
    themeModel: KeyboardTheme,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Surface(
            onClick = onClick,
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(8.dp)),
            color = themeModel.panelBackground,
            border = if (isSelected)
                BorderStroke(2.dp, themeModel.accentColor)
            else null,
            shape = RoundedCornerShape(8.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Row {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .background(themeModel.keyBackground)
                    )
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .background(themeModel.accentColor)
                    )
                }
            }
        }
        Text(
            text = themeModel.name,
            fontSize = 9.sp,
            color = LocalKeyboardTheme.current.keyText.copy(alpha = 0.7f),
        )
    }
}

// ── Language Picker ───────────────────────────────────────────────────────────

@Composable
private fun LanguagePicker(
    currentCode: String,
    onSelect: (String) -> Unit,
    theme: KeyboardTheme,
) {
    Column {
        Text(
            "Active language",
            fontSize = 12.sp,
            color = theme.keyText,
            modifier = Modifier.padding(bottom = 4.dp),
        )
        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            items(KeyboardLanguage.entries) { lang ->
                val selected = lang.code == currentCode
                FilterChip(
                    selected = selected,
                    onClick = { onSelect(lang.code) },
                    label = { Text(lang.label, fontSize = 11.sp) },
                    modifier = Modifier.height(26.dp),
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = theme.accentColor,
                        selectedLabelColor = theme.panelBackground,
                        labelColor = theme.keyText,
                        containerColor = theme.keyBackground.copy(alpha = 0.5f),
                    ),
                )
            }
        }
    }
}

// ── One-handed Mode Picker ────────────────────────────────────────────────────

@Composable
private fun OneHandedModePicker(
    current: OneHandedMode,
    onSelect: (OneHandedMode) -> Unit,
    theme: KeyboardTheme,
) {
    Column {
        Text(
            "One-handed mode",
            fontSize = 12.sp,
            color = theme.keyText,
            modifier = Modifier.padding(bottom = 4.dp),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(
                OneHandedMode.OFF   to "Off",
                OneHandedMode.LEFT  to "◀ Left",
                OneHandedMode.RIGHT to "Right ▶",
            ).forEach { (mode, label) ->
                val selected = mode == current
                FilterChip(
                    selected = selected,
                    onClick = { onSelect(mode) },
                    label = { Text(label, fontSize = 11.sp) },
                    modifier = Modifier.height(26.dp),
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = theme.accentColor,
                        selectedLabelColor = theme.panelBackground,
                        labelColor = theme.keyText,
                        containerColor = theme.keyBackground.copy(alpha = 0.5f),
                    ),
                )
            }
        }
    }
}

// ── Height Slider ─────────────────────────────────────────────────────────────

@Composable
private fun HeightSlider(
    current: Float,
    onChange: (Float) -> Unit,
    theme: KeyboardTheme,
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "Keyboard height",
                fontSize = 12.sp,
                color = theme.keyText,
            )
            Text(
                text = when {
                    current < 0.85f -> "Compact"
                    current > 1.15f -> "Tall"
                    else -> "Default"
                },
                fontSize = 11.sp,
                color = theme.accentColor,
                fontWeight = FontWeight.SemiBold,
            )
        }
        Slider(
            value = current,
            onValueChange = onChange,
            valueRange = 0.7f..1.4f,
            steps = 5,
            modifier = Modifier
                .fillMaxWidth()
                .height(28.dp),
            colors = SliderDefaults.colors(
                thumbColor = theme.accentColor,
                activeTrackColor = theme.accentColor,
                inactiveTrackColor = theme.keyText.copy(alpha = 0.2f),
            ),
        )
    }
}

// ── Reusable components ───────────────────────────────────────────────────────

@Composable
private fun SettingToggleRow(
    icon: ImageVector,
    label: String,
    checked: Boolean,
    onToggle: (Boolean) -> Unit,
    theme: KeyboardTheme,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            icon,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = theme.keyText.copy(alpha = 0.6f),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = label,
            modifier = Modifier.weight(1f),
            fontSize = 13.sp,
            color = theme.keyText,
        )
        Switch(
            checked = checked,
            onCheckedChange = onToggle,
            modifier = Modifier
                .height(24.dp)
                .width(44.dp),
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = theme.accentColor,
                uncheckedThumbColor = theme.keyText.copy(alpha = 0.5f),
                uncheckedTrackColor = theme.keyText.copy(alpha = 0.2f),
            ),
        )
    }
}

@Composable
private fun SettingDivider(label: String, theme: KeyboardTheme) {
    Text(
        text = label,
        fontSize = 10.sp,
        fontWeight = FontWeight.SemiBold,
        color = theme.accentColor,
        modifier = Modifier.padding(top = 8.dp, bottom = 2.dp),
    )
}
