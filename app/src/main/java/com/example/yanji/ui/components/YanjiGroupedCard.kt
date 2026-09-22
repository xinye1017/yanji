package com.example.yanji.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.yanji.theme.YanjiCardBorder
import com.example.yanji.theme.YanjiColors
import com.example.yanji.theme.YanjiRadius
import com.example.yanji.theme.YanjiSpacing
import com.example.yanji.theme.yanjiIsDarkTheme

/**
 * iOS-inspired Grouped Card (研迹分组卡片容器)
 *
 * 遵循 iOS HIG Grouped Inset List 结构规范并契合 Android Material3：
 * 1. 外层曲率：采用 [YanjiRadius.GroupedCardRadius] (20dp) 平滑圆角；
 * 2. 层次结构：支持上方组标题 (Section Header) 与下方注脚说明 (Section Footer)；
 * 3. 柔和深度：边框统一走 [YanjiCardBorder]（0.8dp 低对比度描边，暗色更淡），
 *    暗色模式下以表面明度递进表达层次；
 * 4. 杜绝套娃：设计用于包裹一行或多行条目（如 [YanjiSettingsRow]），消除卡片套卡片（Card-in-Card）的杂乱感。
 */
@Composable
fun YanjiGroupedCard(
    modifier: Modifier = Modifier,
    cardModifier: Modifier = Modifier,
    headerTitle: String? = null,
    footerText: String? = null,
    containerColor: Color = MaterialTheme.colorScheme.surface,
    border: BorderStroke? = YanjiCardBorder.stroke(),
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val isDark = yanjiIsDarkTheme()
    val cardShape = RoundedCornerShape(YanjiRadius.GroupedCardRadius)

    Column(modifier = modifier.fillMaxWidth()) {
        if (!headerTitle.isNullOrBlank()) {
            Text(
                text = headerTitle,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = YanjiColors.textTertiary,
                modifier = Modifier
                    .padding(horizontal = YanjiSpacing.CardPaddingCompact, vertical = 6.dp)
                    .semantics { heading() }
            )
        }

        if (onClick != null) {
            Card(
                onClick = onClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .then(cardModifier)
                    .shadow(
                        elevation = if (isDark) 0.dp else 1.5.dp,
                        shape = cardShape,
                        ambientColor = Color.Black.copy(alpha = 0.04f),
                        spotColor = Color.Black.copy(alpha = 0.04f)
                    ),
                shape = cardShape,
                colors = CardDefaults.cardColors(
                    containerColor = containerColor,
                    contentColor = MaterialTheme.colorScheme.onSurface
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                border = border,
                content = content
            )
        } else {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(cardModifier)
                    .shadow(
                        elevation = if (isDark) 0.dp else 1.5.dp,
                        shape = cardShape,
                        ambientColor = Color.Black.copy(alpha = 0.04f),
                        spotColor = Color.Black.copy(alpha = 0.04f)
                    ),
                shape = cardShape,
                colors = CardDefaults.cardColors(
                    containerColor = containerColor,
                    contentColor = MaterialTheme.colorScheme.onSurface
                ),
                border = border,
                content = content
            )
        }

        if (!footerText.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = footerText,
                style = MaterialTheme.typography.bodySmall,
                color = YanjiColors.textTertiary,
                modifier = Modifier.padding(horizontal = YanjiSpacing.CardPaddingCompact, vertical = 4.dp)
            )
        }
    }
}
