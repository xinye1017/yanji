# 研迹（Yanji） · 项目长期记忆

> 只记录跨会话仍有效的事实与约定；日常改动写 `YYYY-MM-DD.md`。
> **品牌名是「研迹」**；「卷卷 / Juanjuan」是产品角色名，不是品牌错误。

## 仓库与 Git

- GitHub `xinye1017/yanji`（**public**）、分支 `main`。身份 Xinye / 1436143769@qq.com，代理 **7898**。
- 凭据助手：`credential.https://github.com.helper = !gh.exe auth git-credential`（缺它 push 报 `could not read Username`）。
- Windows 提交丢 `gradlew` 可执行位 → CI `Permission denied`。修：`git update-index --chmod=+x gradlew`。
- `app/schemas/**` 是迁移测试依据，**必须提交**；改 Entity 忘 bump 版本会覆盖同版本 schema JSON。
- `gradle.properties` 的 `-Dfile.encoding=GBK` 是中文 Windows 路径 workaround，CI 用 `sed` 换 UTF-8，别改。

## 本机构建（Windows 特有坑）

- 单 module：Kotlin + Compose + Room，`minSdk 24 / target 36`，Java 17，包名 `com.example.yanji`。
- Gradle Wrapper **9.1.0**、AGP 9.0.1、Kotlin 2.3.20、Room 2.8.4。`技术栈.md` 的 8.x 是旧信息。
  SDK `C:\Users\dex\Android\Sdk`。**必须用 `./gradlew.bat`**（Git Bash 下 `./gradlew` 报 `ClassNotFoundException: GradleWrapperMain`）。
- Gradle 输出含 NUL 字节 → 管道给 grep 报 `Binary file matches`，先 `tr -d '\000'`。
- Kotlin daemon 偶发 `e: Daemon compilation failed`，Gradle 自动 fallback 且 BUILD SUCCESSFUL；**先看末尾结果再判失败**。多任务并发会加剧。
- 变量名**禁用 `TMP`/`TEMP`**（覆盖 Windows 临时目录，adb server 起不来）。
- 模拟器镜像残缺（缺 `system.img`/`vendor.img`）时 `sdkmanager --install` **不重下**，须把该目录移出 **SDK 根**再重下。
  AVD `DiaryPhone` = API 35 / x86_64 / google_apis，WHPX 可用。
- `git worktree remove` 因 build 长路径失败（`Filename too long`），残留目录需 `Remove-Item -Recurse -Force`。

## 命名约定（2026-09-21 全量改名）

- 随笔模块 `journal*` → `note*`（文件 + 类 + 函数 + testTag）；AI 伴学模块 `Juanjuan*` → `Ai*`。
  目录：`data/note/NoteStore.kt`、`ui/note/*`、`ui/chat/AiChatScreen.kt`、`ui/chat/AiResponseParser.kt`、
  `ui/chat/components/AiMessageBubble.kt`、`ui/components/AiMascot.kt`、`data/AiPrompt.kt`。
- ❗**冻结不改的旧名字**（全部是持久化值 / 线上格式，改名即静默丢数据）：
  - Room 表 `journal_entries`（含 DAO SQL、迁移 DDL、索引名 `index_journal_entries_date`）
  - `ChatSender.JUANJUAN`（持久化为 `chat_messages.sender` 字符串）
  - 成就 ID `journey_journal_first`（存于 `unlocked_achievements`）
  - LLM 协议 token `SAVE_TO_JOURNAL` / `OPEN_JOURNAL`（旧消息文本里已存在，`valueOf` 解析）
  - `QuickStartPreset.TYPE_JOURNAL = "journal"`（`quick_start_presets.type` 的持久化值）
  - 备份 JSON 键 `journalEntries` —— Kotlin 属性已改名 `noteEntries`，用 `@SerialName("journalEntries")` 钉住，
    `BackupCodecTest` 里有旧键 fixture 守着这条格式
  - 角色名「卷卷」与美术资源 `juanjuan.png` / `R.drawable.juanjuan`（5 个吉祥物命名对称，不是模块名）
- 改名时 `scripts/check-runtime-fixtures.sh` 的实体白名单同步改成了 `NoteEntry`，否则零假数据守卫出现盲区。

## UI Design Token

