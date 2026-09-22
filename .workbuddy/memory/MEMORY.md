# 研迹（Yanji）· 项目长期记忆

> 只记跨会话仍有效的事实与约定；日常改动写 `YYYY-MM-DD.md`。
> 品牌名是「研迹」；「卷卷 / Juanjuan」是产品角色名，不是品牌错误。

## 仓库与 Git

- GitHub `xinye1017/yanji`（public）、分支 `main`。身份 Xinye / 1436143769@qq.com，代理端口 **7898**。
- 凭据助手 `credential.https://github.com.helper = !gh.exe auth git-credential`（缺它 push 报 `could not read Username`）。
- Windows 提交丢 `gradlew` 可执行位 → CI `Permission denied`，修：`git update-index --chmod=+x gradlew`。
- `app/schemas/**` 必须随 Entity 变更提交；`gradle.properties` 的 `-Dfile.encoding=GBK` 是中文路径 workaround，别改。

## 本机构建（Windows 坑）

- 单 module：Kotlin + Compose + Room，minSdk 24 / target 36，Java 17，包名 `com.example.yanji`。
- Wrapper **9.1.0** / AGP 9.0.1 / Kotlin 2.3.20 / Room 2.8.4（`技术栈.md` 的 8.x 已过时）。SDK `C:\Users\dex\Android\Sdk`。
- **必须用 `./gradlew.bat`**；Gradle 输出含 NUL → 管道前 `tr -d '\000'`。
- Kotlin daemon 偶发 `e: Daemon compilation failed`，会自动 fallback 且仍 BUILD SUCCESSFUL —— 先看末尾结果再判失败。
- 变量名**禁用 `TMP`/`TEMP`**（覆盖 Windows 临时目录，adb 起不来）。AVD `DiaryPhone` = API 35 / x86_64 / google_apis。
- 模拟器镜像残缺时 `sdkmanager --install` **不重下**，须把目录移出 SDK 根再装。
- `git worktree remove` 因 build 长路径失败 → `Remove-Item -Recurse -Force` 清残留。

## 命名（2026-09-21 全量改名）

- `journal*` → `note*`、`Juanjuan*` → `Ai*`（文件 + 类 + 函数 + testTag）。
- ❗**冻结不改**（全是持久化值 / 线上格式，改名即静默丢数据）：
  表 `journal_entries` + 索引 `index_journal_entries_date`；`ChatSender.JUANJUAN`；成就 ID `journey_journal_first`；
  LLM token `SAVE_TO_JOURNAL`/`OPEN_JOURNAL`；`QuickStartPreset.TYPE_JOURNAL = "journal"`；
  备份键 `journalEntries`（Kotlin 侧已改名 `noteEntries`，用 `@SerialName("journalEntries")` 钉住，`BackupCodecTest` 有旧键 fixture）；
  角色名「卷卷」与 `juanjuan.png` / `R.drawable.juanjuan`。
- `scripts/check-runtime-fixtures.sh` 实体白名单已同步 `NoteEntry`。

## UI / 设计令牌

- Token 唯一来源 `app/src/main/java/com/example/yanji/theme/`（**不是** `ui/theme/`）。
- **`ui/` 下禁止 `Color(0x...)` 字面量与静态亮色 token**；门禁 `scripts/check-design-tokens.sh` 只扫 `ui/`，根包是盲区。
- 卡片圆角 24dp（`YanjiRadius.StandardCardRadius`）；裸 dp 只允许在 `theme/Theme.kt` 的 `YanjiShapes`。
- 页面主标题统一 `YanjiPageHeader`；品牌 TopBar（`ChatTopBar`）不复用。`#5C4BC3` = `YanjiLavenderDeep`（Chat 深度解析专用）。
- 顶部「透明渐变导航栏」的唯一实现是 `ui/components/TopFadeScrim.kt`（Chat 与随笔编辑页共用）：基于 `Brush.verticalGradient`
  与页面背景色 `MaterialTheme.colorScheme.background` 的多段柔和透明度渐变（0.96f → 0.85f → 0.45f → 0.12f → Transparent），
  零色块、零生硬边缘、与页面底色完全融为一体。
  配套铁律：滚动视口的顶部让位必须用 **`contentPadding` / 滚动内容内的 `Spacer`**，**不能**用容器 `padding(top=…)`——
  后者会把内容硬切在导航栏下方，导致内容无法自然滚入渐变羽化区域。
