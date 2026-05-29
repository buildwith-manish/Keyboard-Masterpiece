package com.keyboardmasterpiece.engine

// FIX: MED-005 -- RTL layout support via isRtl parameter.
// FIX: LOW-004 -- Landscape-specific keyboard layouts via isLandscape parameter.
// FEATURE: Emoji panel with categories, search & recent.
// FEATURE: Clipboard history panel with pin & delete.
// FEATURE: Toolbar row with quick actions (Gboard-style).
object KeyboardLayoutFactory {
    private fun k(label: String, output: String = label, weight: Float = 1f, alt: String = "") = KeyboardKey(label, output, 0, weight, alt)
    private fun a(label: String, code: Int, weight: Float = 1f) = KeyboardKey(label, code = code, weight = weight)

    // ═══════════════════════════════════════════════════════════════════════
    // Emoji data arrays -- at least 40 emojis per category
    // ═══════════════════════════════════════════════════════════════════════

    private val emojiSmileys = listOf(
        "😀","😁","😂","🤣","😊","😍","😘","😎","🥳","😭",
        "😃","😄","😅","😆","😉","😋","😌","🤔","😐","😑",
        "😶","😏","😒","🙄","😬","😮","😯","😴","🤒","🤢",
        "🤮","🥵","🥶","🥴","😵","🤯","🤠","🥳","😇","🤓"
    )

    private val emojiGestures = listOf(
        "👍","👎","👊","✊","🤛","🤜","👏","🙌","👐","🤲",
        "🤝","🙏","✌️","🤞","🤟","🤘","🤙","👈","👉","👆",
        "👇","☝️","✋","🤚","🖐","🖖","👋","🤏","💪","🦾",
        "🦿","🦵","🦶","👂","🦻","👃","🧠","🫀","🫁","🦷"
    )

    private val emojiAnimals = listOf(
        "🐶","🐱","🐭","🐹","🐰","🦊","🐻","🐼","🐨","🐯",
        "🦁","🐮","🐷","🐸","🐵","🐔","🐧","🐦","🐤","🦆",
        "🦅","🦉","🦇","🐺","🐗","🐴","🦄","🐝","🪱","🐛",
        "🦋","🐌","🐞","🐜","🪰","🪲","🪳","🦟","🦗","🕷"
    )

    private val emojiFood = listOf(
        "🍕","🍔","🍟","🌭","🍿","🧂","🥓","🥚","🍳","🧇",
        "🥞","🧈","🍞","🥐","🥨","🥯","🥖","🫓","🧀","🥗",
        "🥙","🫔","🌮","🌯","🥪","🍝","🍜","🍲","🍛","🍣",
        "🍺","🍻","🥂","🍷","🥃","🍸","🍹","🧋","☕","🍵"
    )

    private val emojiActivities = listOf(
        "⚽","🏀","🏈","⚾","🥎","🎾","🏐","🏉","🥏","🎱",
        "🪀","🏓","🏸","🏒","🏑","🥍","🏏","🪃","🥅","⛳",
        "🏹","🎣","🤿","🥊","🥋","🎽","🛹","🛼","🛷","⛸",
        "🎵","🎶","🎤","🎧","🎷","🪗","🎸","🎹","🎺","🎻"
    )

    private val emojiTravel = listOf(
        "🚗","🚕","🚙","🚌","🚎","🏎","🚓","🚑","🚒","🚐",
        "🛻","🚚","🚛","🚜","🦯","🦽","🦼","🛴","🚲","🛵",
        "🏍","🛺","🚨","🚔","🚍","🚘","🚖","🚡","🚠","🚟",
        "✈️","🛫","🛬","🪂","🚁","🛩","🛰","🚀","🛸","🏠"
    )

    private val emojiObjects = listOf(
        "💡","🔦","🕯","💻","🖥","🖨","⌨️","🖱","🖲","💾",
        "💿","📀","📷","📸","📹","🎥","📽","🎞","📞","☎️",
        "📟","📠","📺","📻","🎙","🎚","🎛","🧭","⏱","⏲",
        "⏰","🕰","⌛","⏳","📡","🔋","🪫","🔌","💰","💳"
    )

    private val emojiSymbols = listOf(
        "❤️","🧡","💛","💚","💙","💜","🖤","🤍","🤎","💔",
        "❣️","💕","💞","💓","💗","💖","💘","💝","💟","☮️",
        "✝️","☪️","🕉","☸️","✡️","🔯","🕎","☯️","☦️","🛐",
        "♈","♉","♊","♋","♌","♍","♎","♏","♐","♑"
    )

