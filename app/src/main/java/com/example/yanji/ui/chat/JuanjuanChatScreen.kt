package com.example.yanji.ui.chat

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
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
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(top = 16.dp, bottom = 20.dp)
            ) {
                // Timestamp
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
                                    fontWeight = FontWeight.Medium,
                                    fontSize = 12.sp
                                ),
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp)
                            )
                        }
                    }
                }

                if (isNewConversation) {
                    item {
                        ConversationWelcome(
                            contextRecordCount = contextSources.sumOf { it.count }
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

            // Composer Bar (Stitch design with model switcher & thinking status)
            ComposerBar(
                inputText = inputText,
                onTextChange = { inputText = it },
                onSend = {
                    if (inputText.isNotBlank()) {
                        val text = inputText
                        inputText = ""
                        viewModel.sendChatMessage(text, model = activeModel)
                    }
                },
                activeModel = activeModel,
                onModelClick = { showAiSettings = true }
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
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.Start,
        verticalAlignment = Alignment.Top
    ) {
        // Juanjuan Avatar with sparkle badge (40dp)
        Box(modifier = Modifier.size(40.dp)) {
            JuanjuanAvatar(size = 40.dp)
            Box(
                modifier = Modifier
                    .size(16.dp)
                    .align(Alignment.BottomEnd)
                    .offset(x = 2.dp, y = 2.dp)
                    .clip(CircleShape)
                    .background(YanjiPrimary),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.AutoAwesome,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(10.dp)
                )
            }
        }

        Spacer(modifier = Modifier.width(10.dp))

        Column(
            modifier = Modifier
                .weight(1f, fill = false)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.Start,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // Header: Name + Badge
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = "卷卷",
                    style = MaterialTheme.typography.labelMedium.copy(
                        color = YanjiTextSecondary,
                        fontWeight = FontWeight.Medium,
                        fontSize = 12.sp
                    )
                )

                Surface(
                    shape = CircleShape,
                    color = YanjiPrimarySoft
                ) {
                    Text(
                        text = "专属学伴",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = YanjiPrimaryStrong,
                            fontWeight = FontWeight.Medium,
                            fontSize = 10.sp
                        ),
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                    )
                }
            }

            // Welcome Bubble (white card matching Stitch design)
            Surface(
                shape = RoundedCornerShape(
                    topStart = 4.dp,
                    topEnd = 16.dp,
                    bottomStart = 16.dp,
                    bottomEnd = 16.dp
                ),
                color = YanjiSurface,
                shadowElevation = 1.dp,
                border = BorderStroke(0.5.dp, YanjiBorder.copy(alpha = 0.6f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "嗨，今天已经专注备考啦！🌱",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            color = YanjiTextPrimary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    )

                    Text(
                        text = "我刚刚看了你的近期模考成绩与研迹日记：核心基础整体非常扎实，准备好迎接今天的突破了吗？",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            color = YanjiTextPrimary,
                            lineHeight = 22.sp,
                            fontSize = 14.sp
                        )
                    )

                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = YanjiSurfaceBlue,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "今天想聊聊考场时间分配、草稿折痕法，还是单纯想吐吐槽放松一下？卷卷随时在听哦。",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                color = YanjiTextSecondary,
                                lineHeight = 20.sp,
                                fontSize = 13.sp
                            ),
                            modifier = Modifier.padding(12.dp)
                        )
                    }

                    if (contextRecordCount > 0) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Psychology,
                                contentDescription = null,
                                tint = YanjiLavender,
                                modifier = Modifier.size(15.dp)
                            )
                            Text(
                                text = "已关联近 $contextRecordCount 项学习记录深度思考",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = Color(0xFF5C4BC3),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            )
                        }
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
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            text = "可以从这些开始",
            style = MaterialTheme.typography.labelMedium.copy(
                color = YanjiTextSecondary,
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp
            ),
            modifier = Modifier.padding(start = 2.dp, top = 4.dp)
        )

        androidx.compose.foundation.layout.FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            maxItemsInEachRow = 2
        ) {
            questions.forEach { question ->
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = YanjiSurface,
                    border = BorderStroke(1.dp, YanjiBorder),
                    shadowElevation = 0.5.dp,
                    modifier = Modifier
                        .clickable { onQuestionClick(question) }
                        .weight(1f, fill = false)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.NorthEast,
                            contentDescription = null,
                            tint = YanjiPrimary,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = question,
                            style = MaterialTheme.typography.labelMedium.copy(
                                color = YanjiTextPrimary,
                                fontWeight = FontWeight.Normal,
                                fontSize = 12.sp
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}
