package com.keyboardmasterpiece.engine

object KeyboardLayoutFactory {
    private fun k(label: String, output: String = label, weight: Float = 1f, alt: String = "") = KeyboardKey(label, output, 0, weight, alt)
    private fun a(label: String, code: Int, weight: Float = 1f) = KeyboardKey(label, code = code, weight = weight)

    fun layout(panel: Panel, shifted: Boolean, caps: Boolean, numberRow: Boolean): List<List<KeyboardKey>> = when (panel) {
        Panel.QWERTY -> qwerty(shifted || caps, numberRow)
        Panel.SYMBOLS -> symbols()
        Panel.EMOJI -> emoji()
        Panel.CLIPBOARD -> clipboardShell()
        Panel.NUMPAD -> numpad()
        Panel.EDIT -> edit()
    }

    private fun qwerty(upper: Boolean, numberRow: Boolean): List<List<KeyboardKey>> {
        fun c(ch: String, alt: String = ""): KeyboardKey {
            val out = if (upper) ch.uppercase() else ch
            return k(out, out, alt = alt)
        }
        val rows = mutableListOf<List<KeyboardKey>>()
        if (numberRow) rows += "1234567890".map { k(it.toString()) }
        rows += listOf("qwertyuiop".map { c(it.toString(), altFor(it)) })
        rows += listOf("asdfghjkl".map { c(it.toString(), altFor(it)) })
        rows += listOf(listOf(a(if (upper) "⇧" else "⇧", KeyCodes.SHIFT, 1.25f)) + "zxcvbnm".map { c(it.toString(), altFor(it)) } + a("⌫", KeyCodes.BACKSPACE, 1.25f))
        rows += listOf(
            a("?123", KeyCodes.SYMBOLS, 1.35f), a("🌐", KeyCodes.LANGUAGE, .9f), a("🎙", KeyCodes.VOICE, .9f),
            a("space", KeyCodes.SPACE, 4.2f), a("🙂", KeyCodes.EMOJI, .9f), a("⏎", KeyCodes.ENTER, 1.35f)
        )
        return rows
    }

    private fun symbols() = listOf(
        listOf("1","2","3","4","5","6","7","8","9","0").map { k(it) },
        listOf("@","#","$","_","&","-","+","(",")","/").map { k(it) },
        listOf("*", "\"", "'", ":", ";", "!", "?", "€", "£", "¥").map { k(it) },
        listOf(a("ABC", KeyCodes.ABC, 1.4f), k(","), k("."), k("₹"), k("%"), k("="), a("⌫", KeyCodes.BACKSPACE, 1.4f)),
        listOf(a("123", KeyCodes.NUMPAD, 1.2f), a("✂", KeyCodes.EDIT, 1.0f), a("space", KeyCodes.SPACE, 4f), a("⏎", KeyCodes.ENTER, 1.4f))
    )

    private fun emoji(): List<List<KeyboardKey>> = listOf(
        listOf("😀","😁","😂","🤣","😊","😍","😘","😎","🥳","😭").map { k(it) },
        listOf("👍","🙏","👏","🔥","❤️","💯","✨","🎉","✅","⭐").map { k(it) },
        listOf("🍕","☕","⚽","🚗","✈️","🏠","💼","📌","🎵","🎁").map { k(it) },
        listOf(a("ABC", KeyCodes.ABC, 1.3f), a("GIF", KeyCodes.EMOJI, 1f), k("🔍", ""), a("⌫", KeyCodes.BACKSPACE, 1.3f)),
        listOf(a("📋", KeyCodes.CLIPBOARD, 1f), a("space", KeyCodes.SPACE, 4f), a("⏎", KeyCodes.ENTER, 1.3f))
    )

    private fun clipboardShell() = listOf(
        listOf(a("ABC", KeyCodes.ABC), a("Paste", KeyCodes.PASTE), a("Copy", KeyCodes.COPY), a("Cut", KeyCodes.CUT), a("All", KeyCodes.SELECT_ALL)),
        listOf(a("Undo", KeyCodes.UNDO), a("Redo", KeyCodes.REDO), a("←", KeyCodes.LEFT), a("→", KeyCodes.RIGHT)),
        listOf(a("⌫", KeyCodes.BACKSPACE), a("space", KeyCodes.SPACE, 3f), a("⏎", KeyCodes.ENTER))
    )

    private fun numpad() = listOf(
        listOf("7","8","9").map { k(it) } + a("⌫", KeyCodes.BACKSPACE),
        listOf("4","5","6").map { k(it) } + k("/"),
        listOf("1","2","3").map { k(it) } + k("*"),
        listOf(a("ABC", KeyCodes.ABC), k("0", weight = 2f), k("."), a("⏎", KeyCodes.ENTER))
    )

    private fun edit() = listOf(
        listOf(a("ABC", KeyCodes.ABC), a("←", KeyCodes.LEFT), a("↑", KeyCodes.UP), a("→", KeyCodes.RIGHT), a("All", KeyCodes.SELECT_ALL)),
        listOf(a("Copy", KeyCodes.COPY), a("Cut", KeyCodes.CUT), a("Paste", KeyCodes.PASTE), a("Undo", KeyCodes.UNDO), a("Redo", KeyCodes.REDO)),
        listOf(a("Mode", KeyCodes.MODE), a("Theme", KeyCodes.THEME), a("Incog", KeyCodes.INCOGNITO), a("↓", KeyCodes.DOWN), a("⌫", KeyCodes.BACKSPACE))
    )

    private fun altFor(ch: Char): String = when (ch) {
        'a' -> "áàâäæãåā"; 'e' -> "éèêëēėę"; 'i' -> "íìîïīį"; 'o' -> "óòôöõøōœ"; 'u' -> "úùûüū"; 'c' -> "çćč"; 'n' -> "ñń"; 's' -> "ßśš"; else -> ""
    }
}