- 唯一来源 `app/src/main/java/com/example/yanji/theme/`（`Color/Radius/Spacing/Type/Theme.kt`），**不是** `ui/theme/`。
- **`ui/` 下禁止 `Color(0x...)` 字面量**，用 `MaterialTheme.colorScheme.*` / `YanjiColors.*`（静态亮色 token 在暗色下仍是亮色值）。
  门禁 `scripts/check-design-tokens.sh` 只扫 `ui/`，**根包 `com/example/yanji/*.kt` 是盲区**。
- `#5C4BC3` = `YanjiLavenderDeep`（Chat 深度解析前景专用），别与 `YanjiLavender` 混用。
- 标准卡片圆角固定 24dp（`YanjiRadius.StandardCardRadius`）；裸 dp 只允许出现在 `theme/Theme.kt` 的 `YanjiShapes`。
- 页面主标题统一 `YanjiPageHeader(title, subtitle?, trailing?)`；品牌 TopBar（`ChatTopBar`）不复用。

## 毛玻璃 haze（易致「启动即崩」，务必遵守）

- 依赖 `dev.chrisbanes.haze` **1.5.4**。
- ❗**每个 `Modifier.hazeEffect { }` 块必须显式设 `backgroundColor`**：该版本 RenderEffect 路径硬性 `require(bg.isSpecified)`，
  三级解析全空即抛 `IllegalArgumentException("backgroundColor not specified...")`。属**启动期绘制崩溃，单测测不出，只真机暴露**。
- 当前唯一调用点 `ui/components/GlassSurface.kt`（`backgroundColor = Color.Transparent` 垫底，着色由 `tints` 承担）；
  `Navigation.kt` 只用 `hazeSource`，`GlassBottomBar.kt` 复用 `GlassSurface`。
- 断言点：真机启动后 `logcat -b crash` 为空。

## 数据层（重要）

- **单一事实来源是 Room**；内存 MutableStateFlow 只是同步读缓存，由 DAO Flow 回灌，**别做双写**。
- **绝不写假数据**。「表为空」是合法状态。只有 `user_settings` 默认值与首页快捷操作默认三项可初始化。
- **绝不用 `fallbackToDestructiveMigration()`**。
- 迁移覆写 `migrate(connection: SQLiteConnection)`（不要 `SupportSQLiteDatabase` 版）；
  **禁用 `ALTER TABLE ... DROP COLUMN`**（minSdk 24 真机 SQLite < 3.35）→ 建新表 / INSERT SELECT / DROP / RENAME。
- Room TableInfo 允许「entity 无 DEFAULT、DB 有 DEFAULT」，加列后不必补 `@ColumnInfo(defaultValue)`。
- 当前 version = **15**（最新迁移 `MIGRATION_14_15`，`schemas/…/15.json` 已提交）。**别信记忆，去 `YanjiDatabase.kt` 的 `@Database(version=…)` 核**；`YanjiMigrationTest` 起点 DDL 必须与真实结构一致。

## 凭据与隐私

- AI API Key 不进 Room / SharedPreferences；在 `noBackupFilesDir` 用 Keystore AES-GCM 保存。Keystore 异常时**拒绝降级为明文**（Fail-Closed）。
- 备份只含 `database` 域；明文 HTTP 全局关闭，仅放行 `127.0.0.1 / localhost / 10.0.2.2`。

## 计时与专注

- 计时事实来自**单调时钟**（`SystemClock.elapsedRealtime` 差）；「计时结束→落库」由 `ActiveSessionCoordinator` 负责。
- 语义：倒计时归零 / 主动结束 → COMPLETED 落库；放弃 → CANCELLED 不落库；< 60 秒不记录。
- 统一状态源 `data/timer/FocusLiveState.kt`（sealed + `focusLiveStateOf()`）。
- `FocusTimerService` 两条流同源：`liveState`（语义，仅状态变更发射）与 `elapsedSecondsForUi`（每秒镜像，**只给 Compose**）。
  运行中计时交系统 Chronometer，**严禁每秒 notify()**。
- 通知 Action 字符串唯一定义在 `liveactivity/FocusTimerActions.kt`；文案在 `FocusNotificationSpec(s)`（JVM 可测）。
- Service 里不能用字段初始化取 `applicationContext` → `lateinit` + `onCreate`。
- `TimerState`/`timerState` 是遗留镜像，只剩 `ExamScreen` 在用。

## 系统栏 / 主题

