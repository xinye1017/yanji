---
version: "alpha"
name: "Yanji Rounded Blue"
description: "研迹 Android mobile design system: calm blue-and-white, highly rounded, minimal, spacious, study-focused, with subtle lavender accents inspired by 卷卷."
colors:
  primary: "#356AE6"
  primary-strong: "#2453BF"
  primary-soft: "#EAF1FF"
  on-primary: "#FFFFFF"
  background: "#F7F9FC"
  surface: "#FFFFFF"
  surface-soft: "#F1F5FB"
  surface-blue: "#F4F7FF"
  text-primary: "#172033"
  text-secondary: "#667085"
  text-tertiary: "#98A2B3"
  border: "#E4EAF2"
  divider: "#EDF1F6"
  accent-lavender: "#8B7CF6"
  accent-lavender-soft: "#F0EDFF"
  success: "#2F9E6D"
  success-soft: "#E8F7F0"
  warning: "#D99024"
  warning-soft: "#FFF5E3"
  danger: "#D94B4B"
  danger-soft: "#FDECEC"
colors-dark:
  primary: "#5B8BF5"
  primary-strong: "#82A9F8"
  primary-soft: "#1E293B"
  on-primary: "#FFFFFF"
  background: "#0F172A"
  surface: "#1E293B"
  surface-soft: "#243247"
  surface-blue: "#1E293B"
  text-primary: "#F1F5F9"
  text-secondary: "#94A3B8"
  text-tertiary: "#64748B"
  border: "#334155"
  divider: "#1E293B"
  accent-lavender: "#A78BFA"
  accent-lavender-soft: "#2A244D"
  success: "#34D399"
  success-soft: "#14382B"
  warning: "#FBBF24"
  warning-soft: "#3B2E14"
  danger: "#F87171"
  danger-soft: "#3B1818"
typography:
  display-lg:
    fontFamily: "Roboto"
    fontSize: "40px"
    fontWeight: 700
    lineHeight: "48px"
    letterSpacing: "-0.02em"
  display-md:
    fontFamily: "Roboto"
    fontSize: "32px"
    fontWeight: 700
    lineHeight: "40px"
    letterSpacing: "-0.015em"
  headline-lg:
    fontFamily: "Roboto"
    fontSize: "24px"
    fontWeight: 700
    lineHeight: "32px"
  headline-md:
    fontFamily: "Roboto"
    fontSize: "20px"
    fontWeight: 600
    lineHeight: "28px"
  title-md:
    fontFamily: "Roboto"
    fontSize: "16px"
    fontWeight: 600
    lineHeight: "24px"
  body-lg:
    fontFamily: "Roboto"
    fontSize: "16px"
    fontWeight: 400
    lineHeight: "26px"
  body-md:
    fontFamily: "Roboto"
    fontSize: "14px"
    fontWeight: 400
    lineHeight: "22px"
  label-lg:
    fontFamily: "Roboto"
    fontSize: "14px"
    fontWeight: 600
    lineHeight: "20px"
  label-md:
    fontFamily: "Roboto"
    fontSize: "12px"
    fontWeight: 500
    lineHeight: "18px"
  metric-xl:
    fontFamily: "Roboto"
    fontSize: "52px"
    fontWeight: 700
    lineHeight: "60px"
    letterSpacing: "-0.03em"
rounded:
  xs: "8px"
  sm: "12px"
  md: "16px"
  lg: "20px"
  xl: "24px"
  xxl: "28px"
  pill: "999px"
spacing:
  xs: "4px"
  sm: "8px"
  md: "12px"
  lg: "16px"
  xl: "20px"
  xxl: "24px"
  xxxl: "32px"
  section: "40px"
