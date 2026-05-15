package com.tapnix.keyboard.ui.keyboard

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tapnix.keyboard.data.Suggestion
import com.tapnix.keyboard.engine.GrammarEngine
import com.tapnix.keyboard.ime.TapNixIMEService
import com.tapnix.keyboard.ui.theme.LocalKeyboardTheme
import com.tapnix.keyboard.viewmodel.KeyboardViewModel

/**
 * SuggestionsBar
 *
 * Premium suggestion bar with:
 *  - Autocorrect chips (highlighted with accent color, check icon)
 *  - Next-word prediction chips (shown after space, subtle styling)
 *  - Grammar hint strip (inline correction notice)
 *  - Animated entry/exit for each chip group
 *  - Dividers between chips for readability
 *  - Horizontal scroll for overflow
 */
@Composable
fun SuggestionsBar(
    viewModel: KeyboardViewModel,
    imeService: TapNixIMEService,
) {
    val suggestions by viewModel.suggestions.collectAsStateWithLifecycle()
    val pendingCorrection by viewModel.pendingAutocorrect.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val theme = LocalKeyboardTheme.current

    val showBar = suggestions.isNotEmpty() || (pendingCorrection != null && settings.showGrammarHints)
    if (!showBar) return

    AnimatedVisibility(
        visible = showBar,
        enter = slideInVertically(initialOffsetY = { -it }, animationSpec = tween(120)) +
                fadeIn(animationSpec = tween(120)),
        exit = slideOutVertically(targetOffsetY = { -it }, animationSpec = tween(80)) +
                fadeOut(animationSpec = tween(80)),
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .height(38.dp),
            color = theme.suggestionBarBackground,
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(3.dp),
            ) {

                // ── Autocorrect chip (highest priority, shown first) ──────────
                val stableCorrection = pendingCorrection
                if (stableCorrection != null && settings.showGrammarHints) {
                    AutocorrectChip(
                        correction = stableCorrection,
                        onApply = {
                            imeService.deleteBackward(stableCorrection.original.length)
                            imeService.commitText(stableCorrection.corrected)
                            viewModel.applyAutocorrect(stableCorrection)
                        },
                        onDismiss = { viewModel.dismissAutocorrect() },
                        theme = theme,
                    )
                    if (suggestions.isNotEmpty()) {
                        VerticalDivider(
                            modifier = Modifier
                                .height(18.dp)
                                .padding(horizontal = 2.dp),
                            color = theme.keyText.copy(alpha = 0.15f),
                        )
                    }
                }

                // ── Regular suggestions / next-word predictions ───────────────
                suggestions.forEachIndexed { index, suggestion ->
                    SuggestionChip(
                        suggestion = suggestion,
                        onClick = {
                            imeService.commitText(suggestion.text + " ")
                            viewModel.onSuggestionChosen(suggestion.text)
                        },
                        theme = theme,
                    )

                    if (index < suggestions.size - 1) {
                        VerticalDivider(
                            modifier = Modifier
                                .height(16.dp)
                                .padding(horizontal = 2.dp),
                            color = theme.keyText.copy(alpha = 0.15f),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SuggestionChip(
    suggestion: Suggestion,
    onClick: () -> Unit,
    theme: com.tapnix.keyboard.data.KeyboardTheme,
) {
    val isNextWord = suggestion.isNextWord
    val isAutocorrect = suggestion.isAutocorrect

    val bgColor = when {
        isAutocorrect -> theme.accentColor.copy(alpha = 0.18f)
        isNextWord -> theme.accentColor.copy(alpha = 0.08f)
        else -> Color.Transparent
    }

    val textColor = when {
        isAutocorrect -> theme.accentColor
        else -> theme.keyText
    }

    val fontWeight = when {
        isAutocorrect -> FontWeight.SemiBold
        isNextWord -> FontWeight.Normal
        else -> FontWeight.Normal
    }

    TextButton(
        onClick = onClick,
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
        modifier = if (bgColor != Color.Transparent) {
            Modifier
                .clip(RoundedCornerShape(6.dp))
                .background(bgColor)
        } else Modifier,
    ) {
        if (isAutocorrect) {
            Icon(
                Icons.Default.Check,
                contentDescription = null,
                modifier = Modifier
                    .size(11.dp)
                    .padding(end = 2.dp),
                tint = theme.accentColor,
            )
        }
        Text(
            text = suggestion.text,
            style = MaterialTheme.typography.bodySmall.copy(
                fontWeight = fontWeight,
                fontSize = if (isNextWord) 12.sp else 13.sp,
                color = textColor,
            ),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun AutocorrectChip(
    correction: GrammarEngine.Correction,
    onApply: () -> Unit,
    onDismiss: () -> Unit,
    theme: com.tapnix.keyboard.data.KeyboardTheme,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(theme.accentColor.copy(alpha = 0.12f))
            .padding(horizontal = 6.dp, vertical = 1.dp),
    ) {
        Icon(
            Icons.Default.AutoFixHigh,
            contentDescription = "Autocorrect",
            modifier = Modifier.size(11.dp),
            tint = theme.accentColor,
        )
        Spacer(Modifier.width(2.dp))
        Text(
            text = correction.original,
            style = MaterialTheme.typography.bodySmall.copy(
                fontSize = 11.sp,
                color = theme.keyText.copy(alpha = 0.5f),
            ),
            maxLines = 1,
        )
        Text(
            text = "→",
            style = MaterialTheme.typography.bodySmall.copy(
                fontSize = 10.sp,
                color = theme.keyText.copy(alpha = 0.4f),
            ),
        )
        TextButton(
            onClick = onApply,
            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp),
        ) {
            Text(
                text = correction.corrected,
                style = MaterialTheme.typography.bodySmall.copy(
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = theme.accentColor,
                ),
                maxLines = 1,
            )
        }
    }
}