- 主题模式 `YanjiThemeMode`（SYSTEM/LIGHT/DARK）持久化在 `user_settings.themeMode`（存 name 字符串，`fromStorage` 宽松解析、未知值回落 SYSTEM）。
  `YanjiTheme(darkTheme=…)` 收 Boolean，由 `mode.resolveDarkTheme()` 解析。
- 亮色看 `DESIGN.md`，暗色看仓库根 `design_dark.md`（Midnight Blue）：底 `#0D111A` / 卡 `#151B28` / 控件 `#1D2536` / 浮层 `#222C40`；
  主蓝 `#4F7DF3`、强调 `#7197F7`；文字 `#F0F4FC`/`#94A3B8`/`#64748B`；**禁止纯黑 `#000000`**。
- 暗色专属：底栏玻璃面板 `YanjiDarkDockPanel`、专注环自发光渐变、学科序列色 `yanjiSeriesToken()` 升调。**§3.5 AI 紫雾卡未做**。
- 系统栏图标颜色必须由 App 主题驱动（`ui/SystemBarAppearance.kt` 的 `SystemBarAppearance(darkTheme)`），
  **不能**依赖 `enableEdgeToEdge()` 无参默认值（它按系统 `uiMode` 判定）。
- **根容器背景不得硬编码 `YanjiBackground`**，用 `MaterialTheme.colorScheme.background`。
- 启动窗口主题 `Theme.Yanji` 需 `values/` + `values-night/` 两套（`windowBackground` + `windowLightStatusBar`）。
  `FocusSystemBarAppearance`（专注页）刻意强制深色图标 + 亮色画布。

## Android 16 Live Update / ColorOS（真机实测）

- OPPO PKB110 / ColorOS V16.1.0 / API 36：`canPostPromotedNotifications() == false` → 走「普通常驻通知 + Chronometer」。
- `dumpsys` 的 `promoted=true` ≠ `canPostPromotedNotifications()`，**只信运行时 API**。
- `POST_PROMOTED_NOTIFICATIONS` / `Settings.ACTION_MANAGE_APP_PROMOTED_NOTIFICATIONS` 在 API 36 android.jar 中不存在 →
  权限用字面量声明，弹窗走 `ACTION_APP_NOTIFICATION_SETTINGS`。
- ColorOS 流体云是自有 livealert/pantanal + 包名 denylist（`livealert_disable_seedling`、`fluid_cloud_not_support`、
  `oplus_no_fluid_capsule_animation`），**不要**用 Hidden API / Reflection。OPPO 原生流体云需企业认证 + serviceId + UPK。
- 诊断可复现：`LiveActivityCapabilityInstrumentedTest` → 报告 `externalCacheDir/live-activity-capability.txt`。

## 测试约定

- `:app:testDebugUnitTest` 必须全绿；新增迁移补进 `YanjiMigrationTest`。
- 单测类路径需 Room jvm 变体（`app/build.gradle.kts` 的 `UnitTest` configurations 替换块，**别删**）。
- 本地 JVM 单测解析 JSON 必须 `testImplementation(libs.org.json)`（Android 的 org.json 是 stub）。
- **UI 测试定位一律走 testTag，不断言中文整句文案**。已导出锚点：
  `ComposerBar.kt` → `ChatInputTestTag`/`ChatSendButtonTestTag`；`UserMessageBubble.kt`/`AiMessageBubble.kt` → 同名 TestTag；
  `NoteEditorScreen.kt` → `object NoteEditorTags`（ContentInput/BlockersInput/SaveButton/PlanInput/PlanAddButton/`moodOption(score)`）。
- **一个 test method 只能调一次 `setContent`**。
- `NoteViewModel.saveNote` 是 `open`，留给插桩测试注入不落库子类。
- 插桩必须用一次性数据库，**绝不能打用户真库**；条件不变量用 `assumeTrue/assumeFalse` 表达。
- 判定「测试失败是否自己引入」：`git worktree add <tmp> HEAD` 拉干净基线独立跑，比对总数与通过情况。
- 本机验证走 API 35 模拟器；CI 走 API 34（`android-ci.yml`）。目标架构（进行中）：Compose UI → Feature ViewModel → Repository → Room/Service。

## 判定测试真伪（易自欺）

- ⚠️ **`> Task :app:testDebugUnitTest FROM-CACHE` = 测试根本没跑**，那次「10 秒通过」不是证据。
  要真跑：`--rerun-tasks --no-build-cache`；验收看 `N actionable tasks: N executed, 0 from cache`。