    // FIX: MED-005 -- Added isRtl parameter. When true, reverses key order in each row for QWERTY.
// FIX: LOW-004 -- Added isLandscape parameter. In landscape, wider key weights and optional number row.
// FEATURE: Added emojiCategory, recentEmojis, clipHistory, pinnedIndices for dynamic panels.
    fun layout(
        panel: Panel,
        shifted: Boolean,
        caps: Boolean,
        numberRow: Boolean,
        isRtl: Boolean = false,
        isLandscape: Boolean = false,
        emojiCategory: EmojiCategory = EmojiCategory.SMILEYS,
        recentEmojis: List<String> = emptyList(),
        clipHistory: List<String> = emptyList(),
        pinnedIndices: Set<Int> = emptySet()
    ): List<List<KeyboardKey>> {
        val baseLayout = when (panel) {
            Panel.QWERTY -> qwerty(shifted || caps, numberRow || isLandscape, isRtl, isLandscape)
            Panel.SYMBOLS -> symbols(isRtl)
            Panel.EMOJI -> emoji(emojiCategory, recentEmojis, isRtl)
            Panel.CLIPBOARD -> clipboardShell(clipHistory, pinnedIndices, isRtl)
            Panel.NUMPAD -> numpad(isRtl)
            Panel.EDIT -> edit(isRtl)
        }
        // FEATURE: Toolbar row -- prepend to all panels except NUMPAD
        return if (panel != Panel.NUMPAD) {
            listOf(toolbar()) + baseLayout
        } else {
            baseLayout
        }
    }

    // FEATURE: Toolbar strip -- Gboard-style quick action buttons.
// Single row of compact buttons that appears above the keyboard on all panels except NUMPAD.
    private fun toolbar(): List<KeyboardKey> = listOf(
        a("😊", KeyCodes.EMOJI, .8f),
        a("📋", KeyCodes.TOOLBAR_CLIPBOARD, .8f),
        a("⚙️", KeyCodes.TOOLBAR_SETTINGS, .8f),
        a("🎨", KeyCodes.TOOLBAR_THEME, .8f),
        a("🤚", KeyCodes.TOOLBAR_ONEHAND, .8f),
        a("🎙", KeyCodes.TOOLBAR_VOICE, .8f),
        a("🥷", KeyCodes.TOOLBAR_INCOGNITO, .8f)
    )

    // FIX: MED-005 -- RTL: reverse key order in each row, swap Shift/Backspace sides.
// FIX: LOW-004 -- Landscape: wider key weights for space and action keys.
    private fun qwerty(upper: Boolean, numberRow: Boolean, isRtl: Boolean, isLandscape: Boolean): List<List<KeyboardKey>> {
        fun c(ch: String, alt: String = ""): KeyboardKey {
            val out = if (upper) ch.uppercase() else ch
            return k(out, out, alt = alt)
        }
        val rows = mutableListOf<List<KeyboardKey>>()
        if (numberRow) rows += "1234567890".map { k(it.toString()) }

        // FIX: LOW-004 -- Landscape uses slightly wider spacing
        val spaceWeight = if (isLandscape) 5.0f else 4.2f
        val sideKeyWeight = if (isLandscape) 1.5f else 1.35f

        val letterRow1 = "qwertyuiop".map { c(it.toString(), altFor(it)) }
        val letterRow2 = "asdfghjkl".map { c(it.toString(), altFor(it)) }

        // FIX: MED-005 -- RTL: Shift on right, Backspace on left
        val letterRow3 = if (isRtl) {
            listOf(a("⌫", KeyCodes.BACKSPACE, 1.25f)) + "zxcvbnm".map { c(it.toString(), altFor(it)) } + a(if (upper) "⇧" else "⇧", KeyCodes.SHIFT, 1.25f)
        } else {
            listOf(a(if (upper) "⇧" else "⇧", KeyCodes.SHIFT, 1.25f)) + "zxcvbnm".map { c(it.toString(), altFor(it)) } + a("⌫", KeyCodes.BACKSPACE, 1.25f)
        }

        // TASK3 -- Photo picker and file picker buttons added to toolbar row
        // FIX: MED-005 -- RTL: reverse bottom row key order
        // FIX: Replaced LANGUAGE (🌐) with EMOJI (😊) -- emoji access is more frequent;
        //   long-press on EMOJI key shows input method picker instead
        val bottomRow = if (isRtl) {
            listOf(
                a("⏎", KeyCodes.ENTER, sideKeyWeight), a("📷", KeyCodes.PHOTO_PICKER, .9f),
                a("📎", KeyCodes.FILE_PICKER, .9f),
                a("space", KeyCodes.SPACE, spaceWeight), a("😊", KeyCodes.EMOJI, .9f),
                a("?123", KeyCodes.SYMBOLS, sideKeyWeight)
            )
        } else {
            listOf(
                a("?123", KeyCodes.SYMBOLS, sideKeyWeight), a("😊", KeyCodes.EMOJI, .9f),
                a("📷", KeyCodes.PHOTO_PICKER, .9f), a("📎", KeyCodes.FILE_PICKER, .9f),
                a("space", KeyCodes.SPACE, spaceWeight), a("⏎", KeyCodes.ENTER, sideKeyWeight)
            )
        }

        rows += letterRow1
        rows += letterRow2
        rows += letterRow3
        rows += bottomRow

        // FIX: MED-005 -- RTL: reverse each row's key order
        return if (isRtl) {
            rows.map { row -> row.reversed() }
        } else {
            rows
        }
    }

