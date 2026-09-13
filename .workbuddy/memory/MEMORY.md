# 研迹 · 项目长期记忆

> 只记录跨会话仍然有效的事实与约定。日常改动写在 `YYYY-MM-DD.md`。

## 仓库

- GitHub：`xinye1017/yanji`（**私密**），分支 `main`，远端 `origin`。
- `git config` 身份：Xinye / 1436143769@qq.com；HTTP 代理 127.0.0.1:7898（git config 与环境变量都已设置）。
- **凭据助手已配置**：`gh auth setup-git` 把
  `credential.https://github.com.helper = !gh.exe auth git-credential` 写进了 global config。
  没有它，普通 `git push` 会报 `could not read Username for 'https://github.com'`
  （`gh repo create --push` 自带凭据，所以第一次推送不会暴露这个问题）。
- **Windows 提交后 `gradlew` 会丢可执行位**（NTFS 不存 +x）。CI 会报
  `./gradlew: Permission denied`。修法：`git update-index --chmod=+x gradlew`。
- `app/schemas/**` 是迁移测试的校验依据，**必须随代码提交**；改 Entity 忘了 bump 版本号
  会把同版本号的 schema JSON 覆盖掉，务必避免。
- `scripts/adb-backup.sh`、`scripts/adb-push.sh` 是可复用的真机脚本；一次性运维脚本
  （清库/恢复）在 `build/` 下，不入库。
- `gradle.properties` 的 `-Dfile.encoding=GBK` 是中文 Windows 路径 workaround，
  CI 里用 `sed` 换成 UTF-8，不要"顺手修正"。
- **不要用 `TMP`/`TEMP` 当 shell 变量名**（会覆盖 Windows 临时目录，adb server 起不来）。

## 项目定位

- 单 Android App module：Kotlin + Jetpack Compose + Room，`minSdk 24 / target 36`，Java 17。
- 包名 `com.example.yanji`，主入口 `MainActivity`。
- 构建：Gradle Wrapper **9.1.0**（`gradle/wrapper`）、AGP 9.0.1、Kotlin 2.3.20、Room 2.8.4。
  `技术栈.md` 里写的 Gradle 8.x 是旧信息。
- 本机可直接验证：`./gradlew.bat :app:testDebugUnitTest` 与 `:app:assembleDebug` 都能离线跑通
  （SDK 在 `C:\Users\dex\Android\Sdk`）。
- **本机必须用 `./gradlew.bat`，不要用 `./gradlew`**：Git Bash 下后者报
  `ClassNotFoundException: org.gradle.wrapper.GradleWrapperMain`。
- **Gradle 输出含 NUL 字节**，管道给 `grep` 会报 `Binary file (standard input) matches`
  → 先 `tr -d '\000'`。
- **Kotlin daemon 偶发崩溃**（`e: Daemon compilation failed` + `IncrementalCachesManager.close` 文件锁），
  Gradle 会自动 `Using fallback strategy: Compile without Kotlin daemon` 并 BUILD SUCCESSFUL。
  看到 `e:` 先确认末尾有没有 `BUILD SUCCESSFUL` 再判定为编译失败。
  多任务并发跑 Gradle 会加剧，必要时 `./gradlew.bat --stop` 后串行重跑。

## UI Design Token

- 配色/圆角/间距的**唯一来源**是 `app/src/main/java/com/example/yanji/theme/`
  （`Color.kt` / `Radius.kt` / `Spacing.kt` / `Type.kt` / `Theme.kt`），
  **不是** `ui/theme/`（该目录不存在，容易找错）。
- **`ui/` 下不允许出现 `Color(0x...)` 字面量**，静态检查：
  `grep -R "Color(0x" app/src/main/java/com/example/yanji/ui` 必须 0 命中。
  新增颜色先加进 `theme/Color.kt` 并起语义名。
- `#5C4BC3` 是 `YanjiLavenderDeep`（Chat 深度解析前景专用），不要与 `YanjiLavender` 混用：
  后者铺在 `YanjiLavenderSoft` 上对比度不足。
- 标准卡片圆角固定 `YanjiRadius.StandardCardRadius = 24.dp`（DESIGN.md standard card），
  `YanjiCard` 默认 shape 不再走 `CardDefaults.shape`（M3 = Shapes.medium = 16dp）。
- 页面主标题统一用 `YanjiPageHeader(title, subtitle?, trailing?)`；
  品牌化 TopBar（`ChatTopBar`）不复用。
- raw `fontSize` 只在**计时器 / 图表主数字**上保留，普通正文与 Label 一律走
  `MaterialTheme.typography`。

## 判定「测试失败是否自己引入」

