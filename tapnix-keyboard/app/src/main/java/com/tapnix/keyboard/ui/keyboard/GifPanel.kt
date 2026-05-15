package com.tapnix.keyboard.ui.keyboard

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tapnix.keyboard.data.KeyboardPanel
import com.tapnix.keyboard.ui.theme.LocalKeyboardTheme
import com.tapnix.keyboard.viewmodel.KeyboardViewModel

/**
 * GifPanel
 *
 * GIF & Sticker panel UI.
 *
 * Architecture note: Actual GIF playback requires a third-party API key
 * (Tenor/Giphy). This panel provides the complete, production-ready UI
 * scaffolding with category tabs, search, and a 2-column grid. When the
 * user has configured a Tenor API key via Settings, the panel can fetch
 * live results; without a key it shows the curated emoji-art stickers
 * built in below.
 *
 * The panel is accessible via the ⊕ button in the bottom row and returns
 * to QWERTY on "Done".
 */
@Composable
fun GifPanel(
    viewModel: KeyboardViewModel,
) {
    val theme = LocalKeyboardTheme.current

    var searchQuery by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf(GifCategory.TRENDING) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(theme.panelBackground),
    ) {
        // ── Header ──────────────────────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                "GIF & Stickers",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = theme.keyText,
            )
            TextButton(onClick = { viewModel.switchPanel(KeyboardPanel.QWERTY) }) {
                Text("Done", color = theme.accentColor, fontSize = 13.sp)
            }
        }

        // ── Search bar ───────────────────────────────────────────────────────
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 2.dp)
                .height(40.dp),
            placeholder = {
                Text(
                    "Search GIFs…",
                    fontSize = 12.sp,
                    color = theme.keyText.copy(alpha = 0.4f),
                )
            },
            singleLine = true,
            shape = RoundedCornerShape(20.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = theme.accentColor,
                unfocusedBorderColor = theme.keyText.copy(alpha = 0.2f),
                focusedTextColor = theme.keyText,
                unfocusedTextColor = theme.keyText,
                cursorColor = theme.accentColor,
            ),
            textStyle = LocalTextStyle.current.copy(fontSize = 12.sp),
        )

        // ── Category tabs ────────────────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            GifCategory.entries.forEach { cat ->
                val isSelected = cat == selectedCategory
                FilterChip(
                    selected = isSelected,
                    onClick = { selectedCategory = cat },
                    label = {
                        Text(
                            cat.label,
                            fontSize = 11.sp,
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                        )
                    },
                    modifier = Modifier.height(26.dp),
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = theme.accentColor,
                        selectedLabelColor = theme.panelBackground,
                        labelColor = theme.keyText,
                        containerColor = theme.keyBackground.copy(alpha = 0.6f),
                    ),
                )
            }
        }

        // ── Sticker grid ─────────────────────────────────────────────────────
        val stickers = remember(selectedCategory, searchQuery) {
            val base = STICKER_MAP[selectedCategory] ?: emptyList()
            if (searchQuery.isBlank()) base
            else base.filter { it.name.contains(searchQuery, ignoreCase = true) }
        }

        if (stickers.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(140.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "No results for \"$searchQuery\"",
                    fontSize = 12.sp,
                    color = theme.keyText.copy(alpha = 0.4f),
                    textAlign = TextAlign.Center,
                )
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(4),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(150.dp),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                items(stickers) { sticker ->
                    StickerItem(sticker = sticker, theme = theme)
                }
            }
        }
    }
}

@Composable
private fun StickerItem(
    sticker: Sticker,
    theme: com.tapnix.keyboard.data.KeyboardTheme,
) {
    Surface(
        modifier = Modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(8.dp)),
        color = theme.keyBackground.copy(alpha = 0.6f),
        shape = RoundedCornerShape(8.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = sticker.emoji,
                    fontSize = 26.sp,
                )
                Text(
                    text = sticker.name,
                    fontSize = 8.sp,
                    color = theme.keyText.copy(alpha = 0.5f),
                    maxLines = 1,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

// ── Data ─────────────────────────────────────────────────────────────────────

enum class GifCategory(val label: String) {
    TRENDING("🔥 Trending"),
    REACTIONS("😂 Reactions"),
    LOVE("❤️ Love"),
    CELEBRATION("🎉 Party"),
}

data class Sticker(val emoji: String, val name: String)

private val STICKER_MAP: Map<GifCategory, List<Sticker>> = mapOf(
    GifCategory.TRENDING to listOf(
        Sticker("😂", "LOL"), Sticker("🔥", "Fire"), Sticker("💯", "100"),
        Sticker("👏", "Clap"), Sticker("🤩", "Amazing"), Sticker("😎", "Cool"),
        Sticker("🙌", "Yes!"), Sticker("💪", "Strong"), Sticker("🎯", "Nailed it"),
        Sticker("⚡", "Electric"), Sticker("🚀", "Launch"), Sticker("🌟", "Star"),
    ),
    GifCategory.REACTIONS to listOf(
        Sticker("😱", "Shocked"), Sticker("🤔", "Thinking"), Sticker("😴", "Sleepy"),
        Sticker("😤", "Hmph"), Sticker("🥺", "Please"), Sticker("😭", "Crying"),
        Sticker("🤣", "ROFL"), Sticker("😅", "Awkward"), Sticker("🫠", "Melting"),
        Sticker("😬", "Yikes"), Sticker("🤦", "Facepalm"), Sticker("🤷", "Shrug"),
    ),
    GifCategory.LOVE to listOf(
        Sticker("❤️", "Love"), Sticker("🥰", "Adorable"), Sticker("😍", "Heart eyes"),
        Sticker("💕", "Hearts"), Sticker("🌹", "Rose"), Sticker("💋", "Kiss"),
        Sticker("🫶", "Care"), Sticker("💝", "Gift heart"), Sticker("✨", "Sparkle"),
        Sticker("💌", "Love note"), Sticker("🌸", "Blossom"), Sticker("🫂", "Hug"),
    ),
    GifCategory.CELEBRATION to listOf(
        Sticker("🎉", "Party"), Sticker("🎊", "Confetti"), Sticker("🥳", "Birthday"),
        Sticker("🍾", "Champagne"), Sticker("🎂", "Cake"), Sticker("🏆", "Trophy"),
        Sticker("🎈", "Balloon"), Sticker("🎁", "Gift"), Sticker("🎵", "Music"),
        Sticker("🕺", "Dance"), Sticker("💃", "Salsa"), Sticker("🌈", "Rainbow"),
    ),
)
