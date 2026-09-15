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
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.yanji.theme.*
import com.example.yanji.theme.YanjiColors

/** UI 测试定位锚点：与 androidTest 共享，避免断言依赖中文文案。 */
const val ChatInputTestTag = "chat_input"
const val ChatSendButtonTestTag = "chat_send_button"

/**
 * 思考强度模式
 */
enum class ThinkingIntensity(
    val label: String,
    val title: String,
    val description: String
) {
    DEEP("深度思考已开启", "深度思考", "标准完整推导 · 适合大题与复杂概念"),
    EXTREME("极致推理已开启", "极致推理", "高强度发散论证 · 适合压轴题拆解"),
    LIGHT("轻度思考已开启", "轻度思考", "精简思考脉络 · 响应敏捷高速"),
    OFF("深度思考已关闭", "标准模式", "直接输出回答 · 无推理耗时");

    /**
     * 强度指示点颜色。
     *
     * 写成 `@Composable` 函数而**不是**枚举构造参数：枚举常量在类初始化时求值，
     * 那个时刻读不到 `MaterialTheme`；这样写同时也让它随亮暗主题一起切换。
     */
    @Composable
    @ReadOnlyComposable
    fun dotColor(): Color = when (this) {
        DEEP -> MaterialTheme.colorScheme.tertiary
        EXTREME -> MaterialTheme.colorScheme.secondary
        LIGHT -> MaterialTheme.colorScheme.primary
        OFF -> YanjiColors.textTertiary
    }
}

@Composable
fun ComposerBar(
    inputText: String,
    onTextChange: (String) -> Unit,
    onSend: () -> Unit,
    isAiConfigured: Boolean,
    activeModel: String,
    modifier: Modifier = Modifier,
    availableModels: List<String> = emptyList(),
    onModelSelect: (String) -> Unit = {},
    thinkingIntensity: ThinkingIntensity = ThinkingIntensity.DEEP,
    onThinkingIntensityChange: (ThinkingIntensity) -> Unit = {},
    onOpenAiSettings: () -> Unit = {}
) {
    val focusManager = LocalFocusManager.current
    var modelMenuExpanded by remember { mutableStateOf(false) }
    var intensityMenuExpanded by remember { mutableStateOf(false) }

    val displayModel = if (!isAiConfigured) {
        "未配置 AI"
    } else {
        activeModel.ifBlank { "已连接 AI" }
    }

    val imeBottom = WindowInsets.ime.asPaddingValues().calculateBottomPadding()
    val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val isKeyboardOpen = imeBottom > 0.dp
    // When keyboard is open, imeBottom covers from screen bottom to keyboard top.
    // Use strictly imeBottom to avoid double-counting navigation bar height.
    val insetsBottom = if (isKeyboardOpen) imeBottom else navBottom
    val extraBottomPadding = if (isKeyboardOpen) 4.dp else 8.dp

    // Outer column with NO background (purely transparent over screen background)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(
                start = 16.dp,
                end = 16.dp,
                top = 4.dp,
                bottom = insetsBottom + extraBottomPadding
            ),
        verticalArrangement = Arrangement.spacedBy(6.dp)
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
                    color = MaterialTheme.colorScheme.surface,
                    shadowElevation = 0.5.dp,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.8f)),
                    modifier = Modifier
                        .clip(CircleShape)
                        .clickable {
                            if (!isAiConfigured) {
                                onOpenAiSettings()
                            } else {
                                modelMenuExpanded = true
                            }
                        }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Icon(
                            imageVector = if (!isAiConfigured) Icons.Default.Settings else Icons.Default.Psychology,
                            contentDescription = null,
                            tint = if (!isAiConfigured) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = displayModel,
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = if (!isAiConfigured) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Icon(
                            imageVector = Icons.Default.UnfoldMore,
                            contentDescription = "切换模型",
                            tint = YanjiColors.textTertiary,
                            modifier = Modifier.size(15.dp)
                        )
                    }
                }

                // Inline Model Selection Dropdown (Only when configured)
                if (isAiConfigured) {
                    DropdownMenu(
                        expanded = modelMenuExpanded,
                        onDismissRequest = { modelMenuExpanded = false },
                        modifier = Modifier.width(240.dp),
                        shape = RoundedCornerShape(16.dp),
                        containerColor = MaterialTheme.colorScheme.surface,
                        tonalElevation = 0.dp,
                        shadowElevation = 0.dp,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                    ) {
                        Text(
                            text = "可用 AI 模型",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            ),
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                        )

                        if (availableModels.isNotEmpty()) {
                            availableModels.forEach { modelName ->
                                val isSelected = activeModel.equals(modelName, ignoreCase = true)
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            text = modelName,
                                            style = MaterialTheme.typography.bodyMedium.copy(
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                                fontSize = 13.sp
                                            ),
                                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                        )
                                    },
                                    trailingIcon = if (isSelected) {
                                        {
                                            Icon(
                                                imageVector = Icons.Default.Check,
                                                contentDescription = "已选择",
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    } else null,
                                    onClick = {
                                        modelMenuExpanded = false
                                        onModelSelect(modelName)
                                    },
                                    modifier = Modifier
                                        .padding(horizontal = 6.dp, vertical = 1.dp)
                                        .clip(RoundedCornerShape(8.dp)),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                                )
                            }
                        } else {
                            // No list fetched yet, show current model
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = activeModel.ifBlank { "当前模型" },
                                        style = MaterialTheme.typography.bodyMedium.copy(
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.sp
                                        ),
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                },
                                trailingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = "已选择",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                },
                                onClick = { modelMenuExpanded = false },
                                modifier = Modifier
                                    .padding(horizontal = 6.dp, vertical = 1.dp)
                                    .clip(RoundedCornerShape(8.dp)),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }

                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            color = MaterialTheme.colorScheme.outlineVariant
                        )

                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = "AI 设置 / 刷新可用模型",
                                    style = MaterialTheme.typography.labelMedium.copy(
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                )
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Settings,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
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
                                .background(thinkingIntensity.dotColor(), CircleShape)
                        )
                        Text(
                            text = thinkingIntensity.label,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 11.sp,
                                color = YanjiColors.textTertiary,
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
                    containerColor = MaterialTheme.colorScheme.surface,
                    tonalElevation = 0.dp,
                    shadowElevation = 0.dp,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                ) {
                    Text(
                        text = "调节思考强度",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
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
                                            .background(intensity.dotColor(), CircleShape)
                                    )
                                    Column {
                                        Text(
                                            text = intensity.title,
                                            style = MaterialTheme.typography.bodyMedium.copy(
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                                fontSize = 13.sp
                                            ),
                                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = intensity.description,
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                fontSize = 10.sp,
                                                color = YanjiColors.textTertiary
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
                                        tint = MaterialTheme.colorScheme.primary,
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
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = 3.dp,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.6f)),
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
                        .padding(vertical = 8.dp)
                        .testTag(ChatInputTestTag),
                    textStyle = MaterialTheme.typography.bodyMedium.copy(
                        color = MaterialTheme.colorScheme.onSurface,
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
                                    color = YanjiColors.textTertiary,
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
                    color = if (inputText.isNotBlank()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primary.copy(alpha = 0.35f),
                    shadowElevation = if (inputText.isNotBlank()) 2.dp else 0.dp,
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .testTag(ChatSendButtonTestTag)
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
