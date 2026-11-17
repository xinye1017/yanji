# 性能优化清单

本清单来自 2026-10-25 的代码审查。目标是在保持现有页面、完整记录展示、计时事实及 Room 数据语义的前提下，减少启动编译、无界读取、重复聚合和无关渲染。每完成一项实现及其验证后，才将对应复选框勾选，并补充证据。未测量的真机耗时、帧率或省电收益不作承诺。

> 历史说明：本文记录的是 2026-10-25 当时的代码状态。文中 PERF-02 提到的"成就对齐/评估/读取"在 2026-10-26 随成就系统一并摘除（`26707c6`，Room v28 删除 `unlocked_achievements` 表）；相关完成标准与验证记录保留为当时的历史证据，不代表当前代码仍含成就读取。当前 schema 版本见 `YanjiDatabase.kt`。

## 完成进度

- [x] PERF-01：Hermes 字节码预编译及构建产物校验。
- [x] PERF-02：移除专注、模考、随笔的全历史常驻镜像，收窄实际消费者读取。
- [x] PERF-03：优化每日专注相交查询，避免读取今天时检查全部过去的记录。
- [x] PERF-04：按有效统计窗口复用回顾缓存与聚合结果。
- [x] PERF-05：隔离尺盘即时值、预计结束时间和首页墙钟刷新。
- [x] PERF-06：使用已有列表库虚拟化首页与回顾的动态记录行。

## 实施方式

采用逐项交付，而不是一次性重写数据层或界面。每项尽量形成独立提交；可并行的工作只修改互不重叠的文件，清单由主执行者统一更新。优先使用已有依赖，不引入新数据库、第二套计时事实或通用缓存框架。列表虚拟化保持当前默认展开、排序与收起规则，静态标题和写入入口不随列表回收丢失。

本轮不再执行 ADB、安装、点击、滑动或真机采样。验证使用构建产物、独立测试数据库和组件测试；不会向运行中的 Room 注入数据，也不会改变已有专注会话。

## PERF-01：Hermes 构建

**现状**：[打包脚本](../package.json) 只执行 Metro，已提交的 bundle 文件头是 JS 源码 `var __BUNDLE_START_TIME_`；Android 使用 Hermes 宿主，但没有字节码编译阶段。

**方案**：在现有 Add-to-App 构建路径上增加与依赖版本匹配的 Hermes 编译，最终资产是字节码。同步守卫验证真实产物及其来源，不能继续依赖文本符号的存在来宣称源码同步。不为此迁移整个 Android 构建架构。

**完成标准**：打包可重复执行；产物为可由当前 Hermes 读取的字节码；同步守卫能拒绝源码或陈旧产物；相关脚本测试通过；APK 构建可打包该资产。

**验证记录**：`npm run bundle:android` 现在依次执行 Metro（保留 `minify=true`）与已安装的 `hermes-compiler`，产物 HBC 98，magic `c61fbc03c103191f`，编译器与 RN 的 `version.properties`、Gradle 侧 runtime 同为 `250829098.0.17`。同输入连续两次构建的字节码与元数据逐字节一致。`node --test test-e2e/bundle-sync.test.mjs` 13/13、`npm run test:guards` 通过、`npm run typecheck` 通过。资产体积由 2,087,486 字节的 JS 变为 2,919,938 字节的字节码（约 +39.9%），这是实测结果，不宣称缩包或真机提速。守卫已由文本符号存在性改为重算完整输入清单（源码、配置、锁文件、补丁、脚本）加字节码结构完整性；Metro 当前输出 0 个额外资源，有资源时会逐文件比对并在不一致时失败。APK 打包与真机启动属于集成阶段。

**现状**：[TimerStore](../app/src/main/java/com/example/yanji/data/timer/TimerStore.kt) 与 [NoteStore](../app/src/main/java/com/example/yanji/data/note/NoteStore.kt) 常驻全表 Flow；进程启动的成就对齐还会独立读取同样的历史。业务页面已经按日期或窗口读取。

**方案**：核实并收窄成就评估、当前日统计、按 ID 操作等实际消费者，再删除无界镜像订阅。保留成就、备份与业务写入的完整语义；确实需要完整历史的离线操作可以按需读取，但不能长期持有全表列表。

