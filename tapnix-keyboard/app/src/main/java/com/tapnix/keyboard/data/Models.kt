package com.tapnix.keyboard.data

import androidx.compose.ui.graphics.Color

// ── Keyboard Models ──────────────────────────────────────────────────────────

enum class KeyboardPanel { QWERTY, NUMERIC, EMOJI, CLIPBOARD, SETTINGS }
enum class ShiftState { OFF, ON, CAPS_LOCK }

data class KeyDef(
    val char: Char,
    val longPressChars: List<Char> = emptyList(),
    val widthWeight: Float = 1f,
)

data class Suggestion(
    val text: String,
    val score: Float = 0f,
)

// ── Emoji Models ─────────────────────────────────────────────────────────────

data class Emoji(
    val unicode: String,
    val name: String,
    val keywords: List<String> = emptyList(),
    val category: String = "",
)

data class EmojiCategory(
    val id: String,
    val label: String,
    val icon: String,
    val emojis: List<Emoji>,
)

// ── Clipboard Models ─────────────────────────────────────────────────────────

data class ClipboardEntry(
    val id: Long,
    val preview: String,
    val fullText: String,
    val isPinned: Boolean,
    val label: String?,
    val createdAt: Long,
    val sizeChars: Int,
)

// ── Theme Models ─────────────────────────────────────────────────────────────

data class KeyboardTheme(
    val id: String,
    val name: String,
    val keyBackground: Color,
    val keyPressed: Color,
    val keyText: Color,
    val panelBackground: Color,
    val suggestionBarBackground: Color,
    val accentColor: Color,
    val keyCornerRadius: Float,
    val enableBlur: Boolean,
    val isAmoled: Boolean,
    val shadowElevation: Float,
) {
    companion object {
        val Default = KeyboardTheme(
            id = "default",
            name = "Default",
            keyBackground = Color(0xFFFFFFFF),
            keyPressed = Color(0xFFE0E0E0),
            keyText = Color(0xFF1C1B1F),
            panelBackground = Color(0xFFDEE1E6),
            suggestionBarBackground = Color(0xFFFFFFFF),
            accentColor = Color(0xFF6750A4),
            keyCornerRadius = 8f,
            enableBlur = false,
            isAmoled = false,
            shadowElevation = 2f,
        )

        val Amoled = KeyboardTheme(
            id = "amoled",
            name = "AMOLED Black",
            keyBackground = Color(0xFF1A1A1A),
            keyPressed = Color(0xFF333333),
            keyText = Color(0xFFFFFFFF),
            panelBackground = Color(0xFF000000),
            suggestionBarBackground = Color(0xFF0D0D0D),
            accentColor = Color(0xFF00E5FF),
            keyCornerRadius = 10f,
            enableBlur = false,
            isAmoled = true,
            shadowElevation = 0f,
        )

        val Ocean = KeyboardTheme(
            id = "ocean",
            name = "Ocean Blue",
            keyBackground = Color(0xFF1565C0),
            keyPressed = Color(0xFF0D47A1),
            keyText = Color(0xFFFFFFFF),
            panelBackground = Color(0xFF0A3069),
            suggestionBarBackground = Color(0xFF1976D2),
            accentColor = Color(0xFF40C4FF),
            keyCornerRadius = 12f,
            enableBlur = false,
            isAmoled = false,
            shadowElevation = 4f,
        )

        val Forest = KeyboardTheme(
            id = "forest",
            name = "Forest Green",
            keyBackground = Color(0xFF2E7D32),
            keyPressed = Color(0xFF1B5E20),
            keyText = Color(0xFFFFFFFF),
            panelBackground = Color(0xFF1A3B1C),
            suggestionBarBackground = Color(0xFF388E3C),
            accentColor = Color(0xFF69F0AE),
            keyCornerRadius = 8f,
            enableBlur = false,
            isAmoled = false,
            shadowElevation = 3f,
        )

        val Candy = KeyboardTheme(
            id = "candy",
            name = "Candy",
            keyBackground = Color(0xFFEC407A),
            keyPressed = Color(0xFFC2185B),
            keyText = Color(0xFFFFFFFF),
            panelBackground = Color(0xFFAD1457),
            suggestionBarBackground = Color(0xFFE91E63),
            accentColor = Color(0xFFFFD740),
            keyCornerRadius = 16f,
            enableBlur = false,
            isAmoled = false,
            shadowElevation = 3f,
        )

        val Glass = KeyboardTheme(
            id = "glass",
            name = "Glassmorphism",
            keyBackground = Color(0x33FFFFFF),
            keyPressed = Color(0x55FFFFFF),
            keyText = Color(0xFFFFFFFF),
            panelBackground = Color(0x22000000),
            suggestionBarBackground = Color(0x44000000),
            accentColor = Color(0xFF80DEEA),
            keyCornerRadius = 14f,
            enableBlur = true,
            isAmoled = false,
            shadowElevation = 0f,
        )

        val allThemes = listOf(Default, Amoled, Ocean, Forest, Candy, Glass)
    }
}

// ── Settings Model ───────────────────────────────────────────────────────────

data class KeyboardSettings(
    val themeId: String = "default",
    val hapticFeedback: Boolean = true,
    val soundFeedback: Boolean = false,
    val autoCapitalize: Boolean = true,
    val doubleSpacePeriod: Boolean = true,
    val incognitoMode: Boolean = false,
    val emojiRepeatInitialDelayMs: Long = 500L,
    val emojiRepeatMinIntervalMs: Long = 28L,
    val emojiRepeatStartIntervalMs: Long = 110L,
    val clipboardEnabled: Boolean = true,
    val showSuggestions: Boolean = true,
) {
    companion object {
        val Default = KeyboardSettings()
    }
}
