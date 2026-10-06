#!/usr/bin/env bash
# 设计令牌防回归检查 —— 刻意保持极低复杂度：只有 grep + wc 计数，没有自定义 lint 插件。
#
# 规则 1（硬性，0 容忍）：ui/ 层不得出现 `Color(0x...)` 颜色字面量。
#   颜色的唯一事实源是 app/src/main/java/com/example/yanji/theme/Color.kt；
#   新增颜色请先在 Color.kt 里起语义名，再引用。
#
# 规则 2（棘轮，已降到 0 容忍）：ui/ 层裸 `RoundedCornerShape(<N>.dp)` 数量不得超过 RADIUS_CEILING。
#   - RADIUS_CEILING 现为 **0**：ui/ 层已无裸圆角字面量，所有产品级圆角一律走 theme/Radius.kt 的
#     YanjiRadius 语义 token（取值与原字面量逐一相等，视觉零变化），因此上限直接压到 0；
#   - 允许存在的情形只剩「局部几何」：自定义图表、进度条轨道/填充、以及确实没有同值 token 的
#     紧凑控件——这些行必须写 `// token-exempt: <原因>`（必须写原因）。
#   - 计数按「出现次数」而非「行数」：一行两处算两处；注释里的示例代码也会被计入，
#     所以注释请引用 token 名（如 RoundedCornerShape(YanjiRadius.Small)）而不是裸数字；
#   - 行级豁免：在行尾写 `// token-exempt: <原因>`（必须写原因）。
#     豁免只应用于「确实属于局部几何、而不是产品组件圆角」的地方。
#
# 规则 3（硬性，0 容忍）：ui/ 层不得引用**静态亮色 token**（YanjiPrimary / YanjiTextPrimary /
#   YanjiSurface … 共 24 个，见 PATTERN_STATIC 枚举）。它们是 theme/Color.kt 的顶层 val 编译期
#   常量，暗色模式下永远返回亮色值，等于暗色失效。必须改用 `MaterialTheme.colorScheme.*`
#   （M3 有槽位的）或 `YanjiColors.*`（M3 没有的角色，见 theme/ExtraColors.kt）；
#   学科序列色用 `yanjiSubjectColor*()`。
#   允许 ui 层引用的专用实色 token（故不入清单）：YanjiSegmentTrack / YanjiSegmentTrackOnPage /
#   YanjiDarkSegmentTrackOnPage / YanjiDarkSegmentPillOnPage / YanjiPowerSaving* / Achievement*。
#
#   ⚠ 历史坑（勿改回）：本条曾把「反斜杠 + b」写成了单个退格符 0x08，正则因此要求行内出现一个
#   退格字节，永远 0 命中、永远显示绿色通过 —— 而当时的「0 命中」纯属巧合正确。
#   下面的自检会在模式串混入控制字符、或边界写法失效时直接失败，而不是静默放行。
#
# 规则 4/5/6（棘轮，只降不涨）：间距 / 字号 / 时长的裸字面量数量。
#   这三条轴目前**没有完整的 token 可供替换**（theme/Spacing.kt 只有 12 个角色名、
#   theme/Motion.kt 完全没有时长与缓动档位），强行给 6/10/14/18.dp 编名字只会造出第二套命名法。
#   所以这里不要求「立刻用 token」，只要求「别再变多」：数值下降后把 CEILING 改成新的实际值。
#   计数是**下界**：一行里第二个 `vertical = 6.dp` 这类不会被计入（正则锚在函数名上）。
set -uo pipefail
cd "$(dirname "$0")/.."

UI_DIR="app/src/main/java/com/example/yanji/ui"
RAW_COLOR_CEILING=0
RADIUS_CEILING=0
STATIC_COLOR_CEILING=0
SPACING_CEILING=294
PADDING_VALUES_CEILING=18
FONT_SIZE_CEILING=111
TWEEN_CEILING=48

PATTERN_COLOR='Color\(0x'
PATTERN_RADIUS='RoundedCornerShape\([0-9]+(\.[0-9]+)?\.dp\)'
PATTERN_SPACING='(spacedBy|padding)\(([^)]*= )?[0-9]+(\.[0-9]+)?\.dp'
PATTERN_PADDING_VALUES='PaddingValues\([^)]*[0-9](\.[0-9]+)?\.dp'
PATTERN_FONT_SIZE='fontSize = [0-9]+(\.[0-9]+)?\.sp'
PATTERN_TWEEN='tween\('
PATTERN_STATIC='Yanji(OnPrimary|Background|SurfaceBlue|SurfaceSoft|Surface|TextPrimary|TextSecondary|TextTertiary|QuaternaryLabel|ElevatedSurface|Border|Divider|Lavender|LavenderSoft|LavenderDeep|Primary|PrimaryStrong|PrimarySoft|Success|SuccessSoft|Warning|WarningSoft|Danger|DangerSoft)\b'

# 自检 A：模式串必须是纯可见字符。0x08 这类隐形字节会被 grep 当字面量匹配，使规则静默失效。
for pat in "$PATTERN_COLOR" "$PATTERN_RADIUS" "$PATTERN_STATIC"; do
  if printf '%s' "$pat" | LC_ALL=C grep -q '[^[:print:]]'; then
    echo "x 守卫自检失败：模式串含控制字符，规则将静默失效" >&2
    printf '%s' "$pat" | cat -v >&2; echo >&2
    exit 1
  fi