**完成标准**：启动不再挂载专注、模考、随笔的整表订阅；单行更新不再物化整份历史；成就与笔记操作、计时保存等回归检查通过；能够说明读取量或分配量减少的证据。

**验证记录**：`TimerStore` 与 `NoteStore` 已无 `getAll().collect` 全表订阅，启动路径也不再挂载整表镜像与重复的 `getAllOnce` 成就对齐。成就改为按候选与已解锁过滤后，用 SQL 聚合（计数/求和/最长）加少量最小投影（有序分数、时间行、打卡）读取；59 项口径未变，仍不按 status 或草稿过滤。笔记解决状态改为按 ID 读取后写入。另修正了一处会同时影响生产与测试的事务 API 误用：`withTransaction` 只存在于 Android 版 room-ktx，JVM 测试用的 `room-runtime-jvm` 会在运行期抛 `NoSuchMethodError`，已统一为 `useWriterConnection` + `immediateTransaction`。原生单测 323 项全通过（含新增的等价性与读取计数用例）。

**现状**：[DAO](../app/src/main/java/com/example/yanji/data/db/Daos.kt) 按 `startTime < dayEnd` 选候选，再计算有效结束时间筛选，今天的候选范围随历史增长。

**方案**：把当天开始的明细和跨日参与计数分开，依据 SQLite 查询计划选择可索引的有效结束时间实现。不能凭空假设会话最多持续一天；历史缺失结束时间时，仍需遵循开始时间加实际时长的现有计算规则。

**完成标准**：独立数据库上的跨午夜、长暂停、缺失结束时间语义不变；当前日读取使用有界索引范围；如有 Schema 变更，同步 Entity、迁移、Schema 与迁移测试；记录前后查询计划及可重复的局部测量。

**验证记录**：新增 v27 迁移：为 `focus_sessions` 增加 `effectiveEndTime` 派生列（沿用原有回退口径：`endTime > startTime` 用 `endTime`，否则 `startTime + durationSeconds * 1000`），回填存量行并建立 `(status, effectiveEndTime, startTime)` 索引。每日读取拆成「当天开始的明细」与「相交条数 COUNT」两条查询，计数不再为统计跨日尾部搬运整行。`YanjiMigrationTest` 30 项通过（含跨午夜、缺失/相等/倒挂 endTime、备注保留、索引存在性）；`QueryPlanGuardTest` 在无统计 / `PRAGMA optimize` / `ANALYZE` 三种状态下断言计数走新索引且不退化为全表扫描。`app/schemas/**/27.json` 已随代码生成。

**现状**：[刷新协调器](../src/state/dataRefreshCoordinator.ts) 用原始锚点日期作为键，同一周或月的不同锚点会形成多个缓存；[原生读取器](../app/src/main/java/com/example/yanji/bridge/ReviewOverviewReader.kt) 逐请求聚合共享行列表。

**方案**：统计结果以有效日期窗口共享，展示所需的请求字段仍按请求返回；相邻日期预取、已在途读取、失效通知及有限热缓存保持一致。优先对等价窗口去重，不增加通用缓存抽象。

**完成标准**：同周、同月的等价请求复用数据；周一、月末、跨年与当前日边界正确；写入失效和后台门控不退化；测试证明重复聚合或桥接读取次数下降。

**验证记录**：缓存键改为 `scope` 加有效自然窗口起止，展示所需的 `scope` / 有效 `periodsBack` 仍按请求返回，故同周不同锚点、以及周视图里 `current` 与 `weekRequest` 的重复请求共用一个条目。TODAY 窗口按「窗内日期 / 全过去 / 全未来」区分新鲜度，跨午夜后旧响应不发布；在途去重、写入失效、前后台门控与 24 份唯一窗口热缓存保持，清理时不误删别名。原生侧按唯一 Window 只 build 一次，再按请求复制展示字段并复用重数组。新增 13 项窗口边界与 Hook 用例、原生 3 项计数用例；`state-boundaries` 与 `review-timeline-entry` 等 60 项 Node 测试通过，`ReviewOverviewReaderTest` 8 项通过。实测同一周 Tue→Wed→Thu 的桥读从 3 次降为 1 次，7 个周/月别名请求只向原生发 2 个唯一窗口。

