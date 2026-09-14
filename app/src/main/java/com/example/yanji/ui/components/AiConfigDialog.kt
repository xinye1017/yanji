package com.example.yanji.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.PopupProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.yanji.di.yanjiViewModel
import com.example.yanji.data.security.CleartextPolicy
import com.example.yanji.theme.*
import kotlinx.coroutines.launch

private data class ProviderPreset(
    val name: String,
    val baseUrl: String,
    val defaultModel: String,
    val description: String = ""
)

private val AI_PRESETS = listOf(
    ProviderPreset("DeepSeek", "https://api.deepseek.com/v1", "deepseek-chat", "官方推荐"),
    ProviderPreset("硅基流动", "https://api.siliconflow.cn/v1", "deepseek-ai/DeepSeek-V3", "多模型聚合"),
    ProviderPreset("智谱 GLM", "https://open.bigmodel.cn/api/paas/v4", "glm-4-flash", "开放平台"),
    ProviderPreset("OpenAI", "https://api.openai.com/v1", "gpt-4o-mini", "官方接口"),
    ProviderPreset("本地 Ollama", "http://127.0.0.1:11434/v1", "qwen2.5:latest", "adb reverse 到本机"),
    ProviderPreset("自定义", "", "", "自由输入地址与模型")
)

@Composable
fun AiConfigDialog(
    onDismissRequest: () -> Unit,
    viewModel: AiConfigViewModel = yanjiViewModel { container -> AiConfigViewModel(container.repository) }
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val settings = state.settings
    var provider by remember(settings.aiProvider) { mutableStateOf(settings.aiProvider) }
    var baseUrl by remember(settings.aiBaseUrl) { mutableStateOf(settings.aiBaseUrl) }
    var apiKey by remember(settings.aiApiKey) { mutableStateOf(settings.aiApiKey) }
    var model by remember(settings.aiModel) { mutableStateOf(settings.aiModel) }
    var showPassword by remember { mutableStateOf(false) }

    val coroutineScope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current

    var isTestingConnection by remember { mutableStateOf(false) }
    var connectionMessage by remember { mutableStateOf<String?>(null) }
    var connectionSucceeded by remember { mutableStateOf<Boolean?>(null) }
    var availableModels by remember { mutableStateOf<List<String>>(emptyList()) }
    var providerDropdownExpanded by remember { mutableStateOf(false) }
    var modelDropdownExpanded by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismissRequest,
        shape = RoundedCornerShape(20.dp),
        containerColor = YanjiSurface,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Outlined.Psychology,
                    contentDescription = null,
                    tint = YanjiPrimary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("AI 后端连接设置", fontWeight = FontWeight.Bold, fontSize = 18.sp)
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = "兼容 OpenAI 格式。支持下拉选择主流预设或完全自定义提供商与地址，手机联网即可直连。",
                    fontSize = 12.sp,
                    color = YanjiTextSecondary,
                    lineHeight = 17.sp
                )
                Spacer(modifier = Modifier.height(14.dp))

                // 1. 提供商 (支持自定义，支持下拉菜单栏)
                Box(modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = provider,
                        onValueChange = {
                            provider = it
                            connectionMessage = null
                            connectionSucceeded = null
                        },
                        label = { Text("服务商 (可下拉选择预设或自定义)") },
                        placeholder = { Text("选择或直接输入服务商名称") },
                        trailingIcon = {
                            IconButton(
                                onClick = {
                                    keyboardController?.hide()
                                    focusManager.clearFocus()
                                    providerDropdownExpanded = !providerDropdownExpanded
                                }
                            ) {
                                Icon(
                                    imageVector = if (providerDropdownExpanded) Icons.Default.ArrowDropUp else Icons.Default.ArrowDropDown,
                                    contentDescription = "展开服务商下拉列表"
                                )
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(YanjiRadius.Small),
                        singleLine = true
                    )

                    DropdownMenu(
                        expanded = providerDropdownExpanded,
                        onDismissRequest = { providerDropdownExpanded = false },
                        properties = PopupProperties(focusable = true, dismissOnClickOutside = true, clippingEnabled = false),
                        modifier = Modifier
                            .fillMaxWidth(0.85f)
                            .heightIn(max = 300.dp)
                            .background(YanjiSurface)
                    ) {
                        Text(
                            text = "选择服务商预设 (自动填充默认地址)",
                            fontSize = 12.sp,
                            color = YanjiTextSecondary,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                        )
                        HorizontalDivider(color = YanjiDivider, thickness = 0.5.dp)

                        AI_PRESETS.forEach { preset ->
                            DropdownMenuItem(
                                text = {
                                    Column {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                text = preset.name,
                                                fontSize = 13.sp,
                                                fontWeight = if (provider == preset.name) FontWeight.Bold else FontWeight.Normal,
                                                color = if (provider == preset.name) YanjiPrimary else YanjiTextPrimary
                                            )
                                            if (preset.description.isNotBlank()) {
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text(
                                                    text = "(${preset.description})",
                                                    fontSize = 11.sp,
                                                    color = YanjiTextTertiary
                                                )
                                            }
                                        }
                                        if (preset.baseUrl.isNotBlank()) {
                                            Text(
                                                text = preset.baseUrl,
                                                fontSize = 10.sp,
                                                color = YanjiTextTertiary,
                                                maxLines = 1
                                            )
                                        }
                                    }
                                },
                                onClick = {
                                    if (preset.name != "自定义") {
                                        provider = preset.name
                                        baseUrl = preset.baseUrl
                                        model = preset.defaultModel
                                    } else {
                                        provider = ""
                                    }
                                    providerDropdownExpanded = false
                                    connectionMessage = null
                                    connectionSucceeded = null
                                    keyboardController?.hide()
                                    focusManager.clearFocus(force = true)
                                },
                                trailingIcon = if (provider == preset.name) {
                                    {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = null,
                                            tint = YanjiPrimary,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                } else null
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // 2. Base URL
                // 明文 HTTP 会被 Network Security Config 直接拦掉，这里提前把原因说清楚，
                // 避免用户保存后只看到一个看不懂的网络异常。
                val cleartextWarning = CleartextPolicy.warningFor(baseUrl)
                OutlinedTextField(
                    value = baseUrl,
                    onValueChange = {
                        baseUrl = it
                        connectionMessage = null
                        connectionSucceeded = null
                    },
                    label = { Text("Base URL (API 地址)") },
                    placeholder = { Text("https://api.deepseek.com/v1") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(YanjiRadius.Small),
                    singleLine = false,
                    isError = cleartextWarning != null,
                    supportingText = cleartextWarning?.let { message ->
                        {
                            Text(
                                text = message,
                                fontSize = 11.sp,
                                lineHeight = 15.sp,
                                color = YanjiWarning
                            )
                        }
                    }
                )

                Spacer(modifier = Modifier.height(10.dp))

                // 3. API Key
                OutlinedTextField(
                    value = apiKey,
                    onValueChange = {
                        apiKey = it
                        viewModel.clearSecurityError()
                        connectionMessage = null
                        connectionSucceeded = null
                    },
                    label = { Text("API Key (局域网私有化可留空)") },
                    placeholder = { Text("sk-...") },
                    visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { showPassword = !showPassword }) {
                            Icon(
                                imageVector = if (showPassword) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                contentDescription = null
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(YanjiRadius.Small),
                    singleLine = true,
                    isError = state.securityError != null,
                    supportingText = state.securityError?.let { message ->
                        {
                            Text(
                                text = message,
                                fontSize = 11.sp,
                                lineHeight = 15.sp,
                                color = YanjiDanger
                            )
                        }
                    }
                )

                Spacer(modifier = Modifier.height(10.dp))

                // 4. 模型名称 (支持下拉和手动输入)
                Box(modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = model,
                        onValueChange = { model = it },
                        label = { Text("模型名称") },
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
                                    imageVector = if (modelDropdownExpanded) Icons.Default.ArrowDropUp else Icons.Default.ArrowDropDown,
                                    contentDescription = if (availableModels.isEmpty()) {
                                        "测试连接成功后可展开模型列表"
                                    } else {
                                        "展开在线模型列表"
                                    }
                                )
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(YanjiRadius.Small),
                        singleLine = true
                    )

                    DropdownMenu(
                        expanded = modelDropdownExpanded && availableModels.isNotEmpty(),
                        onDismissRequest = { modelDropdownExpanded = false },
                        properties = PopupProperties(focusable = true, dismissOnClickOutside = true, clippingEnabled = false),
                        modifier = Modifier
                            .fillMaxWidth(0.85f)
                            .heightIn(max = 260.dp)
                            .background(YanjiSurface)
                    ) {
                        Text(
                            text = "在线探测可用模型 (${availableModels.size})",
                            fontSize = 12.sp,
                            color = YanjiTextSecondary,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                        )
                        HorizontalDivider(color = YanjiDivider, thickness = 0.5.dp)

                        availableModels.forEach { m ->
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = m,
                                        fontSize = 13.sp,
                                        fontWeight = if (m == model) FontWeight.Bold else FontWeight.Normal,
                                        color = if (m == model) YanjiPrimary else YanjiTextPrimary
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
                                            imageVector = Icons.Default.Check,
                                            contentDescription = null,
                                            tint = YanjiPrimary,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                } else null
                            )
                        }
                    }
                }

                // 5. 测试连接反馈状态卡片
                connectionMessage?.let { message ->
                    Spacer(modifier = Modifier.height(10.dp))
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = if (connectionSucceeded == true) YanjiSuccess.copy(alpha = 0.1f) else YanjiError.copy(alpha = 0.1f),
                        border = BorderStroke(
                            1.dp,
                            if (connectionSucceeded == true) YanjiSuccess.copy(alpha = 0.3f) else YanjiError.copy(alpha = 0.3f)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.Top,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = if (connectionSucceeded == true) {
                                    Icons.Outlined.CheckCircle
                                } else {
                                    Icons.Outlined.ErrorOutline
                                },
                                contentDescription = null,
                                tint = if (connectionSucceeded == true) YanjiSuccess else YanjiError,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = message,
                                fontSize = 12.sp,
                                lineHeight = 16.sp,
                                color = if (connectionSucceeded == true) YanjiSuccess else YanjiError
                            )
                        }
                    }
                }

                state.securityError?.let { message ->
                    Spacer(modifier = Modifier.height(10.dp))
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = YanjiError.copy(alpha = 0.1f),
                        border = BorderStroke(1.dp, YanjiError.copy(alpha = 0.3f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Lock,
                                contentDescription = null,
                                tint = YanjiError,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = message,
                                style = MaterialTheme.typography.bodySmall,
                                color = YanjiError
                            )
                        }
                    }
                }
            }
        },
        // 3. 将“测试连接”放到最下面，和“确认保存”放在同一栏
        confirmButton = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 左侧：测试连接
                OutlinedButton(
                    onClick = {
                        keyboardController?.hide()
                        focusManager.clearFocus()
                        if (baseUrl.isBlank()) {
                            connectionSucceeded = false
                            connectionMessage = "请先输入 Base URL"
                            return@OutlinedButton
                        }
                        coroutineScope.launch {
                            isTestingConnection = true
                            connectionMessage = null
                            val result = viewModel.fetchAvailableModels(baseUrl, apiKey)
                            isTestingConnection = false
                            result.fold(
                                onSuccess = { list ->
                                    availableModels = list
                                    modelDropdownExpanded = list.isNotEmpty()
                                    if (model.isBlank() && list.isNotEmpty()) {
                                        model = list.first()
                                    }
                                    connectionSucceeded = true
                                    connectionMessage = if (list.isEmpty()) {
                                        "连接正常！服务未返回公开模型列表，可手动输入模型名"
                                    } else {
                                        "连接成功！已获取 ${list.size} 个可用模型，可点击右侧下拉选择"
                                    }
                                },
                                onFailure = { err ->
                                    availableModels = emptyList()
                                    modelDropdownExpanded = false
                                    connectionSucceeded = false
                                    connectionMessage = "连接失败：${err.message ?: "请检查地址、密钥与手机网络"}"
                                }
                            )
                        }
                    },
                    enabled = !isTestingConnection,
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = YanjiPrimarySoft,
                        contentColor = YanjiPrimary
                    ),
                    border = BorderStroke(1.dp, YanjiPrimary.copy(alpha = 0.4f)),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    if (isTestingConnection) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(14.dp),
                            strokeWidth = 2.dp,
                            color = YanjiPrimary
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("测试中...", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    } else {
                        Icon(
                            imageVector = Icons.Outlined.NetworkCheck,
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
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Text("取消", color = YanjiTextSecondary, fontSize = 13.sp)
                    }
                    Button(
                        onClick = {
                            val saved = viewModel.updateSettings(
                                settings.copy(
                                    aiProvider = provider.trim(),
                                    aiBaseUrl = baseUrl.trim(),
                                    aiApiKey = apiKey.trim(),
                                    aiModel = model.trim()
                                )
                            )
                            if (saved) onDismissRequest()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = YanjiPrimary),
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                    ) {
                        Text("确认保存", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                }
            }
        },
        dismissButton = null
    )
}
