package com.example.yanji.ui.chat.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.NorthEast
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.yanji.theme.*
import com.example.yanji.ui.components.JuanjuanAvatar

@Composable
fun ConversationWelcome(contextRecordCount: Int) {
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

            // Welcome Bubble
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
                                    color = YanjiLavenderDeep,
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
fun QuickQuestionsRow(
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
