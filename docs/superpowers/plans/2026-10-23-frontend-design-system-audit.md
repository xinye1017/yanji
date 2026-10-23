# 前端全局设计系统与体验审查实施计划 (Frontend Design System Audit & Refinement Plan)

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 依据 10 项严谨的前端设计与体验规范，全面重构与审查「研迹」React Native 前端设计系统（Tokens）、文案、颜色（Radix UI Colors）、边框线条（0.5px）、排版字阶（≤4种，最小12px）、几何同心圆角与动效（250ms ease-out），打造极致克制、秩序与精密的高质感界面。

**Architecture:** 
1. 以 `src/theme/tokens.ts` 为核心，将全仓库色彩系统完整映射至 Radix UI Colors 12 步色彩阶梯规范；将布局边框统一定义为 `0.5px`；将全站文字字号收敛为 4 阶核心体系（Hero Display, Title 20px, Body 15px, Caption/Meta 12px）。
2. 在此基础上，逐屏逐组件重构 Today、Focus、Review、Settings、Modals 与导航栏，全面移除中点分隔符 “·” 与冗余唠叨文案，消除卡片左侧色条与厚重视觉阴影，修正同心圆角与光学对齐。
3. 同步修正测试契约（`test-e2e/`），保证 237 个 E2E 测试全部 PASS，并在真机上完成验证。

**Tech Stack:** React Native, TypeScript, Radix UI Colors 规范, React Native SVG, Node test runner.

**Spec:** 用户指令 10 条前端审查规则：
1. 不使用中点 “·” 作为分隔符；
2. 剔除冗余解释性描述文本；
3. 文案符合人群、自然适中、不造词；
4. 全面换用 Radix UI Colors 12 阶颜色体系规范；
5. 优化对齐、光学对齐、嵌套同心圆圆角；
6. 线条/边框统一使用 0.5px；
7. 动效克制，统一采用 250ms ease-out；
8. 界面字号不超过 4 种（最好 3 种），最小 12px；
9. 降低判断交互，自然布局，重组优先级；
10. 克制圆角与阴影，禁止卡片左侧色条。

---

## Global Constraints

- 不使用中点 “·” 作为文案分隔符。
- 界面文字尺寸严格限制为最多 4 种，最小不小于 12px。
- 边框和线条统一使用 0.5px。
- 颜色体系严格基于 Radix UI Colors（Sand/Slate 作为中性色，Tomato/Rust 作为主品牌色，Amber/Jade/Ruby 为状态色）。
- 禁止卡片左侧出现带颜色的线条或重度阴影。
- 动效统一收敛为 250ms ease-out。
- 不得破坏 Room 真实数据源，不注入虚假 Mock 数据。
- 修改必须通过 `npm run typecheck`、`npm test`（237 项测试通过）、`npm run bundle:android` 和 `assembleDebug`。

---

## Review Focus

1. **测试契约兼容**：`test-e2e/contracts/theme-tokens.js` 中关于 Canvas 必须为 warm near-black（不能是 #000000）以及卡片边框 <= 1px 的守卫必须保持严格满足。
2. **中点符号彻底清除**：确保所有页面的日期、标签、标题、元数据展示中再无 “·”，用空格、括号或微小间隙自然分隔。
3. **字号阶梯统一**：替换掉分散的 14px、16px、22px、24px、28px 等碎片字号，收敛为严格的 4 阶（Display, Title: 20px, Body: 15px, Caption/Meta: 12px）。
4. **同心圆角公式**：检查内嵌元素与容器圆角，确保 $R_{inner} = \max(0, R_{outer} - padding)$。
5. **真机无闪退与无渲染异常**：真机验证界面所有滚动、切换与弹窗流畅无报错。

---

## Task Decomposition

### Task 1: 升级 Design Tokens 体系（Radix Colors + 4阶字号 + 0.5px边框 + 几何同心圆角）

**Files:**
- Modify: `src/theme/tokens.ts`
- Modify: `src/theme/ThemeProvider.tsx`
- Modify: `test-e2e/contracts/theme-tokens.js`

