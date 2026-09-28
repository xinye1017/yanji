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
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
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
    val protocol: AiProtocolType = AiProtocolType.OPENAI_CHAT
)

private const val CUSTOM_PROVIDER = "自定义"

private val AI_PRESETS = listOf(
    ProviderPreset("DeepSeek", "https://api.deepseek.com/v1"),
    ProviderPreset("OpenAI", "https://api.openai.com/v1"),
    ProviderPreset("Kimi", "https://api.moonshot.cn/v1"),
    ProviderPreset("GLM", "https://open.bigmodel.cn/api/paas/v4"),
    ProviderPreset(CUSTOM_PROVIDER, "")
)

private val AI_PROVIDER_NAMES = AI_PRESETS.map { it.name }.toSet()

private fun providerOptionFor(configuredProvider: String): String =
    when {
        configuredProvider == "智谱 GLM" -> "GLM"
        configuredProvider in AI_PROVIDER_NAMES && configuredProvider != CUSTOM_PROVIDER -> configuredProvider
        else -> CUSTOM_PROVIDER
    }

@Composable
fun AiConfigDialog(
    onDismissRequest: () -> Unit,
    viewModel: AiConfigViewModel = yanjiViewModel { container -> AiConfigViewModel(container.repository) }
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val settings = state.settings
    val configuredProvider = settings.aiProvider
    val initialProviderSelection = providerOptionFor(configuredProvider)
    var selectedProvider by remember(configuredProvider) { mutableStateOf(initialProviderSelection) }
    var customProviderName by remember(configuredProvider) {
        mutableStateOf(
            configuredProvider.takeIf {
                initialProviderSelection == CUSTOM_PROVIDER && it != CUSTOM_PROVIDER
            }.orEmpty()
        )
    }
    val provider = if (selectedProvider == CUSTOM_PROVIDER) customProviderName else selectedProvider
    val apiKeysByProvider = remember(configuredProvider, settings.aiApiKey) {
        mutableMapOf(initialProviderSelection to settings.aiApiKey)
    }
    val baseUrlsByProvider = remember(configuredProvider, settings.aiBaseUrl) {
        mutableMapOf(initialProviderSelection to settings.aiBaseUrl)
    }
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
    var providerDropdownExpanded by remember { mutableStateOf(false) }
    var modelDropdownExpanded by remember { mutableStateOf(false) }

    fun selectProvider(preset: ProviderPreset) {
        if (selectedProvider != preset.name) {
            apiKeysByProvider[selectedProvider] = apiKey
            baseUrlsByProvider[selectedProvider] = baseUrl
            selectedProvider = preset.name
            apiKey = apiKeysByProvider[preset.name].orEmpty()
            baseUrl = baseUrlsByProvider[preset.name] ?: preset.baseUrl
            model = ""
            protocol = preset.protocol
            viewModel.clearSecurityError()
            availableModels = emptyList()
            modelDropdownExpanded = false
            connectionMessage = null
            connectionSucceeded = null
        }
        providerDropdownExpanded = false
        keyboardController?.hide()
        focusManager.clearFocus()
    }

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
                Text(
                    text = "AI API 配置",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
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
                // 1. 模型提供商
                YanjiFormFieldLabel("模型提供商")
                Spacer(modifier = Modifier.height(6.dp))
                Box(modifier = Modifier.fillMaxWidth()) {
                    Surface(
                        onClick = { providerDropdownExpanded = true },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("ai-selected-provider")
                            .semantics {
                                contentDescription = "选择模型提供商"
                                stateDescription = selectedProvider
                            },
                        shape = RoundedCornerShape(YanjiRadius.InputRadius),
                        color = YanjiColors.inputFill
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = selectedProvider,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.weight(1f)
                            )
                            Icon(
                                imageVector = if (providerDropdownExpanded) RemixIcons.ArrowUpSLine else RemixIcons.ArrowDownSLine,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    DropdownMenu(
                        expanded = providerDropdownExpanded,
                        onDismissRequest = { providerDropdownExpanded = false },
                        modifier = Modifier
                            .fillMaxWidth(0.9f)
                            .background(MaterialTheme.colorScheme.surface)
                    ) {
                        AI_PRESETS.forEach { preset ->
                            val isSelected = selectedProvider == preset.name
                            DropdownMenuItem(
                                modifier = Modifier.testTag("ai-provider-option-${preset.name}"),
                                text = {
                                    Text(
                                        text = preset.name,
                                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                    )
                                },
                                onClick = { selectProvider(preset) },
                                trailingIcon = if (isSelected) {
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

                // 自定义服务商名称
                if (selectedProvider == CUSTOM_PROVIDER) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        YanjiFormFieldLabel("服务商名称")
                        TextField(
                            value = customProviderName,
                            onValueChange = { customProviderName = it },
                            placeholder = { Text("如 MiniMax、Moonshot 等") },
                            modifier = Modifier
                                .fillMaxWidth()
                                .semantics { contentDescription = "服务商名称" },
                            shape = RoundedCornerShape(YanjiRadius.InputRadius),
                            colors = yanjiBorderlessTextFieldColors(),
                            singleLine = true
                        )
                    }
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
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    YanjiFormFieldLabel("API 地址 (Base URL)")
                    TextField(
                        value = baseUrl,
                        onValueChange = {
                            baseUrl = it
                            connectionMessage = null
                            connectionSucceeded = null
                        },
                        placeholder = { Text("https://api.deepseek.com/v1") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .semantics { contentDescription = "API 地址 (Base URL)" },
                        shape = RoundedCornerShape(YanjiRadius.InputRadius),
                        colors = yanjiBorderlessTextFieldColors(),
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
                }

                Spacer(modifier = Modifier.height(10.dp))

                // 4. API Key
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    YanjiFormFieldLabel("API Key")
                    TextField(
                        value = apiKey,
                        onValueChange = {
                            apiKey = it
                            viewModel.clearSecurityError()
                            connectionMessage = null
                            connectionSucceeded = null
                        },
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
                        modifier = Modifier
                            .fillMaxWidth()
                            .semantics { contentDescription = "API Key" },
                        shape = RoundedCornerShape(YanjiRadius.InputRadius),
                        colors = yanjiBorderlessTextFieldColors(),
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
                }

                Spacer(modifier = Modifier.height(10.dp))

                // 5. 模型名称与在线获取列表
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    YanjiFormFieldLabel("模型名称")
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

                Spacer(modifier = Modifier.height(6.dp))
                Box(modifier = Modifier.fillMaxWidth()) {
                    TextField(
                        value = model,
                        onValueChange = { model = it },
                        placeholder = { Text("选择模型或直接输入模型ID") },
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
                        modifier = Modifier
                            .fillMaxWidth()
                            .semantics { contentDescription = "模型名称" },
                        shape = RoundedCornerShape(YanjiRadius.InputRadius),
                        colors = yanjiBorderlessTextFieldColors(),
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
                        HorizontalDivider(color = YanjiColors.rowDivider, thickness = 0.5.dp)

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
                            imageVector = RemixIcons.Link,
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