components:
  app-background:
    backgroundColor: "{colors.background}"
    textColor: "{colors.text-primary}"
  card-hero:
    backgroundColor: "{colors.surface}"
    textColor: "{colors.text-primary}"
    borderColor: "{colors.border}"
    borderRadius: "{rounded.xxl}"
  card:
    backgroundColor: "{colors.surface}"
    textColor: "{colors.text-primary}"
    borderColor: "{colors.border}"
    borderRadius: "{rounded.xl}"
  card-compact:
    backgroundColor: "{colors.surface}"
    textColor: "{colors.text-primary}"
    borderColor: "{colors.border}"
    borderRadius: "{rounded.md}"
  card-soft:
    backgroundColor: "{colors.surface-blue}"
    textColor: "{colors.text-primary}"
    borderColor: "{colors.divider}"
    borderRadius: "{rounded.xl}"
  button-primary:
    backgroundColor: "{colors.primary}"
    textColor: "{colors.on-primary}"
    borderRadius: "{rounded.sm}"
  button-secondary:
    backgroundColor: "{colors.primary-soft}"
    textColor: "{colors.primary-strong}"
    borderColor: "{colors.border}"
    borderRadius: "{rounded.sm}"
  button-danger:
    backgroundColor: "{colors.danger-soft}"
    textColor: "{colors.danger}"
    borderRadius: "{rounded.sm}"
  input:
    backgroundColor: "{colors.surface-soft}"
    textColor: "{colors.text-primary}"
    borderColor: "{colors.border}"
    borderRadius: "{rounded.md}"
  nav-active:
    backgroundColor: "{colors.primary-soft}"
    textColor: "{colors.primary}"
    borderRadius: "{rounded.pill}"
  nav-inactive:
    backgroundColor: "{colors.surface}"
    textColor: "{colors.text-secondary}"
  chip-blue:
    backgroundColor: "{colors.primary-soft}"
    textColor: "{colors.primary-strong}"
    borderRadius: "{rounded.pill}"
  chip-lavender:
    backgroundColor: "{colors.accent-lavender-soft}"
    textColor: "{colors.accent-lavender}"
    borderRadius: "{rounded.pill}"
  success-state:
    backgroundColor: "{colors.success-soft}"
    textColor: "{colors.success}"
    borderRadius: "{rounded.md}"
  warning-state:
    backgroundColor: "{colors.warning-soft}"
    textColor: "{colors.warning}"
    borderRadius: "{rounded.md}"
  caption:
    backgroundColor: "{colors.surface}"
    textColor: "{colors.text-tertiary}"
---

## Overview

### Product

**研迹（Yanji）** 是一款仅供个人使用的 Android 考研日记与学习管理应用。它围绕四件事展开：

1. 写下每天的考研日记；
2. 记录真实专注时长；
3. 进行数学、专业课、英语、政治等模拟考试倒计时；
4. 通过统计和第三方 AI API 复盘阶段学习状态。

产品 IP 为拟人化圆角笔记本角色 **“卷卷”**。卷卷代表“记录、陪伴、积累”，而不是监督者或打鸡血的教练。

### Design direction

The visual direction is **Calm Rounded Study Companion**.

界面应该像一个安静、可靠、每天都愿意打开的学习伙伴，而不是企业数据后台、效率工具仪表盘或游戏化打卡 App。

核心视觉关键词：

- **圆角化**
- **蓝白**
- **清爽**
- **克制**
- **柔和**
- **大留白**
- **少边框**
- **信息层级清晰**
- **移动端原生感**
- **轻量 Material 3 气质**
- **有温度但不幼稚**

第一眼应当感受到“干净、可靠、舒服”，第二眼才看到统计信息。

### Non-goals

The UI must NOT look like:

- 企业 SaaS Dashboard；
- Web 管理后台；
- Notion 克隆；
- 游戏化签到工具；
- 霓虹科技风；
- 玻璃拟态 / Glassmorphism；
- 过度渐变的 AI 产品；
- 密集卡片墙；
- 黑金、赛博朋克、电竞风；
- “高考倒计时”式焦虑红色界面。

### Platform and canvas

Design for **Android mobile first**.

Reference viewport:

- Width: 390–412 dp
- Height: 844–915 dp
- Portrait orientation
- Respect Android status bar and gesture/navigation inset
- Primary interactive controls must be comfortable for one-handed touch

Generate mobile screens, not desktop-responsive pages.

---

## Colors

### Palette philosophy

Use a **blue + white** foundation.

