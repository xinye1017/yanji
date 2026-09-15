package com.example.yanji.ui.exam

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.yanji.data.ExamSession
import com.example.yanji.theme.*
import com.example.yanji.theme.YanjiColors

@Composable
fun ExamScoreDialog(
    session: ExamSession,
    onDismiss: () -> Unit,
    onConfirm: (score: Double?, note: String) -> Unit
) {
    var scoreInput by rememberSaveable(session.id) { mutableStateOf("") }
    var noteInput by rememberSaveable(session.id) { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            Button(
                onClick = {
                    val scoreVal = scoreInput.toDoubleOrNull()
                    onConfirm(scoreVal, noteInput)
                },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                shape = RoundedCornerShape(YanjiRadius.ButtonRadius)
            ) {
                Text("保存成绩与复盘", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("暂不录入分数", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        title = {
            Text("模拟考试结束 · 成绩录入", fontWeight = FontWeight.Bold, fontSize = 18.sp)
        },
        text = {
            Column {
                Text(
                    text = "考试科目：${session.subjectName}",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "实际用时：${session.actualDurationSeconds / 60} 分钟",
                    fontSize = 13.sp,
                    color = YanjiColors.textTertiary
                )
                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = scoreInput,
                    onValueChange = { scoreInput = it },
                    label = { Text("卷面得分 (满分 150/100)") },
                    placeholder = { Text("例如 128") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(YanjiRadius.Small),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = noteInput,
                    onValueChange = { noteInput = it },
                    label = { Text("答题复盘与失分点") },
                    placeholder = { Text("例如：选择题全对，证明题构造辅助函数耗时过长...") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(YanjiRadius.Small),
                    maxLines = 3
                )
            }
        },
        shape = RoundedCornerShape(24.dp),
        containerColor = MaterialTheme.colorScheme.surface
    )
}
