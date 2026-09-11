package com.example.yanji.ui.chat

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.yanji.data.ChatContextSource
import com.example.yanji.data.ChatMessage
import com.example.yanji.data.ChatSender
import com.example.yanji.data.ChatSession
import com.example.yanji.data.JournalEntry
import com.example.yanji.data.JuanjuanAction
import com.example.yanji.data.YanjiRepository
import com.example.yanji.theme.*
import com.example.yanji.ui.chat.components.*
import com.example.yanji.ui.components.JuanjuanAvatar
import com.example.yanji.ui.components.AiConfigDialog
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JuanjuanChatScreen(
    onNavigateBack: () -> Unit,
    viewModel: ChatViewModel = viewModel { ChatViewModel(YanjiRepository.getInstance()) }
) {
    BackHandler(onBack = onNavigateBack)
    val context = LocalContext.current
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val messages = state.messages
    val isAiReplying = state.isAiReplying
    val sessions = state.sessions
    val currentSessionId = state.currentSessionId

    val activeModel = state.currentSession?.model ?: state.settings.aiModel.ifBlank { "deepseek-chat" }
    val isNewConversation = messages.isEmpty()

    var inputText by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()

    var showHistorySheet by remember { mutableStateOf(false) }
    var showAiSettings by remember { mutableStateOf(false) }

    // Real context sources from repository（随消息/会话流在 ViewModel 内重算）
    val contextSources = state.contextSources

    // Quick questions for empty state
    val quickQuestions = listOf(
        "数学解答题草稿纸写乱",
        "数学大题做不完怎么办？",
        "今天做题错误率高很受挫",
        "408知识点多怎么串联？",
        "英语真题阅读第二遍怎么做？",
        "政治大题背诵有什么技巧？"
    )

    // Auto-scroll when message changes
    LaunchedEffect(messages.size, isAiReplying) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    Scaffold(
        topBar = {
            ChatTopBar(
                onBack = onNavigateBack,
                onHistory = { showHistorySheet = true },
                onNewChat = {
                    viewModel.createNewChatSession(activeModel)
                    Toast.makeText(context, "已开启新对话", Toast.LENGTH_SHORT).show()
                },
                onAiSettings = { showAiSettings = true }
            )
        },
        containerColor = YanjiBackground
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Chat Stream Body
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(top = 16.dp, bottom = 20.dp)
            ) {
                if (isNewConversation) {
                    item {
                        ConversationWelcome(
                            contextRecordCount = contextSources.sumOf { it.count }
                        )
                    }
                } else {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = YanjiSurfaceSoft
                            ) {
                                Text(
                                    text = SimpleDateFormat("今天 HH:mm", Locale.getDefault()).format(Date()),
                                    style = MaterialTheme.typography.labelMedium.copy(
                                        color = YanjiTextTertiary,
                                        fontWeight = FontWeight.Medium
                                    ),
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp)
                                )
                            }
                        }
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
                                viewModel.sendChatMessage(followup, model = activeModel)
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

                // Suggestions are an on-ramp for a fresh conversation, not a repeated interruption.
                if (isNewConversation) {
                    item {
                        QuickQuestionsRow(
                            questions = quickQuestions,
                            onQuestionClick = { question ->
                                val cleanQuery = question.replace(Regex("^[^一-龥a-zA-Z0-9]+"), "").trim()
                                viewModel.sendChatMessage(cleanQuery, model = activeModel)
                            }
                        )
                    }
                }
            }

            // Composer Bar
            ComposerBar(
                inputText = inputText,
                onTextChange = { inputText = it },
                onSend = {
                    if (inputText.isNotBlank()) {
                        val text = inputText
                        inputText = ""
                        viewModel.sendChatMessage(text, model = activeModel)
                    }
                }
            )
        }
    }

    // History Bottom Sheet
    if (showHistorySheet) {
        val dateFormat = remember { SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()) }
        var sessionToDelete by remember { mutableStateOf<ChatSession?>(null) }

        ModalBottomSheet(
            onDismissRequest = { showHistorySheet = false },
            containerColor = YanjiSurface,
            dragHandle = { BottomSheetDefaults.DragHandle() },
            shape = RoundedCornerShape(topStart = YanjiRadius.PageRadius, topEnd = YanjiRadius.PageRadius)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "历史对话",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = YanjiTextPrimary
                            )
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Surface(
                            shape = CircleShape,
                            color = YanjiSurfaceSoft
                        ) {
                            Text(
                                text = "${sessions.size}",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = YanjiTextSecondary,
                                    fontWeight = FontWeight.SemiBold
                                ),
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                            )
                        }
                    }

                    Button(
                        onClick = {
                            viewModel.createNewChatSession(activeModel)
                            showHistorySheet = false
                            Toast.makeText(context, "已开启新对话", Toast.LENGTH_SHORT).show()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = YanjiPrimary),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                        shape = RoundedCornerShape(YanjiRadius.ButtonRadius)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("新建对话", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                if (sessions.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("暂无历史对话记录", color = YanjiTextTertiary, style = MaterialTheme.typography.bodyMedium)
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 420.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(sessions, key = { it.id }) { s ->
                            val isSelected = s.id == currentSessionId
                            Surface(
                                shape = RoundedCornerShape(YanjiRadius.MessageRadius),
                                color = if (isSelected) YanjiPrimarySoft.copy(alpha = 0.45f) else YanjiSurfaceSoft,
                                border = if (isSelected) androidx.compose.foundation.BorderStroke(1.5.dp, YanjiPrimary) else androidx.compose.foundation.BorderStroke(0.5.dp, YanjiDivider),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        viewModel.switchChatSession(s.id)
                                        showHistorySheet = false
                                    }
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 14.dp, vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = if (isSelected) Icons.Default.ChatBubble else Icons.Default.ChatBubbleOutline,
                                        contentDescription = null,
                                        tint = if (isSelected) YanjiPrimary else YanjiTextSecondary,
                                        modifier = Modifier.size(20.dp)
                                    )

                                    Spacer(modifier = Modifier.width(12.dp))

                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                text = s.title,
                                                style = MaterialTheme.typography.bodyMedium.copy(
                                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                                    color = if (isSelected) YanjiPrimary else YanjiTextPrimary
                                                ),
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                modifier = Modifier.weight(1f, fill = false)
                                            )
                                            if (isSelected) {
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Surface(
                                                    shape = RoundedCornerShape(YanjiRadius.ChipRadius),
                                                    color = YanjiPrimary
                                                ) {
                                                    Text(
                                                        text = "当前",
                                                        color = Color.White,
                                                        style = MaterialTheme.typography.labelMedium,
                                                        fontWeight = FontWeight.Bold,
                                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp)
                                                    )
                                                }
                                            }
                                        }

                                        Spacer(modifier = Modifier.height(4.dp))

                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                text = dateFormat.format(Date(s.updatedAt)),
                                                style = MaterialTheme.typography.labelMedium.copy(
                                                    color = YanjiTextTertiary
                                                )
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(
                                                text = "·  ${s.model}",
                                                style = MaterialTheme.typography.labelMedium.copy(
                                                    color = YanjiTextTertiary
                                                ),
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }

                                    IconButton(
                                        onClick = { sessionToDelete = s },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.DeleteOutline,
                                            contentDescription = "删除对话",
                                            tint = YanjiTextTertiary,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    if (sessionToDelete != null) {
                        val target = sessionToDelete!!
                        AlertDialog(
                            onDismissRequest = { sessionToDelete = null },
                            title = { Text("删除该对话", fontWeight = FontWeight.Bold) },
                            text = { Text("确定要删除对话「${target.title}」及其所有消息记录吗？") },
                            confirmButton = {
                                TextButton(
                                    onClick = {
                                        viewModel.deleteChatSession(target.id)
                                        sessionToDelete = null
                                    }
                                ) {
                                    Text("删除", color = YanjiDanger, fontWeight = FontWeight.Bold)
                                }
                            },
                            dismissButton = {
                                TextButton(onClick = { sessionToDelete = null }) {
                                    Text("取消")
                                }
                            }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }

    // AI Settings Dialog
    if (showAiSettings) {
        AiConfigDialog(
            onDismissRequest = { showAiSettings = false }
        )
    }
}

@Composable
private fun ConversationWelcome(contextRecordCount: Int) {
    Surface(
        shape = RoundedCornerShape(YanjiRadius.PageRadius),
        color = YanjiSurfaceBlue,
        border = androidx.compose.foundation.BorderStroke(1.dp, YanjiPrimary.copy(alpha = 0.10f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                JuanjuanAvatar(size = 46.dp)
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "嗨，我是卷卷",
                        style = MaterialTheme.typography.titleMedium.copy(
                            color = YanjiTextPrimary,
                            fontWeight = FontWeight.Bold
                        )
                    )
                    Text(
                        text = "你的备考学伴",
                        style = MaterialTheme.typography.labelMedium.copy(color = YanjiPrimary)
                    )
                }
                Surface(shape = CircleShape, color = YanjiSuccessSoft) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .background(YanjiSuccess, CircleShape)
                        )
                        Spacer(modifier = Modifier.width(5.dp))
                        Text(
                            text = "在线",
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = YanjiSuccess,
                                fontWeight = FontWeight.SemiBold
                            )
                        )
                    }
                }
            }

            Text(
                text = "今天想先解决哪件事？学习方法、做题卡点和情绪波动，我都陪你一起理清。",
                style = MaterialTheme.typography.bodyMedium.copy(
                    color = YanjiTextSecondary,
                    lineHeight = 22.sp
                )
            )

            if (contextRecordCount > 0) {
                Surface(
                    shape = RoundedCornerShape(YanjiRadius.ButtonRadius),
                    color = Color.White.copy(alpha = 0.72f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = null,
                            tint = YanjiPrimary,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(7.dp))
                        Text(
                            text = "本次对话会参考 $contextRecordCount 项学习记录",
                            style = MaterialTheme.typography.labelMedium.copy(
                                color = YanjiPrimaryStrong,
                                fontWeight = FontWeight.Medium
                            )
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun QuickQuestionsRow(
    questions: List<String>,
    onQuestionClick: (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            text = "可以从这些开始",
            style = MaterialTheme.typography.labelLarge.copy(
                color = YanjiTextSecondary,
                fontWeight = FontWeight.SemiBold
            ),
            modifier = Modifier.padding(start = 2.dp)
        )
        androidx.compose.foundation.layout.FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            maxItemsInEachRow = 2
        ) {
            questions.forEach { question ->
                Surface(
                    shape = RoundedCornerShape(YanjiRadius.ButtonRadius),
                    color = YanjiSurface,
                    border = androidx.compose.foundation.BorderStroke(1.dp, YanjiPrimary.copy(alpha = 0.14f)),
                    modifier = Modifier
                        .clickable { onQuestionClick(question) }
                        .weight(1f, fill = false)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.NorthEast,
                            contentDescription = null,
                            tint = YanjiPrimary,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(7.dp))
                        Text(
                            text = question,
                            style = MaterialTheme.typography.labelMedium.copy(
                                color = YanjiPrimary,
                                fontWeight = FontWeight.Medium
                            ),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
    }
}