用 `git worktree add <tmp> HEAD` 拉一份干净基线，在**独立目录**跑 `./gradlew.bat :app:testDebugUnitTest`，
比对测试总数与通过情况（工作副本的 `app/build/test-results/**/*.xml` 与基线互不覆盖）。
2026-09-13 实测：HEAD 74 测试全绿，工作副本 102 测试其中 `ActiveSessionPersistenceTest` 8 个失败
—— 失败源自未提交的计时重构，与当轮 UI 改动无关。**先做这个对照，再决定是否要修。**

## 数据层约定（重要）

- **数据库单一事实来源是 Room**。仓库里的内存 `MutableStateFlow` 只是同步读缓存，
  由 DAO Flow 回灌；新代码不要再做「先改内存再写库」的双写。
- **绝不写假数据**。「表为空」是合法业务状态，不是「需要初始化」。只有
  `user_settings` 默认值与首页快捷操作默认三项可以初始化。
- **绝不使用 `fallbackToDestructiveMigration()`**。迁移路径缺失时让 Room 抛异常。
- **迁移必须覆写 `migrate(connection: SQLiteConnection)`**，不要用
  `migrate(db: SupportSQLiteDatabase)`（原因见 2026-09-11 日志的踩坑记录 #2）。
- **不要在迁移里用 `ALTER TABLE ... DROP COLUMN`**（minSdk 24 真机 SQLite < 3.35）。
  统一走「建新表 → INSERT SELECT → DROP → RENAME」。
- 改任何 Entity 字段时**必须同步 bump `@Database(version)`**，否则 KSP 会用新 schema
  覆盖掉同版本号的 `app/schemas/<v>.json`，毁掉历史 schema。
- `app/schemas/**` 是迁移测试的真实校验依据，**要当源码提交，不要删**。
  当前有 `7.json` ~ `10.json`（1~6 的历史 JSON 从未被提交过）。当前 version 10。
- **Room 的 TableInfo 校验允许「entity 侧无 DEFAULT、DB 侧有 DEFAULT」**（2026-09-12 实测）：
  `ALTER TABLE ... ADD COLUMN x T NOT NULL DEFAULT ''` 后不需要给 Entity 加 @ColumnInfo(defaultValue)。
- YanjiMigrationTest 的「起点 DDL」必须用与该版本真实结构一致的 DDL：
  v7 之后的起点不能用 v7Ddl 直接充当（v8 重建移除了 journal.studyDurationSeconds 与
  user_settings.aiApiKey），需用测试里的 `v9Ddl`（v7Ddl + 表替换）。

## 凭据与隐私

- AI API Key 不进 Room、不进 SharedPreferences（两者默认都在 Auto Backup 范围内）。
  它在 `noBackupFilesDir` 里用 Android Keystore 的 AES-GCM 加密保存（`KeystoreSecretStore`）。
- 备份策略：只备份 `database` 域（学习数据）；凭据靠 no-backup 目录天然排除。
- 明文 HTTP 全局关闭，仅放行 `127.0.0.1 / localhost / 10.0.2.2`。
  局域网 Ollama 请用 HTTPS 反代或 `adb reverse`。

## 计时与业务边界

- 计时事实来自**单调时钟**（`SystemClock.elapsedRealtime` 差），每秒 tick 只刷新展示。
- 「计时结束 → 落库」由 `ActiveSessionCoordinator`（业务层）负责，
  **不允许**再出现「由 Compose 页面决定这次学习算不算数」的写法。
- 语义：倒计时归零 / 主动「结束」→ COMPLETED 落库；「放弃」→ CANCELLED 不落库；
  专注不足 60 秒不记录。

## 专注展示层（FocusLiveState / liveactivity）

- **统一状态源是 `data/timer/FocusLiveState.kt`**（sealed + `focusLiveStateOf()` 纯函数）。
  App UI、系统通知、Android Live Update、ColorOS 流体云都消费它，不要再各自推文案。
- `FocusTimerService` 暴露两条流，同源于 TimerMachine，**不是两套计时器**：
  - `liveState`：语义状态，只在 START / PAUSE / RESUME / FINISH / DISCARD 发射 → 通知层消费；
  - `elapsedSecondsForUi`：每秒展示镜像 → **只给 Compose**，通知层不准消费。
  运行中的 `44:59→44:58` 交给系统 Chronometer，**禁止每秒 `notify()`**。
- 通知 Action 字符串唯一定义在 `liveactivity/FocusTimerActions.kt`，Service 的 `ACTION_*` 引用它。
- 通知文案/动作的「该显示什么」在 `FocusNotificationSpec(s)`（纯 Kotlin，JVM 可测），
  「怎么 setXxx」在 `StandardNotificationController`。「暂停必须关掉 Chronometer」有单测守门。