    // FIX: MED-005 -- RTL support for symbols panel.
    private fun symbols(isRtl: Boolean = false): List<List<KeyboardKey>> {
        val rows = listOf(
            listOf("1","2","3","4","5","6","7","8","9","0").map { k(it) },
            listOf("@","#","$","_","&","-","+","(",")","/").map { k(it) },
            listOf("*", "\"", "'", ":", ";", "!", "?", "€", "£", "¥").map { k(it) },
            listOf(a("ABC", KeyCodes.ABC, 1.4f), k(","), k("."), k("₹"), k("%"), k("="), a("⌫", KeyCodes.BACKSPACE, 1.4f)),
            listOf(a("123", KeyCodes.NUMPAD, 1.0f), a("😊", KeyCodes.EMOJI, 1.0f), a("✂", KeyCodes.EDIT, 1.0f), a("space", KeyCodes.SPACE, 3.5f), a("⏎", KeyCodes.ENTER, 1.4f))
        )
        return if (isRtl) rows.map { it.reversed() } else rows
    }

    // FEATURE: Emoji panel with categories, search & recent.
// Row 0: Category tabs (horizontal scrollable)
// Rows 1-5: Emoji grid (8 per row = 40 per page)
// Row 6: ABC + Backspace
    private fun emoji(category: EmojiCategory, recentEmojis: List<String>, isRtl: Boolean = false): List<List<KeyboardKey>> {
        // Category tab row
        val categoryRow = listOf(
            a("😀", KeyCodes.EMOJI_CATEGORY_SMILEYS, 1f),
            a("👋", KeyCodes.EMOJI_CATEGORY_GESTURES, 1f),
            a("🐻", KeyCodes.EMOJI_CATEGORY_ANIMALS, 1f),
            a("🍕", KeyCodes.EMOJI_CATEGORY_FOOD, 1f),
            a("⚽", KeyCodes.EMOJI_CATEGORY_ACTIVITIES, 1f),
            a("🚗", KeyCodes.EMOJI_CATEGORY_TRAVEL, 1f),
            a("💡", KeyCodes.EMOJI_CATEGORY_OBJECTS, 1f),
            a("🏳️‍🌈", KeyCodes.EMOJI_CATEGORY_SYMBOLS, 1f),
            a("🕐", KeyCodes.EMOJI_CATEGORY_RECENT, 1f)
        )

        // Select emoji list based on category
        val emojis = when (category) {
            EmojiCategory.SMILEYS -> emojiSmileys
            EmojiCategory.GESTURES -> emojiGestures
            EmojiCategory.ANIMALS -> emojiAnimals
            EmojiCategory.FOOD -> emojiFood
            EmojiCategory.ACTIVITIES -> emojiActivities
            EmojiCategory.TRAVEL -> emojiTravel
            EmojiCategory.OBJECTS -> emojiObjects
            EmojiCategory.SYMBOLS -> emojiSymbols
            EmojiCategory.RECENT -> if (recentEmojis.isNotEmpty()) recentEmojis else listOf("--")
        }

        // Build emoji grid rows (8 per row)
        val gridRows = mutableListOf<List<KeyboardKey>>()
        val chunked = emojis.chunked(8)
        // Show up to 5 rows of emoji grid
        for (i in 0 until 5.coerceAtMost(chunked.size)) {
            gridRows.add(chunked[i].map { k(it) })
        }
        // Pad with empty rows if fewer than 5 rows
        while (gridRows.size < 5) {
            gridRows.add(emptyList())
        }

        // Bottom row: ABC + Backspace
        val bottomRow = listOf(
            a("ABC", KeyCodes.ABC, 1.3f),
            a("📋", KeyCodes.CLIPBOARD, 1f),
            a("space", KeyCodes.SPACE, 4f),
            a("⌫", KeyCodes.BACKSPACE, 1.3f)
        )

        val rows = mutableListOf<List<KeyboardKey>>()
        rows.add(categoryRow)
        rows.addAll(gridRows)
        rows.add(bottomRow)

        return if (isRtl) rows.map { it.reversed() } else rows
    }

