package com.example.yanji.ui.chat.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.UnfoldMore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.yanji.theme.*

/**
 * 思考强度模式
 */
enum class ThinkingIntensity(
    val label: String,
    val title: String,
    val description: String,
    val dotColor: Color
) {
    DEEP("深度思考已开启", "深度思考", "标准完整推导 · 适合大题与复杂概念", YanjiSuccess),
    EXTREME("极致推理已开启", "极致推理", "高强度发散论证 · 适合压轴题拆解", YanjiLavender),
    LIGHT("轻度思考已开启", "轻度思考", "精简思考脉络 · 响应敏捷高速", YanjiPrimary),
    OFF("深度思考已关闭", "标准模式", "直接输出回答 · 无推理耗时", YanjiTextTertiary)
}

/**
 * 可选模型定义
 */
data class ModelOption(
    val id: String,
    val displayName: String,
    val description: String
)

val AVAILABLE_MODELS = listOf(
    ModelOption("deepseek-reasoner", "DeepSeek-R1", "深度推理 · 强化学习长链思考"),
    ModelOption("deepseek-chat", "DeepSeek-V3", "全面多能 · 高速日常伴学"),
    ModelOption("glm-4-flash", "智谱 GLM-4", "开放平台 · 响应极速"),
    ModelOption("gpt-4o-mini", "GPT-4o Mini", "OpenAI · 均衡高效"),
    ModelOption("qwen2.5:latest", "Qwen 2.5", "本地/通义 · 语义理解")
)

fun getModelDisplayName(modelId: String): String {
    val lower = modelId.lowercase()
    return when {
        lower.contains("reasoner") || lower.contains("r1") -> "DeepSeek-R1 (深度推理)"
        lower.contains("v3") -> "DeepSeek-V3"
        lower.contains("chat") -> "DeepSeek-V3"
        lower.contains("glm") -> "智谱 GLM-4"
        lower.contains("gpt") -> "GPT-4o Mini"
        lower.contains("qwen") -> "Qwen 2.5"
        else -> modelId.ifBlank { "DeepSeek-R1 (深度推理)" }
    }
}

