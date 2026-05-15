package com.keyboardmasterpiece.engine

import android.graphics.RectF

enum class Panel { QWERTY, SYMBOLS, EMOJI, CLIPBOARD, NUMPAD, EDIT }
enum class LayoutMode { FULL, ONE_HANDED_LEFT, ONE_HANDED_RIGHT, FLOATING, SPLIT }
enum class ThemeMode { LIGHT, DARK }

data class KeyboardKey(
    val label: String,
    val output: String = label,
    val code: Int = 0,
    val weight: Float = 1f,
    val alt: String = "",
    val rect: RectF = RectF(),
    val sticky: Boolean = false
) {
    val isAction: Boolean get() = code != 0
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
}
