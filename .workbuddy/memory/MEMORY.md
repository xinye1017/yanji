# 研迹（Yanji） · 项目长期记忆

> 只记录跨会话仍有效的事实与约定。日常改动写 `YYYY-MM-DD.md`。
> **品牌名是「研迹」**（`app_name`/DESIGN.md/UI 文案一致）；「卷卷 / Juanjuan」是产品角色名，不是品牌错误。

## 仓库与 Git

- GitHub `xinye1017/yanji`（私密），`main`。身份 Xinye / 1436143769@qq.com，代理 7898。
- 凭据助手：`credential.https://github.com.helper = !gh.exe auth git-credential`（缺它 push 报 `could not read Username`）。
- **Windows 提交丢 `gradlew` 可执行位** → CI `Permission denied`。修：`git update-index --chmod=+x gradlew`。
- `app/schemas/**` 是迁移测试依据，**必须提交**；改 Entity 忘 bump 版本会覆盖同版本 schema JSON。
- `gradle.properties` 的 `-Dfile.encoding=GBK` 是中文 Windows 路径 workaround，CI 用 `sed` 换 UTF-8，别改。

## 本机构建（Windows 特有坑）

- 单 module：Kotlin + Compose + Room，`minSdk 24 / target 36`，Java 17，包名 `com.example.yanji`。
- Gradle Wrapper **9.1.0**、AGP 9.0.1、Kotlin 2.3.20、Room 2.8.4、activity-compose 1.13.0。
  `技术栈.md` 写的 8.x 是旧信息。SDK 在 `C:\Users\dex\Android\Sdk`。
- **必须用 `./gradlew.bat`**（Git Bash 下 `./gradlew` 报 `ClassNotFoundException: GradleWrapperMain`）。
- Gradle 输出含 NUL 字节 → 管道给 grep 报 `Binary file matches`，先 `tr -d '\000'`。
- Kotlin daemon 偶发崩溃（`e: Daemon compilation failed`），Gradle 自动 fallback 且 BUILD SUCCESSFUL。
  看到 `e:` 先确认末尾有没有 BUILD SUCCESSFUL。多任务并发会加剧。
- 变量名**禁用 `TMP`/`TEMP`**（覆盖 Windows 临时目录，adb server 起不来）。
- 模拟器镜像被删残（缺 `system.img`/`vendor.img`）时 `sdkmanager --install` 不重下；
  必须把该目录移出 **SDK 根**再重下。AVD `DiaryPhone` = API 35 / x86_64 / google_apis，WHPX 可用。
- `git worktree remove` 会因 build 目录长路径失败（`Filename too long`），残留目录需
  Windows 原生递归删除（`Remove-Item -Recurse -Force`）。

## UI Design Token

- 唯一来源 `app/src/main/java/com/example/yanji/theme/`（`Color/Radius/Spacing/Type/Theme.kt`），
  **不是** `ui/theme/`（不存在）。
- **`ui/` 下禁止 `Color(0x...)` 字面量**，也必须用 `MaterialTheme.colorScheme.*` / `YanjiColors.*`
  而非静态亮色 token（`YanjiPrimary`/`YanjiBackground` 等是编译期常量，暗色下永远是亮色值）。
  ⚠️ 门禁 `scripts/check-design-tokens.sh` 只扫 `ui/` 目录，**根包 `com/example/yanji/*.kt` 是盲区**。
- `#5C4BC3` = `YanjiLavenderDeep`（Chat 深度解析前景专用），别与 `YanjiLavender` 混用。
- 标准卡片圆角固定 24dp（`YanjiRadius.StandardCardRadius`）；裸 dp 只允许出现在 `theme/Theme.kt` 的 `YanjiShapes`。
- 页面主标题统一 `YanjiPageHeader(title, subtitle?, trailing?)`；品牌 TopBar（`ChatTopBar`）不复用。

## 数据层（重要）