@Composable
fun ComposerBar(
    inputText: String,
    onTextChange: (String) -> Unit,
    onSend: () -> Unit,
    activeModel: String = "deepseek-reasoner",
    onModelSelect: (String) -> Unit = {},
    thinkingIntensity: ThinkingIntensity = ThinkingIntensity.DEEP,
    onThinkingIntensityChange: (ThinkingIntensity) -> Unit = {},
    onOpenAiSettings: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val focusManager = LocalFocusManager.current
    var modelMenuExpanded by remember { mutableStateOf(false) }
    var intensityMenuExpanded by remember { mutableStateOf(false) }

    val displayModel = getModelDisplayName(activeModel)

    // Outer column with NO background (purely transparent over screen background)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .imePadding()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Model Indicator & Thinking Status Row (No overall background)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left: Model Selection Pill + Dropdown Menu
            Box {
                Surface(
                    shape = CircleShape,
                    color = YanjiSurface,
                    shadowElevation = 0.5.dp,
                    border = BorderStroke(1.dp, YanjiBorder.copy(alpha = 0.8f)),
                    modifier = Modifier.clip(CircleShape).clickable { modelMenuExpanded = true }
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

                // Inline Model Selection Dropdown (Zero shadow)
                DropdownMenu(
                    expanded = modelMenuExpanded,
                    onDismissRequest = { modelMenuExpanded = false },
                    modifier = Modifier.width(240.dp),
                    shape = RoundedCornerShape(16.dp),
                    containerColor = YanjiSurface,
                    tonalElevation = 0.dp,
                    shadowElevation = 0.dp,
                    border = BorderStroke(1.dp, YanjiBorder)
                ) {
                    Text(
                        text = "选择 AI 模型",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            color = YanjiTextSecondary
                        ),
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                    )

                    AVAILABLE_MODELS.forEach { model ->
                        val isSelected = activeModel.equals(model.id, ignoreCase = true) ||
                                (model.id == "deepseek-reasoner" && (activeModel.contains("reasoner", ignoreCase = true) || activeModel.contains("r1", ignoreCase = true))) ||
                                (model.id == "deepseek-chat" && activeModel.contains("chat", ignoreCase = true) && !activeModel.contains("reasoner", ignoreCase = true))

                        DropdownMenuItem(
                            text = {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = model.displayName,
                                        style = MaterialTheme.typography.bodyMedium.copy(
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                            fontSize = 13.sp
                                        ),
                                        color = if (isSelected) YanjiPrimary else YanjiTextPrimary
                                    )
                                    Text(
                                        text = model.description,
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontSize = 10.sp,
                                            color = YanjiTextTertiary
                                        ),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            },
                            trailingIcon = if (isSelected) {
                                {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = "已选择",
                                        tint = YanjiPrimary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            } else null,
                            onClick = {
                                modelMenuExpanded = false
                                onModelSelect(model.id)
                            },
                            modifier = Modifier
                                .padding(horizontal = 6.dp, vertical = 1.dp)
                                .clip(RoundedCornerShape(8.dp)),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }

                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        color = YanjiDivider
                    )

                    DropdownMenuItem(
                        text = {
                            Text(
                                text = "更多 AI 配置 / API 密钥",
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontSize = 12.sp,
                                    color = YanjiTextSecondary
                                )
                            )
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = null,
                                tint = YanjiTextSecondary,
                                modifier = Modifier.size(16.dp)
                            )
                        },
                        onClick = {
                            modelMenuExpanded = false
                            onOpenAiSettings()
                        },
                        modifier = Modifier
                            .padding(horizontal = 6.dp, vertical = 1.dp)
                            .clip(RoundedCornerShape(8.dp)),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }

            // Right: Thinking Intensity Indicator & Dropdown Menu
            Box {
                Surface(
                    shape = CircleShape,
                    color = Color.Transparent,
                    modifier = Modifier
                        .clip(CircleShape)
                        .clickable { intensityMenuExpanded = true }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .background(thinkingIntensity.dotColor, CircleShape)
                        )
                        Text(
                            text = thinkingIntensity.label,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 11.sp,
                                color = YanjiTextTertiary,
                                fontWeight = FontWeight.Medium
                            )
                        )
                    }
                }

                // Inline Thinking Intensity Dropdown (Zero shadow)
                DropdownMenu(
                    expanded = intensityMenuExpanded,
                    onDismissRequest = { intensityMenuExpanded = false },
                    modifier = Modifier.width(220.dp),
                    shape = RoundedCornerShape(16.dp),
                    containerColor = YanjiSurface,
                    tonalElevation = 0.dp,
                    shadowElevation = 0.dp,
                    border = BorderStroke(1.dp, YanjiBorder)
                ) {
                    Text(
                        text = "调节思考强度",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            color = YanjiTextSecondary
                        ),
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                    )

                    ThinkingIntensity.values().forEach { intensity ->
                        val isSelected = intensity == thinkingIntensity

                        DropdownMenuItem(
                            text = {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .background(intensity.dotColor, CircleShape)
                                    )
                                    Column {
                                        Text(
                                            text = intensity.title,
                                            style = MaterialTheme.typography.bodyMedium.copy(
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                                fontSize = 13.sp
                                            ),
                                            color = if (isSelected) YanjiPrimary else YanjiTextPrimary
                                        )
                                        Text(
                                            text = intensity.description,
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                fontSize = 10.sp,
                                                color = YanjiTextTertiary
                                            ),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            },
                            trailingIcon = if (isSelected) {
                                {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = "已选择",
                                        tint = YanjiPrimary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            } else null,
                            onClick = {
                                intensityMenuExpanded = false
                                onThinkingIntensityChange(intensity)
                            },
                            modifier = Modifier
                                .padding(horizontal = 6.dp, vertical = 1.dp)
                                .clip(RoundedCornerShape(8.dp)),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                }
            }
        }

        // Pill-shaped input container (The ONLY element with a solid background)
        Surface(
            shape = CircleShape,
            color = YanjiSurface,
            shadowElevation = 3.dp,
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
                        .clip(CircleShape)
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
