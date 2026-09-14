# 研迹（Yanji） · 项目长期记忆

> 只记录跨会话仍有效的事实与约定。日常改动写 `YYYY-MM-DD.md`。
> **品牌名唯一正确答案是「研迹」**：`res/values/strings.xml` 的 `app_name`（= 桌面显示名）、
> DESIGN.md、UI 文案、备份/AI 文案全部一致。README 曾误写「研己」，2026-09-14 已全量修正。
> 「卷卷 / Juanjuan」是产品角色名，不是品牌错误。

## 仓库与 Git

- GitHub `xinye1017/yanji`（私密），`main`。身份 Xinye / 1436143769@qq.com，代理 7898。
- 凭据助手已配：`credential.https://github.com.helper = !gh.exe auth git-credential`（缺它 push 报
  `could not read Username`；`gh repo create --push` 自带凭据所以首次不暴露）。
- **Windows 提交会丢 `gradlew` 可执行位** → CI 报 `Permission denied`。修：`git update-index --chmod=+x gradlew`。
- `app/schemas/**` 是迁移测试依据，**必须提交**；改 Entity 忘 bump 版本会覆盖同版本 schema JSON。
- `gradle.properties` 的 `-Dfile.encoding=GBK` 是中文 Windows 路径 workaround，CI 用 `sed` 换 UTF-8，别改。

## 本机构建（Windows 特有坑）

- 单 module：Kotlin + Compose + Room，`minSdk 24 / target 36`，Java 17，包名 `com.example.yanji`。
- 构建：Gradle Wrapper **9.1.0**、AGP 9.0.1、Kotlin 2.3.20、Room 2.8.4。`技术栈.md` 写的 8.x 是旧信息。
- **必须用 `./gradlew.bat`**（Git Bash 下 `./gradlew` 报 `ClassNotFoundException: GradleWrapperMain`）。SDK 在 `C:\Users\dex\Android\Sdk`。
- Gradle 输出含 NUL 字节，管道给 grep 报 `Binary file matches` → 先 `tr -d '\000'`。
- Kotlin daemon 偶发崩溃（`e: Daemon compilation failed`），Gradle 自动 fallback 且 BUILD SUCCESSFUL。
  看到 `e:` 先确认末尾有没有 BUILD SUCCESSFUL。多任务并发会加剧，必要时 `--stop` 后串行。
- 变量名**禁用 `TMP`/`TEMP`**（覆盖 Windows 临时目录，adb server 起不来）。
- **模拟器镜像可能被删残**：`system-images/android-35/google_apis/x86_64` 缺 `system.img`/`vendor.img`
  时报 `No initial system image for this configuration!`。`sdkmanager --install` 因为目录里还有
  `package.xml` 会判定「已安装」而**不重下**；必须把该目录移出 **SDK 根**（放 `Sdk/` 下也会被扫到）
  再重下（~3.5 GB）。AVD `DiaryPhone` = API 35 / x86_64 / google_apis，WHPX 加速可用。
- **`git worktree remove` 会因 Gradle build 目录长路径失败**（`Filename too long`），
  但 worktree 注册已被摘除；残留目录用 python `shutil.rmtree` 加长路径前缀也清不掉，
  得用 Windows 原生递归删除（`Remove-Item -Recurse -Force`）。

## UI Design Token

- 唯一来源 `app/src/main/java/com/example/yanji/theme/`（`Color/Radius/Spacing/Type/Theme.kt`），
  **不是** `ui/theme/`（不存在）。
- **`ui/` 下禁止 `Color(0x...)` 字面量**；检查 `grep -R "Color(0x" app/src/main/java/com/example/yanji/ui` 必须 0 命中。
- `#5C4BC3` = `YanjiLavenderDeep`（Chat 深度解析前景专用），别与 `YanjiLavender` 混用。
- 标准卡片圆角固定 24dp（`YanjiRadius.StandardCardRadius`）；raw `fontSize` 只在计时器/图表主数字保留。
- 页面主标题统一 `YanjiPageHeader(title, subtitle?, trailing?)`；品牌 TopBar（`ChatTopBar`）不复用。