- **单一事实来源是 Room**；内存 MutableStateFlow 只是同步读缓存，由 DAO Flow 回灌，别做双写。
- **绝不写假数据**。「表为空」是合法状态。只有 `user_settings` 默认值与首页快捷操作默认三项可初始化。
- **绝不用 `fallbackToDestructiveMigration()`**。
- 迁移覆写 `migrate(connection: SQLiteConnection)`（不要 `SupportSQLiteDatabase` 版）；
  **禁用 `ALTER TABLE ... DROP COLUMN`**（minSdk 24 真机 SQLite < 3.35）→ 建新表/INSERT SELECT/DROP/RENAME。
- Room TableInfo 允许「entity 无 DEFAULT、DB 有 DEFAULT」，加列后不必补 `@ColumnInfo(defaultValue)`。
- 当前 version = **12**（`MIGRATION_11_12`，`app/schemas/…/12.json` 已提交）。YanjiMigrationTest 起点 DDL 必须与真实结构一致。

## 凭据与隐私

- AI API Key 不进 Room / SharedPreferences；在 `noBackupFilesDir` 用 Keystore AES-GCM 保存。
- 备份只含 `database` 域；明文 HTTP 全局关闭，仅放行 `127.0.0.1 / localhost / 10.0.2.2`。

## 计时与专注展示层

- 计时事实来自单调时钟（`SystemClock.elapsedRealtime` 差）；「计时结束→落库」由 `ActiveSessionCoordinator` 负责。
- 语义：倒计时归零/主动结束 → COMPLETED 落库；放弃 → CANCELLED 不落库；< 60 秒不记录。
- 统一状态源 `data/timer/FocusLiveState.kt`（sealed + `focusLiveStateOf()`）。
- `FocusTimerService` 两条流同源：`liveState`（语义，仅状态变更发射）与 `elapsedSecondsForUi`（每秒镜像，**只给 Compose**）。
  运行中计时交系统 Chronometer，**禁止每秒 notify()**。
- 通知 Action 字符串唯一定义在 `liveactivity/FocusTimerActions.kt`；文案在 `FocusNotificationSpec(s)`（JVM 可测）。
- Service 里不能用字段初始化取 `applicationContext` → `lateinit` + `onCreate`。
- `TimerState`/`timerState` 是遗留镜像，只剩 `ExamScreen` 在用。

## 系统栏 / 主题（含 2026-09-15 修复）

- 主题模式 `YanjiThemeMode`（SYSTEM/LIGHT/DARK）持久化在 `user_settings.themeMode`（存 name 字符串，
  `fromStorage` 宽松解析、未知值回落 SYSTEM）。`YanjiTheme(darkTheme=…)` 收 Boolean，由 `mode.resolveDarkTheme()` 解析。
- 设计规范：亮色 `DESIGN.md`，暗色仓库根 `design_dark.md`（Midnight Blue）。
  暗色色值：底 `#0D111A` / 卡 `#151B28` / 控件 `#1D2536` / 浮层 `#222C40`；主蓝 `#4F7DF3`、强调 `#7197F7`；
  文字 `#F0F4FC`/`#94A3B8`/`#64748B`；**禁止纯黑 `#000000`**。
- 暗色专属：底栏玻璃面板 `YanjiDarkDockPanel`、专注环自发光渐变、学科序列色 `yanjiSeriesToken()` 升调。
  **§3.5 的 AI 紫雾卡未做**（属亮暗共用新设计）。
- **系统栏图标颜色必须由 App 主题驱动**（`ui/SystemBarAppearance.kt` 的 `SystemBarAppearance(darkTheme)`），
  **不能**依赖 `enableEdgeToEdge()` 无参默认值——它按系统 `uiMode` 判定，App 主题与系统不一致时必然有一边看不清。
- **根容器背景不得硬编码 `YanjiBackground`**：状态栏/导航栏区域靠根容器铺底，硬编码会让两种主题下
  都呈近白色 `#F7F9FC`。用 `MaterialTheme.colorScheme.background`。
- 启动窗口主题 `Theme.Yanji` 需 `values/` + `values-night/` 两套（`windowBackground` + `windowLightStatusBar`），
  保证启动瞬间图标与底色对比足够。`FocusSystemBarAppearance`（专注页）刻意强制深色图标 + 亮色画布。

## Android 16 Live Update / ColorOS（真机实测）