- [ ] **Step 1.1**: 在 `src/theme/tokens.ts` 中引入 Radix UI Colors 12 步色彩体系：
  - 中性阶梯（Sand/Slate）：
    - Light: 1 (#FDFDFC 页面底色), 2 (#F9F9F8 卡片次底色), 3 (#F1F0EF 组件底色), 6 (#DAD9D6 边框), 11 (#63635E 次要文本), 12 (#21201C 高对比文本).
    - Dark: 1 (#111315 页面底色), 2 (#16191D 卡片次底色), 3 (#1F2328 组件底色), 6 (#3B3A37 边框), 11 (#9BA1A6 次要文本), 12 (#EEEEEC 高对比文本).
  - 主色与状态阶梯（Tomato, Amber, Jade, Ruby）：
    - Brand (Tomato 9: #E54D2E, Text 11: Light #CA3214 / Dark #FF977D, Subtle 4: Light rgba(229,77,46,0.08) / Dark rgba(229,77,46,0.12)).
    - Amber (Amber 9: #FFB224, Text 11: Light #8F4E00 / Dark #FFCA16, Subtle 4).
    - Jade (Positive 9: #29A383, Text 11: Light #18794E / Dark #5FD4A6, Subtle 4).
    - Ruby (Danger 9: #E5484D, Text 11: Light #CD2B31 / Dark #FF9592, Subtle 4).
  - 科目色彩映射至 Radix 9：数学（Indigo #3E63DD）、408（Jade #29A383）、英语（Amber #F76808）、政治（Plum #AB4ABA）。
- [ ] **Step 1.2**: 在 `tokens.ts` 中将 `YanjiPaperLayout.hairlineWidth` 与默认边框设定为 `0.5`。
- [ ] **Step 1.3**: 收敛 `YanjiPaperTypography`：
  - 核心字号规范为 3 阶主要阶梯 + 1 阶特化 Hero：
    - `displayHero`: 44px (lineHeight: 52px)
    - `title`: 20px (lineHeight: 28px, 600 weight)
    - `body`: 15px (lineHeight: 22px, 400/500 weight)
    - `caption`: 12px (lineHeight: 16px, 500 weight, 最小可用字号)
  - 针对大时钟特化展示（`displayTimer`: 76px, `displayDial`: 64px）保持规范对齐。
- [ ] **Step 1.4**: 调整 `YanjiShadow`：大幅弱化或去除卡片阴影，纯依靠 Radix Step 6 (0.5px) 精密细线与背景层级区分，仅保留浮层弹窗的极轻微微阴影。
- [ ] **Step 1.5**: 验证测试契约并运行 `npm run typecheck`。

---

### Task 2: 全面移除文案中的 “·” 中点与冗余唠叨文案

**Files:**
- Modify: `src/components/BackupDataSection.tsx`
- Modify: `src/components/DecisionModelSection.tsx`
- Modify: `src/components/RecordMomentModal.tsx`
- Modify: `src/components/SettingsSheets.tsx`
- Modify: `src/screens/FocusScreen.tsx`
- Modify: `src/screens/ReviewScreen.tsx`
- Modify: `src/screens/SettingsScreen.tsx`
- Modify: `src/screens/TodayScreen.tsx`
- Modify: `src/screens/todayViewLogic.ts`

- [ ] **Step 2.1**: 清理 `src/screens/TodayScreen.tsx` 与 `todayViewLogic.ts` 中所有的 “·”：
  - `昨日计划 · 暂未完成` -> `昨日未完成计划`
  - `${source.getMonth() + 1}月${source.getDate()}日计划 · 暂未完成` -> `${source.getMonth() + 1}月${source.getDate()}日未完成`
  - `目标 ${goalClock(goalSeconds)} · 达成率` -> `目标 ${goalClock(goalSeconds)} (${percent}%)`
  - `{selection.subjectName} · {duration(selection.seconds)}` -> `{selection.subjectName} {duration(selection.seconds)}`
  - 移除冗余口语化文案：将 `按自己的步调开始今天的第一轮专注。` -> `暂无专注记录`；`写下一个待办，也可以直接开始专注。` -> `暂无待办任务`；`随手记点想法，不给专注添负担。` -> `暂无随记`。
- [ ] **Step 2.2**: 清理 `src/screens/ReviewScreen.tsx`：
  - `备考手账` -> `回顾`
  - `数据洞察 · 真实记录沉淀` -> `趋势分析`
  - `${year} 年 ${month} 月 · 第 ${week} 周` -> `${year}年${month}月 第${week}周`
  - `疑点 · 已解决` -> `已解决`；`疑点 · 待办` -> `待办`
  - 移除卡片左侧色条（如有）。
- [ ] **Step 2.3**: 清理 `FocusScreen.tsx`、`SettingsScreen.tsx`、`BackupDataSection.tsx`、`DecisionModelSection.tsx`、`RecordMomentModal.tsx` 中的所有 “·”。
- [ ] **Step 2.4**: 运行 `npm test` 保证逻辑与测试断言一致。

---

### Task 3: 优化 Today 界面（对齐、同心圆角、0.5px边框、Radix色彩与字阶）

**Files:**
- Modify: `src/screens/TodayScreen.tsx`
- Modify: `src/components/YanjiUI.tsx`

- [ ] **Step 3.1**: 将 Today 界面所有边框改为 `0.5px`（`borderWidth: layout.hairlineWidth` 或 `0.5`）。
- [ ] **Step 3.2**: 统一卡片与子元素的圆角为同心圆角关系：
  - 卡片外框：`radius.lg` (16px)，内边距 padding: 12px，内部按钮/标签圆角为 `16 - 12 = 4px`（或 pill）。
- [ ] **Step 3.3**: 规范 Today 界面文字字号，收敛为 12px / 15px / 20px / 44px（累计用时大字）。
- [ ] **Step 3.4**: 运行 `npm run typecheck` 验证。

---

### Task 4: 优化 Focus 界面（寸阴尺羽化、时钟、按钮动效 250ms、光学对齐）

**Files:**
- Modify: `src/screens/FocusScreen.tsx`
- Modify: `src/components/FocusVernierDial.tsx`
- Modify: `src/components/RollingTimeDisplay.tsx`

- [ ] **Step 4.1**: 确保准备态寸阴尺与沉浸态计时器使用 Radix UI Colors。
- [ ] **Step 4.2**: 动效统一设定为 `duration: 250`，`easing: Easing.out(Easing.ease)`。
- [ ] **Step 4.3**: 统一边框为 `0.5px`，文字字号对齐至规范体系（12px / 15px / 20px）。
- [ ] **Step 4.4**: 运行 `npm test` 验证。

---

### Task 5: 优化 Review 界面（信息秩序、消除左侧边框色线、纯净卡片）

**Files:**
- Modify: `src/screens/ReviewScreen.tsx`

- [ ] **Step 5.1**: 彻底检查时间线与趋势图表卡片，移除所有 `borderLeftWidth` 装饰色线或卡片左侧色条，统一使用统一的 Radix 0.5px 边框。
- [ ] **Step 5.2**: 优化时间线（Timeline）与周视图胶片（WeekFilmstrip）的光学对齐。
- [ ] **Step 5.3**: 规范文字字号为 12px / 15px / 20px，色彩对齐 Radix 11/12。
- [ ] **Step 5.4**: 运行 `npm run typecheck` 验证。

---

### Task 6: 优化 Settings、底部导航栏与通用 Modals

**Files:**
- Modify: `src/screens/SettingsScreen.tsx`
- Modify: `src/navigation/BottomTabBar.tsx`
- Modify: `src/components/RecordMomentModal.tsx`
- Modify: `src/components/SessionNoteModal.tsx`

- [ ] **Step 6.1**: 导航栏与设置页统一切换至 Radix Colors、0.5px 边框与 12px/15px/20px 字号。
- [ ] **Step 6.2**: 弹出层弹窗（RecordMomentModal / SessionNoteModal）确保外圆角与内边距遵循同心圆角。
- [ ] **Step 6.3**: 移除所有残留的口语冗余文案。

---

### Task 7: 全流程测试验证、打包与真机部署

**Files:**
- Test: `test-e2e/**`
- Artifact: `app/build/outputs/apk/debug/app-debug.apk`

- [ ] **Step 7.1**: 执行 `npm run typecheck`。
- [ ] **Step 7.2**: 执行 `npm test`，确保 237 个测试 100% PASS。
- [ ] **Step 7.3**: 执行 `npm run bundle:android` 生成脱机离线 bundle。
- [ ] **Step 7.4**: 执行 `.\gradlew.bat assembleDebug` 构建 APK。
- [ ] **Step 7.5**: 单工具调用推送安装至真机并截屏验证各主屏幕效果。
- [ ] **Step 7.6**: Git 提交并推送至 `main` 分支。