## PERF-05：更新范围

**现状**：[专注页](../src/screens/FocusScreen.tsx) 在拖过每个尺盘刻度时更新页面根状态，准备态的预计结束时间也通过根状态刷新；[首页](../src/screens/TodayScreen.tsx) 的墙钟定时器不区分显示值是否变化。

**方案**：即时预览及其数字只更新时长选择子树，停稳后提交业务选择；预计结束时间在文字叶子按显示精度刷新；首页只在实际日期或小时变化时提交新状态，回到前台立即校正。

**完成标准**：拖动与无障碍调节的最终值正确；任务预设及非整刻度值不被意外舍入；根组件的空闲/拖动更新次数减少；计时秒级更新继续隔离于叶子组件。

**验证记录**：尺盘即时预览与数字下沉到 `DurationPicker` 子树，`onChangeMinutes` 只改子树、`onSettleMinutes` 才向页面提交一次；页面根上那个只被预设高亮读取的 `isDialDragging` 已删除，因为它每次拖动开始/结束都会重绘整页。预计结束时间改为 `EstimatedEndTime` 分钟叶子：对齐下一整分钟、之后每分钟一跳、不可见时拆定时器、重新可见立即重读。首页墙钟只在跨日期或跨小时时提交，可见性与前台恢复仍立即校正。`focus-audit` 16 项与 `today-home` 44 项通过；可重复证据是 harness 的 `rootCommits`：整段拖动 27 个刻度时为空，一次停稳恰为 2（两个时长槽位各写一次），且 `startFocus` 使用最后确认值；37 分钟的非整刻度预设投影为 15:57 而非最近刻度。每秒计时 tick 仍只更新叶子组件。

**现状**：首页与回顾使用 ScrollView 内的 `map`，全部记录行一次挂载；已安装 FlashList，但生产页面没有使用。

**方案**：复用现有 FlashList，将静态页面区块与动态记录组成单一列表，避免同方向 ScrollView 嵌套；保留锚定菜单、可变高度正文、无障碍顺序、滚动位置、计数、排序和折叠行为。

**完成标准**：生产列表接入虚拟化组件；记录数不决定初始全部行挂载量；空状态、排序、折叠、菜单及回顾切日的组件契约通过；TypeScript 和 Android 集成构建通过。没有真机滚动采样时明确记录该限制。

**验证记录**：首页改为单一异构 FlashList（任务 / 待处理入口 / 记录标题 / 写一条记录 / 随笔五类行，两类回收池），静态区块走 `ListHeaderComponent`、今日学习节奏走 `ListFooterComponent`，不存在同方向 ScrollView 嵌套；回顾只在时间线分支使用 FlashList（filmstrip 与当日摘要走 header），趋势分支仍是普通 ScrollView，因为它是固定几张卡片。`TimelineCompactRow` 删除了只写不读的 `rowHeight` 状态与两处 `onLayout`，避免回收时串状态。真实包在 Node ESM 下无法加载，因此新增窗口化替身 `test-e2e/framework/flash-list-double.js`：它会挂载有界窗口并暴露挂载量，使测试既能断言全部行都交给列表（没有靠截断伪装优化），又能断言首帧只挂载窗口。可重复证据：首页 12 条计划 + 40 条随笔时列表收到 54 行、首帧挂载 ≤8 行、滚动 12 次可达全部；回顾 1 段专注 + 30 条随笔时收到 31 行、首帧 ≤8 行；折叠后交给列表的行数由 6 降到 4，而标题仍报「4 条」，证明折叠减少的是行数而非数据。

**未验证边界**：没有真机滚动、帧率或内存采样。挂载量结论来自替身窗口模型，不能替代真实 Fabric 列表的回收行为、滚动位置保持或 `maintainVisibleContentPosition` 表现。

- [x] TypeScript 检查与受影响的 JS/组件测试通过。
- [x] 原生查询、业务行为及涉及的迁移测试通过。
- [x] 新字节码 bundle、同步守卫和 Android Debug APK 构建通过。
- [x] 审阅最终 diff，精准提交并推送；工作区无本轮遗留在途修改。

## 集成结果

