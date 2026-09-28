package com.example.yanji.ui.stats

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.yanji.data.DurationFormatter
import com.example.yanji.data.MonthBarData
import com.example.yanji.theme.*
import com.example.yanji.theme.YanjiColors

/**
 * 月度详情抽屉：样式对齐 [StatsDayDetailSheet]，数据换成月粒度。
 *
 * 与日抽屉的两处刻意差异：
 * 1. 标题是「2026年9月」而不是带星期的日期 —— 月份没有“星期几”这回事；
 * 2. 没有底部跳转按钮。日抽屉那颗按钮指向日详情页，而**本月没有月详情页**，
 *    挂一个点了没反应的按钮比不挂更糟。抽屉本身已经把该看的都说完了。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsMonthDetailSheet(
    month: MonthBarData,
    sheetState: SheetState,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = YanjiRadius.SheetRadius, topEnd = YanjiRadius.SheetRadius),
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${month.year}年${month.month}月",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = DurationFormatter.formatHoursMinutes(month.durationSeconds),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            // 月粒度独有的第二行：一个月的“有效学习 N 天”比单日有信息量。
            Text(
                text = "有效学习 ${month.activeDays} 天",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Medium,
                color = YanjiColors.success
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "当月科目投入：",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))

            if (month.subjectDistribution.isEmpty()) {
                Text(
                    text = "当月暂无分科记录",
                    style = MaterialTheme.typography.labelMedium,
                    color = YanjiColors.textTertiary
                )
            } else {
                month.subjectDistribution.forEach { (subName, secs) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(text = subName, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
                        Text(
                            text = DurationFormatter.formatHoursMinutes(secs),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(28.dp))
        }
    }
}
