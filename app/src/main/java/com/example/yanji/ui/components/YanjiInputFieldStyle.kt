package com.example.yanji.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldColors
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import com.example.yanji.theme.YanjiColors

@Composable
internal fun YanjiFormFieldLabel(
    text: String,
    modifier: Modifier = Modifier
) {
    Text(
        text = text,
        modifier = modifier,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
internal fun yanjiBorderlessTextFieldColors(): TextFieldColors =
    TextFieldDefaults.colors(
        focusedContainerColor = YanjiColors.inputFill,
        unfocusedContainerColor = YanjiColors.inputFill,
        disabledContainerColor = YanjiColors.inputFill,
        errorContainerColor = YanjiColors.inputFill,
        focusedIndicatorColor = Color.Transparent,
        unfocusedIndicatorColor = Color.Transparent,
        disabledIndicatorColor = Color.Transparent,
        errorIndicatorColor = Color.Transparent
    )
