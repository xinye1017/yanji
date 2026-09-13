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
import com.example.yanji.theme.YanjiTextPrimary
import com.example.yanji.theme.YanjiTextSecondary

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
@Composable
fun YanjiPageHeader(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
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
                Arrangement.spacedBy(YanjiSpacing.TightGap)
            }
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineMedium,
                color = YanjiTextPrimary,
                modifier = Modifier.semantics { heading() }
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = YanjiTextSecondary,
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
