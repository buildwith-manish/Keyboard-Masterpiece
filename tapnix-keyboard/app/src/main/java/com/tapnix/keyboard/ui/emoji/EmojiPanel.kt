package com.tapnix.keyboard.ui.emoji

import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tapnix.keyboard.data.Emoji
import com.tapnix.keyboard.data.KeyboardPanel
import com.tapnix.keyboard.ime.TapNixIMEService
import com.tapnix.keyboard.ui.keyboard.FunctionKeyButton
import com.tapnix.keyboard.ui.theme.LocalKeyboardTheme
import com.tapnix.keyboard.viewmodel.KeyboardViewModel

/**
 * EmojiPanel
 *
 * Full emoji keyboard with:
 *  - Recent tab + 8 category tabs
 *  - Search with 150ms debounce
 *  - LazyVerticalGrid for smooth scrolling through 3,600+ emoji
 *  - Stable item keys to prevent unnecessary recomposition
 *  - Long-press → continuous emoji spam (calls LongPressEngine)
 *  - Back to QWERTY button
 */
@Composable
fun EmojiPanel(
    viewModel: KeyboardViewModel,
    imeService: TapNixIMEService,
) {
    val categories by viewModel.emojiCategories.collectAsStateWithLifecycle()
    val recentEmojis by viewModel.recentEmojis.collectAsStateWithLifecycle()
    val searchQuery by viewModel.emojiSearchQuery.collectAsStateWithLifecycle()
    val searchResults by viewModel.emojiSearchResults.collectAsStateWithLifecycle()
    val theme = LocalKeyboardTheme.current
    val haptic = LocalHapticFeedback.current

    var selectedCategoryIndex by remember { mutableIntStateOf(0) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(theme.panelBackground),
    ) {
        // ── Search Bar ──────────────────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { viewModel.onEmojiSearchChanged(it) },
                placeholder = {
                    Text("Search emoji…", fontSize = 13.sp, color = theme.keyText.copy(alpha = 0.5f))
                },
                singleLine = true,
                leadingIcon = {
                    Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp))
                },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { viewModel.onEmojiSearchChanged("") }) {
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

            // Back to QWERTY
            FunctionKeyButton(
                label = "ABC",
                onClick = { viewModel.switchPanel(KeyboardPanel.QWERTY) },
                minWidth = 44.dp,
            )
        }

        // ── Category Tabs (hidden during search) ────────────────────────────
        AnimatedVisibility(visible = searchQuery.isEmpty()) {
            EmojiCategoryTabs(
                categories = buildList {
                    add("🕐" to "Recent")
                    addAll(categories.map { it.icon to it.label })
                },
                selectedIndex = selectedCategoryIndex,
                onSelect = { selectedCategoryIndex = it },
                theme = theme,
            )
        }

        // ── Emoji Grid ──────────────────────────────────────────────────────
        val displayEmojis: List<Emoji> = when {
            searchQuery.isNotEmpty() -> searchResults
            selectedCategoryIndex == 0 -> recentEmojis
            else -> categories.getOrNull(selectedCategoryIndex - 1)?.emojis ?: emptyList()
        }

        if (displayEmojis.isEmpty() && searchQuery.isEmpty() && selectedCategoryIndex == 0) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("No recent emoji", color = theme.keyText.copy(alpha = 0.5f), fontSize = 13.sp)
                    Text("Tap the category tabs to browse", color = theme.keyText.copy(alpha = 0.3f), fontSize = 11.sp)
                }
            }
        } else if (displayEmojis.isEmpty() && searchQuery.isNotEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text("No emoji found for \"$searchQuery\"", color = theme.keyText.copy(alpha = 0.5f), fontSize = 13.sp)
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 44.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp),
                contentPadding = PaddingValues(4.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                items(
                    count = displayEmojis.size,
                    key = { displayEmojis[it].unicode },
                ) { index ->
                    val emoji = displayEmojis[index]
                    EmojiKey(
                        emoji = emoji,
                        onTap = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            imeService.commitText(emoji.unicode)
                            viewModel.recordEmojiUse(emoji)
                        },
                        onLongPressStart = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            viewModel.startLongPress(payload = emoji.unicode) { text ->
                                imeService.commitText(text)
                            }
                        },
                        onLongPressEnd = {
                            viewModel.cancelLongPress()
                        },
                    )
                }
            }
        }

        // ── Delete + Back ────────────────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            FunctionKeyButton(
                label = "⌫",
                onClick = { imeService.deleteBackward() },
                onLongClick = {
                    viewModel.startLongPress(payload = "⌫") {
                        imeService.deleteBackward()
                    }
                },
            )
        }
    }
}

@Composable
private fun EmojiCategoryTabs(
    categories: List<Pair<String, String>>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    theme: com.tapnix.keyboard.data.KeyboardTheme,
) {
    ScrollableTabRow(
        selectedTabIndex = selectedIndex,
        modifier = Modifier.fillMaxWidth(),
        containerColor = theme.panelBackground,
        contentColor = theme.accentColor,
        edgePadding = 4.dp,
        indicator = @Composable { tabPositions ->
            if (selectedIndex < tabPositions.size) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .wrapContentSize(align = Alignment.BottomStart, unbounded = true)
                        .offset(x = tabPositions[selectedIndex].left)
                        .width(tabPositions[selectedIndex].width)
                        .height(2.dp)
                        .background(theme.accentColor)
                )
            }
        },
        divider = {},
    ) {
        categories.forEachIndexed { index, (icon, label) ->
            Tab(
                selected = selectedIndex == index,
                onClick = { onSelect(index) },
                modifier = Modifier.height(36.dp),
            ) {
                Text(
                    text = icon,
                    fontSize = 18.sp,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@Composable
fun EmojiKey(
    emoji: Emoji,
    onTap: () -> Unit,
    onLongPressStart: () -> Unit,
    onLongPressEnd: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(RoundedCornerShape(8.dp))
            .combinedClickable(
                onClick = onTap,
                onLongClick = onLongPressStart,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = emoji.unicode,
            fontSize = 26.sp,
            textAlign = TextAlign.Center,
        )
    }
}
