package com.example.yanji.ui.stats

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.yanji.data.DayBarData
import com.example.yanji.data.DurationFormatter
import com.example.yanji.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsDayDetailSheet(
    day: DayBarData,
    sheetState: SheetState,
    onDismiss: () -> Unit,
    onNavigateToDailyDetail: (String) -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = YanjiRadius.SheetRadius, topEnd = YanjiRadius.SheetRadius),
        containerColor = YanjiSurface
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
                    text = DurationFormatter.formatDateWithWeekday(day.date),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = YanjiTextPrimary
                )
                Text(
                    text = DurationFormatter.formatHoursMinutes(day.durationSeconds),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = YanjiPrimary
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "当日科目投入：",
                style = MaterialTheme.typography.bodyMedium,
                color = YanjiTextSecondary
            )
            Spacer(modifier = Modifier.height(8.dp))

            if (day.subjectDistribution.isEmpty()) {
                Text(
                    text = "当天暂无分科记录",
                    style = MaterialTheme.typography.labelMedium,
                    color = YanjiTextTertiary
                )
            } else {
                day.subjectDistribution.forEach { (subName, secs) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(text = subName, style = MaterialTheme.typography.bodyMedium, color = YanjiTextPrimary)
                        Text(
                            text = DurationFormatter.formatHoursMinutes(secs),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = YanjiPrimary
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = {
                    val d = day.date
                    onDismiss()
                    onNavigateToDailyDetail(d)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                colors = ButtonDefaults.buttonColors(containerColor = YanjiPrimary),
                shape = RoundedCornerShape(20.dp)
            ) {
                Text("查看当天记录 →", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
            }

            Spacer(modifier = Modifier.height(28.dp))
        }
    }
}
