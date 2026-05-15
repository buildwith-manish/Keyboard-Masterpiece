package com.tapnix.keyboard

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.inputmethod.InputMethodManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.tapnix.keyboard.ui.theme.TapNixTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            TapNixTheme {
                SetupScreen()
            }
        }
    }
}

@Composable
fun SetupScreen() {
    val context = LocalContext.current
    val imm = context.getSystemService(InputMethodManager::class.java)

    val isEnabled = remember(context) {
        imm.enabledInputMethodList.any { it.packageName == context.packageName }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "TapNix Keyboard",
                        fontWeight = FontWeight.Bold
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Hero card
            ElevatedCard(
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "⌨️",
                        style = MaterialTheme.typography.displayLarge
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Welcome to TapNix",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "A premium keyboard with emoji spam, smart clipboard, and beautiful themes",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Text(
                text = "Setup Steps",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold
            )

            SetupStep(
                step = 1,
                icon = Icons.Default.Keyboard,
                title = "Enable TapNix Keyboard",
                description = "Add TapNix to your active keyboards in system settings.",
                isDone = isEnabled,
                actionLabel = "Open Keyboard Settings",
                onAction = {
                    context.startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
                }
            )

            SetupStep(
                step = 2,
                icon = Icons.Default.CheckCircle,
                title = "Set as Default",
                description = "Tap the keyboard icon in any text field and select TapNix.",
                isDone = false,
                actionLabel = "Switch Keyboard",
                onAction = {
                    val imm2 = context.getSystemService(InputMethodManager::class.java)
                    @Suppress("DEPRECATION")
                    imm2.showInputMethodPicker()
                }
            )

            HorizontalDivider()

            Text(
                text = "Features",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold
            )

            FeatureGrid()

            Spacer(modifier = Modifier.height(32.dp))

            Button(
                onClick = {
                    context.startActivity(
                        Intent(context, com.tapnix.keyboard.ui.settings.SettingsActivity::class.java)
                    )
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Settings, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Open Settings")
            }
        }
    }
}

@Composable
fun SetupStep(
    step: Int,
    icon: ImageVector,
    title: String,
    description: String,
    isDone: Boolean,
    actionLabel: String,
    onAction: () -> Unit,
) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Surface(
                shape = MaterialTheme.shapes.small,
                color = if (isDone) MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.secondaryContainer,
                modifier = Modifier.size(40.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    if (isDone) {
                        Icon(
                            Icons.Default.Check,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                    } else {
                        Text(
                            text = "$step",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(text = title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(onClick = onAction) {
                    Text(actionLabel)
                }
            }
        }
    }
}

@Composable
fun FeatureGrid() {
    val features = listOf(
        Triple("😂", "Emoji Spam", "Hold any emoji for rapid-fire repeat"),
        Triple("📋", "Smart Clipboard", "50-entry history with pin & search"),
        Triple("🎨", "6 Themes", "AMOLED, Ocean, Forest, Candy & more"),
        Triple("🔤", "QWERTY", "Smooth full-layout keyboard"),
        Triple("🔒", "Private", "Incognito mode & password detection"),
        Triple("⚡", "Fast", "Low-latency, optimized for speed"),
    )

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        features.chunked(2).forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                row.forEach { (emoji, title, desc) ->
                    ElevatedCard(modifier = Modifier.weight(1f)) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(text = emoji, style = MaterialTheme.typography.titleLarge)
                            Text(text = title, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                            Text(
                                text = desc,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                if (row.size == 1) Spacer(modifier = Modifier.weight(1f))
            }
        }
    }
}