## 数据层（重要）

- **单一事实来源是 Room**；内存 MutableStateFlow 只是同步读缓存，由 DAO Flow 回灌，别做双写。
- **绝不写假数据**。「表为空」是合法状态。只有 `user_settings` 默认值与首页快捷操作默认三项可初始化。
- **绝不用 `fallbackToDestructiveMigration()`**。
- 迁移必须覆写 `migrate(connection: SQLiteConnection)`（不要 `SupportSQLiteDatabase` 版）。
- 迁移里**禁用 `ALTER TABLE ... DROP COLUMN`**（minSdk 24 真机 SQLite < 3.35）→ 建新表/INSERT SELECT/DROP/RENAME。
- Room TableInfo 允许「entity 无 DEFAULT、DB 有 DEFAULT」，加列后不必补 `@ColumnInfo(defaultValue)`。
- 当前 schema 有 7~11.json，version 11（README 谓 v11）。YanjiMigrationTest 起点 DDL 必须与真实结构一致。

## 凭据与隐私

- AI API Key 不进 Room / SharedPreferences；在 `noBackupFilesDir` 用 Keystore AES-GCM 保存。
- 备份只含 `database` 域；明文 HTTP 全局关闭，仅放行 `127.0.0.1 / localhost / 10.0.2.2`。

## 计时与专注展示层

- 计时事实来自单调时钟（`SystemClock.elapsedRealtime` 差）；「计时结束→落库」由
  `ActiveSessionCoordinator` 负责，Compose 页面不决定这次学习算不算数。
- 语义：倒计时归零/主动结束 → COMPLETED 落库；放弃 → CANCELLED 不落库；< 60 秒不记录。
- 统一状态源 `data/timer/FocusLiveState.kt`（sealed + `focusLiveStateOf()`）；App UI/通知/Live Update/流体云都消费它。
- `FocusTimerService` 两条流同源：`liveState`（语义，仅状态变更发射，通知层消费）与
  `elapsedSecondsForUi`（每秒镜像，**只给 Compose**）。运行中计时交系统 Chronometer，**禁止每秒 notify()**。
- 通知 Action 字符串唯一定义在 `liveactivity/FocusTimerActions.kt`；文案在 `FocusNotificationSpec(s)`（JVM 可测）。
- Service 里不能用字段初始化取 `applicationContext`（先构造后 attach）→ `lateinit` + `onCreate`。
- `TimerState`/`timerState` 是遗留镜像，只剩 `ExamScreen` 在用。

## Android 16 Live Update / ColorOS（真机实测）

- OPPO PKB110 / ColorOS V16.1.0 / API 36：`canPostPromotedNotifications() == false`，走「普通常驻通知 + Chronometer」。
- `dumpsys` 的 AppSettings `promoted=true` ≠ `canPostPromotedNotifications()`，只信运行时 API。
- `POST_PROMOTED_NOTIFICATIONS` / `Settings.ACTION_MANAGE_APP_PROMOTED_NOTIFICATIONS` 在 API 36 android.jar 中不存在 →
  权限用字面量声明，设置页常量不可引用，弹窗走 `ACTION_APP_NOTIFICATION_SETTINGS`。
- ColorOS 流体云是自有 livealert/pantanal + 包名 denylist（`livealert_disable_seedling`、`fluid_cloud_not_support`、
  `oplus_no_fluid_capsule_animation`），**不要**用 Hidden API/Reflection。
- OPPO 原生流体云需企业认证+serviceId+UPK（商务接入）；`ColorOsFluidCloudController` 只探测+降级。
- 诊断可复现：`LiveActivityCapabilityInstrumentedTest` → 报告 `externalCacheDir/live-activity-capability.txt`。

