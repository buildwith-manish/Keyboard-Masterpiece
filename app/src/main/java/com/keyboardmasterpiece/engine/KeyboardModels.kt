package com.keyboardmasterpiece.engine

import android.graphics.RectF

enum class Panel { QWERTY, SYMBOLS, EMOJI, CLIPBOARD, NUMPAD, EDIT }
enum class LayoutMode { FULL, ONE_HANDED_LEFT, ONE_HANDED_RIGHT, FLOATING, SPLIT }
enum class ThemeMode { LIGHT, DARK }

// FIX: INFO-003 -- Separate layout info from model to decouple view state.
// KeyLayoutInfo holds view-managed geometry. KeyboardKey remains the logical model.
// For backward compatibility, KeyboardKey.rect still exists but is @Deprecated.
data class KeyLayoutInfo(
    val rect: RectF = RectF()
)

data class KeyboardKey(
    val label: String,
    val output: String = label,
    val code: Int = 0,
    val weight: Float = 1f,
    val alt: String = "",
    @Deprecated("Use KeyLayoutInfo managed by KeyboardView instead. This field couples model to view state.")
    val rect: RectF = RectF(),
    val sticky: Boolean = false
) {
    val isAction: Boolean get() = code != 0

    // FIX: INFO-003 -- External layout info, managed by the view layer.
    var layoutInfo: KeyLayoutInfo? = null
}

object KeyCodes {
    const val SHIFT = -1
    const val BACKSPACE = -5
    const val ENTER = -10
    const val SPACE = -20
    const val SYMBOLS = -30
    const val ABC = -31
    const val EMOJI = -32
    const val CLIPBOARD = -33
    const val LANGUAGE = -34
    const val SETTINGS = -35
    const val NUMPAD = -36
    const val EDIT = -37
    const val VOICE = -38
    const val MODE = -39
    const val UNDO = -40
    const val REDO = -41
    const val COPY = -42
    const val CUT = -43
    const val PASTE = -44
    const val SELECT_ALL = -45
    const val LEFT = -46
    const val RIGHT = -47
    const val UP = -48
    const val DOWN = -49
    const val INCOGNITO = -50
    const val THEME = -51
    // TASK3 -- Photo picker button in keyboard toolbar
    const val PHOTO_PICKER = -52
    // TASK3 -- File picker button (PDF, docs, etc.) in keyboard toolbar
    const val FILE_PICKER = -53

    // Emoji category key codes
    const val EMOJI_CATEGORY_SMILEYS = -60
    const val EMOJI_CATEGORY_GESTURES = -61
    const val EMOJI_CATEGORY_ANIMALS = -62
    const val EMOJI_CATEGORY_FOOD = -63
    const val EMOJI_CATEGORY_ACTIVITIES = -64
    const val EMOJI_CATEGORY_TRAVEL = -65
    const val EMOJI_CATEGORY_OBJECTS = -66
    const val EMOJI_CATEGORY_SYMBOLS = -67
    const val EMOJI_CATEGORY_RECENT = -68

    // Clipboard action key codes
    const val CLIP_ITEM = -70
    const val CLIP_PIN = -71
    const val CLIP_DELETE = -72
    const val CLIP_CLEAR = -73
    const val CLIP_ACTION_URL = -90
    const val CLIP_ACTION_EMAIL = -91
    const val CLIP_ACTION_PHONE = -92

    // Toolbar key codes
    const val TOOLBAR_CLIPBOARD = -80
    const val TOOLBAR_SETTINGS = -81
    const val TOOLBAR_THEME = -82
    const val TOOLBAR_ONEHAND = -83
    const val TOOLBAR_VOICE = -84
    const val TOOLBAR_INCOGNITO = -85
}

// Emoji category enum for tracking current category in the emoji panel
enum class EmojiCategory {
    SMILEYS, GESTURES, ANIMALS, FOOD, ACTIVITIES, TRAVEL, OBJECTS, SYMBOLS, RECENT
}