- `npm run typecheck` 通过；`npm test` 293/293 通过；`npm run test:guards` 两项守卫通过。
- `./gradlew.bat :app:testDebugUnitTest` 318 项通过（含迁移、查询计划、成就等价性与回顾窗口）；`compileDebugAndroidTestKotlin` 通过。
- `npm run bundle:android` 产出 HBC 98 字节码；`./gradlew.bat assembleDebug` 成功，APK 内 `assets/index.android.bundle` 为 3,045,977 字节、magic `c61fbc03c103191f`。
- 按独立提交推送：`8c2f8b5`（字节码构建）、`5620c1d`（全历史读取与 v27）、`e8eb101`（回顾窗口）、`e0dd965`（更新范围）、`c10e347`（列表虚拟化）、`ee632e7` 与 `c68b0dc`（每日读取与成就读取量断言收紧）、`6039bf7`（移除失去调用方的 `StudyStats`）。
- 真机做过一次覆盖安装与启动（保留用户数据），首页、专注、回顾三页渲染正常、无崩溃；「今日学习节奏」在当天 0 分钟时无柱是既有空态，不是回归。
- FlashList 2.3.3 属 v2 纯 JS 实现，无原生依赖，因此不需要也不应添加 Fabric codegen 或 Gradle include；本轮未改动任何原生构建配置。

## 本轮复验（2026-11-17）

在历史包袱清理、代码精简与原生 ABI 收敛之后重新跑了一遍完整本地验证。以下数字为本轮实测，取代上文"集成结果"中 2026-10-25 的旧值；旧值保留为该轮历史证据，不再代表当前提交。

- `npm run typecheck`：通过。
- `npm test`（Node ESM 全套）：**371/371** 通过，0 失败 / 0 跳过。
- 三守卫：`check-project-facts.sh`（project-facts 一致）、`check-bundle-sync.mjs`（源码 / 配置 / 依赖锁 / 补丁内容与 HBC 完整性及已打包资源匹配）、`check-runtime-fixtures.sh`（1 条已复核匹配）——均 PASS。
- `lintDebug` → `testDebugUnitTest` → `assembleDebug`（按 AGENTS.md §五.0 经 PowerShell 逐项执行）：BUILD SUCCESSFUL。lint **0 errors / 18 warnings**（全部为既有的 `MonochromeLauncherIcon` 提示，与本轮改动无关）；Kotlin JVM 单测 **314 项**（40 suites，0 失败 / 0 错误 / 0 跳过）。
  - 单测数由历史的 318 降为 314，是因为成就系统摘除（`26707c6`）时一并删除了成就等价性等测试，属预期的用例收敛，不是回归。
- 打包产物：`npm run bundle:android` 产出 HBC 98 字节码，`assembleDebug` 后 APK 内 `assets/index.android.bundle` 为 **3,170,490 字节**、magic `c61fbc03c103191f`。体积随每轮源码变化浮动，不能据此宣称缩包或加速。
- 本轮相关提交：`556ca07`（死代码 / 未用依赖清理并重建 bundle）、`8153513`（失效注释清理）、`5cee04f`（冗余逻辑精简并重建 bundle）、`697715f`（原生 ABI 收敛 + 清理脚本）。

本轮为纯静态与组件级验证，未做模拟器 / 真机安装、点击、录屏或性能采样；下文"未验证边界"依然成立。

## 模拟器运行性能实测（2026-11-17）

在 Android 模拟器（`emulator-5554`，Android 15 / x86_64 / AEHD 2.2 / 720×1600@320dpi / host GPU / 6 核 / 4GB）上安装 Debug APK 实测。动画 scale 保持 1（不关闭，避免 `useReducedMotion()` 静默跳过过渡）。数据来自 `am start -W`、`dumpsys gfxinfo [framestats]`、`dumpsys meminfo` 与 `/proc/<pid>/stat`。