## 测试约定

- `:app:testDebugUnitTest` 必须全绿；新增迁移补进 `YanjiMigrationTest`。
- 单测类路径需 Room jvm 变体（见 `app/build.gradle.kts` 的 `UnitTest` configurations 替换块，别删）。
- 本地 JVM 单测解析 JSON 必须 `testImplementation(libs.org.json)`（Android 的 org.json 是 stub）。
- 插桩测试入口已配；`AppInitializerInstrumentedTest`/`BackupTransferInstrumentedTest` 是关键不变量守门。
  **插桩必须用一次性数据库，绝不能打用户真库。**
- 可测试性：会破坏性改写数据/承载不变量的逻辑抽成 `internal` 类，以 `YanjiDatabase` 为参数
  （AGP friend-path 让 androidTest 可访问）。已抽出：`AppInitializer`、`BackupTransfer`、`AiClient/AiProtocol`。
- 判定「测试失败是否自己引入」：`git worktree add <tmp> HEAD` 拉干净基线独立跑，比对测试总数与通过情况。
- **UI 测试定位一律走 testTag，不断言中文整句文案**（文案迭代会打红测试）。已导出的锚点常量：
  `ui/chat/components/ComposerBar.kt` → `ChatInputTestTag` / `ChatSendButtonTestTag`；
  `UserMessageBubble.kt` → `UserMessageBubbleTestTag`；`JuanjuanMessageBubble.kt` → `JuanjuanMessageBubbleTestTag`；
  `JournalEditorScreen.kt` → `object JournalEditorTags`（ContentInput / BlockersInput / SaveButton / PlanInput /
  PlanAddButton / `moodOption(score)`）。
- **一个 test method 只能调用一次 `setContent`**（Compose 宿主 Activity 二次 setContent 抛
  `IllegalStateException: Activity has already called setContent`）→ 每个组件各自成测试。
- `JournalViewModel.saveJournal` 是 `open`，这是**给插桩测试留的接缝**：默认实现会写到 App 全局单例
  指向的**用户真实库**，插桩测试必须注入不落库的子类（见 `JournalEditorScreenInstrumentedTest`）。
- `LiveActivityCapabilityInstrumentedTest` 用 `assumeTrue/assumeFalse` 表达「条件不变量」，
  低版本/未授权环境会**跳过**而非假绿；纯逻辑契约另有 JVM 单测 `LiveActivityCapabilityTest`。
- 本机验证走 API 35 模拟器；CI 走 API 34（`android-ci.yml` 的 instrumented job）。
- 目标架构（进行中）：Compose UI → Feature ViewModel → Repository → Room/Service。

## 并发改造风险（2026-09-14 实测踩中）

- 曾在**同一仓库上同时跑两个改造任务**，另一个进程（成就系统 V2 重构）每 7~15 秒改写一个文件，
  导致工作区反复不可编译（`MainActivity.kt` 的 `onCreate` 被删成语法错误；
  `AchievementsScreen.kt` / `HomeScreen.kt` 删了 `androidx.compose.material.icons.*` 导入但用法还在）。
- **教训**：验证必须在 `git worktree add` 的隔离副本里做，不要在活动工作区反复构建；
  见到「刚还能编译、一分钟后语法错误」先怀疑并发写入，而不是自己改坏了。

## adb 运维

- 脚本开头 `set -euo pipefail`；写设备库前先断言本地副本 `user_version` 与关键表存在。**先快照后动手**。
- 拉库要连 `-wal`/`-shm` 一起；本地 sqlite3 打开会回放 WAL，改完 `PRAGMA wal_checkpoint(TRUNCATE)`，写回删设备 `-wal`/`-shm`。
- 可复用脚本 `scripts/adb-backup.sh`、`scripts/adb-push.sh`；一次性脚本在 `build/` 下不入库。
