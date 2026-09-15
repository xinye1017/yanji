package com.example.yanji.ui.focus

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.yanji.data.FocusSession
import com.example.yanji.theme.*

@Composable
fun FocusSummaryDialog(
    session: FocusSession,
    todayFocusSeconds: Long,
    onDismiss: () -> Unit
) {
    val totalH = todayFocusSeconds / 3600
    val totalM = (todayFocusSeconds % 3600) / 60
    val sessionH = session.durationSeconds / 3600
    val sessionM = (session.durationSeconds % 3600) / 60
    val sessionS = session.durationSeconds % 60

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                shape = RoundedCornerShape(YanjiRadius.ButtonRadius)
            ) {
                Text("太棒了，收下轨迹", fontWeight = FontWeight.Bold)
            }
        },
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(imageVector = Icons.Default.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary)
                Spacer(modifier = Modifier.width(8.dp))
                Text("专注完成！", fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column {
                Text(
                    text = "本次投入 ${session.subjectName} 学习：",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = if (sessionH > 0) "${sessionH}小时 ${sessionM}分钟 ${sessionS}秒" else "${sessionM}分钟 ${sessionS}秒",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "今天已累计专注 ${totalH}h ${totalM}m。每一份真实的专注，都是上岸的阶梯。",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                    lineHeight = 18.sp
                )
            }
        },
        shape = RoundedCornerShape(24.dp),
        containerColor = MaterialTheme.colorScheme.surface
    )
}
