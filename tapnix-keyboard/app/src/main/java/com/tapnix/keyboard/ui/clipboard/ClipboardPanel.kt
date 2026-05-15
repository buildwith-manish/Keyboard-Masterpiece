package com.tapnix.keyboard.ui.clipboard

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tapnix.keyboard.data.ClipboardEntry
import com.tapnix.keyboard.data.KeyboardPanel
import com.tapnix.keyboard.ime.TapNixIMEService
import com.tapnix.keyboard.ui.keyboard.FunctionKeyButton
import com.tapnix.keyboard.ui.theme.LocalKeyboardTheme
import com.tapnix.keyboard.viewmodel.KeyboardViewModel
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

/**
 * ClipboardPanel
 *
 * Shows clipboard history with:
 *  - Search bar
 *  - Pinned section at top
 *  - Tap to paste (chunked for huge entries)
 *  - Pin / Unpin toggle
 *  - Delete entry
 *  - Clear all unpinned
 *  - Character count indicator for large clips
 */
@Composable
fun ClipboardPanel(
    viewModel: KeyboardViewModel,
    imeService: TapNixIMEService,
) {
    val scope = rememberCoroutineScope()
    val searchQuery by viewModel.clipboardSearchQuery.collectAsStateWithLifecycle()
    val entries by viewModel.clipboardSearchResults.collectAsStateWithLifecycle()
    val theme = LocalKeyboardTheme.current

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(theme.panelBackground),
    ) {
        // ── Header Row ──────────────────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { viewModel.onClipboardSearchChanged(it) },
                placeholder = {
                    Text("Search clipboard…", fontSize = 13.sp, color = theme.keyText.copy(alpha = 0.5f))
                },
                singleLine = true,
                leadingIcon = {
                    Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp))
                },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { viewModel.onClipboardSearchChanged("") }) {
                            Icon(Icons.Default.Clear, contentDescription = "Clear")
                        }
                    }
                },
                modifier = Modifier
                    .weight(1f)
                    .height(44.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = theme.keyText,
                    unfocusedTextColor = theme.keyText,
                    focusedContainerColor = theme.keyBackground,
                    unfocusedContainerColor = theme.keyBackground,
                    focusedBorderColor = theme.accentColor,
                    unfocusedBorderColor = theme.keyText.copy(alpha = 0.2f),
                ),
                textStyle = LocalTextStyle.current.copy(fontSize = 13.sp),
            )

            FunctionKeyButton(
                label = "ABC",
                onClick = { viewModel.switchPanel(KeyboardPanel.QWERTY) },
                minWidth = 44.dp,
            )
        }

        // ── Clear All button ─────────────────────────────────────────────────
        if (entries.any { !it.isPinned }) {
            TextButton(
                onClick = { viewModel.clearClipboardHistory() },
                modifier = Modifier
                    .align(Alignment.End)
                    .padding(end = 8.dp),
            ) {
                Icon(Icons.Default.DeleteSweep, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("Clear all", fontSize = 12.sp, color = theme.accentColor)
            }
        }

        // ── Entry List ───────────────────────────────────────────────────────
        if (entries.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("📋", fontSize = 32.sp)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "No clipboard history",
                        color = theme.keyText.copy(alpha = 0.5f),
                        fontSize = 13.sp,
                    )
                    Text(
                        "Copy some text to get started",
                        color = theme.keyText.copy(alpha = 0.3f),
                        fontSize = 11.sp,
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(240.dp),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                // Pinned section
                val pinned = entries.filter { it.isPinned }
                if (pinned.isNotEmpty()) {
                    item(key = "pinned_header") {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.PushPin,
                                contentDescription = null,
                                modifier = Modifier.size(12.dp),
                                tint = theme.accentColor,
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                "Pinned",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = theme.accentColor,
                            )
                        }
                    }
                    pinned.forEach { entry ->
                        item(key = "pin_${entry.id}") {
                            ClipboardCard(
                                entry = entry,
                                onPaste = {
                                    scope.launch {
                                        viewModel.clipboardEngine.commitToInput(entry.id) { text ->
                                            imeService.commitText(text)
                                        }
                                    }
                                },
                                onPin = { viewModel.toggleClipboardPin(entry.id) },
                                onDelete = { viewModel.deleteClipboardEntry(entry.id) },
                                theme = theme,
                            )
                        }
                    }
                }

                // Unpinned section
                val unpinned = entries.filter { !it.isPinned }
                if (unpinned.isNotEmpty() && pinned.isNotEmpty()) {
                    item(key = "unpinned_header") {
                        Text(
                            "Recent",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = theme.keyText.copy(alpha = 0.5f),
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                }
                unpinned.forEach { entry ->
                    item(key = "unpin_${entry.id}") {
                        ClipboardCard(
                            entry = entry,
                            onPaste = {
                                scope.launch {
                                    viewModel.clipboardEngine.commitToInput(entry.id) { text ->
                                        imeService.commitText(text)
                                    }
                                }
                            },
                            onPin = { viewModel.toggleClipboardPin(entry.id) },
                            onDelete = { viewModel.deleteClipboardEntry(entry.id) },
                            theme = theme,
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ClipboardCard(
    entry: ClipboardEntry,
    onPaste: () -> Unit,
    onPin: () -> Unit,
    onDelete: () -> Unit,
    theme: com.tapnix.keyboard.data.KeyboardTheme,
) {
    val dateStr = remember(entry.createdAt) {
        val sdf = SimpleDateFormat("MMM d, h:mm a", Locale.getDefault())
        sdf.format(Date(entry.createdAt))
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = if (entry.isPinned)
            theme.accentColor.copy(alpha = 0.08f)
        else theme.keyBackground,
        tonalElevation = if (entry.isPinned) 2.dp else 0.dp,
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    entry.label?.let {
                        Text(
                            text = it,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = theme.accentColor,
                        )
                    }

                    Text(
                        text = entry.preview,
                        fontSize = 13.sp,
                        color = theme.keyText,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                        lineHeight = 17.sp,
                    )

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = dateStr,
                            fontSize = 10.sp,
                            color = theme.keyText.copy(alpha = 0.4f),
                        )
                        if (entry.sizeChars > 200) {
                            Text(
                                text = "${formatCount(entry.sizeChars)} chars",
                                fontSize = 10.sp,
                                color = theme.accentColor.copy(alpha = 0.7f),
                            )
                        }
                    }
                }

                // Actions
                Column(
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    IconButton(onClick = onPaste, modifier = Modifier.size(28.dp)) {
                        Icon(
                            Icons.Default.ContentPaste,
                            contentDescription = "Paste",
                            tint = theme.accentColor,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                    IconButton(onClick = onPin, modifier = Modifier.size(28.dp)) {
                        Icon(
                            if (entry.isPinned) Icons.Default.PushPin else Icons.Default.PinDrop,
                            contentDescription = if (entry.isPinned) "Unpin" else "Pin",
                            tint = if (entry.isPinned) theme.accentColor else theme.keyText.copy(alpha = 0.4f),
                            modifier = Modifier.size(16.dp),
                        )
                    }
                    IconButton(onClick = onDelete, modifier = Modifier.size(28.dp)) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "Delete",
                            tint = theme.keyText.copy(alpha = 0.4f),
                            modifier = Modifier.size(14.dp),
                        )
                    }
                }
            }
        }
    }
}

private fun formatCount(count: Int): String = when {
    count >= 1_000_000 -> "${count / 1_000_000}M"
    count >= 1_000 -> "${count / 1_000}K"
    else -> "$count"
}
