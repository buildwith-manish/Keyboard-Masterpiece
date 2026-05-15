package com.tapnix.keyboard.ui.settings

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tapnix.keyboard.data.KeyboardTheme
import com.tapnix.keyboard.ui.theme.TapNixTheme
import com.tapnix.keyboard.viewmodel.KeyboardViewModel
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class SettingsActivity : ComponentActivity() {

    private val viewModel: KeyboardViewModel by viewModels {
        KeyboardViewModel.Factory(applicationContext)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            TapNixTheme {
                FullSettingsScreen(
                    viewModel = viewModel,
                    onBack = { finish() },
                    onOpenInputSettings = {
                        startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
                    },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FullSettingsScreen(
    viewModel: KeyboardViewModel,
    onBack: () -> Unit,
    onOpenInputSettings: () -> Unit,
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val currentTheme by viewModel.currentTheme.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("TapNix Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                )
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                SettingsSectionHeader("Themes")
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(vertical = 8.dp),
                ) {
                    items(KeyboardTheme.allThemes, key = { it.id }) { theme ->
                        LargeThemeCard(
                            themeModel = theme,
                            isSelected = theme.id == currentTheme.id,
                            onClick = { viewModel.setTheme(theme.id) },
                        )
                    }
                }
            }

            item { SettingsSectionHeader("Feedback") }
            item {
                FullSettingToggle(
                    icon = Icons.Default.Vibration,
                    title = "Haptic Feedback",
                    subtitle = "Vibrate on each keypress",
                    checked = settings.hapticFeedback,
                    onToggle = { viewModel.setHapticFeedback(it) },
                )
            }
            item {
                FullSettingToggle(
                    icon = Icons.Default.VolumeUp,
                    title = "Sound Feedback",
                    subtitle = "Play click sound on keypress",
                    checked = settings.soundFeedback,
                    onToggle = { viewModel.setSoundFeedback(it) },
                )
            }

            item { SettingsSectionHeader("Typing") }
            item {
                FullSettingToggle(
                    icon = Icons.Default.TextFields,
                    title = "Auto-capitalize",
                    subtitle = "Capitalize first letter of sentences",
                    checked = settings.autoCapitalize,
                    onToggle = { viewModel.setAutoCapitalize(it) },
                )
            }
            item {
                FullSettingToggle(
                    icon = Icons.Default.Speed,
                    title = "Double space → period",
                    subtitle = "Double-tap space to insert \". \"",
                    checked = settings.doubleSpacePeriod,
                    onToggle = { viewModel.setDoubleSpacePeriod(it) },
                )
            }
            item {
                FullSettingToggle(
                    icon = Icons.Default.Lightbulb,
                    title = "Word Suggestions",
                    subtitle = "Show next-word suggestions while typing",
                    checked = settings.showSuggestions,
                    onToggle = { viewModel.setShowSuggestions(it) },
                )
            }

            item { SettingsSectionHeader("Privacy") }
            item {
                FullSettingToggle(
                    icon = Icons.Default.VisibilityOff,
                    title = "Incognito Mode",
                    subtitle = "Don't save history while active",
                    checked = settings.incognitoMode,
                    onToggle = { viewModel.setIncognitoMode(it) },
                )
            }
            item {
                FullSettingToggle(
                    icon = Icons.Default.ContentPaste,
                    title = "Clipboard History",
                    subtitle = "Save copied text for easy re-paste",
                    checked = settings.clipboardEnabled,
                    onToggle = { viewModel.setShowSuggestions(it) },
                )
            }

            item { SettingsSectionHeader("System") }
            item {
                OutlinedButton(
                    onClick = onOpenInputSettings,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Default.OpenInNew, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Open Input Method Settings")
                }
            }

            item {
                Text(
                    "TapNix v1.0.0 — Production-grade Android keyboard",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }
    }
}

@Composable
fun SettingsSectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 4.dp, bottom = 4.dp),
    )
}

@Composable
fun FullSettingToggle(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onToggle: (Boolean) -> Unit,
) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Switch(checked = checked, onCheckedChange = onToggle)
        }
    }
}

@Composable
fun LargeThemeCard(
    themeModel: KeyboardTheme,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    ElevatedCard(
        onClick = onClick,
        modifier = Modifier
            .width(100.dp)
            .height(80.dp),
        border = if (isSelected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Row(modifier = Modifier.fillMaxSize()) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .padding(4.dp),
                ) {
                    Surface(
                        color = themeModel.panelBackground,
                        modifier = Modifier.fillMaxSize(),
                        shape = MaterialTheme.shapes.small,
                    ) {}
                }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .padding(4.dp),
                ) {
                    Surface(
                        color = themeModel.keyBackground,
                        modifier = Modifier.fillMaxSize(),
                        shape = MaterialTheme.shapes.small,
                    ) {}
                }
            }
            if (isSelected) {
                Icon(
                    Icons.Default.Check,
                    contentDescription = null,
                    modifier = Modifier
                        .size(20.dp)
                        .align(Alignment.TopEnd)
                        .padding(4.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }
        Text(
            text = themeModel.name,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
        )
    }
}