done
# 自检 B：规则 3 必须能命中一个已知静态 token（防止边界写法被改到永不匹配）。
if ! printf 'val c = YanjiSurface\n' | grep -qE "$PATTERN_STATIC"; then
  echo "x 守卫自检失败：PATTERN_STATIC 无法匹配示例 YanjiSurface，规则 3 已失效" >&2
  exit 1
fi
# 自检 C：不得误伤派生名与暗色同名 token。
if printf 'val c = YanjiDarkSurface YanjiSegmentTrackOnPage\n' | grep -qE "$PATTERN_STATIC"; then
  echo "x 守卫自检失败：PATTERN_STATIC 缺少词边界，误伤暗色/专用 token" >&2
  exit 1
fi

scoped() {
  grep -rnE "$1" "$UI_DIR" --include='*.kt' 2>/dev/null \
    | grep -v 'token-exempt:' \
    | grep -oE "$1" | wc -l | tr -d ' '
}

# 失败时打印具体命中行（同样过滤豁免行）。
listed() {
  grep -rnE "$1" "$UI_DIR" --include='*.kt' 2>/dev/null \
    | grep -v 'token-exempt:' | sed 's/^/    /'
}

raw_color=$(scoped "$PATTERN_COLOR")
radius=$(scoped "$PATTERN_RADIUS")
static_color=$(scoped "$PATTERN_STATIC")
status=0
spacing=$(scoped "$PATTERN_SPACING")
padding_values=$(scoped "$PATTERN_PADDING_VALUES")
font_size=$(scoped "$PATTERN_FONT_SIZE")
tween=$(scoped "$PATTERN_TWEEN")

if [ "$raw_color" -gt "$RAW_COLOR_CEILING" ]; then
  echo "✗ 颜色：ui/ 层出现 $raw_color 处 Color(0x...) 字面量（上限 $RAW_COLOR_CEILING）"
  listed "$PATTERN_COLOR"
  echo "  → 请先在 theme/Color.kt 添加语义 token，再引用；不要内联 hex。"
  status=1
else
  echo "✓ 颜色：ui/ 层 Color(0x...) 命中 $raw_color（上限 $RAW_COLOR_CEILING）"
fi

if [ "$radius" -gt "$RADIUS_CEILING" ]; then
  echo "✗ 圆角：ui/ 层裸 RoundedCornerShape(<N>.dp) = $radius，超过棘轮上限 $RADIUS_CEILING"
  listed "$PATTERN_RADIUS"
  echo "  → 请优先复用 theme/Radius.kt 的 YanjiRadius 语义 token；"
  echo "     确属局部几何时，在该行加 '// token-exempt: <原因>'，或按实际值下调 RADIUS_CEILING。"
  status=1
else
  echo "✓ 圆角：ui/ 层裸 RoundedCornerShape(<N>.dp) = $radius（棘轮上限 $RADIUS_CEILING）"
fi

if [ "$static_color" -gt "$STATIC_COLOR_CEILING" ]; then
  echo "✗ 主题：ui/ 层仍有 $static_color 处静态亮色 token（上限 $STATIC_COLOR_CEILING）"
  listed "$PATTERN_STATIC"
  echo "  → 静态 token 是编译期常量，暗色下不会变。请改用："
  echo "     MaterialTheme.colorScheme.*（primary/surface/onSurface/outline/tertiary/error…）"
  echo "     或 YanjiColors.*（textTertiary / warning / warningSoft / lavenderDeep / surfaceBlue）。"
  status=1
else
  echo "✓ 主题：ui/ 层静态亮色 token 命中 $static_color（上限 $STATIC_COLOR_CEILING）"
fi

# 规则 4/5/6：三条「只降不涨」的棘轮。这三条轴目前**没有可替换的完整 token**
# （Spacing.kt 只有 12 个角色名、Motion.kt 完全没有时长与缓动档位），
# 所以不要求立刻改用 token，只要求别再变多；数值下降后请把对应 CEILING 改成新的实际值。
report_ratchet() {
  name="$1"; actual="$2"; ceiling="$3"; hint="$4"
  if [ "$actual" -gt "$ceiling" ]; then
    echo "✗ ${name}：ui/ 层裸字面量 = ${actual}，超过棘轮上限 ${ceiling}"
    echo "  → ${hint}"
    status=1
  else
    echo "✓ ${name}：ui/ 层裸字面量 = ${actual}（棘轮上限 ${ceiling}）"
  fi
}

report_ratchet "间距" "$spacing" "$SPACING_CEILING" "优先改用 YanjiSpacing 角色 token；确需新值请先在 theme/Spacing.kt 命名。"
report_ratchet "PaddingValues" "$padding_values" "$PADDING_VALUES_CEILING" "同上，改用 YanjiSpacing。"
report_ratchet "字号" "$font_size" "$FONT_SIZE_CEILING" "约九成裸 fontSize 与 theme/Type.kt 既有 style 同值，请改用 MaterialTheme.typography.* 或 YanjiTypography。"
report_ratchet "时长" "$tween" "$TWEEN_CEILING" "新增过渡请复用 YanjiMotion 既有 spec，别再造新时长。"

exit $status
