package com.tapnix.keyboard.ui.keyboard

import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tapnix.keyboard.data.KeyDef
import com.tapnix.keyboard.ui.theme.LocalKeyboardTheme

/**
 * KeyButton
 *
 * A single keyboard key with:
 *  - Press animation (scale down)
 *  - Haptic feedback on press
 *  - Long-press support (calls onLongPressStart/End for repeat engine)
 *  - Optional long-press symbol shown in top-right corner
 *  - Custom width weight for special keys
 */
@Composable
fun KeyButton(
    keyDef: KeyDef,
    shifted: Boolean,
    onTap: () -> Unit,
    onLongPressStart: ((String) -> Unit)? = null,
    onLongPressEnd: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    label: String? = null,
    backgroundColor: Color? = null,
    textColor: Color? = null,
    minWidth: Dp = 28.dp,
    minHeight: Dp = 46.dp,
) {
    val theme = LocalKeyboardTheme.current
    val haptic = LocalHapticFeedback.current
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.88f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "KeyScale",
    )

    val displayLabel = label ?: run {
        val c = if (shifted) keyDef.char.uppercaseChar() else keyDef.char
        c.toString()
    }
    val longPressSymbol = keyDef.longPressChars.firstOrNull()?.toString()
    val bgColor = backgroundColor ?: theme.keyBackground
    val fgColor = textColor ?: theme.keyText
    val cornerShape = RoundedCornerShape(theme.keyCornerRadius.dp)

    Box(
        modifier = modifier
            .widthIn(min = minWidth)
            .height(minHeight)
            .padding(horizontal = 2.dp, vertical = 3.dp)
            .shadow(
                elevation = if (isPressed) 0.dp else theme.shadowElevation.dp,
                shape = cornerShape,
                clip = false,
            )
            .clip(cornerShape)
            .background(if (isPressed) theme.keyPressed else bgColor)
            .combinedClickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onTap()
                },
                onLongClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onLongPressStart?.invoke(displayLabel)
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        // Long-press hint symbol (top-right corner)
        if (longPressSymbol != null) {
            Text(
                text = longPressSymbol,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 9.sp,
                    color = fgColor.copy(alpha = 0.5f),
                    fontWeight = FontWeight.Light,
                ),
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 2.dp, end = 3.dp),
            )
        }

        // Main key label
        Text(
            text = displayLabel,
            style = MaterialTheme.typography.bodyMedium.copy(
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                color = fgColor,
            ),
        )
    }
}

/**
 * FunctionKey — for Shift, Backspace, Enter, Space, panel-switch keys.
 */
@Composable
fun FunctionKeyButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    minWidth: Dp = 40.dp,
    minHeight: Dp = 46.dp,
) {
    val theme = LocalKeyboardTheme.current
    val haptic = LocalHapticFeedback.current
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val cornerShape = RoundedCornerShape(theme.keyCornerRadius.dp)

    Box(
        modifier = modifier
            .widthIn(min = minWidth)
            .height(minHeight)
            .padding(horizontal = 2.dp, vertical = 3.dp)
            .shadow(
                elevation = if (isPressed) 0.dp else theme.shadowElevation.dp,
                shape = cornerShape,
                clip = false,
            )
            .clip(cornerShape)
            .background(
                if (isPressed) theme.keyPressed
                else theme.keyBackground.copy(alpha = 0.7f)
            )
            .combinedClickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onClick()
                },
                onLongClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onLongClick?.invoke()
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall.copy(
                fontWeight = FontWeight.SemiBold,
                color = theme.keyText,
                fontSize = 13.sp,
            ),
        )
    }
}