- **禁止时间依赖的测试写法**：`Calendar.getInstance()` 派生「同一时刻」时**必须 `set(Calendar.MILLISECOND, 0)`**，
  边界值只计算一次并复用。曾因此出过 0.0398% flaky（`StudyStatsTest.durationSince`）。
- 验证守卫有效性要**注入违规**再确认失败——恒真门禁等于没有。

## 并发改造风险（09-14、09-16 两次踩中）

- 同仓库并发跑两个会话：另一进程每 7~15 秒改写文件、**重置未提交编辑**，还会把 HEAD 切到它自己的分支
  （09-16 被切走两次、2 个 commit 落错分支、1 处未提交编辑被重置）。
- ⇒ **大批量改动前先确认工作区独占**；commit 后**立即 push**；commit 前先 `git branch --show-current`；
  落错分支用 cherry-pick 回来 + `git branch -f <对方分支> <原 tip>` 还原对方指针。

## adb 运维

- **完整流程在仓库根 `AGENT.md` 第六节**（设备掉线重试、`scripts/dump_ui.py` 解析 uiautomator XML 取中心坐标、`cmd uimode night` 交叉验证）。
  真机 `PKB110` 逻辑分辨率 **1256×2760**。设备串 / IP / 端口一律 mDNS 动态发现，不写死。
- 脚本开头 `set -euo pipefail`；写设备库前先断言本地副本 `user_version` 与关键表存在。**先快照后动手**。
- 拉库连 `-wal`/`-shm` 一起；本地 sqlite3 打开会回放 WAL，改完 `PRAGMA wal_checkpoint(TRUNCATE)`，写回删设备 `-wal`/`-shm`。
- 可复用脚本 `scripts/adb-backup.sh`、`scripts/adb-push.sh`；一次性脚本放 `build/` 下不入库。
- **验证「是否还崩」先 `adb logcat -c`**；崩溃归属比对 crash log 最早时间戳与上次安装先后。
- 日常推送 `adb -s <id> install -r …/app-debug.apk`（`-r` 保留数据）。**真机固定 debug 签名**（`~/.android/debug.keystore`，至 2056），
  换 release 签名必须卸载重装 → 数据全丢；**不要**给 debug 加 `applicationIdSuffix`。
- 覆盖安装安全取决于 `@Database(version)`：装前读真机库 `PRAGMA user_version` 与代码比对；不同时务必先在模拟器上用真实旧库验证。
- **严禁 AI 自行 `screencap` 截屏验证**（除非用户当轮明确要求）。

## 依赖校验与依赖图

- `gradle/verification-metadata.xml` 已启用：`verify-metadata=true` / `verify-signatures=false`。
- ❗**元数据必须用 `scripts/regen-verification-metadata.sh` 重建，绝不要按 CI 报错一次手补一个 checksum**（每轮 CI ~11 分钟）。
- 覆盖不全的两个叠加原因：① 普通 build 不解析 AGP 内部配置 `_internal-unified-test-platform-*` → 只有 **instrumented job** 红；
  ② `--write-verification-metadata` 不记录本地缓存里已有的 descriptor，不加 `--refresh-dependencies` 会「构建成功但文件不全」，CI 冷缓存才炸。
- 脚本只允许新增，删改既有 checksum 会报错还原。正常跳过 `debugUnitTestCompileClasspath` / `debugAndroidTestCompileClasspath`（无缺口）。
- 新增 checksum 必须**独立复核**（从 Maven Central 重下重算 sha256），不能因 Gradle 生成了就当可信。
- 给 `gradlew.bat` 传 `-I` **禁用 POSIX 绝对路径**（`/d/AI项目/…` 会拼成 `D:\AI项目\yanji\d\AI项目\…`）；用相对路径。
- **依赖图**：仓库 public → dependency review 无需 GHAS 授权。❗GitHub **不做 Gradle 静态解析** → 必须用
  Dependency Submission API（`gradle/actions/dependency-submission`）提交解析后的图。
  探针：`dependency-graph/sbom` → 404 且 `compare/A...B` → 403 ⇒ 未启用；最权威是真跑一次提交报 `disabled`。
- `dependency-submission.yml` 是**喂数据不是门禁** → `continue-on-error: true`，图未开时只 warning，**绝不能让 PR 变红**；
  fork PR 只有只读 token，用 job 级 `if` 跳过。真正门禁是 `dependency-review.yml`。
- 用户说「已开启」不等于生效，必须探针复核。
