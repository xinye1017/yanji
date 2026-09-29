package com.example.yanji.ui.focus

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.yanji.data.QuickStartPreset
import com.example.yanji.ui.components.YanjiCard
import com.example.yanji.ui.components.YanjiCardVariant
import com.example.yanji.ui.icons.RemixIcons

/**
 * 快捷专注：一组「科目 + 计时模式 + 备注」的启动器。
 *
 * 与今日计划的区别在于**绑定对象**——快捷专注绑定的是用户在专注准备页当前选中的
 * 科目与模式，属于「发起一次专注」这条链的一环，因此留在专注页；
 * 今日计划绑定的是「今天」，属于跨页面、当天有效的意图，故已迁到首页
 * （见 `ui/home/TodayPlanCard.kt`）。
 */
@Composable
internal fun QuickPresetSection(
    presets: List<QuickStartPreset>,
    onStart: (QuickStartPreset) -> Unit,
    onDelete: (String) -> Unit
) {
    Text(
        text = "快捷专注",
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurface
    )
    Spacer(modifier = Modifier.height(10.dp))
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        presets.take(4).forEach { preset ->
            YanjiCard(
                modifier = Modifier.fillMaxWidth(),
                variant = YanjiCardVariant.Grouped
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 8.dp, top = 10.dp, bottom = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .clickable { onStart(preset) }
                    ) {
                        Text(
                            text = preset.label.ifBlank { preset.subjectName },
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = buildString {
                                append(preset.subLabel.ifBlank { preset.mode })
                                if (preset.note.isNotBlank()) {
                                    append(" · ")
                                    append(preset.note)
                                }
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    TextButton(onClick = { onStart(preset) }) {
                        Text("开始")
                    }
                    IconButton(onClick = { onDelete(preset.id) }) {
                        Icon(
                            imageVector = RemixIcons.DeleteBinLine,
                            contentDescription = "删除快捷专注",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}