- 系统栏图标由 `SystemBarAppearance(darkTheme)` 驱动，**不能**靠无参 `enableEdgeToEdge()`；根容器背景用 `colorScheme.background`。
- 暗色看 `design_dark.md`（Midnight Blue），**禁止纯黑 `#000000`**。

## haze（易致启动即崩）

- 依赖 `dev.chrisbanes.haze` 1.5.4。**每个 `Modifier.hazeEffect { }` 必须显式设 `backgroundColor`**，否则 RenderEffect 路径抛
  `IllegalArgumentException`（启动期绘制崩溃，单测测不出）。唯一调用点 `ui/components/GlassSurface.kt`。

## 数据层

- Room 唯一事实源；内存 Flow 只作同步读缓存，禁双写。**零假数据**（空表合法）。
- 禁 `fallbackToDestructiveMigration()`；迁移覆写 `migrate(connection: SQLiteConnection)`；**禁 `ALTER TABLE DROP COLUMN`**（SQLite < 3.35）。
- version = **15**（以 `YanjiDatabase.kt` 的 `@Database(version=…)` 为准，别信记忆）。

## 计时 / 专注

- 物理事实走 `SystemClock.elapsedRealtime`；`ActiveSessionCoordinator` 负责落库：结束 → COMPLETED，放弃 → CANCELLED，< 60s 不记录。
- 状态源 `data/timer/FocusLiveState.kt`；`elapsedSecondsForUi` 是每秒镜像只给 Compose；运行中交系统 Chronometer，**禁每秒 notify()**。
- Service 取 `applicationContext` 用 `lateinit` + `onCreate`。

## 凭据

- AI Key 只存 `noBackupFilesDir` + Keystore AES-GCM，异常时 Fail-Closed（不降级明文）。明文 HTTP 关闭，仅放行 localhost / 10.0.2.2。

## 测试

- `:app:testDebugUnitTest` 全绿；新增迁移补进 `YanjiMigrationTest`。
- ⚠️ **`FROM-CACHE` = 没真跑**。验收用 `--rerun-tasks --no-build-cache`，看 `N executed, 0 from cache`。
- UI 定位一律 testTag，不断言中文整句；一个 test method 一次 `setContent`；时间依赖测试必须 `set(MILLISECOND, 0)`。
- 插桩用一次性数据库，绝不打用户真库。本机 API 35 模拟器，CI API 34。

## adb / 真机

- 完整流程见 `AGENTS.md` §五 / §七；设备串一律 mDNS 动态发现，不写死。PKB110 逻辑分辨率 1256×2760。
- 推送 `adb -s <id> install -r app-debug.apk`（保留数据）；固定 debug 签名，换签名 = 丢数据。
- **严禁自行操作真机与截屏验证**（禁 `input tap/swipe`、禁 `uiautomator dump`、禁 `screencap`）；交付终点止于 `am start` 拉起主界面，用户亲自测试并及时反馈。
- Android 16 / ColorOS：实测 `canPostPromotedNotifications() == false` → 走常驻通知 + Chronometer；`dumpsys promoted=true` 不可信，别用 Hidden API。

## 依赖校验

- 元数据必须用 `scripts/regen-verification-metadata.sh` 重建（含 `--refresh-dependencies`），**绝不手补 checksum**；新增项独立复核 sha256。
- 给 `gradlew.bat` 传 `-I` 必须用相对路径。`dependency-submission.yml` 是喂数据（continue-on-error），真正门禁是 `dependency-review.yml`。

## 并发改造风险

- 同仓库并发两个会话会互相重置未提交编辑、甚至切分支 → 大改前确认独占；commit 后立即 push；commit 前先 `git branch --show-current`。
