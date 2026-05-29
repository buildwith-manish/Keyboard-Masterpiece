package com.keyboardmasterpiece.engine

import android.graphics.Color

/**
 * Professional theme palette system with 10 themes.
 * Each theme defines: background, key, action key, space key, border, text, accent, path, popup, suggestion highlight colors.
 * Feature 4: Multi-Theme System — replaces the old light/dark boolean toggle with 8+ professional themes.
 */
object ThemePalette {
    data class Theme(
        val name: String,
        val bgColor: Int,
        val keyColor: Int,
        val actionKeyColor: Int,
        val spaceKeyColor: Int,
        val borderColor: Int,
        val textColor: Int,
        val accentColor: Int,
        val pathColor: Int,
        val popupColor: Int,
        val suggestionHighlightColor: Int
    )

    val THEMES = listOf(
        // 0: Classic Light
        Theme("Classic Light",
            bgColor = Color.rgb(238, 241, 246),
            keyColor = Color.WHITE,
            actionKeyColor = Color.rgb(218, 224, 235),
            spaceKeyColor = Color.WHITE,
            borderColor = Color.rgb(210, 215, 225),
            textColor = Color.rgb(24, 27, 32),
            accentColor = Color.rgb(79, 124, 255),
            pathColor = Color.argb(160, 79, 124, 255),
            popupColor = Color.WHITE,
            suggestionHighlightColor = Color.argb(40, 79, 124, 255)
        ),
        // 1: Classic Dark
        Theme("Classic Dark",
            bgColor = Color.rgb(21, 23, 28),
            keyColor = Color.rgb(42, 45, 52),
            actionKeyColor = Color.rgb(55, 59, 68),
            spaceKeyColor = Color.rgb(48, 52, 60),
            borderColor = Color.rgb(68, 72, 82),
            textColor = Color.WHITE,
            accentColor = Color.rgb(79, 124, 255),
            pathColor = Color.argb(160, 79, 124, 255),
            popupColor = Color.rgb(32, 34, 40),
            suggestionHighlightColor = Color.argb(50, 79, 124, 255)
        ),
        // 2: AMOLED Black
        Theme("AMOLED Black",
            bgColor = Color.BLACK,
            keyColor = Color.rgb(18, 18, 18),
            actionKeyColor = Color.rgb(28, 28, 28),
            spaceKeyColor = Color.rgb(12, 12, 12),
            borderColor = Color.rgb(40, 40, 40),
            textColor = Color.WHITE,
            accentColor = Color.rgb(100, 180, 255),
            pathColor = Color.argb(160, 100, 180, 255),
            popupColor = Color.rgb(10, 10, 10),
            suggestionHighlightColor = Color.argb(40, 100, 180, 255)
        ),
        // 3: Ocean Blue
        Theme("Ocean Blue",
            bgColor = Color.rgb(15, 25, 45),
            keyColor = Color.rgb(25, 45, 75),
            actionKeyColor = Color.rgb(35, 60, 100),
            spaceKeyColor = Color.rgb(20, 35, 60),
            borderColor = Color.rgb(50, 80, 130),
            textColor = Color.rgb(200, 220, 255),
            accentColor = Color.rgb(80, 180, 255),
            pathColor = Color.argb(160, 80, 180, 255),
            popupColor = Color.rgb(20, 35, 60),
            suggestionHighlightColor = Color.argb(50, 80, 180, 255)
        ),
        // 4: Rose Pink
        Theme("Rose Pink",
            bgColor = Color.rgb(45, 15, 25),
            keyColor = Color.rgb(75, 25, 45),
            actionKeyColor = Color.rgb(100, 35, 60),
            spaceKeyColor = Color.rgb(60, 20, 35),
            borderColor = Color.rgb(130, 50, 80),
            textColor = Color.rgb(255, 200, 220),
            accentColor = Color.rgb(255, 100, 150),
            pathColor = Color.argb(160, 255, 100, 150),
            popupColor = Color.rgb(60, 20, 35),
            suggestionHighlightColor = Color.argb(50, 255, 100, 150)
        ),
        // 5: Forest Green
        Theme("Forest Green",
            bgColor = Color.rgb(15, 30, 20),
            keyColor = Color.rgb(25, 50, 35),
            actionKeyColor = Color.rgb(35, 70, 45),
            spaceKeyColor = Color.rgb(20, 40, 28),
            borderColor = Color.rgb(50, 100, 65),
            textColor = Color.rgb(200, 255, 220),
            accentColor = Color.rgb(80, 255, 130),
            pathColor = Color.argb(160, 80, 255, 130),
            popupColor = Color.rgb(20, 40, 28),
            suggestionHighlightColor = Color.argb(50, 80, 255, 130)
        ),
        // 6: Sunset Orange
        Theme("Sunset Orange",
            bgColor = Color.rgb(40, 20, 15),
            keyColor = Color.rgb(65, 35, 25),
            actionKeyColor = Color.rgb(90, 45, 30),
            spaceKeyColor = Color.rgb(50, 25, 18),
            borderColor = Color.rgb(120, 60, 40),
            textColor = Color.rgb(255, 220, 180),
            accentColor = Color.rgb(255, 150, 50),
            pathColor = Color.argb(160, 255, 150, 50),
            popupColor = Color.rgb(50, 25, 18),
            suggestionHighlightColor = Color.argb(50, 255, 150, 50)
        ),
        // 7: Material You (Dynamic pastel)
        Theme("Material You",
            bgColor = Color.rgb(230, 225, 240),
            keyColor = Color.rgb(245, 240, 255),
            actionKeyColor = Color.rgb(215, 205, 235),
            spaceKeyColor = Color.rgb(240, 235, 250),
            borderColor = Color.rgb(190, 180, 210),
            textColor = Color.rgb(30, 20, 50),
            accentColor = Color.rgb(120, 80, 200),
            pathColor = Color.argb(160, 120, 80, 200),
            popupColor = Color.rgb(240, 235, 250),
            suggestionHighlightColor = Color.argb(40, 120, 80, 200)
        ),
        // 8: Nord Frost
        Theme("Nord Frost",
            bgColor = Color.rgb(46, 52, 64),
            keyColor = Color.rgb(59, 66, 82),
            actionKeyColor = Color.rgb(67, 76, 94),
            spaceKeyColor = Color.rgb(55, 62, 78),
            borderColor = Color.rgb(76, 86, 106),
            textColor = Color.rgb(216, 222, 233),
            accentColor = Color.rgb(136, 192, 208),
            pathColor = Color.argb(160, 136, 192, 208),
            popupColor = Color.rgb(55, 62, 78),
            suggestionHighlightColor = Color.argb(50, 136, 192, 208)
        ),
        // 9: Dracula
        Theme("Dracula",
            bgColor = Color.rgb(40, 42, 54),
            keyColor = Color.rgb(55, 57, 73),
            actionKeyColor = Color.rgb(68, 71, 90),
            spaceKeyColor = Color.rgb(48, 50, 64),
            borderColor = Color.rgb(88, 91, 112),
            textColor = Color.rgb(248, 248, 242),
            accentColor = Color.rgb(189, 147, 249),
            pathColor = Color.argb(160, 189, 147, 249),
            popupColor = Color.rgb(48, 50, 64),
            suggestionHighlightColor = Color.argb(50, 189, 147, 249)
        )
    )

    fun current(prefs: UserPreferences): Theme = THEMES[prefs.themeIndex]

    fun cycle(prefs: UserPreferences): Theme {
        prefs.themeIndex = (prefs.themeIndex + 1) % THEMES.size
        return current(prefs)
    }
}
