package com.example.yanji.ui.chat.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.UnfoldMore
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.yanji.theme.*

@Composable
fun ComposerBar(
    inputText: String,
    onTextChange: (String) -> Unit,
    onSend: () -> Unit,
    activeModel: String = "DeepSeek-R1",
    onModelClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val focusManager = LocalFocusManager.current

    val displayModel = when {
        activeModel.contains("r1", ignoreCase = true) || activeModel.contains("reasoner", ignoreCase = true) -> "DeepSeek-R1 (深度推理)"
        activeModel.contains("v3", ignoreCase = true) -> "DeepSeek-V3"
        activeModel.contains("chat", ignoreCase = true) -> "DeepSeek-Chat"
        activeModel.contains("glm", ignoreCase = true) -> "GLM-4"
        activeModel.contains("gpt", ignoreCase = true) -> "GPT-4o"
        activeModel.contains("qwen", ignoreCase = true) -> "Qwen 2.5"
        else -> activeModel.ifBlank { "DeepSeek-R1 (深度推理)" }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .imePadding()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Model Indicator & Thinking Status Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = CircleShape,
                color = YanjiSurface,
                shadowElevation = 1.dp,
                border = BorderStroke(1.dp, YanjiBorder.copy(alpha = 0.8f)),
                modifier = Modifier.clickable { onModelClick() }
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Psychology,
                        contentDescription = null,
                        tint = YanjiLavender,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = displayModel,
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = YanjiTextPrimary
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Icon(
                        imageVector = Icons.Default.UnfoldMore,
                        contentDescription = "切换模型",
                        tint = YanjiTextTertiary,
                        modifier = Modifier.size(15.dp)
                    )
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .background(YanjiSuccess, CircleShape)
                )
                Text(
                    text = "深度思考已开启",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 11.sp,
                        color = YanjiTextTertiary
                    )
                )
            }
        }

        // Pill-shaped input container
        Surface(
            shape = CircleShape,
            color = YanjiSurface,
            shadowElevation = 4.dp,
            border = BorderStroke(1.dp, YanjiBorder.copy(alpha = 0.6f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                BasicTextField(
                    value = inputText,
                    onValueChange = onTextChange,
                    modifier = Modifier
                        .weight(1f)
                        .padding(vertical = 8.dp),
                    textStyle = MaterialTheme.typography.bodyMedium.copy(
                        color = YanjiTextPrimary,
                        fontSize = 14.sp
                    ),
                    keyboardOptions = KeyboardOptions.Default.copy(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(
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
                                text = "有问题尽管问卷卷……",
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    color = YanjiTextTertiary,
                                    fontSize = 14.sp
                                ),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        innerTextField()
                    }
                )

                Spacer(modifier = Modifier.width(6.dp))

                // Send button (40dp circle)
                Surface(
                    shape = CircleShape,
                    color = if (inputText.isNotBlank()) YanjiPrimary else YanjiPrimary.copy(alpha = 0.35f),
                    shadowElevation = if (inputText.isNotBlank()) 2.dp else 0.dp,
                    modifier = Modifier
                        .size(40.dp)
                        .clickable(enabled = inputText.isNotBlank()) {
                            onSend()
                            focusManager.clearFocus()
                        }
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.fillMaxSize()
                    ) {
                        Icon(
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
}