Blue represents focus, reliability and calmness. White and cool off-white create breathing room. Lavender is reserved as a small brand accent that visually connects the product UI with the purple background of the supplied “卷卷” mascot/icon.

The page must stay predominantly neutral:

- 70–80% background / white surfaces
- 15–25% blue family
- <= 5% lavender or semantic colors

Do not turn every card blue.

### Primary blue

**Primary — `#356AE6`**

Use for:

- primary actions;
- active navigation;
- focus timer progress;
- links;
- selected states;
- key chart series;
- important but non-dangerous emphasis.

**Primary Strong — `#2453BF`**

Use for:

- pressed/strong states;
- blue text on pale blue surfaces;
- selected labels requiring stronger contrast.

**Primary Soft — `#EAF1FF`**

Use for:

- selected chips;
- active navigation pill;
- soft highlight cards;
- icon containers;
- subtle progress backgrounds.

### Background and surfaces

**App Background — `#F7F9FC`**

This is the default page background. It is intentionally not pure white so white cards remain visible without heavy shadow or border.

**Surface — `#FFFFFF`**

Primary cards, sheets and floating surfaces.

**Surface Soft — `#F1F5FB`**

Inputs, subdued controls, secondary blocks.

**Surface Blue — `#F4F7FF`**

Very subtle blue-tinted feature cards. Use sparingly.

### Text

**Text Primary — `#172033`**

Main titles, values, timer digits, body text requiring strong readability.

**Text Secondary — `#667085`**

Descriptions, metadata and secondary labels.

**Text Tertiary — `#98A2B3`**

Hints, disabled information and minor captions.

Do not use pure black `#000000`.

### Brand accent

**Lavender — `#8B7CF6`**

Use only for:

- 卷卷-related decorative moments;
- AI analysis badge;
- occasional secondary chart series;
- small brand illustration details.

**Lavender Soft — `#F0EDFF`**

Use as the matching soft container.

Lavender must never compete with primary blue for the main CTA.

### Semantic colors

Green, amber and red are functional colors, not decoration.

- Success `#2F9E6D`
- Warning `#D99024`
- Danger `#D94B4B`

Never use red for the exam countdown merely because time is decreasing. Red is reserved for destructive actions or genuine warnings.

### Gradients

Default: **no large gradients**.

A very subtle blue tonal gradient may appear only in a hero/background accent when a flat fill feels too empty, but the UI should still read as essentially flat.

Avoid:

- blue-purple neon gradients;
- gradient text;
- glowing gradients;
- multiple gradient cards on one screen.

---

## Typography

### Typeface

Use **Roboto** throughout the Android UI.

Do not mix serif and sans-serif fonts. Do not introduce a display font just to create personality. Personality comes from spacing, rounded geometry, the mascot and calm composition.

### Hierarchy

#### Large metric

Use `metric-xl` for:

- focus timer;
- exam countdown;
- major accumulated study time.

Example:

`02:47:36`

Large numeric metrics should have generous negative space around them.

#### Display

Use `display-md` / `display-lg` for:

- “105 天” exam countdown;
- weekly total study hours;
- important hero metrics.

#### Headlines

Use `headline-lg` for primary screen titles.

Examples:

- 今天
- 专注
- 日记
- 学习统计
- AI 复盘

Use `headline-md` for card titles and section headers.

#### Body

Body copy must remain comfortably readable. Avoid tiny text.

Normal body minimum: 14 px equivalent.

### Numerical styling

Numbers are central to this product.

Timer and statistics numbers must:

- be visually stable;
- use consistent weight;
- never use decorative condensed fonts;
- align cleanly;
- be larger than surrounding labels;
- avoid overusing bold outside the key metric.

### Chinese text

Chinese copy should be concise and natural.

Prefer:

- “今日学习”
- “本周累计”
- “开始专注”
- “提前交卷”
- “最近 7 天”

Avoid corporate wording such as:

- “数据驾驶舱”
- “核心指标概览”
- “智能化赋能”
- “学习效能管理中心”

---

## Layout

### Global spacing

Use an **8 px rhythm** with a few 4 px refinements.

Default page horizontal padding:

**20 px**

Section spacing:

**32–40 px**

Card internal padding:

**16–20 px**

Avoid placing every element edge-to-edge.

### Screen structure

A standard primary screen follows:

```text
Status Bar / Safe Area

Top spacing

Page title / compact header

Hero or primary information

Section

Section

Bottom breathing room

Persistent bottom navigation
```

### Content width

On a phone, keep nearly all content inside the standard 20 px horizontal page margin.

Full-bleed elements are limited to:

- bottom navigation background;
- modal/sheet background;
- deliberate hero background only when visually justified.

### Card density

Do not build a “card for every line”.

A screen should generally contain:

- 1 dominant hero card or hero metric;
- 2–4 supporting cards/sections;
- some information displayed directly on the page without a card.

Related content should be grouped in one larger rounded card rather than split into many tiny rectangles.

### Alignment

Prefer left-aligned text.

Center alignment is reserved for:

- focus timer;
- exam countdown;
- empty state;
- short completion state.

Statistics labels and values should align predictably.

### Bottom navigation

Persistent 5-item navigation:

1. 首页
2. 专注
3. 日记
4. 统计
5. 我的

Use icon + short label.

Active state should use a soft blue pill or softly filled icon container, not a heavy solid blue block.

Navigation height should feel comfortable, approximately 72–80 px including safe-area breathing room.

### Floating action buttons

Do not use a FAB by default.

Primary actions should normally be explicit rounded buttons or large tappable cards. A FAB may only appear if Stitch needs one for a clearly dominant “写日记” action, but it is not preferred.

---

## Elevation & Depth

### Philosophy

Depth is quiet and functional.

The interface should not look like layers of floating glass.

### Cards

Primary cards:

- white surface;
- rounded 24 px corners;
- either a 1 px very light border OR a soft shadow;
- avoid using both strong border and strong shadow.

Recommended visual shadow:

```text
0 4px 16px rgba(23, 32, 51, 0.06)
```

Larger floating sheets may use:

```text
0 12px 32px rgba(23, 32, 51, 0.10)
```

### Borders

Borders must be subtle.

Use `#E4EAF2` or lighter.

Do not use dark gray outlines around cards, buttons or text fields.

### Layer hierarchy

Preferred hierarchy:

```text
#F7F9FC app background
    ↓
#FFFFFF primary surface
    ↓
#EAF1FF or #F0EDFF soft emphasis
    ↓
#356AE6 primary action
```

No translucent glass panes are required.

---

## Shapes

### Core geometry

**Roundness is a defining feature of 研迹.**

Every major component should visually belong to the same soft geometry family.

Default radii — 与工程实现的 `theme/Radius.kt`《`YanjiRadius`》一一对应：

- small tag / compact item: 12 px
- input / content block: 16 px (`ContentBlockRadius`)
- normal button: 12 px (`ButtonRadius`)
- standard card: 24 px (`StandardCardRadius`, `YanjiCard` 默认 shape)
- page / hero card: 24 px (`PageRadius`)
- large hero card: 24–28 px
- message bubble: 20 px (`MessageRadius`)
- bottom sheet: 28 px top corners
- pill / segmented control: fully rounded (`ChipRadius`)

> 实现说明：圆角的**唯一事实来源是 `theme/Radius.kt`**。
> v1.0.0 已发布的界面上，共享组件按上表取值且视觉稳定；
> 本节曾经描述的「button / input = 20 px」从未在代码中落地，属于设计稿阶段的原始描述，
> 现已按实际实现修正，避免文档与代码互相误导。

Avoid rectangles with 4 px or 6 px corners unless a native Android control requires it.

### Buttons

Buttons should be:

- broad;
- softly rounded;
- minimum comfortable height about 52–56 px;
- visually simple;
- no glossy effect;
- no heavy outline.

Primary button:

- blue background;
- white label;
- 12 px radius (`YanjiRadius.ButtonRadius`).

Secondary button:

- pale blue background;
- strong blue label;
- no dark outline.

### Icon containers

When an icon needs a background, use a rounded 12–16 px square or circle with a very pale blue/lavender fill.

### Charts

Charts should inherit the same softness:

- rounded bar ends;
- smooth line joins;
- restrained grid lines;
- no thick black axes;
- no 3D charts.

---

## Components

### 1. App header

Primary screens use a compact, calm header.

Structure:

```text
研迹 / Page title                  optional icon action
secondary date or context
```

Do not create a full-width blue AppBar by default. Prefer the page background with dark title text.

### 2. Hero metric card

Used for:

- days until exam;
- today’s learning total;
- weekly total.

Visual:

- white or very pale blue background;
- 24–28 px radius;
- one dominant number;
- concise label;
- optional subtle progress element;
- large whitespace.

Example:

```text
距离考研

105 天

2026.12.19
```

The number is the visual anchor.

### 3. Focus CTA

The primary “开始专注” action should be obvious but calm.

Preferred:

```text
[ timer icon ]  开始专注
```

Large blue rounded button or large rounded action card.

No neon glow.

### 4. Timer display

Focus and exam timer screens are intentionally sparse.

Composition:

```text
数学
二重积分刷题

01:32:41

今日累计 6h 12m

        [ 暂停 ]

[ 结束本次专注 ]
```

Timer is the dominant element.

The timer screen must not contain charts, news, motivational quotes or multiple competing cards.

### 5. Subject chips

Use pill-shaped selectable chips.

Examples:

```text
数学   专业课   英语   政治   其他
```

Selected:

- pale blue fill;
- blue text;
- optional blue icon/check.

Unselected:

- white or soft surface;
- secondary text;
- subtle border if needed.

### 6. Progress bars

Use rounded tracks and rounded fills.

- track: pale blue/gray
- fill: primary blue
- height: 8–10 px
- no segmented pixel-like look

### 7. Diary card

Diary list cards should feel like journal entries, not task cards.

Structure:

```text
9 月 5 日 · 周六                       4/5

今天数学状态不错

下午的 408 注意力有些下降，但整体……
```

Use:

- white surface;
- 24 px radius;
- 16–20 px padding;
- date metadata;
- title/body preview;
- very subtle state indicator.

Avoid checkboxes unless the content is truly a checklist.

### 8. Diary editor

Use large, low-chrome writing surfaces.

Fields should not look like a dense enterprise form.

Preferred:

- section labels;
- soft rounded input areas;
- plenty of vertical space.

Example sections:

- 今日状态
- 今日总结
- 今日问题
- 明日计划

### 9. Statistics cards

Statistics should emphasize trends, not visual spectacle.

A statistic card should answer one question.

Examples:

- 本周学习多久？
- 哪个科目投入最多？
- 最近 7 天是否稳定？

Use:

- large value;
- small supporting delta/label;
- one simple chart.

Do not put 8 KPIs into one grid on mobile.

### 10. Charts

#### 7-day trend

Preferred:

- smooth blue line OR rounded vertical bars;
- no area gradient unless extremely subtle;
- x-axis labels minimal;
- no unnecessary y-axis labels;
- pale divider/grid lines.

#### Subject distribution

Prefer:

- horizontal rounded bars

over pie charts when exact comparison matters.

A donut chart is acceptable for a simple overview, but never use a rainbow palette.

Suggested series:

- Mathematics: `#356AE6`
- Major/408: `#6F91EA`
- English: `#8B7CF6`
- Politics: `#7CB6D9`
- Other: `#B8C6DF`

### 11. AI analysis card

AI is visually represented as a reflective assistant, not futuristic magic.

Use:

- white or soft lavender card;
- small lavender “AI 复盘” badge;
- optional supplied 卷卷 mascot;
- clear text hierarchy.

Example:

```text
AI 复盘

最近 7 天

你的总学习时长比较稳定，
但数学投入集中在上午……

[ 查看完整分析 ]
```

No glowing magic stars, holograms or robot imagery.

### 12. Exam mode card

Exam presets appear as large rounded rows/cards.

Example:

```text
数学模拟
180 分钟

[ 开始 ]
```

The start action may use a compact blue pill button.

### 13. Exam countdown screen

Extremely low-distraction.

Visual structure:

