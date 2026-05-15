package com.tapnix.keyboard.data

object KeyboardLayout {

    val QWERTY_ROWS: List<List<KeyDef>> = listOf(
        // Row 1: Q-P
        listOf(
            KeyDef('q', listOf('1', '!')),
            KeyDef('w', listOf('2', '@')),
            KeyDef('e', listOf('3', '#', 'è', 'é', 'ê', 'ë')),
            KeyDef('r', listOf('4', '$', 'ř')),
            KeyDef('t', listOf('5', '%', 'þ', 'ţ')),
            KeyDef('y', listOf('6', '^', 'ý', 'ÿ')),
            KeyDef('u', listOf('7', '&', 'ù', 'ú', 'û', 'ü')),
            KeyDef('i', listOf('8', '*', 'ì', 'í', 'î', 'ï')),
            KeyDef('o', listOf('9', '(', 'ò', 'ó', 'ô', 'ö', 'ø')),
            KeyDef('p', listOf('0', ')', 'ψ')),
        ),
        // Row 2: A-L
        listOf(
            KeyDef('a', listOf('à', 'á', 'â', 'ã', 'ä', 'å', 'æ')),
            KeyDef('s', listOf('ß', 'š', 'ś')),
            KeyDef('d', listOf('ð', 'ď')),
            KeyDef('f', listOf()),
            KeyDef('g', listOf()),
            KeyDef('h', listOf()),
            KeyDef('j', listOf()),
            KeyDef('k', listOf()),
            KeyDef('l', listOf('ł')),
        ),
        // Row 3: Shift, Z-M, Backspace
        listOf(
            KeyDef('z', listOf('ź', 'ž', 'ż')),
            KeyDef('x', listOf()),
            KeyDef('c', listOf('ç', 'č', 'ć')),
            KeyDef('v', listOf()),
            KeyDef('b', listOf()),
            KeyDef('n', listOf('ñ', 'ń')),
            KeyDef('m', listOf()),
        ),
    )

    val NUMERIC_ROWS: List<List<KeyDef>> = listOf(
        listOf(
            KeyDef('1'), KeyDef('2'), KeyDef('3'), KeyDef('4'), KeyDef('5'),
            KeyDef('6'), KeyDef('7'), KeyDef('8'), KeyDef('9'), KeyDef('0'),
        ),
        listOf(
            KeyDef('-'), KeyDef('/'), KeyDef(':'), KeyDef(';'),
            KeyDef('('), KeyDef(')'), KeyDef('$'), KeyDef('&'), KeyDef('@'), KeyDef('"'),
        ),
        listOf(
            KeyDef('['), KeyDef(']'), KeyDef('{'), KeyDef('}'), KeyDef('#'),
            KeyDef('%'), KeyDef('^'), KeyDef('*'), KeyDef('+'), KeyDef('='),
        ),
        listOf(
            KeyDef('_'), KeyDef('\\'), KeyDef('|'), KeyDef('~'), KeyDef('<'),
            KeyDef('>'), KeyDef('€'), KeyDef('£'), KeyDef('¥'), KeyDef('•'),
        ),
    )
}
