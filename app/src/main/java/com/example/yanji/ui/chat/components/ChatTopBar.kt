package com.example.yanji.ui.chat.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clipScrollableContainer
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.yanji.theme.YanjiPrimary
import com.example.yanji.theme.YanjiPrimarySoft
import com.example.yanji.theme.YanjiRadius
import com.example.yanji.theme.YanjiSurface
import com.example.yanji.theme.YanjiTextPrimary
import com.example.yanji.theme.YanjiTextSecondary

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
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        HeaderIconButton(
            icon = Icons.AutoMirrored.Filled.ArrowBack,
            contentDescription = "返回",
            onClick = onBack
        )
        MoreMenu(
            onHistory = onHistory,
            onNewChat = onNewChat,
            onAiSettings = onAiSettings
        )
    }
}

@Composable
private fun HeaderIconButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    onClick: () -> Unit
) {
    IconButton(onClick = onClick, modifier = Modifier.size(44.dp)) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = YanjiTextPrimary,
            modifier = Modifier.size(23.dp)
        )
    }
}

@Composable
private fun MoreMenu(
    onHistory: () -> Unit,
    onNewChat: () -> Unit,
    onAiSettings: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Box {
        HeaderIconButton(
            icon = Icons.Default.MoreHoriz,
            contentDescription = "更多操作",
            onClick = { expanded = !expanded }
        )

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.width(208.dp),
            shape = RoundedCornerShape(20.dp),
            containerColor = YanjiSurface,
            tonalElevation = 0.dp,
            shadowElevation = 12.dp,
            border = androidx.compose.foundation.BorderStroke(1.dp, YanjiPrimary.copy(alpha = 0.12f))
        ) {
            Text(
                text = "对话操作",
                style = MaterialTheme.typography.labelMedium.copy(
                    color = YanjiTextSecondary,
                    fontWeight = FontWeight.SemiBold
                ),
                modifier = Modifier.padding(start = 18.dp, top = 14.dp, bottom = 8.dp)
            )
            MenuAction(
                icon = Icons.Default.History,
                label = "历史记录",
                onClick = { onHistory(); expanded = false }
            )
            MenuAction(
                icon = Icons.Default.Add,
                label = "新建对话",
                emphasized = true,
                onClick = { onNewChat(); expanded = false }
            )
            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                color = YanjiPrimary.copy(alpha = 0.08f)
            )
            MenuAction(
                icon = Icons.Default.Settings,
                label = "AI 设置",
                onClick = { onAiSettings(); expanded = false }
            )
            Spacer(modifier = Modifier.height(6.dp))
        }
    }
}

@Composable
private fun MenuAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    emphasized: Boolean = false,
    onClick: () -> Unit
) {
    val rowShape = RoundedCornerShape(YanjiRadius.ButtonRadius)
    DropdownMenuItem(
        text = {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                color = if (emphasized) YanjiPrimary else YanjiTextPrimary
            )
        },
        leadingIcon = {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (emphasized) YanjiPrimary else YanjiTextSecondary,
                modifier = Modifier.size(19.dp)
            )
        },
        onClick = onClick,
        modifier = Modifier
            .padding(horizontal = 8.dp, vertical = 1.dp)
            .clip(rowShape)
            .background(if (emphasized) YanjiPrimarySoft else Color.Transparent),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 2.dp)
    )
}