```text
数学模拟

02:47:36

13:30 — 16:30

[ 暂停 ]

提前交卷
```

No red countdown.

Only when remaining time is critically low may a subtle amber state be introduced; do not flash.

### 14. Settings rows

Use grouped rounded sections rather than separate cards for every setting.

Example:

```text
AI 设置
────────────────
API 服务商                      >
模型                            >
测试连接                        >
```

Rows have generous height and subtle dividers.

### 15. Bottom sheets

Use 28 px top corners.

Bottom sheet background: white.

Handle: subtle gray rounded capsule.

Use sheets for:

- subject selection;
- custom duration;
- date selection;
- secondary actions.

### 16. Dialogs

Dialogs are for genuine confirmation only.

Examples:

- 删除日记；
- 取消正在进行的考试；
- 导入数据会覆盖现有内容。

Do not use dialogs for ordinary navigation.

### 17. Empty states

Empty states use:

- optional small 卷卷 illustration;
- short one-line explanation;
- one clear action.

Example:

```text
今天还没有留下学习轨迹。

[ 开始专注 ]
```

Do not fill empty states with long motivational copy.

### 18. Mascot — 卷卷

When the actual supplied 卷卷 asset is available, use that asset consistently.

Do not redesign or invent a different mascot.

Usage:

- small empty-state illustration;
- AI analysis header;
- completion state;
- occasional homepage greeting.

Mascot must remain secondary to information.

Do not place 卷卷 on every card.

---

## Do's and Don'ts

### Do

- Use blue and white as the dominant language.
- Use lavender only as a restrained brand accent.
- Use 20–28 px corner radii generously.
- Use large whitespace.
- Keep each screen focused on one primary task.
- Use one dominant metric instead of many equal metrics.
- Prefer one large card over many tiny cards.
- Let typography establish hierarchy before adding decoration.
- Keep backgrounds cool white/light gray instead of pure white everywhere.
- Use pale tinted surfaces for selection and emphasis.
- Keep charts simple and rounded.
- Keep touch targets comfortably large.
- Use native-feeling Android interaction patterns.
- Make the focus and exam screens particularly sparse.
- Keep the UI calming during long-term daily use.
- Treat the user as an adult preparing seriously for an exam.

### Don't

- Do not use black backgrounds in the default theme.
- Do not use saturated red as a countdown color.
- Do not use neon colors.
- Do not use glassmorphism.
- Do not use frosted blur cards.
- Do not use strong gradients.
- Do not use glowing buttons.
- Do not use 3D charts.
- Do not use hard 4 px card corners.
- Do not use heavy dark borders.
- Do not put every data item into its own card.
- Do not generate a desktop-style dashboard grid.
- Do not overuse icons.
- Do not add emoji as core UI decoration.
- Do not use random illustrations unrelated to 卷卷.
- Do not add gamified coins, XP, level bars or leaderboards.
- Do not create motivational quote banners.
- Do not use excessive badges.
- Do not put two or three primary CTA buttons side by side.
- Do not make the visual design feel childish.
- Do not make AI analysis look like a sci-fi assistant.

---

## Motion

Motion is subtle and calm.

Recommended durations:

- micro interaction: 120–180 ms
- standard transition: 200–280 ms
- modal/sheet: 240–320 ms

Recommended easing:

- smooth ease-out
- Material-like spring with low overshoot only where appropriate

Allowed:

- button press scale to ~0.98;
- progress bar smooth fill;
- numeric transition;
- card fade/slide;
- bottom sheet movement;
- subtle mascot entrance.

Avoid:

- bouncing repeatedly;
- confetti;
- fireworks;
- shaking;
- glowing pulses;
- continuous decorative motion.

Timer digits should update without layout jitter.

---

## Iconography

Use simple rounded Material-style line icons.

Rules:

- consistent stroke weight;
- default 20–24 px;
- active icons can use filled or stronger blue treatment;
- do not mix multiple icon families;
- do not put icons next to every text label.

Recommended semantic icons:

- Home
- Timer
- MenuBook / EditNote
- BarChartRounded
- Settings / Person
- CalendarMonth
- Psychology / AutoAwesome only for AI, used sparingly
- PlayArrow
- PauseRounded
- StopRounded
- ChevronRightRounded

