package com.example.yanji.ui.chat

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.yanji.data.ChatSender
import com.example.yanji.di.yanjiViewModel
import com.example.yanji.theme.*
import com.example.yanji.theme.YanjiColors
import com.example.yanji.ui.chat.components.*
import com.example.yanji.ui.components.AiConfigDialog
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JuanjuanChatScreen(
    onNavigateBack: () -> Unit,
    viewModel: ChatViewModel = yanjiViewModel { container ->
        ChatViewModel(container.repository)
    }
) {
    BackHandler(onBack = onNavigateBack)
    val context = LocalContext.current
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val messages = state.messages
    val isAiReplying = state.isAiReplying
    val sessions = state.sessions
    val currentSessionId = state.currentSessionId

    DisposableEffect(viewModel) {
        onDispose { viewModel.cancelReply() }
    }

    val activeModel = state.currentSession?.model ?: state.settings.aiModel
    val isNewConversation = messages.none { it.sender == ChatSender.USER }

    var inputText by rememberSaveable { mutableStateOf("") }
    val listState = rememberLazyListState()

    var showHistorySheet by rememberSaveable { mutableStateOf(false) }
    var showAiSettings by rememberSaveable { mutableStateOf(false) }

    val contextSources = state.contextSources

    val quickQuestions = listOf(
        "考研数学草稿纸分区与排版建议",
        "数学大题做不完如何合理分配时间？",
        "今天做题错误率高很受挫怎么办？",
        "408专业课知识点多怎么建立体系？",
        "考研英语真题精读与长难句拆解",
        "考研政治分析题答题逻辑与技巧"
    )

    var selectedModel by rememberSaveable(activeModel) { mutableStateOf(activeModel) }
    var thinkingIntensity by rememberSaveable { mutableStateOf(ThinkingIntensity.DEEP) }

    val statusBarHeight = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val imeBottom = WindowInsets.ime.asPaddingValues().calculateBottomPadding()
    val isKeyboardOpen = imeBottom > 0.dp

    // Auto-scroll when message changes or keyboard opens
    LaunchedEffect(messages.lastOrNull()?.id, isAiReplying) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }
    LaunchedEffect(isKeyboardOpen) {
        if (isKeyboardOpen && messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Chat Stream Body
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(
                top = statusBarHeight + 10.dp,
                bottom = if (isKeyboardOpen) imeBottom + 105.dp else 125.dp
            )
        ) {
            if (state.hasMoreMessages) {
                item(key = "load-more") {
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        TextButton(onClick = viewModel::loadMoreMessages) {
                            Text("加载更早消息", style = MaterialTheme.typography.labelLarge)
                        }
                    }
                }
            }

            // Timestamp
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 2.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Text(
                            text = Instant.now().atZone(ZoneId.systemDefault()).format(
                                DateTimeFormatter.ofPattern("'今天' HH:mm", Locale.getDefault())
                            ),
                            style = MaterialTheme.typography.labelMedium.copy(
                                color = YanjiColors.textTertiary,
                                fontWeight = FontWeight.Medium,
                                fontSize = 11.sp
                            ),
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 3.dp)
                        )
                    }
                }
            }

            if (isNewConversation) {
                item {
                    ConversationWelcome(
                        contextRecordCount = contextSources.sumOf { it.count },
                        isAiConfigured = state.isAiConfigured,
                        onOpenAiSettings = { showAiSettings = true }
                    )
                }
            }

            // Messages list
            items(messages, key = { it.id }) { msg ->
                if (msg.sender == ChatSender.USER) {
                    UserMessageBubble(
                        content = msg.content
                    )
                } else {
                    JuanjuanMessageBubble(
                        message = msg,
                        learningRecordCount = contextSources.sumOf { it.count },
                        onActionClick = { action ->
                            viewModel.executeAction(action, context)
                            Toast.makeText(context, "已执行：${action.label}", Toast.LENGTH_SHORT).show()
                        },
                        onFollowupClick = { followup ->
                            viewModel.sendChatMessage(followup, model = selectedModel)
                        },
                        onContextSourceClick = { source ->
                            Toast.makeText(context, "查看 ${source.type.name.replace("_", " ")}", Toast.LENGTH_SHORT).show()
                        }
                    )
                }
            }

            // AI Thinking indicator
            if (isAiReplying) {
                item {
                    AiThinkingIndicator()
                }
            }

            state.replyState.failure?.let { failure ->
                item(key = "reply-error-$currentSessionId") {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(YanjiRadius.ContentBlockRadius),
                        color = MaterialTheme.colorScheme.errorContainer,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.22f))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = failure.userMessage,
                                modifier = Modifier.weight(1f),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            TextButton(
                                onClick = viewModel::retryFailedReply,
                                enabled = !isAiReplying
                            ) {
                                Text("重试", color = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                }
            }

            if (isNewConversation) {
                item {
                    QuickQuestionsRow(
                        questions = quickQuestions,
                        onQuestionClick = { question ->
                            if (!state.isAiConfigured) {
                                Toast.makeText(context, "请先配置 AI 密钥与模型", Toast.LENGTH_SHORT).show()
                                showAiSettings = true
                            } else {
                                val cleanQuery = question.replace(Regex("^[^一-龥a-zA-Z0-9]+"), "").trim()
                                viewModel.sendChatMessage(cleanQuery, model = selectedModel)
                            }
                        }
                    )
                }
            }
        }

        // Top Gradient Blur Overlay
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(statusBarHeight + 56.dp)
                .align(Alignment.TopCenter)
                .zIndex(5f)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.background.copy(alpha = 0.96f),
                            MaterialTheme.colorScheme.background.copy(alpha = 0.85f),
                            MaterialTheme.colorScheme.background.copy(alpha = 0.45f),
                            MaterialTheme.colorScheme.background.copy(alpha = 0.12f),
                            Color.Transparent
                        )
                    )
                )
        )

        // Floating Top Bar
        ChatTopBar(
            onBack = onNavigateBack,
            onHistory = { showHistorySheet = true },
            onNewChat = {
                viewModel.createNewChatSession(selectedModel)
                Toast.makeText(context, "已开启新对话", Toast.LENGTH_SHORT).show()
            },
            onAiSettings = { showAiSettings = true },
            modifier = Modifier
                .align(Alignment.TopCenter)
                .zIndex(10f)
        )

        // Floating Bottom Composer Bar
        ComposerBar(
            inputText = inputText,
            onTextChange = { inputText = it },
            onSend = {
                if (inputText.isNotBlank()) {
                    if (!state.isAiConfigured) {
                        Toast.makeText(context, "请先配置 AI 密钥与模型", Toast.LENGTH_SHORT).show()
                        showAiSettings = true
                    } else {
                        val text = inputText
                        inputText = ""
                        viewModel.sendChatMessage(text, model = selectedModel)
                    }
                }
            },
            isAiConfigured = state.isAiConfigured,
            activeModel = selectedModel,
            availableModels = state.availableModels,
            onModelSelect = { newModel ->
                selectedModel = newModel
                viewModel.updateModel(newModel)
                Toast.makeText(context, "已切换模型：$newModel", Toast.LENGTH_SHORT).show()
            },
            thinkingIntensity = thinkingIntensity,
            onThinkingIntensityChange = { newIntensity ->
                thinkingIntensity = newIntensity
                Toast.makeText(context, "思考强度：${newIntensity.title}", Toast.LENGTH_SHORT).show()
            },
            onOpenAiSettings = { showAiSettings = true },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .zIndex(10f)
        )
    }

    if (showHistorySheet) {
        ChatHistorySheet(
            sessions = sessions,
            currentSessionId = currentSessionId,
            onDismiss = { showHistorySheet = false },
            onSelectSession = {
                viewModel.switchChatSession(it)
                showHistorySheet = false
            },
            onNewChat = {
                viewModel.createNewChatSession(activeModel)
                showHistorySheet = false
                Toast.makeText(context, "已开启新对话", Toast.LENGTH_SHORT).show()
            },
            onDeleteSession = { viewModel.deleteChatSession(it) }
        )
    }

    if (showAiSettings) {
        AiConfigDialog(
            onDismissRequest = { showAiSettings = false }
        )
    }
}
