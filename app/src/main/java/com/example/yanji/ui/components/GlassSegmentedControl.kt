package com.example.yanji.ui.components

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 统一分段控制器 (GlassSegmentedControl)
 *
 * 统一委托至 [YanjiSegmentedControl]，采用 iOS/macOS 现代设计系统级的「微投影悬浮药丸」方案：
 * 1. 外层凹槽底轨：浅灰柔和半透底（surfaceVariant 65% alpha），带细致边框；
 * 2. 滑动指示器：纯白悬浮药丸（MaterialTheme.colorScheme.surface），配合 2dp 浅柔阴影与顶部高光细描边，
 *    物理弹簧曲线（YanjiMotion.NavigationSpring）平滑位移；
 * 3. 文本排版：选中项为鲜明主题蓝（primary，Bold），未选中项为柔和次级灰（onSurfaceVariant，Normal），
 *    文字颜色伴随弹簧平滑过渡；
 * 4. 无障碍支持：完整适配 Role.Tab 与 selected 语义树。
 */
@Composable
fun <T> GlassSegmentedControl(
    items: List<T>,
    selectedIndex: Int,
    onItemSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
    variant: YanjiSegmentedControlVariant = YanjiSegmentedControlVariant.InCard,
    height: Dp = 40.dp,
    shape: Shape = CircleShape,
    itemLabel: (T) -> String = { it.toString() }
) {
    YanjiSegmentedControl(
        items = items,
        selectedIndex = selectedIndex,
        onItemSelected = onItemSelected,
        modifier = modifier,
        variant = variant,
        height = height,
        shape = shape,
        itemLabel = itemLabel
    )
}

