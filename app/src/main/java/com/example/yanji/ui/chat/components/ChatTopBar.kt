package com.example.yanji.ui.chat.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AddComment
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.yanji.theme.*

@Composable
fun ChatTopBar(
    onBack: () -> Unit,
    onHistory: () -> Unit,
    onNewChat: () -> Unit,
    onAiSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Return button: 36dp round surface with subtle shadow
        Surface(
            modifier = Modifier.size(36.dp),
            shape = CircleShape,
            color = YanjiSurface,
            shadowElevation = 1.dp
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .fillMaxSize()
                    .clickable(onClick = onBack)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "返回",
                    tint = YanjiTextSecondary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        // More actions button: 36dp round surface with subtle shadow & dropdown menu
        Box {
            var expanded by remember { mutableStateOf(false) }

            Surface(
                modifier = Modifier.size(36.dp),
                shape = CircleShape,
                color = YanjiSurface,
                shadowElevation = 1.dp
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .fillMaxSize()
                        .clickable { expanded = !expanded }
                ) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = "更多操作",
                        tint = YanjiTextSecondary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                modifier = Modifier.width(180.dp),
                shape = RoundedCornerShape(16.dp),
                containerColor = YanjiSurface,
                tonalElevation = 0.dp,
                shadowElevation = 8.dp,
                border = BorderStroke(1.dp, YanjiBorder)
            ) {
                DropdownMenuItem(
                    text = {
                        Text(
                            text = "新建对话",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = FontWeight.Medium,
                                fontSize = 14.sp
                            ),
                            color = YanjiPrimary
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.AddComment,
                            contentDescription = null,
                            tint = YanjiPrimary,
                            modifier = Modifier.size(18.dp)
                        )
                    },
                    onClick = {
                        expanded = false
                        onNewChat()
                    },
                    modifier = Modifier
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                        .clip(RoundedCornerShape(10.dp)),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                )

                DropdownMenuItem(
                    text = {
                        Text(
                            text = "查看对话历史",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = FontWeight.Normal,
                                fontSize = 14.sp
                            ),
                            color = YanjiTextPrimary
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.History,
                            contentDescription = null,
                            tint = YanjiTextSecondary,
                            modifier = Modifier.size(18.dp)
                        )
                    },
                    onClick = {
                        expanded = false
                        onHistory()
                    },
                    modifier = Modifier
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                        .clip(RoundedCornerShape(10.dp)),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                )

                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                    color = YanjiDivider
                )

                DropdownMenuItem(
                    text = {
                        Text(
                            text = "AI 设置",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = FontWeight.Normal,
                                fontSize = 14.sp
                            ),
                            color = YanjiTextPrimary
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = null,
                            tint = YanjiTextSecondary,
                            modifier = Modifier.size(18.dp)
                        )
                    },
                    onClick = {
                        expanded = false
                        onAiSettings()
                    },
                    modifier = Modifier
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                        .clip(RoundedCornerShape(10.dp)),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                )
            }
        }
    }
}
