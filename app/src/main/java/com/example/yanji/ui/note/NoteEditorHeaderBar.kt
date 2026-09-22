package com.example.yanji.ui.note

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.yanji.ui.components.YanjiPeekRating
import com.example.yanji.ui.icons.RemixIcons
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * 随笔编辑页的「状态 / 记录时间」行。
 *
 * 作为**正文的第一行**随正文一起滚动（不再是悬浮顶栏，因此没有折叠与残留背景问题）。
 * 左侧状态打分，右侧记录时间靠右对齐。
 */
@Composable
fun NoteEditorHeaderBar(
    moodScore: Int,
    onMoodScoreChange: (Int) -> Unit,
    createdAt: Long,
    modifier: Modifier = Modifier
) {
    val formattedTime = remember(createdAt) {
        val instant = Instant.ofEpochMilli(createdAt)
        val zone = ZoneId.systemDefault()
        DateTimeFormatter.ofPattern("HH:mm", Locale.getDefault()).format(instant.atZone(zone))
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(HeaderRowHeight),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = "状态",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        YanjiPeekRating(
            value = moodScore,
            onValueChange = onMoodScoreChange,
            size = 22.dp,
            lift = 3.dp,
            showTip = false
        )

        // 记录时间与状态同行，靠右对齐；纯文本展示，不做成假按钮。
        Spacer(modifier = Modifier.weight(1f))
        HeaderMetaCell(
            icon = RemixIcons.TimeLine,
            value = "$formattedTime 记录"
        )
    }
}

/** 状态行的固定高度。按状态行内容的实际高度（星星 22dp + 文字行高 + 少量留白）紧凑取值。 */
internal val HeaderRowHeight = 28.dp

/**
 * 状态行与正文之间的固定间距。
 * 调整为 14dp，使状态行上下间距平衡适中，同时保持正文整体位置稳定。
 */
internal val HeaderRowGap = 14.dp

@Composable
private fun HeaderMetaCell(
    icon: ImageVector,
    value: String,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(15.dp)
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = value,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
