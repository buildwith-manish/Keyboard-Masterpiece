package com.keyboardmasterpiece.engine

/**
 * FIX: MED-005 — RTL layout support via isRtl parameter.
 * FIX: LOW-004 — Landscape-specific keyboard layouts via isLandscape parameter.
 */
object KeyboardLayoutFactory {
    private fun k(label: String, output: String = label, weight: Float = 1f, alt: String = "") = KeyboardKey(label, output, 0, weight, alt)
    private fun a(label: String, code: Int, weight: Float = 1f) = KeyboardKey(label, code = code, weight = weight)

    /**
     * FIX: MED-005 — Added isRtl parameter. When true, reverses key order in each row for QWERTY.
     * FIX: LOW-004 — Added isLandscape parameter. In landscape, wider key weights and optional number row.
     */
    fun layout(panel: Panel, shifted: Boolean, caps: Boolean, numberRow: Boolean, isRtl: Boolean = false, isLandscape: Boolean = false): List<List<KeyboardKey>> = when (panel) {
        Panel.QWERTY -> qwerty(shifted || caps, numberRow || isLandscape, isRtl, isLandscape) // FIX: LOW-004 — Always show number row in landscape
        Panel.SYMBOLS -> symbols(isRtl)
        Panel.EMOJI -> emoji(isRtl)
        Panel.CLIPBOARD -> clipboardShell(isRtl)
        Panel.NUMPAD -> numpad(isRtl)
        Panel.EDIT -> edit(isRtl)
    }

    /**
     * FIX: MED-005 — RTL: reverse key order in each row, swap Shift/Backspace sides.
     * FIX: LOW-004 — Landscape: wider key weights for space and action keys.
     */
    private fun qwerty(upper: Boolean, numberRow: Boolean, isRtl: Boolean, isLandscape: Boolean): List<List<KeyboardKey>> {
        fun c(ch: String, alt: String = ""): KeyboardKey {
            val out = if (upper) ch.uppercase() else ch
            return k(out, out, alt = alt)
        }
        val rows = mutableListOf<List<KeyboardKey>>()
        if (numberRow) rows += "1234567890".map { k(it.toString()) }

        // FIX: LOW-004 — Landscape uses slightly wider spacing
        val spaceWeight = if (isLandscape) 5.0f else 4.2f
        val sideKeyWeight = if (isLandscape) 1.5f else 1.35f

        val letterRow1 = "qwertyuiop".map { c(it.toString(), altFor(it)) }
        val letterRow2 = "asdfghjkl".map { c(it.toString(), altFor(it)) }

        // FIX: MED-005 — RTL: Shift on right, Backspace on left
        val letterRow3 = if (isRtl) {
            listOf(a("⌫", KeyCodes.BACKSPACE, 1.25f)) + "zxcvbnm".map { c(it.toString(), altFor(it)) } + a(if (upper) "⇧" else "⇧", KeyCodes.SHIFT, 1.25f)
        } else {
            listOf(a(if (upper) "⇧" else "⇧", KeyCodes.SHIFT, 1.25f)) + "zxcvbnm".map { c(it.toString(), altFor(it)) } + a("⌫", KeyCodes.BACKSPACE, 1.25f)
        }

        // FIX: MED-005 — RTL: reverse bottom row key order
        val bottomRow = if (isRtl) {
            listOf(
                a("⏎", KeyCodes.ENTER, sideKeyWeight), a("🙂", KeyCodes.EMOJI, .9f),
                a("space", KeyCodes.SPACE, spaceWeight), a("🎙", KeyCodes.VOICE, .9f), a("🌐", KeyCodes.LANGUAGE, .9f),
                a("?123", KeyCodes.SYMBOLS, sideKeyWeight)
            )
        } else {
            listOf(
                a("?123", KeyCodes.SYMBOLS, sideKeyWeight), a("🌐", KeyCodes.LANGUAGE, .9f), a("🎙", KeyCodes.VOICE, .9f),
                a("space", KeyCodes.SPACE, spaceWeight), a("🙂", KeyCodes.EMOJI, .9f), a("⏎", KeyCodes.ENTER, sideKeyWeight)
            )
        }

        rows += letterRow1
        rows += letterRow2
        rows += letterRow3
        rows += bottomRow

        // FIX: MED-005 — RTL: reverse each row's key order
        return if (isRtl) {
            rows.map { row -> row.reversed() }
        } else {
            rows
        }
    }

