package com.example.yanji.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.yanji.theme.*

/**
 * 研迹标准文本输入框。
 * 浅灰底、轻边框、圆角 16dp，聚焦时转主色。
 */
@Composable
fun YanjiTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    placeholder: String? = null,
    isError: Boolean = false,
    supportingText: String? = null,
    singleLine: Boolean = false,
    maxLines: Int = if (singleLine) 1 else Int.MAX_VALUE,
    leadingIcon: (@Composable () -> Unit)? = null,
    trailingIcon: (@Composable () -> Unit)? = null,
    enabled: Boolean = true
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth(),
        enabled = enabled,
        singleLine = singleLine,
        maxLines = maxLines,
        label = label?.let { { Text(it) } },
        placeholder = placeholder?.let { { Text(it, color = YanjiTextTertiary) } },
        isError = isError,
        supportingText = supportingText?.let { { Text(it) } },
        leadingIcon = leadingIcon,
        trailingIcon = trailingIcon,
        shape = RoundedCornerShape(YanjiRadius.ContentBlockRadius),
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = YanjiSurface,
            unfocusedContainerColor = YanjiSurfaceSoft,
            focusedBorderColor = YanjiPrimary,
            unfocusedBorderColor = YanjiBorder,
            errorBorderColor = YanjiDanger,
            focusedTextColor = YanjiTextPrimary,
            unfocusedTextColor = YanjiTextPrimary
        )
    )
}