- OPPO PKB110 / ColorOS V16.1.0 / API 36：`canPostPromotedNotifications() == false`，走「普通常驻通知 + Chronometer」。
- `dumpsys` 的 AppSettings `promoted=true` ≠ `canPostPromotedNotifications()`，只信运行时 API。
- `POST_PROMOTED_NOTIFICATIONS` / `Settings.ACTION_MANAGE_APP_PROMOTED_NOTIFICATIONS` 在 API 36 android.jar 中不存在 →
  权限用字面量声明，弹窗走 `ACTION_APP_NOTIFICATION_SETTINGS`。
- ColorOS 流体云是自有 livealert/pantanal + 包名 denylist（`livealert_disable_seedling`、`fluid_cloud_not_support`、
  `oplus_no_fluid_capsule_animation`），**不要**用 Hidden API/Reflection。OPPO 原生流体云需企业认证+serviceId+UPK。
- 诊断可复现：`LiveActivityCapabilityInstrumentedTest` → 报告 `externalCacheDir/live-activity-capability.txt`。

## 测试约定

- `:app:testDebugUnitTest` 必须全绿；新增迁移补进 `YanjiMigrationTest`。
- 单测类路径需 Room jvm 变体（见 `app/build.gradle.kts` 的 `UnitTest` configurations 替换块，别删）。
- 本地 JVM 单测解析 JSON 必须 `testImplementation(libs.org.json)`（Android 的 org.json 是 stub）。
- **UI 测试定位一律走 testTag，不断言中文整句文案**（文案迭代会打红测试）。已导出锚点：
  `ComposerBar.kt` → `ChatInputTestTag`/`ChatSendButtonTestTag`；`UserMessageBubble.kt`/`JuanjuanMessageBubble.kt` → 同名 TestTag；
  `JournalEditorScreen.kt` → `object JournalEditorTags`（ContentInput/BlockersInput/SaveButton/PlanInput/PlanAddButton/`moodOption(score)`）。
- **一个 test method 只能调一次 `setContent`**（二次调用抛 `IllegalStateException: Activity has already called setContent`）。
- `JournalViewModel.saveJournal` 是 `open`，留给插桩测试注入不落库子类（默认实现写用户真库）。
- 插桩必须用一次性数据库，**绝不能打用户真库**。`LiveActivityCapabilityInstrumentedTest` 用 `assumeTrue/assumeFalse`
  表达条件不变量（低版本会跳过而非假绿）。
- 判定「测试失败是否自己引入」：`git worktree add <tmp> HEAD` 拉干净基线独立跑，比对总数与通过情况。
- 本机验证走 API 35 模拟器；CI 走 API 34（`android-ci.yml`）。目标架构（进行中）：Compose UI → Feature ViewModel → Repository → Room/Service。

## 并发改造风险（2026-09-14 实测踩中）

- 曾在同一仓库同时跑两个改造任务，另一进程每 7~15 秒改写一个文件，导致工作区反复不可编译。
- **更严重**：该进程会**重置工作树**。一次跨 42 文件、700 处的主题化 sweep 在 15 分钟后被整体回滚
  （`HEAD` 未动，但 `git status` 里的改动全没了）。
- ⇒ **跨几十个文件的大批量改动前，先确认工作区归自己独占**；验证优先在 `git worktree add` 隔离副本里做；
  **改动一旦成形就尽早 commit 固化**。

## adb 运维

- **真机推送 / UI 定位 / 截图验证的完整流程在仓库根 `AGENT.md` 第六节**（设备掉线等待重试、
  `scripts/dump_ui.py` 解析 uiautomator 单行 XML 输出中心坐标、`cmd uimode night` 做主题交叉验证）。
  真机 `PKB110` 逻辑分辨率是 **1256×2760**。
- 脚本开头 `set -euo pipefail`；写设备库前先断言本地副本 `user_version` 与关键表存在。**先快照后动手**。
- 拉库要连 `-wal`/`-shm` 一起；本地 sqlite3 打开会回放 WAL，改完 `PRAGMA wal_checkpoint(TRUNCATE)`，写回删设备 `-wal`/`-shm`。
- 可复用脚本 `scripts/adb-backup.sh`、`scripts/adb-push.sh`；一次性脚本在 `build/` 下不入库。
