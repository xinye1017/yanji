package com.example.yanji.ui.chat.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.yanji.theme.YanjiPrimary
import com.example.yanji.theme.YanjiRadius
import com.example.yanji.theme.YanjiSurface
import com.example.yanji.theme.YanjiSurfaceSoft
import com.example.yanji.theme.YanjiTextPrimary
import com.example.yanji.theme.YanjiTextTertiary

@Composable
fun ComposerBar(
    inputText: String,
    onTextChange: (String) -> Unit,
    onSend: () -> Unit,
    modifier: Modifier = Modifier
) {
    val focusManager = LocalFocusManager.current
    val inputShape = RoundedCornerShape(YanjiRadius.MessageRadius)

    Surface(
        color = YanjiSurface,
        shape = RoundedCornerShape(topStart = YanjiRadius.PageRadius, topEnd = YanjiRadius.PageRadius),
        shadowElevation = 10.dp,
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .imePadding()
                .padding(horizontal = 20.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            androidx.compose.foundation.text.BasicTextField(
                value = inputText,
                onValueChange = onTextChange,
                modifier = Modifier
                    .weight(1f)
                    .background(YanjiSurfaceSoft, inputShape)
                    .border(
                        width = 1.dp,
                        color = YanjiPrimary.copy(alpha = if (inputText.isNotBlank()) 0.26f else 0.08f),
                        shape = inputShape
                    )
                    .padding(horizontal = 14.dp, vertical = 11.dp)
                    .heightIn(min = 46.dp),
                textStyle = MaterialTheme.typography.bodyMedium.copy(color = YanjiTextPrimary),
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions.Default.copy(imeAction = ImeAction.Send),
                keyboardActions = androidx.compose.foundation.text.KeyboardActions(
                    onSend = {
                        if (inputText.isNotBlank()) {
                            onSend()
                            focusManager.clearFocus()
                        }
                    }
                ),
                decorationBox = { innerTextField ->
                    if (inputText.isEmpty()) {
                        Text(
                            text = "问问卷卷，或说说今天的心情…",
                            style = MaterialTheme.typography.bodyMedium.copy(color = YanjiTextTertiary),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(start = 4.dp)
                        )
                    }
                    innerTextField()
                }
            )

            Surface(
                shape = CircleShape,
                color = if (inputText.isNotBlank()) YanjiPrimary else YanjiPrimary.copy(alpha = 0.30f),
                shadowElevation = if (inputText.isNotBlank()) 2.dp else 0.dp,
                modifier = Modifier
                    .size(42.dp)
                    .clickable(enabled = inputText.isNotBlank()) { onSend() }
            ) {
                Box(contentAlignment = Alignment.Center) {
                    androidx.compose.material3.Icon(
                        imageVector = Icons.AutoMirrored.Filled.Send,
                        contentDescription = "发送",
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}
