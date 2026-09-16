package com.example.yanji.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.yanji.theme.YanjiSpacing

/**
 * 标准页面页眉 —— DESIGN.md「Components / 1. App header」的工程化实现。
 *
 * ```text
 * 页面标题                              [trailing]
 * 副标题 / 上下文
 * ```
 *
 * 设计约束（不要为了“统一”破坏）：
 * - 不使用整宽蓝色 AppBar，标题直接落在页面背景上，用 `headlineMedium` 建立层级；
 * - 副标题是可选的一行上下文，使用 `bodyMedium` + 次级文字色；
 * - [trailing] 用于放该屏**自己的**主操作（如日记页的「记今天」），
 *   避免让每个页面各自拼 `Row + Column + Text` 而产生字号漂移。
 *
 * 品牌化独立 TopBar（如 Chat 的 `ChatTopBar`）**不**使用本组件。
 */
/**
 * 标准页面页眉 —— 支持标准中标题与 iOS-inspired Large Title 两种层级。
 *
 * 1. 一级页面（Home、Focus、Journal、Stats、Profile）：
 *    采用 Large Title (34sp / Bold)，呈现舒展安静的沉浸大标题与上下文副标；
 * 2. 次级页面与模块内：
 *    采用标准 headlineMedium (20sp / SemiBold)；
 * 3. 完整的语义树：自带 semantics { heading() }，确保 TalkBack 用户快速在标题间跳转。
 */
@Composable
fun YanjiPageHeader(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    largeTitle: Boolean = false,
    trailing: (@Composable () -> Unit)? = null
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier.weight(1f, fill = false),
            verticalArrangement = if (subtitle.isNullOrBlank()) {
                Arrangement.Top
            } else {
                Arrangement.spacedBy(if (largeTitle) 4.dp else YanjiSpacing.TightGap)
            }
        ) {
            Text(
                text = title,
                style = if (largeTitle) com.example.yanji.theme.YanjiTypography.largeTitle else MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.semantics { heading() }
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    style = if (largeTitle) com.example.yanji.theme.YanjiTypography.subheadline else MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        if (trailing != null) {
            Row(
                modifier = Modifier.padding(start = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                trailing()
            }
        }
    }
}

/**
 * 专供一级 Tab 页面使用的 iOS Large Title 标题栏
 */
@Composable
fun YanjiLargeTitleHeader(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    trailing: (@Composable () -> Unit)? = null
) {
    YanjiPageHeader(
        title = title,
        modifier = modifier,
        subtitle = subtitle,
        largeTitle = true,
        trailing = trailing
    )
}
