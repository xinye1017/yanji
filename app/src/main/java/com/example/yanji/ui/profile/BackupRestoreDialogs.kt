package com.example.yanji.ui.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.yanji.data.backup.BackupDecodeResult
import com.example.yanji.theme.*
import com.example.yanji.theme.YanjiColors
import com.example.yanji.ui.components.AiAvatar

@Composable
fun ImportConfirmDialog(
    decoded: BackupDecodeResult.Success,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    val backup = decoded.backup
    val warnings = decoded.warnings

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                shape = RoundedCornerShape(YanjiRadius.ButtonRadius)
            ) {
                Text("确认覆盖本机数据", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        title = { Text("确认从备份恢复？", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) },
        text = {
            Column {
                Text(
                    "本机现有的专注、模考、日记、对话、打卡与成就将被整体替换为备份内容。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(10.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(12.dp)
                ) {
                    Text(
                        "备份内容：${backup.countsSummary()}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                if (warnings.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(10.dp))
                    warnings.forEach { warning ->
                        Text("· $warning", style = MaterialTheme.typography.labelMedium, color = YanjiColors.warning)
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    "导入前会自动在本机留一份快照，可随时找回。",
                    style = MaterialTheme.typography.labelMedium,
                    color = YanjiColors.textTertiary
                )
            }
        },
        shape = RoundedCornerShape(20.dp),
        containerColor = MaterialTheme.colorScheme.surface
    )
}

@Composable
fun ImportErrorDialog(
    message: String,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                shape = RoundedCornerShape(YanjiRadius.ButtonRadius)
            ) {
                Text("知道了", fontWeight = FontWeight.Bold)
            }
        },
        title = { Text("导入未执行", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) },
        text = { Text(message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface) },
        shape = RoundedCornerShape(20.dp),
        containerColor = MaterialTheme.colorScheme.surface
    )
}

@Composable
fun AboutYanjiDialog(
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                shape = RoundedCornerShape(YanjiRadius.ButtonRadius)
            ) {
                Text("了解", fontWeight = FontWeight.Bold)
            }
        },
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AiAvatar(size = 36.dp)
                Spacer(modifier = Modifier.width(10.dp))
                Text("关于研迹 (Yanji)", fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column {
                Text(
                    text = "研迹（Yanji）是一款仅供个人使用的 Android 考研日记与学习管理应用。\n\n核心价值：记录、专注、积累、复盘。\n\n设计原则：\n• Local First：研迹不提供自建云同步；学习数据库可按 Android 系统设置参与云备份或设备迁移，AI Key 与活动计时快照不参与备份\n• 真实记录：专注计时基于真实时间戳\n• 低干扰：不做复杂社交、排行榜和过度鸡血\n• 学伴陪伴：当前由${currentMascotTheme().name}陪你走过这段备考旅程。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        },
        shape = RoundedCornerShape(24.dp),
        containerColor = MaterialTheme.colorScheme.surface
    )
}
