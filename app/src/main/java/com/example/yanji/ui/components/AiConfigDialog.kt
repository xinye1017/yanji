package com.example.yanji.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.PopupProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.yanji.data.ai.AiProtocolType
import com.example.yanji.data.security.CleartextPolicy
import com.example.yanji.di.yanjiViewModel
import com.example.yanji.theme.*
import com.example.yanji.ui.icons.RemixIcons
import kotlinx.coroutines.launch

private data class ProviderPreset(
    val name: String,
    val baseUrl: String,
    val defaultModel: String,
    val protocol: AiProtocolType = AiProtocolType.OPENAI_CHAT,
    val tag: String = ""
)

private val AI_PRESETS = listOf(
    ProviderPreset("DeepSeek", "https://api.deepseek.com/v1", "deepseek-chat", AiProtocolType.OPENAI_CHAT, "推荐"),
    ProviderPreset("硅基流动", "https://api.siliconflow.cn/v1", "deepseek-ai/DeepSeek-V3", AiProtocolType.OPENAI_CHAT, "聚合"),
    ProviderPreset("智谱 GLM", "https://open.bigmodel.cn/api/paas/v4", "glm-4-flash", AiProtocolType.OPENAI_CHAT, "开放平台"),
    ProviderPreset("OpenAI", "https://api.openai.com/v1", "gpt-4o-mini", AiProtocolType.OPENAI_CHAT, "官方"),
    ProviderPreset("Anthropic", "https://api.anthropic.com", "claude-3-5-sonnet-20241022", AiProtocolType.ANTHROPIC, "Claude"),
    ProviderPreset("本地 Ollama", "http://192.168.1.100:11434/v1", "qwen2.5:latest", AiProtocolType.OPENAI_CHAT, "本地/内网"),
    ProviderPreset("自定义", "", "", AiProtocolType.OPENAI_CHAT, "手动输入")
)