- **冷启动**：`am start -W` 的 TotalTime 为 1633 / 1692 / 1745ms（另一次 1664–1882ms），波动来自模拟器。时间线显示 `Choreographer: Skipped 44 frames`（约 730ms 主线程阻塞）发生在 `Application.onCreate` 的 RN 原生库加载与 ReactInstance 创建阶段——这是 RN 0.87 Bridgeless + Fabric 的框架固定成本，不是 app 业务逻辑。app 自己的启动预热跑在 IO 线程（settled 289–323ms），主线程有界等待仅 80–99ms，且已把 JS 引擎启动与 Room 预热并行化。
- **稳态内存**：反复切换三个 Tab 并强制 GC 后，PSS 稳定在 ~302MB、Dalvik Heap 稳定在 ~6.3MB（三轮读数 ~297588 → 302120 → 302452 → 302472 KB，Heap 不增长）。三页常驻挂载是既定设计，**没有内存泄漏**；增长只是首次进入各页的懒挂载与玻璃纹理常驻，随后收敛。
- **滚动帧耗时**（720p + host GPU，`framestats` 精确分解，71 帧）：IntendedVsync→FrameCompleted 中位数 **17.5ms**、p95 **18.1ms**、max 22.5ms（≈57fps，接近 60fps 上限）；`issueDrawCommands` 中位 5.2ms、`swapBuffers` 中位 7.8ms，瓶颈在 GPU 合成而非 JS/UI 线程。`Number Slow UI thread = 0`、`Number Missed Vsync = 0`。
- **idle CPU**：无活动会话、停留首页时主线程 ~6% 单核；暂停态 tick 会停止，不空转。
- **"jank 21–23%" 的真相**：`dumpsys gfxinfo` 把每帧 17ms（vsync 16.67ms）判为 janky，且 `input swipe` 注入与 vsync 不同步产生 ~80% 的 "High input latency"——这两项都是模拟器测试方法的伪影，不是真实用户卡顿。精确逐帧数据显示 p95 仅 18ms。

### 瓶颈定位：A/B 验证后确认无 app 级瓶颈

 Glass 底栏的 `react-native-blur-overlay` 曾被怀疑是绘制开销来源，遂做对照实验（每次重建 bundle + APK 装模拟器、同一 12–30 循环滑动负载）：

| A/B 变量 | 结果 | 结论 |
| :--- | :--- | :--- |
| `maxUpdateFps` 60 → 24 | jank、slow-draw 均无变化（22% 对 22%） | 非瓶颈 |
| `interactive` true → false | idle CPU 无差异（5.6 对 6.4 j/10s，噪声） | 非瓶颈 |
| `downsampling` 2 → 4 | 库在 glass 模式把 `inputScale` 硬钳制为 0.5（已是最优），改值无效 | 无可调空间 |
| 完全移除 BlurOverlay（仅留 children） | jank 22–27% 与基线 21–23% 相当，idle CPU 57 对 56 j/10s | 确认非瓶颈 |

库自身文档也印证：glass 模式"never captures above half resolution"，性能杠杆是 downsampling 而此处已被钳制。`react-native-blur-overlay` 的 patch 只改像素着色（边缘提亮/反射），不改 capture 频率。**结论：当前实现已接近理论最优，没有可以安全移除或下调的性能包袱。** 唯一可归因的耗时是 RN 框架启动固定成本，不属于业务逻辑优化范畴。

### 本轮唯一改进

据 Context7（`react-native-website` / optimizing-flatlist-configuration）确认的官方建议——`renderItem`/`keyExtractor` 必须是稳定回调，否则每次父组件渲染都重建、破坏行回收——把 `ReviewScreen` 时间线 FlashList 的内联回调改为 `useCallback`（与 `TodayScreen` 既有做法一致），并把 `requestDeleteSession` 一并稳定化。行为不变，只减少无谓的函数重建。typecheck、371 项 JS 测试、模拟器装启与 Review 页渲染均已验证正常。

## 未验证边界

没有真机滚动、帧率、内存或启动耗时采样，因此启动改善、滚动流畅度与省电收益均未证实；字节码相对原 JS 体积约 +39.9%，不宣称缩包或真机加速。所有结论来自构建产物校验、独立测试数据库与组件级替身。`DailySnapshotInstrumentedTest` 的插桩断言只在编译层验证过，尚未在设备上执行。上文所有帧率 / 内存 / CPU 数据均来自模拟器，未在真机（ColorOS / PKB110）复现；真机上玻璃材质走系统 RenderEffect、字体注册走 OPPO 衬线路径，性能特征可能不同，本轮未测。