Use icons as navigation/support, not decoration.

---

## Accessibility

### Contrast

Maintain WCAG AA-level contrast for normal text whenever possible.

Never place light blue text on white simply for aesthetics.

Primary body copy must use `text-primary` or `text-secondary`.

### Touch targets

Interactive targets should be at least approximately:

**48 × 48 dp**

Primary actions should be larger.

### Font size

Do not create critical text below 12 px.

Normal reading content should be 14–16 px or larger.

### Color independence

Do not rely on color alone to communicate:

- paused/running;
- success/failure;
- selected/unselected;
- warning state.

Pair color with label, icon or shape.

### Timer readability

Timer digits must remain clear at a glance and under different screen brightness levels.

### Reduced motion

The design should remain usable with animations reduced or removed.

---

## Stitch Screen Generation Brief

The following section describes the screens Stitch should generate using this design system.

### Global Stitch instruction

Generate a cohesive set of **Android mobile app screens** for 研迹.

All screens must:

- share the same blue-white visual system;
- use rounded 20–28 px geometry;
- use a cool off-white page background;
- use white cards sparingly;
- use Roboto-like Android typography;
- maintain 20 px horizontal margins;
- include generous whitespace;
- feel native to a polished modern Android app;
- use Material 3 principles without looking like an untouched Material template;
- avoid desktop dashboard layouts;
- avoid excessive cards;
- avoid glassmorphism and gradients;
- preserve the persistent five-tab bottom navigation on appropriate primary screens.

When a real 卷卷 image asset is provided, use it; otherwise reserve an illustration slot rather than inventing a different character.

---

## Screen 01 — 首页 / Today

### Purpose

Let the user understand today’s preparation status within five seconds.

### Layout

1. Simple top header:
   - “研迹”
   - today’s date in smaller gray text
   - small optional 卷卷 avatar/action on the right

2. Exam countdown hero:
   - “距离考研”
   - large “105 天”
   - small target date
   - pale blue or white hero card

3. Today study block:
   - “今日学习”
   - large `7h 32m`
   - daily goal `目标 10h`
   - rounded blue progress bar

4. Subject distribution:
   - Math 3h 42m
   - Major 2h 30m
   - English 1h 20m
   - compact rounded bars, not a pie chart

5. Primary CTA:
   - large blue “开始专注” button

6. Secondary actions:
   - 模拟考试
   - 写日记
   - two understated rounded secondary actions

7. Small calm 卷卷 status/insight block if space permits.

The home screen must not look like a KPI dashboard.

---

## Screen 02 — 开始专注

### Layout

Header: “专注”

Large clean setup card/section:

- 科目 selector with rounded chips
- optional note input
- mode: 正向计时
- large primary button “开始专注”

Show a small line of today’s accumulated time below.

Keep setup extremely simple.

---

## Screen 03 — 专注进行中

### Purpose

A distraction-free timer.

### Layout

- centered subject: 数学
- small note: 二重积分刷题
- enormous timer: `01:32:41`
- small “今日累计 6h 12m”
- primary rounded pale-blue/blue pause control
- secondary text-style “结束本次专注”

No bottom navigation while deeply focused if hiding it improves immersion.

No chart.

No motivational quote.

---

## Screen 04 — 专注完成

### Layout

Centered completion state.

- optional small 卷卷
- “本次专注”
- `1h 43m`
- “数学”
- “今日累计 6h 12m”
- blue “完成” button

Calm satisfaction, no confetti.

---

## Screen 05 — 模拟考试选择

### Layout

Header: “模拟考试”

Intro text:
“选择科目并进入全屏倒计时。”

Large rounded preset rows:

- 数学模拟 — 180 分钟
- 专业课模拟 — 180 分钟
- 英语模拟 — 180 分钟
- 政治模拟 — 180 分钟

Final row:
- 自定义考试

Each item uses one simple icon + title + duration + chevron / compact start action.

---

## Screen 06 — 模拟考试进行中

### Layout

Ultra-minimal:

- “数学模拟”
- enormous `02:47:36`
- `13:30 — 16:30`
- pause button
- “提前交卷” text/secondary button

Use blue, not red.

Remove unrelated navigation and cards.

---

## Screen 07 — 日记列表

### Layout

Header:
- “日记”
- month selector `2026 年 9 月`

Optional horizontal compact calendar/date strip.

Diary cards vertically:

- date
- mood/state score
- short title
- 2–3 line preview
- small today study duration

Use generous gaps between entries.

Primary action:
“写今天的日记”

Do not make the page resemble an email inbox.

---

## Screen 08 — 日记编辑

### Layout

Header:
- back
- “9 月 5 日”
- save action

Content:

1. 今日状态 — simple 1–5 selector
2. 今日总结 — large rounded writing area
3. 今日问题 — medium writing area
4. 明日计划 — medium writing area

At bottom show a read-only soft summary:

`今日学习 8h 21m`

This is a writing screen, so reduce decorative cards and maximize quiet writing space.

---

## Screen 09 — 学习统计

### Layout

Header: “统计”

Segmented period selector:

- 本周
- 本月
- 全部

Hero total:
`48h 32m`

Supporting:
`日均 6h 56m`

7-day trend chart in one large rounded card.

Subject distribution as horizontal rounded bars.

Small metrics section:
- 连续学习 7 天
- 最长专注 2h 16m
- 模拟考试 3 次

Do not use a 2×4 dashboard KPI grid.

---

## Screen 10 — AI 复盘

### Layout

Header:
“AI 复盘”

Soft lavender brand card:
- optional 卷卷
- period selector: 最近 7 天
- small AI badge

Primary action:
“分析最近 7 天”

After-result state:

Sections with generous spacing:

- 本阶段概览
- 做得好的地方
- 值得注意的问题
- 趋势
- 未来 3 天建议

Use a reading-oriented layout.

Do not render the analysis as chat bubbles.

---

## Screen 11 — 我的 / 设置

### Layout

Header:
“我的”

Grouped rounded settings sections.

Group 1 — 考研
- 考研日期
- 每日目标
- 有效学习日阈值

Group 2 — AI
- API 设置
- 模型
- 测试连接

Group 3 — 偏好
- 声音
- 震动
- 外观

Group 4 — 数据
- 导出备份
- 导入备份

Footer:
- 关于研迹
- version

Avoid profile/social elements because the product has one local user and no account system.

---

## Screen 12 — AI API 设置

### Layout

Header:
“AI 设置”

Fields:

- 服务名称
- Base URL
- API Key
- Model
- Temperature

Use soft filled rounded inputs.

API Key:
- obscured by default;
- eye visibility toggle.

Bottom:

- secondary “测试连接”
- primary “保存”

This screen is technical, but visually consistent with the rest of the app.

---

## Screen 13 — 数据备份

### Layout

Header:
“数据备份”

Two clear large sections:

**导出**
- last backup date if available
- “导出 JSON” primary/secondary action

**导入**
- short caution text
- “选择备份文件”

Use a subtle warning container only when an import may overwrite data.

---

## Final Visual Validation Checklist

Before accepting any generated screen, verify:

1. Does it look like a mobile Android app, not a web dashboard?
2. Is the background predominantly cool white/light gray?
3. Is primary blue `#356AE6` visually dominant over lavender?
4. Are corners consistently rounded around 20–28 px?
5. Is there enough empty space?
6. Are cards used only for meaningful grouping?
7. Is there one obvious primary action?
8. Are timer/statistical numbers visually dominant where appropriate?
9. Are borders and shadows subtle?
10. Is there no glassmorphism?
11. Is there no neon gradient?
12. Is there no unnecessary red?
13. Is the UI calm rather than gamified?
14. Does the bottom navigation contain exactly 首页 / 专注 / 日记 / 统计 / 我的 on primary screens?
15. Is 卷卷 used sparingly and consistently if the real asset is supplied?
16. Can all Chinese labels be read comfortably without tiny type?
17. Would this interface still feel pleasant after opening it every day for six months?

If a generated screen violates several of these rules, regenerate or simplify it rather than adding more decoration.