@Composable
fun AiConfigDialog(
    onDismissRequest: () -> Unit,
    viewModel: AiConfigViewModel = yanjiViewModel { container -> AiConfigViewModel(container.repository) }
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val settings = state.settings
    var provider by remember(settings.aiProvider) { mutableStateOf(settings.aiProvider) }
    var protocol by remember(settings.aiProtocol) { mutableStateOf(AiProtocolType.fromId(settings.aiProtocol)) }
    var baseUrl by remember(settings.aiBaseUrl) { mutableStateOf(settings.aiBaseUrl) }
    var apiKey by remember(settings.aiApiKey) { mutableStateOf(settings.aiApiKey) }
    var model by remember(settings.aiModel) { mutableStateOf(settings.aiModel) }
    var showPassword by remember { mutableStateOf(false) }

    val coroutineScope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current

    var isTestingConnection by remember { mutableStateOf(false) }
    var isFetchingModelsOnly by remember { mutableStateOf(false) }
    var connectionMessage by remember { mutableStateOf<String?>(null) }
    var connectionSucceeded by remember { mutableStateOf<Boolean?>(null) }
    var availableModels by remember { mutableStateOf<List<String>>(emptyList()) }
    var modelDropdownExpanded by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismissRequest,
        shape = RoundedCornerShape(22.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(RoundedCornerShape(YanjiRadius.ItemRadius))
                            .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = RemixIcons.BrainLine,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Column {
                        Text(
                            text = "AI API 配置",
                            fontWeight = FontWeight.Bold,
                            fontSize = 17.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "配置大模型以支持诊断答疑与个性化复盘",
                            fontSize = 11.sp,
                            color = YanjiColors.textTertiary
                        )
                    }
                }
                IconButton(
                    onClick = onDismissRequest,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = RemixIcons.CloseLine,
                        contentDescription = "关闭",
                        tint = YanjiColors.textTertiary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                // 1. 服务商快捷预设横条
                Text(
                    text = "快速预设服务商",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    AI_PRESETS.forEach { preset ->
                        val isSelected = (preset.name == "自定义" && (provider == "自定义" || (provider.isNotBlank() && AI_PRESETS.none { it.name != "自定义" && it.name == provider }))) ||
                                (preset.name != "自定义" && provider == preset.name)
                        Surface(
                            shape = RoundedCornerShape(YanjiRadius.ItemRadius),
                            color = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                            border = BorderStroke(
                                1.dp,
                                if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                            ),
                            modifier = Modifier.clickable {
                                keyboardController?.hide()
                                focusManager.clearFocus()
                                if (preset.name != "自定义") {
                                    provider = preset.name
                                    baseUrl = preset.baseUrl
                                    model = preset.defaultModel
                                    protocol = preset.protocol
                                } else {
                                    provider = "自定义"
                                }
                                connectionMessage = null
                                connectionSucceeded = null
                            }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                if (isSelected) {
                                    Icon(
                                        imageVector = RemixIcons.CheckLine,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(13.dp)
                                    )
                                }
                                Text(
                                    text = preset.name,
                                    fontSize = 12.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                )
                                if (preset.tag.isNotBlank() && preset.name != "自定义") {
                                    Text(
                                        text = preset.tag,
                                        fontSize = 10.sp,
                                        color = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.8f) else YanjiColors.textTertiary
                                    )
                                }
                            }
                        }
                    }
                }

                // 若选择自定义或非预设服务商，显示自定义服务商输入框
                if (provider == "自定义" || (provider.isNotBlank() && AI_PRESETS.none { it.name == provider && it.name != "自定义" })) {
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = if (provider == "自定义") "" else provider,
                        onValueChange = { provider = it },
                        label = { Text("服务商名称") },
                        placeholder = { Text("如 MiniMax、Moonshot 等") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(YanjiRadius.ItemRadius),
                        singleLine = true
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // 2. 接口协议格式分段选择
                Text(
                    text = "接口协议规范",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    AiProtocolType.entries.forEach { proto ->
                        val isSelected = protocol == proto
                        Surface(
                            shape = RoundedCornerShape(YanjiRadius.ItemRadius),
                            color = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                            border = BorderStroke(
                                1.dp,
                                if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .clickable {
                                    keyboardController?.hide()
                                    focusManager.clearFocus()
                                    protocol = proto
                                    connectionMessage = null
                                    connectionSucceeded = null
                                }
                        ) {
                            Box(
                                modifier = Modifier.padding(vertical = 8.dp, horizontal = 4.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = when (proto) {
                                        AiProtocolType.OPENAI_CHAT -> "OpenAI Chat"
                                        AiProtocolType.OPENAI_RESPONSE -> "Response 格式"
                                        AiProtocolType.ANTHROPIC -> "Claude 格式"
                                    },
                                    fontSize = 11.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // 3. API 地址 (Base URL)
                val cleartextWarning = CleartextPolicy.warningFor(baseUrl)
                OutlinedTextField(
                    value = baseUrl,
                    onValueChange = {
                        baseUrl = it
                        connectionMessage = null
                        connectionSucceeded = null
                    },
                    label = { Text("API 地址 (Base URL)") },
                    placeholder = { Text("https://api.deepseek.com/v1") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(YanjiRadius.ItemRadius),
                    singleLine = true,
                    isError = cleartextWarning != null,
                    supportingText = cleartextWarning?.let { warning ->
                        {
                            Text(
                                text = warning,
                                fontSize = 11.sp,
                                lineHeight = 14.sp,
                                color = YanjiColors.warning
                            )
                        }
                    }
                )

                Spacer(modifier = Modifier.height(10.dp))

                // 4. API Key
                OutlinedTextField(
                    value = apiKey,
                    onValueChange = {
                        apiKey = it
                        viewModel.clearSecurityError()
                        connectionMessage = null
                        connectionSucceeded = null
                    },
                    label = { Text("API Key") },
                    placeholder = { Text("sk-... (局域网本地模型可留空)") },
                    visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { showPassword = !showPassword }) {
                            Icon(
                                imageVector = if (showPassword) RemixIcons.EyeLine else RemixIcons.EyeOffLine,
                                contentDescription = if (showPassword) "隐藏密钥" else "显示密钥",
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(YanjiRadius.ItemRadius),
                    singleLine = true,
                    isError = state.securityError != null,
                    supportingText = state.securityError?.let { message ->
                        {
                            Text(
                                text = message,
                                fontSize = 11.sp,
                                lineHeight = 14.sp,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                )

                Spacer(modifier = Modifier.height(10.dp))

                // 5. 模型名称与在线获取列表
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "模型名称",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    TextButton(
                        onClick = {
                            keyboardController?.hide()
                            focusManager.clearFocus()
                            if (baseUrl.isBlank()) {
                                connectionSucceeded = false
                                connectionMessage = "请先输入 API 地址再获取模型列表"
                                return@TextButton
                            }
                            coroutineScope.launch {
                                isFetchingModelsOnly = true
                                val result = viewModel.fetchAvailableModels(baseUrl, apiKey, protocol)
                                isFetchingModelsOnly = false
                                result.fold(
                                    onSuccess = { list ->
                                        availableModels = list
                                        modelDropdownExpanded = list.isNotEmpty()
                                        if (model.isBlank() && list.isNotEmpty()) {
                                            model = list.first()
                                        }
                                        connectionSucceeded = true
                                        connectionMessage = if (list.isEmpty()) {
                                            "已连通，但未返回公开模型列表，可手动输入"
                                        } else {
                                            "已获取 ${list.size} 个可用模型"
                                        }
                                    },
                                    onFailure = { err ->
                                        availableModels = emptyList()
                                        modelDropdownExpanded = false
                                        connectionSucceeded = false
                                        connectionMessage = "获取模型失败：${err.message ?: "请检查地址与网络"}"
                                    }
                                )
                            }
                        },
                        enabled = !isFetchingModelsOnly && !isTestingConnection,
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        if (isFetchingModelsOnly) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(12.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("拉取中...", fontSize = 11.sp)
                        } else {
                            Icon(
                                imageVector = RemixIcons.DownloadCloudLine,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("获取模型列表", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }

                Box(modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = model,
                        onValueChange = { model = it },
                        placeholder = { Text("选择或输入模型，如 deepseek-chat") },
                        trailingIcon = {
                            IconButton(
                                enabled = availableModels.isNotEmpty(),
                                onClick = {
                                    keyboardController?.hide()
                                    focusManager.clearFocus()
                                    modelDropdownExpanded = !modelDropdownExpanded
                                }
                            ) {
                                Icon(
                                    imageVector = if (modelDropdownExpanded) RemixIcons.ArrowUpSLine else RemixIcons.ArrowDownSLine,
                                    contentDescription = if (availableModels.isEmpty()) "点击上方「获取模型列表」可拉取在线模型" else "展开在线模型列表",
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(YanjiRadius.ItemRadius),
                        singleLine = true
                    )

                    DropdownMenu(
                        expanded = modelDropdownExpanded && availableModels.isNotEmpty(),
                        onDismissRequest = { modelDropdownExpanded = false },
                        properties = PopupProperties(focusable = true, dismissOnClickOutside = true, clippingEnabled = false),
                        modifier = Modifier
                            .fillMaxWidth(0.85f)
                            .heightIn(max = 260.dp)
                            .background(MaterialTheme.colorScheme.surface)
                    ) {
                        Text(
                            text = "可用模型 (${availableModels.size})",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, thickness = 0.5.dp)

                        availableModels.forEach { m ->
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = m,
                                        fontSize = 13.sp,
                                        fontWeight = if (m == model) FontWeight.Bold else FontWeight.Normal,
                                        color = if (m == model) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                    )
                                },
                                onClick = {
                                    model = m
                                    modelDropdownExpanded = false
                                    keyboardController?.hide()
                                    focusManager.clearFocus(force = true)
                                },
                                trailingIcon = if (m == model) {
                                    {
                                        Icon(
                                            imageVector = RemixIcons.CheckLine,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                } else null
                            )
                        }
                    }
                }

                // 快捷模型标签行（如果有在线拉取结果）
                if (availableModels.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        availableModels.take(8).forEach { m ->
                            val isChosen = m == model
                            Surface(
                                shape = RoundedCornerShape(YanjiRadius.ItemRadius),
                                color = if (isChosen) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                                border = BorderStroke(
                                    0.5.dp,
                                    if (isChosen) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                                ),
                                modifier = Modifier.clickable {
                                    model = m
                                    keyboardController?.hide()
                                    focusManager.clearFocus()
                                }
                            ) {
                                Text(
                                    text = m,
                                    fontSize = 11.sp,
                                    fontWeight = if (isChosen) FontWeight.SemiBold else FontWeight.Normal,
                                    color = if (isChosen) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }
                }

                // 6. 操作与测试反馈状态胶囊
                connectionMessage?.let { message ->
                    Spacer(modifier = Modifier.height(10.dp))
                    Surface(
                        shape = RoundedCornerShape(YanjiRadius.ItemRadius),
                        color = if (connectionSucceeded == true) YanjiColors.success.copy(alpha = 0.08f) else MaterialTheme.colorScheme.error.copy(alpha = 0.08f),
                        border = BorderStroke(
                            0.5.dp,
                            if (connectionSucceeded == true) YanjiColors.success.copy(alpha = 0.3f) else MaterialTheme.colorScheme.error.copy(alpha = 0.3f)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = if (connectionSucceeded == true) {
                                    RemixIcons.CheckboxCircleLine
                                } else {
                                    RemixIcons.ErrorWarningLine
                                },
                                contentDescription = null,
                                tint = if (connectionSucceeded == true) YanjiColors.success else MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(15.dp)
                            )
                            Text(
                                text = message,
                                fontSize = 12.sp,
                                lineHeight = 15.sp,
                                color = if (connectionSucceeded == true) YanjiColors.success else MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }

                state.securityError?.let { message ->
                    Spacer(modifier = Modifier.height(8.dp))
                    Surface(
                        shape = RoundedCornerShape(YanjiRadius.ItemRadius),
                        color = MaterialTheme.colorScheme.error.copy(alpha = 0.08f),
                        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.3f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = RemixIcons.LockLine,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(15.dp)
                            )
                            Text(
                                text = message,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 左侧：测试连接（对选中的模型发起实际请求测试）
                OutlinedButton(
                    onClick = {
                        keyboardController?.hide()
                        focusManager.clearFocus()
                        if (baseUrl.isBlank()) {
                            connectionSucceeded = false
                            connectionMessage = "请先输入 API 地址"
                            return@OutlinedButton
                        }
                        if (model.isBlank()) {
                            connectionSucceeded = false
                            connectionMessage = "请先输入或选择要测试的模型名称"
                            return@OutlinedButton
                        }
                        coroutineScope.launch {
                            isTestingConnection = true
                            connectionMessage = null
                            val startMs = System.currentTimeMillis()
                            val result = viewModel.testModelConnection(baseUrl, apiKey, model, protocol)
                            val elapsed = System.currentTimeMillis() - startMs
                            isTestingConnection = false
                            result.fold(
                                onSuccess = {
                                    connectionSucceeded = true
                                    connectionMessage = "连接成功 (${elapsed}ms)！模型「${model.trim()}」响应正常"
                                },
                                onFailure = { err ->
                                    connectionSucceeded = false
                                    connectionMessage = "模型测试失败：${err.message ?: "服务未响应"}"
                                }
                            )
                        }
                    },
                    enabled = !isTestingConnection && !isFetchingModelsOnly,
                    shape = RoundedCornerShape(YanjiRadius.ItemRadius),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
                        contentColor = MaterialTheme.colorScheme.primary
                    ),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    if (isTestingConnection) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(14.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("测试中...", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    } else {
                        Icon(
                            imageVector = RemixIcons.NetworkLine,
                            contentDescription = null,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("测试连接", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }
                }

                // 右侧：取消 + 确认保存
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(
                        onClick = onDismissRequest,
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp)
                    ) {
                        Text("取消", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                    }
                    Button(
                        onClick = {
                            val saved = viewModel.updateSettings(
                                settings.copy(
                                    aiProvider = provider.trim(),
                                    aiBaseUrl = baseUrl.trim(),
                                    aiApiKey = apiKey.trim(),
                                    aiModel = model.trim(),
                                    aiProtocol = protocol.id
                                )
                            )
                            if (saved) onDismissRequest()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                        shape = RoundedCornerShape(YanjiRadius.ItemRadius),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
                    ) {
                        Text("确认保存", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                }
            }
        },
        dismissButton = null
    )
}
