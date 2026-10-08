# Original User Request

## 2026-10-08T11:50:53Z

将考研成长记录应用「研迹」重构为 React Native + TypeScript + NativeWind 极简前端架构，保留底层 Kotlin 原生业务核心（Room 数据库、物理单调时钟计时引擎、系统通知），构建「今天、专注、回顾」三大核心维度的安静、克制、高质量成长记录体验。

Working directory: d:/AI项目/yanji
Integrity mode: demo

## Requirements

### R1. React Native 渐进式集成与原生桥接 (Add-to-App)
在现有 Android 原生工程中渐进式接入 React Native + TypeScript + NativeWind，由 Android 容器（如宿主 Activity / ReactRootView）托管主视图。通过强类型 TypeScript 接口及原生模块（Native Modules）桥接既有 Kotlin 业务层（Room 数据库、ActiveSessionCoordinator、FocusTimerService 等），严禁在 JS 层实现第二套计时权威或直接绕过 Repository 操作数据库底层。

### R2. 极简三级信息架构与页面交互
重构全局导航为严格的三级底部导航结构：
1. **今天 (Today)**：提供当日学习状态概览（日期、轻量考研倒计时、今日专注总长）、基于真实数据自适应的单一主行动按钮（继续专注/继续学习/开始专注），以及轻量今日任务列表与「记录此刻」快捷入口。
2. **专注 (Focus)**：以大号计时数字为视觉中心的沉浸式计时界面，支持正计时/倒计时记忆切换，无缝对接 Kotlin 前台物理计时服务；支持在计时进行中无感弹出「记录此刻」弹层输入想法并不中断计时。
3. **回顾 (Review)**：以确定性真实历史为核心的成长时间线，按天聚合专注历程、任务完成状态与随笔记录，辅以近 7 天趋势与科目分布统计；为未来 AI 能力预留优雅扩展槽位，本阶段不开发 AI 对话、人格与记忆。

### R3. 「记录此刻」跨页面即时记录组件
实现跨页面共享的轻量快速记录弹层：仅需文本输入即可保存至本地，自动关联当前系统时间与正在进行的专注会话（若有），保存后瞬时反馈并关闭，软键盘弹出/收起无布局跳动，无网络与 AI 依赖。

### R4. 克制设计系统与工程安全规范
落地统一 Design Tokens 与 NativeWind 样式系统：
- 严格遵循克制配色体系（浅色为柔和蓝灰背景，深色为 Midnight Blue 体系，严禁 `#000000` 纯黑背景）。
- 卡片容器零描边（深浅色均无 BorderStroke，仅依靠表面明度递进与排版建立层级）。
- 确保系统状态栏、手势导航、深浅色模式与返回键无缝适配。
- 绝不改动用户手机系统级深浅色设置，严禁在正式界面注入虚假 Mock/演示数据。

## Acceptance Criteria

### 编译与架构完整性
- [ ] TypeScript 类型检查执行（`npm run typecheck` 或 `npx tsc --noEmit`）无任何类型错误。
- [ ] 原生 Android 构建成功（`./gradlew.bat assembleDebug`）生成有效的 Debug APK 产物。
- [ ] 原生单元测试与 Room 迁移守卫（`./gradlew.bat :app:testDebugUnitTest`）保持通过，未发生破坏性 Schema 变更。

### 核心功能与体验契约
- [ ] 底部主导航严格只有「今天、专注、回顾」三个目的地，系统设置通过「今天」右上角入口进入，无成就勋章、打卡墙或吉祥物等干扰元素。
- [ ] 权威计时物理事实完整保留在 Kotlin 原生层（`SystemClock.elapsedRealtime` + `FocusTimerService`），在退后台、锁屏、通知栏操作及界面重建时不发生时间跳变或丢失。
- [ ] 任务与专注记录来自本地真实持久化数据，支持首次使用（无任务）快速开始、历史配置恢复与正/倒计时无缝切换。
- [ ] 「记录此刻」能在专注计时中顺畅唤起，输入文本保存后直接落库并回显，计时器无卡顿且不暂停。
- [ ] 回顾页面能按天准确展示历史专注记录、完成任务与记录条目，近 7 天统计数据与 Room 本地记录完全一致。
- [ ] AI 对话与生成功能严格排除在本轮之外，未引入不可点击的假 UI 占位或虚假生成数据。
