package com.example.yanji.ui.profile

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.yanji.data.journal.JournalHeaderConfig
import com.example.yanji.theme.YanjiColors
import com.example.yanji.theme.YanjiRadius

/**
 * 随笔顶部信息栏设置弹窗。
 *
 * 满足需求：
 *  - 入口位于「我的」页面偏好组；
 *  - 状态打分固定显示（不可关闭）；
 *  - 记录时间默认开启；
 *  - 天气可按需自定义开启。
 */
@Composable
fun JournalHeaderConfigDialog(
    config: JournalHeaderConfig,
    onConfigChange: (JournalHeaderConfig) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text(
                    text = "随笔顶部信息栏",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "自定义编辑随笔时顶部吸顶常驻的内容",
                    style = MaterialTheme.typography.bodySmall,
                    color = YanjiColors.secondaryLabel
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // 今日状态打分：固定开启
                ConfigSwitchRow(
                    title = "今日状态打分",
                    subtitle = "5 颗星滑动/点击打分（固定显示）",
                    checked = true,
                    enabled = false,
                    onCheckedChange = {}
                )

                HorizontalDivider(color = YanjiColors.separator, thickness = 0.5.dp)

                // 记录时间：默认开启
                ConfigSwitchRow(
                    title = "记录随笔时间",
                    subtitle = "显示当前随笔记录的具体时刻",
                    checked = config.showTime,
                    enabled = true,
                    onCheckedChange = { onConfigChange(config.copy(showTime = it)) }
                )

                HorizontalDivider(color = YanjiColors.separator, thickness = 0.5.dp)

                // 今日天气：自定义选项
                ConfigSwitchRow(
                    title = "今日天气",
                    subtitle = "在顶部胶囊快速选择与记录当天天气",
                    checked = config.showWeather,
                    enabled = true,
                    onCheckedChange = { onConfigChange(config.copy(showWeather = it)) }
                )

            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                shape = RoundedCornerShape(YanjiRadius.ButtonRadius)
            ) {
                Text("完成")
            }
        },
        shape = RoundedCornerShape(YanjiRadius.StandardCardRadius)
    )
}

@Composable
private fun ConfigSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (!enabled) {
                    Spacer(modifier = Modifier.width(6.dp))
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f),
                        shape = RoundedCornerShape(YanjiRadius.Small)
                    ) {
                        Text(
                            text = "固定",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = YanjiColors.textTertiary
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled,
            colors = SwitchDefaults.colors(
                disabledCheckedThumbColor = MaterialTheme.colorScheme.surface,
                disabledCheckedTrackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
            )
        )
    }
}
