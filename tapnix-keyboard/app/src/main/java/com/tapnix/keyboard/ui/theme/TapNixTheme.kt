package com.tapnix.keyboard.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import com.tapnix.keyboard.data.KeyboardTheme

val LocalKeyboardTheme = staticCompositionLocalOf { KeyboardTheme.Default }

@Composable
fun TapNixTheme(
    keyboardTheme: KeyboardTheme = KeyboardTheme.Default,
    content: @Composable () -> Unit,
) {
    val colorScheme = if (keyboardTheme.isAmoled) {
        darkColorScheme(
            primary = keyboardTheme.accentColor,
            background = Color.Black,
            surface = Color.Black,
            onBackground = Color.White,
            onSurface = Color.White,
        )
    } else {
        lightColorScheme(
            primary = keyboardTheme.accentColor,
            background = keyboardTheme.panelBackground,
            surface = keyboardTheme.keyBackground,
            onBackground = keyboardTheme.keyText,
            onSurface = keyboardTheme.keyText,
        )
    }

    CompositionLocalProvider(LocalKeyboardTheme provides keyboardTheme) {
        MaterialTheme(
            colorScheme = colorScheme,
            content = content,
        )
    }
}

@Composable
fun TapNixTheme(content: @Composable () -> Unit) {
    TapNixTheme(keyboardTheme = KeyboardTheme.Default, content = content)
}