    // FEATURE: Clipboard history panel with pin & delete.
// Row 0: Clear All + Edit buttons
// Rows 1-N: Clipboard items (preview text as label, full text as output, CLIP_ITEM code)
// Each item row also has PIN and DELETE buttons with the clip index in the output field
// Last row: ABC + Space + Enter
    private fun clipboardShell(clipHistory: List<String>, pinnedIndices: Set<Int>, isRtl: Boolean = false): List<List<KeyboardKey>> {
        val rows = mutableListOf<List<KeyboardKey>>()

        // Top row: Clear All + Edit actions
        rows.add(listOf(
            a("Clear All", KeyCodes.CLIP_CLEAR, 1.5f),
            a("Paste", KeyCodes.PASTE, 1.2f),
            a("Copy", KeyCodes.COPY, 1f),
            a("Cut", KeyCodes.CUT, 1f),
            a("All", KeyCodes.SELECT_ALL, 1f)
        ))

        // Clipboard history items -- pinned first, then regular
        val pinnedItems = clipHistory.filterIndexed { index, _ -> index in pinnedIndices }
        val regularItems = clipHistory.filterIndexed { index, _ -> index !in pinnedIndices }
        val orderedItems = pinnedItems + regularItems

        // Show up to 6 clipboard items (each with preview + pin + delete)
        val displayItems = orderedItems.take(6)
        for (item in displayItems) {
            val originalIndex = clipHistory.indexOf(item)
            val isPinned = originalIndex in pinnedIndices
            val preview = item.take(15).replace("\n", " ")
            val pinLabel = if (isPinned) "📌" else "📍"
            rows.add(listOf(
                // Clip item: label is preview, output is full text for pasting
                KeyboardKey(preview, item, KeyCodes.CLIP_ITEM, 3f),
                // Pin toggle: output stores the original index as string
                KeyboardKey(pinLabel, originalIndex.toString(), KeyCodes.CLIP_PIN, .8f),
                // Delete: output stores the original index as string
                KeyboardKey("🗑", originalIndex.toString(), KeyCodes.CLIP_DELETE, .8f)
            ))
        }

        // If no clipboard items, show a placeholder
        if (displayItems.isEmpty()) {
            rows.add(listOf(k("No clipboard items", "", 4f)))
        }

        // Bottom row: ABC + Space + Enter
        rows.add(listOf(
            a("ABC", KeyCodes.ABC, 1.3f),
            a("space", KeyCodes.SPACE, 4f),
            a("⏎", KeyCodes.ENTER, 1.3f)
        ))

        return if (isRtl) rows.map { it.reversed() } else rows
    }

    // FIX: MED-005 -- RTL support for numpad panel.
    private fun numpad(isRtl: Boolean = false): List<List<KeyboardKey>> {
        val rows = listOf(
            listOf("7","8","9").map { k(it) } + a("⌫", KeyCodes.BACKSPACE),
            listOf("4","5","6").map { k(it) } + k("/"),
            listOf("1","2","3").map { k(it) } + k("*"),
            listOf(a("ABC", KeyCodes.ABC), k("0", weight = 2f), k("."), a("⏎", KeyCodes.ENTER))
        )
        return if (isRtl) rows.map { it.reversed() } else rows
    }

    // FIX: MED-005 -- RTL support for edit panel.
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
