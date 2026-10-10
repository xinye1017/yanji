# 研迹（Yanji）· 项目长期记忆

> 仅存仍然适用于当前运行代码的长期事实；每日旧开发流水账不自动加载。最近一次源码核对：2026-10-10，Codex 本地工作区 `main@38f5e79`（静态审查，未执行测试）。详细审查见 [2026-10-10.md](./2026-10-10.md)。任何版本号、功能是否接入的判断，以当前源码为准。

## 当前架构和主界面

- **当前 UI = React Native 0.87.1 + React 19.2.3 + TypeScript**，运行在 Android Add-to-App 宿主；Kotlin + Room 仍负责所有真实业务。**旧 Jetpack Compose 页面不是现在的主 UI**，不可据旧笔记对 `app/src/main/java/.../ui/**` 开发后宣称修改了当前 UI。
- RN 入口：`App.tsx`、`src/screens/**`、`src/navigation/**`、`src/components/**`。主导航仅「今天 / 专注 / 回顾」三 Tab，设置从「今天」打开。三屏常驻挂载，切页保留本地编辑状态。
- RN 主题：`src/theme/tokens.ts`、`src/theme/ThemeProvider.tsx`；悬浮玻璃底栏：`src/navigation/BottomTabBar.tsx`、`src/theme/liquidGlass.ts`。旧 Compose 的 `YanjiPageHeader`、`TopFadeScrim`、`hazeEffect` 等不是 RN 设计约束。
- 宿主：`YanjiApplication.kt` / `MainActivity.kt`（Hermes、新架构、手工链接原生包）。新原生 UI 依赖需检查 Gradle、注册与 JNI/codegen，不能只 npm install。
- Kotlin ↔ JS 合同：`app/src/main/java/com/example/yanji/bridge/{YanjiTimerModule,YanjiDataModule,YanjiThemeModule}.kt` 与 `src/bridge/index.ts`，更改接口必须两侧及合同测试同步。

## 数据、计时、安全：硬约束

- **Room 为业务唯一事实源**，数据由 Kotlin Repository 管理；运行时禁止虚构学习记录，空表合法。Room 当前 `@Database(version = 22)`，以 `data/db/YanjiDatabase.kt` 为准；迁移须完整、非破坏性，禁止 `fallbackToDestructiveMigration()`，Entity 修改同时更新 `app/schemas/**`。
- 计时唯一事实源：Kotlin `TimerMachine` + `ActiveSessionCoordinator` + `FocusTimerService`，物理时间基于 `SystemClock.elapsedRealtime`；RN 只消费原生事件，不独立计算权威计时。前台通知依托 Chronometer，勿每秒 `notify()`；保留进程死亡后的活动快照恢复。
- AI API Key 用 Android Keystore 加密并放入 `noBackupFilesDir`，异常 fail-closed；不得写入 Room、JS 持久化、备份、日志或 Git。
- **历史持久化兼容值不可随 Kotlin 符号一起重命名**（当前代码已确认）：Room `journal_entries`；`ChatSender.JUANJUAN` 对应历史 sender；成就 ID `journey_journal_first`；`QuickStartPreset.TYPE_JOURNAL = "journal"`；备份 JSON 字段 `journalEntries`（通过 `@SerialName` 固定）。「卷卷 / Juanjuan」是角色名，「研迹」是产品名。变更前须审查迁移和旧数据兼容。

## 开发与验证

- 权威工程红线：`AGENTS.md`；事实取值优先实际代码、构建配置和测试合同，不依赖本记忆里固定的工具链版本。
- **RN 改动交付顺序**：`npm run typecheck`、相关 `npm test` → `npm run bundle:android` → Android `assembleDebug`；`Gradle` 不保证 JS bundle 自动刷新。Windows 运行 `./gradlew.bat`。
- 质量入口：`test-e2e/`（JS 合同/逻辑和工作流测试）、`app/src/test/**`（Kotlin JVM）、`.github/workflows/android-quality.yml`（原生门禁/插桩）。**旧日期中“全绿”的记录不代表当前提交已测试**；应区分执行成功与缓存复用。
- 保护用户数据和工作树、避免破坏性 Git 操作；改动前检查 `git status`；真机授权遵循最新版 `AGENTS.md` 与当次用户指示。提交后及时 push，其他 Agent 的工作区需再同步 Git。

## 2026-10-10 待检查（只代表静态疑点，不代表已复现）

- `README.md` 顶部 `project-facts` 声明 Room v21，当前代码是 v22，可能使 `scripts/check-project-facts.sh` 拦截。
- `TodayScreen` / `FocusScreen` 将当天日期初始化一次，三页常驻时跨午夜日期可能陈旧。
- RN 多处 `catch {}` 缺乏面向用户的失败反馈；CI 是否显式执行 RN typecheck/test/bundle 需要确认。

> 记忆维护：重大架构变更后更新本文件；不要将旧 UI 操作记录、历史测试数字、临时设备状态持续累加为长期记忆。