    /** FIX: MED-005 — RTL support for symbols panel. */
    private fun symbols(isRtl: Boolean = false): List<List<KeyboardKey>> {
        val rows = listOf(
            listOf("1","2","3","4","5","6","7","8","9","0").map { k(it) },
            listOf("@","#","$","_","&","-","+","(",")","/").map { k(it) },
            listOf("*", "\"", "'", ":", ";", "!", "?", "€", "£", "¥").map { k(it) },
            listOf(a("ABC", KeyCodes.ABC, 1.4f), k(","), k("."), k("₹"), k("%"), k("="), a("⌫", KeyCodes.BACKSPACE, 1.4f)),
            listOf(a("123", KeyCodes.NUMPAD, 1.2f), a("✂", KeyCodes.EDIT, 1.0f), a("space", KeyCodes.SPACE, 4f), a("⏎", KeyCodes.ENTER, 1.4f))
        )
        return if (isRtl) rows.map { it.reversed() } else rows
    }

    /** FIX: MED-005 — RTL support for emoji panel. */
    private fun emoji(isRtl: Boolean = false): List<List<KeyboardKey>> {
        val rows = listOf(
            listOf("😀","😁","😂","🤣","😊","😍","😘","😎","🥳","😭").map { k(it) },
            listOf("👍","🙏","👏","🔥","❤️","💯","✨","🎉","✅","⭐").map { k(it) },
            listOf("🍕","☕","⚽","🚗","✈️","🏠","💼","📌","🎵","🎁").map { k(it) },
            listOf(a("ABC", KeyCodes.ABC, 1.3f), a("GIF", KeyCodes.EMOJI, 1f), k("🔍", ""), a("⌫", KeyCodes.BACKSPACE, 1.3f)),
            listOf(a("📋", KeyCodes.CLIPBOARD, 1f), a("space", KeyCodes.SPACE, 4f), a("⏎", KeyCodes.ENTER, 1.3f))
        )
        return if (isRtl) rows.map { it.reversed() } else rows
    }

    /** FIX: MED-005 — RTL support for clipboard panel. */
    private fun clipboardShell(isRtl: Boolean = false): List<List<KeyboardKey>> {
        val rows = listOf(
            listOf(a("ABC", KeyCodes.ABC), a("Paste", KeyCodes.PASTE), a("Copy", KeyCodes.COPY), a("Cut", KeyCodes.CUT), a("All", KeyCodes.SELECT_ALL)),
            listOf(a("Undo", KeyCodes.UNDO), a("Redo", KeyCodes.REDO), a("←", KeyCodes.LEFT), a("→", KeyCodes.RIGHT)),
            listOf(a("⌫", KeyCodes.BACKSPACE), a("space", KeyCodes.SPACE, 3f), a("⏎", KeyCodes.ENTER))
        )
        return if (isRtl) rows.map { it.reversed() } else rows
    }

    /** FIX: MED-005 — RTL support for numpad panel. */
    private fun numpad(isRtl: Boolean = false): List<List<KeyboardKey>> {
        val rows = listOf(
            listOf("7","8","9").map { k(it) } + a("⌫", KeyCodes.BACKSPACE),
            listOf("4","5","6").map { k(it) } + k("/"),
            listOf("1","2","3").map { k(it) } + k("*"),
            listOf(a("ABC", KeyCodes.ABC), k("0", weight = 2f), k("."), a("⏎", KeyCodes.ENTER))
        )
        return if (isRtl) rows.map { it.reversed() } else rows
    }

    /** FIX: MED-005 — RTL support for edit panel. */
    private fun edit(isRtl: Boolean = false): List<List<KeyboardKey>> {
        val rows = listOf(
            listOf(a("ABC", KeyCodes.ABC), a("←", KeyCodes.LEFT), a("↑", KeyCodes.UP), a("→", KeyCodes.RIGHT), a("All", KeyCodes.SELECT_ALL)),
            listOf(a("Copy", KeyCodes.COPY), a("Cut", KeyCodes.CUT), a("Paste", KeyCodes.PASTE), a("Undo", KeyCodes.UNDO), a("Redo", KeyCodes.REDO)),
            listOf(a("Mode", KeyCodes.MODE), a("Theme", KeyCodes.THEME), a("Incog", KeyCodes.INCOGNITO), a("↓", KeyCodes.DOWN), a("⌫", KeyCodes.BACKSPACE))
        )
        return if (isRtl) rows.map { it.reversed() } else rows
    }

    private fun altFor(ch: Char): String = when (ch) {
        'a' -> "áàâäæãåā"; 'e' -> "éèêëēėę"; 'i' -> "íìîïīį"; 'o' -> "óòôöõøōœ"; 'u' -> "úùûüū"; 'c' -> "çćč"; 'n' -> "ñń"; 's' -> "ßśš"; else -> ""
    }
}
