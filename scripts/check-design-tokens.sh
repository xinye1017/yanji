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
set -uo pipefail
cd "$(dirname "$0")/.."

UI_DIR="app/src/main/java/com/example/yanji/ui"
RAW_COLOR_CEILING=0
RADIUS_CEILING=74

PATTERN_COLOR='Color\(0x'
PATTERN_RADIUS='RoundedCornerShape\([0-9]+(\.[0-9]+)?\.dp\)'

scoped() {
  grep -rnE "$1" "$UI_DIR" --include='*.kt' 2>/dev/null \
    | awk '/token-exempt:/{next} {n++} END{print n+0}'
}

raw_color=$(scoped "$PATTERN_COLOR")
radius=$(scoped "$PATTERN_RADIUS")
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

exit $status