- 新增 `res/drawable/ic_stat_focus.xml` 作 small icon，不用 Launcher 图标、不加 LargeIcon。
- Compose 侧：`ActiveFocusContent(elapsedSeconds: State<Long>)`，**函数体内不读取**，
  只在 `CountdownFocusBody` / `FlowFocusBody` 读 `.value`，避免外层手工 Layout 每秒重测。
- `TimerState`/`timerState` 是遗留镜像，只剩 `ExamScreen` 在用，新代码不要往里加字段。
- **Service 里不能用字段初始化取 `applicationContext`**（组件先构造后 attachBaseContext，会 NPE），
  一律 `lateinit` + `onCreate` 赋值。

## Android 16 Live Update / ColorOS 流体云（已实测）

- 真机 OPPO PKB110 / ColorOS **V16.1.0** / Android 16 / `ro.build.version.sdk_full=36.0`：
  **`canPostPromotedNotifications() == false`**，但 `POST_PROMOTED_NOTIFICATIONS: granted=true`
  → 标准 Live Update 不可用，实际走「普通常驻通知 + Chronometer」。**换 ROM/开系统开关后需重测。**
- `dumpsys notification` 里 AppSettings 的 `promoted=true` **不等于** `canPostPromotedNotifications()`，
  不能当能力判据；只信运行时 API。
- `POST_PROMOTED_NOTIFICATIONS` / `Settings.ACTION_MANAGE_APP_PROMOTED_NOTIFICATIONS`
  在 **API 36 的 android.jar 里不存在**（已全量 class 搜索）。权限只能用字面量声明，
  设置页常量**不可引用**；权限弹窗改走 `ACTION_APP_NOTIFICATION_SETTINGS`。
- ColorOS 流体云是它自己的 livealert/pantanal 框架 + 按包名 denylist
  （`livealert_disable_seedling`、`fluid_cloud_not_support`、
  **`oplus_no_fluid_capsule_animation`** 管小胶囊动画）。**不要**用 Hidden API / Reflection 去碰。
- OPPO **原生**流体云（SeedlingSupportSDK / IntelligentIntent）需**企业认证 + 联系 OPPO +
  serviceId 分配 + UPK**，属商务接入；`ColorOsFluidCloudController` 只做探测与降级，不做私有调用。
- 真机诊断可复现：`LiveActivityCapabilityInstrumentedTest`
  （报告落盘 `externalCacheDir/live-activity-capability.txt`）。

## 设备运维（adb）

- adb 脚本**禁止用 `TMP`/`TEMP` 当变量名**——会覆盖 Windows 的临时目录环境变量，
  adb server 直接起不来（报 `cannot open ...adb.log`）。
- adb 脚本开头必须 `set -euo pipefail`；对设备数据库做写操作前，本地副本必须先断言
  `user_version` 与关键表存在。**先快照、后动手**——这条在 2026-09-11 真的救回过一次数据
  （脚本打开了错误的文件名，把空库推回设备，靠覆盖前几秒的快照恢复）。
- 拉取应用数据库必须连 `-wal` / `-shm` 一起拉；本地用 sqlite3 打开时会自动回放 WAL，
  改完后执行 `PRAGMA wal_checkpoint(TRUNCATE)` 再关闭，写回时删掉设备上的 `-wal`/`-shm`。
- 已备好的脚本：`build/adb-backup.sh`（备份）、`build/adb-push.sh`（推送+校验）、
  `build/adb-clear-seed.sh`、`build/adb-restore.sh`。

## 测试约定

- `:app:testDebugUnitTest` 必须保持全绿；新增迁移必须补进 `YanjiMigrationTest`。
- 单元测试类路径需要 Room 的 jvm 变体，见 `app/build.gradle.kts` 里的
  `configurations.configureEach { name.contains("UnitTest") }` 替换块（不要删）。
- 本地 JVM 单测要解析 JSON 必须加 `testImplementation(libs.org.json)`——
  Android 提供的 org.json 只是 stub，一调用就抛 not mocked。
- 插桩测试入口已配置（`testInstrumentationRunner`），`androidTest` 里
  `AppInitializerInstrumentedTest` / `BackupTransferInstrumentedTest` 是两条关键不变量的守门测试。
  **插桩测试必须用一次性数据库，绝不能打到用户真实库。**
- 可测试性约定：会破坏性改写数据 / 承载业务不变量的逻辑抽成 `internal` 类，
  以 `YanjiDatabase` 作参数（AGP friend-path 让 androidTest 能访问 internal）。
  已按此模式抽出的：`AppInitializer`、`BackupTransfer`、`AiClient`/`AiProtocol`。
- 目标架构（进行中）：Compose UI → Feature ViewModel → Repository → Room/Service。
  已剥离：计时（data/timer）、备份（data/backup）、AI 网络（data/ai）。
  `YanjiRepository` 1700 → 1454 行，剩余：Chat / Study / Journal / Settings 四个仓库与 ViewModel 层。
