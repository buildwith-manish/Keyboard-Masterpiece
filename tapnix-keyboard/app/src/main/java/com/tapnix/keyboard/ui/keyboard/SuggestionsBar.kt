package com.tapnix.keyboard.ui.keyboard

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tapnix.keyboard.ime.TapNixIMEService
import com.tapnix.keyboard.ui.theme.LocalKeyboardTheme
import com.tapnix.keyboard.viewmodel.KeyboardViewModel

@Composable
fun SuggestionsBar(
    viewModel: KeyboardViewModel,
    imeService: TapNixIMEService,
) {
    val suggestions by viewModel.suggestions.collectAsStateWithLifecycle()
    val theme = LocalKeyboardTheme.current

    if (suggestions.isEmpty()) return

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(36.dp),
        color = theme.suggestionBarBackground,
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            suggestions.forEach { suggestion ->
                TextButton(
                    onClick = {
                        imeService.commitText(suggestion.text + " ")
                        viewModel.onSuggestionChosen(suggestion.text)
                    },
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                ) {
                    Text(
                        text = suggestion.text,
                        style = MaterialTheme.typography.bodySmall,
                        color = theme.keyText,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                if (suggestions.indexOf(suggestion) < suggestions.size - 1) {
                    VerticalDivider(
                        modifier = Modifier
                            .height(16.dp)
                            .padding(horizontal = 2.dp),
                        color = theme.keyText.copy(alpha = 0.2f),
                    )
                }
            }
        }
    }
}
