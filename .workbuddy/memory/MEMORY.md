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
  当前只有 `7.json` 与 `8.json`（1~6 的历史 JSON 从未被提交过）。

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
