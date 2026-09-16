#!/usr/bin/env bash
# 设计令牌防回归检查 —— 刻意保持极低复杂度：只有 grep + awk 计数，没有自定义 lint 插件。
#
# 规则 1（硬性，0 容忍）：ui/ 层不得出现 `Color(0x...)` 颜色字面量。
#   颜色的唯一事实源是 app/src/main/java/com/example/yanji/theme/Color.kt；
#   新增颜色请先在 Color.kt 里起语义名，再引用。
#
# 规则 2（棘轮）：ui/ 层裸 `RoundedCornerShape(<N>.dp)` 的数量不得超过 RADIUS_CEILING。
#   - 允许存在的情形：自定义图表、进度条轨道/填充、以及尚未统一的小尺度容器；
#   - 数量不允许回涨；把数量降下来之后，请把 RADIUS_CEILING 改成新的实际值；
#   - 行级豁免：在行尾写 `// token-exempt: <原因>`（必须写原因）。
#     豁免只应用于「确实属于局部几何、而不是产品组件圆角」的地方。
#
# 规则 3（硬性，0 容忍）：ui/ 层不得再引用**静态亮色 token**（YanjiPrimary / YanjiTextPrimary /
#   YanjiSurface … 共 21 个）。它们是顶层 val 编译期常量，暗色模式下永远返回亮色值，
#   等于暗色模式失效。必须改用 `MaterialTheme.colorScheme.*`（M3 有槽位的）
#   或 `YanjiColors.*`（M3 没有的 5 个角色，见 theme/ExtraColors.kt）；
#   学科序列色用 `yanjiSeriesToken()` / `yanjiSeriesColor()`。
set -uo pipefail
cd "$(dirname "$0")/.."

UI_DIR="app/src/main/java/com/example/yanji/ui"
RAW_COLOR_CEILING=0
RADIUS_CEILING=64
STATIC_COLOR_CEILING=0

PATTERN_COLOR='Color\(0x'
PATTERN_RADIUS='RoundedCornerShape\([0-9]+(\.[0-9]+)?\.dp\)'
PATTERN_STATIC='Yanji(OnPrimary|Background|SurfaceBlue|SurfaceSoft|Surface|TextPrimary|TextSecondary|TextTertiary|BorderSoft|Border|Divider|PrimaryGradientSoft|PrimaryStrong|PrimarySoft|Primary|LavenderSoftDeep|LavenderSoft|LavenderDeep|Lavender|SuccessSoft|Success|WarningSoft|Warning|DangerSoft|Danger|Error)'

scoped() {
  grep -rnE "$1" "$UI_DIR" --include='*.kt' 2>/dev/null \
    | awk '/token-exempt:/{next} {n++} END{print n+0}'
}

raw_color=$(scoped "$PATTERN_COLOR")
radius=$(scoped "$PATTERN_RADIUS")
static_color=$(scoped "$PATTERN_STATIC")
status=0

if [ "$raw_color" -gt "$RAW_COLOR_CEILING" ]; then
  echo "✗ 颜色：ui/ 层出现 $raw_color 处 Color(0x...) 字面量（上限 $RAW_COLOR_CEILING）"
  grep -rnE "$PATTERN_COLOR" "$UI_DIR" --include='*.kt' | sed 's/^/    /'
  echo "  → 请先在 theme/Color.kt 添加语义 token，再引用；不要内联 hex。"
  status=1
else
  echo "✓ 颜色：ui/ 层 Color(0x...) 命中 $raw_color（上限 $RAW_COLOR_CEILING）"
fi

if [ "$radius" -gt "$RADIUS_CEILING" ]; then
  echo "✗ 圆角：ui/ 层裸 RoundedCornerShape(<N>.dp) = $radius，超过棘轮上限 $RADIUS_CEILING"
  grep -rnE "$PATTERN_RADIUS" "$UI_DIR" --include='*.kt' | sed 's/^/    /'
  echo "  → 请优先复用 theme/Radius.kt 的 YanjiRadius 语义 token；"
  echo "     确属局部几何时，在该行加 '// token-exempt: <原因>'，或按实际值下调 RADIUS_CEILING。"
  status=1
else
  echo "✓ 圆角：ui/ 层裸 RoundedCornerShape(<N>.dp) = $radius（棘轮上限 $RADIUS_CEILING）"
fi

if [ "$static_color" -gt "$STATIC_COLOR_CEILING" ]; then
  echo "✗ 主题：ui/ 层仍有 $static_color 处静态亮色 token（上限 $STATIC_COLOR_CEILING）"
  grep -rnE "$PATTERN_STATIC" "$UI_DIR" --include='*.kt' | sed 's/^/    /'
  echo "  → 静态 token 是编译期常量，暗色下不会变。请改用："
  echo "     MaterialTheme.colorScheme.*（primary/surface/onSurface/outline/tertiary/error…）"
  echo "     或 YanjiColors.*（textTertiary / warning / warningSoft / lavenderDeep / surfaceBlue）。"
  status=1
else
  echo "✓ 主题：ui/ 层静态亮色 token 命中 $static_color（上限 $STATIC_COLOR_CEILING）"
fi

exit $status
