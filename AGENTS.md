# 研迹 · Agent 操作规范

面向 AI Agent，命令式、去重、可扫描。详细设备手册见 [ADB.md](file:///d:/yanji/ADB.md)，按需查阅，不在本文重复。

**冲突裁决顺序（高→低）**：代码/构建配置/Git 脏工作区/CI 守卫 > 本文 > `docs/adr/**`（无条件服从 ADR）> README（受 `check-project-facts.sh` 保护的字段）> `src/theme/**`+组件代码（UI 设计权威）> `.workbuddy/memory/MEMORY.md`。禁止把历史 Prompt/留档/截图/旧 Compose 页面当作既定事实——运行代码与构建配置才是事实。

**开工 3 步**：① `git status --short`（保护在途修改）；② 读 §1 红线 + §3 架构；③ 按 §2 决策树定位工作流。

---

## 1. 红线（MUST / NEVER，违反即交付失败）

1. **保护在途修改**：开工先 `git status --short`；未要求改的已修改/在途文件不得改写、还原、清空。
2. **禁破坏性 Git**：NEVER `git checkout -- .` / `git reset --hard` / `git clean -fd` / `git stash`；禁全仓库 formatter 覆盖无关文件。
3. **零假数据**：NEVER 注入虚假业务/Mock；Room 是唯一业务事实源，空表合法（`scripts/check-runtime-fixtures.sh` 零容忍）。
4. **凭据零泄漏**：AI API Key 绝不写入 Room/SharedPreferences/日志/Prompt/fixture/备份；Keystore 异常 Fail-Closed，禁降级明文。
5. **迁移安全**：NEVER `fallbackToDestructiveMigration()`；手写 `migrate(connection: SQLiteConnection)`；NEVER `ALTER TABLE DROP COLUMN`（兼容 Android 7.0 / SQLite<3.35）；Entity 变更必须同步提交 `app/schemas/**`。
6. **UI 设计自由**：设计权威仅运行代码与语义 Token（`src/theme/tokens.ts`、`src/theme/ThemeProvider.tsx`、README「统一设计系统」），无独立设计文档；不得把历史色值/固定字号/圆角/零边框/无玻璃效果当红线。可重设计整个视觉系统，但须统一更新语义 Token、深浅色、触控目标、对比度、无障碍，并同步 `src/theme/**`+组件+测试契约。**系统级深浅色禁改**（见 #13）。禁止把旧卡片风格、固定配色、零边框、历史圆角升级为规范。
7. **计时韧性**：物理事实基于单调时钟 `SystemClock.elapsedRealtime`；前台通知交系统 Chronometer；NEVER 每秒 `notify()`。
8. **事实不复制**：SDK/版本/颜色/设备网络参数引用权威源（§5），不在文档硬编码。
9. **ADB 单次调用**：`adb start-server`、设备发现、安装、启动必须在同一次工具调用内完成（否则后台子进程被作业对象回收，误报 `device not found`）。
10. **无证据不宣称**：严格区分「编译成功 / 测试执行成功 / 模拟器安装启动成功 / 真机安装启动成功」；未运行项显式标注，环境故障不得伪装成产品缺陷。
11. **默认模拟器 / 真机按需**：日常测试一律模拟器（截图/交互/改设置/测试数据无限制，截图仅存被忽略的 `build/`）；仅用户明确说「实机测试」才推手机，手机端只允许 `install -r -d` + `am start`，禁截图/录屏/`uiautomator`/模拟交互/改设置/抓库/备份/清数据（除非逐项明确授权）。
12. **修改完成即提交推送**：不写外部知识库日志；推送前按 §6 完成 `lintDebug`→`testDebugUnitTest`→`assembleDebug`→16KB 守卫；精准 `git add <file>`（NEVER `git add .` / `git commit -a`），Conventional Commits 简讯，`git push`（直连失败走代理 7898，见 §5.5）。
13. **系统深浅色**：产品逻辑只读；模拟器自由验证；真机仅明确实机测试且授权时切换——先记基线、测完还原、如实报告（§8）。

---

## 2. 决策树（快速定位）

| 触发条件 | 动作 |
| :--- | :--- |
| 改 UI / 业务 / 主题 | §6 验证门禁 → §7.1 模拟器安装测试；不碰真机 |
| 用户明确要求「实机测试」 | §6 验证门禁 → §7.2 真机安装启动（仅此两项）；禁一切调试操作 |
| 改 Room 实体 / 迁移 | 同步 `app/schemas/**` 与手写 migration；确认 `testDebugUnitTest` 迁移用例通过 |
| 深浅色 / 主题跟随验证 | §8 |
| 新增含 Fabric 组件的原生库 | §3.1 手工链接清单（装 npm 包≠接入完成） |
| 磁盘膨胀 | §9 清理脚本白名单 |
| 任何 push 前 | §6 门禁四项全过 |
| 文档更新 | §11 保留清单，易漂移事实改引用式（§5） |

---

## 3. 架构与权威入口

React Native + React + TypeScript / Hermes / Fabric（New Architecture, Bridgeless）/ Kotlin 原生业务 / Room 唯一持久化。版本以 `package.json`（JS）与 `gradle/libs.versions.toml`（原生）为准，勿在文档复制漂移数值。

| 层 | 权威入口 |
| :--- | :--- |
| UI / 导航 / 动画 | `package.json`、`App.tsx`、`src/screens/**`、`src/components/**`、`src/navigation/**` |
| JS 引擎 / 宿主 | `YanjiApplication.kt`、`MainActivity.kt`、`gradle/libs.versions.toml` |
| 原生业务（计时/通知/数据查询与写入） | `app/src/main/java/com/example/yanji/bridge/**`、`src/bridge/**` |
| 持久化 | `app/src/main/java/com/example/yanji/data/db/YanjiDatabase.kt` |
| 主题 / 玻璃材质 | `src/theme/tokens.ts`、`src/theme/ThemeProvider.tsx`、`src/theme/liquidGlass.ts`、`src/navigation/useNavigationAccessibility.ts` |

**3.1 原生库接入**：本项目 Android Add-to-App，`:app` 未应用 RN app Gradle 插件，第三方库手工链接。新增 Fabric 组件库须同步：`settings.gradle.kts`、`app/build.gradle.kts` 依赖+codegen 任务、`YanjiApplication.kt` Package 注册、`app/src/main/jni/{CMakeLists.txt,OnLoad.cpp}` C++ 编译与描述符注册。

**3.2 前端交付顺序**：`npm run typecheck` → `npm run bundle:android`（Metro + 版本匹配 Hermes → `app/src/main/assets/index.android.bundle`+元数据）→ `gradlew.bat assembleDebug`。单独 Gradle 不刷新 bundle；禁止把旧 bundle 当新 UI 交付。改构建输入后必跑 `node scripts/check-bundle-sync.mjs`；Metro 暂存资源须与 `app/src/main/res` 一致。

**3.3 原生构建并发**：Gradle worker 由 `gradle.properties` 限；CMake/Ninja 任务池由根 `build.gradle.kts` 限。NEVER 为提速覆盖，避免多 ABI 同时启动数十 `clang++` 内存耗尽。

**3.4 导航玻璃边界**：布局/染色/采样在 `liquidGlass.ts`；Android 边缘折射、色散、随染色明度提亮/反射由 `patches/react-native-blur-overlay+3.2.1.patch` 维护（更新见 `patches/README.md`）。`regular`/`clear` 是用途非深浅色；外层容器底色≠玻璃采样遮罩。

**3.5 每日数据边界**：Today/Review 共用 `getDailySnapshot(date)`（一次 Room 事务），契约 `src/bridge/index.ts` + `DailySnapshotReader.kt`。任务用时按任务关联累计，NEVER 按专注日期截断跨日贡献。失效事件仅来自 Room 表变更；RN 协调器合并事件并限制隐藏页/后台查询。计时低频会话快照与每秒 tick 分开订阅，tick 只更新可见计时组件，NEVER 回页面根状态驱动整页重渲染。

**3.6 备份恢复边界**：设置页经 `YanjiBackupModule` + 系统文件选择器；`BackupDocumentWorkflow` 只暂存校验内容并返回预览，用户确认后才交 `BackupStore` 恢复。导出等文件写入关闭完成；恢复等 Room 事务提交；导入前快照失败则拒绝写库；活动计时与恢复互斥；预览必须明确空类别会清空本机对应记录；API 密钥始终排除；完整导出/恢复/回滚测试用独立数据库，禁拿日常真机库做破坏性验证。

---

## 4. 工程原则（设计取向）

- 不为向后兼容妥协：过时路径直接删除，不加兼容层/fallback/migration 兜底。
- 选能完整满足当前需求的最简单实现，不为假想未来预建抽象/配置/间接层。
- 逐层迭代：先端到端可用最小版本，再逐层加能力；不为未完成设计牺牲可用产品。
- 组件模块化、关注点分离。
- 优先成熟维护良好的库，禁无理由重造通用功能。
- 自研或加依赖前先查尽项目已有依赖与库文档/类型定义，别假设其不支持。
- 长期视角决策，禁「现在能用以后再换」的权宜之计。
- 设计前先研究成熟产品的验证过的模式，不重新发明。

---

## 5. 环境与权威值

**5.1 Shell（Windows）**：Gradle 一律 PowerShell `.\gradlew.bat <task> --stacktrace`，NEVER `./gradlew` 或无 `.bat` 后缀。Git Bash 会话用 `powershell.exe -NoProfile -Command '.\gradlew.bat <task> --stacktrace'` 桥接。ADB 脚本用 Bash（§7）。禁 `--no-daemon`（保留常驻 Daemon + Configuration Cache）。

**5.2 权威来源（勿硬编码，按需 grep）**：

| 事实 | 权威源 |
| :--- | :--- |
| `compileSdk`/`minSdk`/`targetSdk`/`versionCode`/`versionName` | `app/build.gradle.kts`；README 顶部 `project-facts`（`scripts/check-project-facts.sh` 校验） |
| Room schema / 库版本 | `YanjiDatabase.kt`；`gradle/libs.versions.toml` |
| 依赖版本 | `gradle/libs.versions.toml`（Version Catalog） |
| 包名 / namespace / 入口 Activity | `app/build.gradle.kts`、`app/src/main/AndroidManifest.xml` |
| RN 设计 Token | `src/theme/tokens.ts`、`src/theme/ThemeProvider.tsx` |
| 设备网络参数 | mDNS 动态发现（`_adb-tls-connect`）或 `$ANDROID_SERIAL` |
| 废弃功能 `QuickStartPreset` | `docs/adr/0001-retain-quick-start-compatibility-tombstone.md`（墓碑，禁运行时读写） |

**5.3 版本约定（自下次更新启用）**：`versionName` 用 `x.x.xxx`，第三段固定 3 位；小更新第三段 +1（`1.0.000`→`1.0.001`），`versionCode` 同步 +1（`2`→`3`）。当前 `1.0.0`/`versionCode=2` 不动。发布时 `app/build.gradle.kts` 与 README `project-facts` 两处同步。

**5.4 Portable**：脚本从 `scripts/` 自身位置推导仓库根，不依赖固定盘符/用户名。SDK 经 `ANDROID_HOME`/`ANDROID_SDK_ROOT`/`local.properties` 发现；ADB 从 PATH 或 `ADB=/path/to/adb`。默认测试目标模拟器 `emulator-*`（`adb devices` 自动发现）。真机推送脚本 `scripts/adb-push.sh`（覆盖变量 `YANJI_APK`/`YANJI_DEVICE_BACKUP_DIR`/`YANJI_PACKAGE`，禁日常滥用）。默认 APK `app/build/outputs/apk/debug/app-debug.apk`，备份 `<repo>/build/device-backup`。

**5.5 Owner 设备（仅参考，禁写入脚本/CI）**：仓库 `github.com/xinye1017/yanji` (main/origin)；Windows；`D:\yanji`；包名 `com.example.yanji`，入口 `.MainActivity`；SDK `C:\Users\dex\Android\Sdk`；ADB 在 PATH；默认模拟器 `emulator-5554`（启动/投屏见 ADB.md §二.0）；真机 `PKB110` 逻辑分辨率 **1256×2760**（非 1080×2400）；GitHub 走代理 **7898**（全局/TUN 会拦 mDNS，`git push` 直连超时用 `git -c http.proxy=http://127.0.0.1:7898 -c https.proxy=http://127.0.0.1:7898 push`）。设备串/IP/端口一律 mDNS 动态获取，禁写死。

---

## 6. 构建与验证门禁

**6.1 推送前必做**（PowerShell 逐条，查 Exit Code，任一失败即停并只重跑失败步；失败报告见 `app/build/reports/lint-results-debug.html`、`app/build/reports/tests/testDebugUnitTest/`）：

```powershell
.\gradlew.bat lintDebug --stacktrace
.\gradlew.bat testDebugUnitTest --stacktrace
.\gradlew.bat assembleDebug --stacktrace
bash scripts/check-16kb-alignment.sh
```

通过标准：Lint 0 errors；单测通过（记录数）；APK 生成；16KB 守卫 PASS（`.so` ELF `LOAD p_align`=16384 且 ZIP 偏移 16KB 对齐，Android 15+/Play 要求；本模块 CMake 已带 `-DANDROID_SUPPORT_FLEXIBLE_PAGE_SIZES=ON`，新增原生库/改 CMake/NDK 参数后 FAIL 先修复）。

**6.2 日常构建**：只 `.\gradlew.bat assembleDebug`（增量+配置缓存，约 30s）。NEVER `gradlew build`（触发全套测试/全仓 Lint/多变体，易挂起）；NEVER 习惯性 `clean`（清空增量缓存→2~3min 冷编译，仅 Room Entity 需重生成 Schema 或注解错乱时针对性 clean）。直接用本机 SDK/JDK/缓存，禁重下依赖/改 `build`。

---

## 7. 设备安装（ADB 铁律：发现+安装+启动同一工具调用）

**7.1 模拟器（默认）——Bash（Git Bash）**：

```bash
adb start-server >/dev/null 2>&1
dev="$(adb devices | awk '/^emulator-[0-9]+[[:space:]]+device$/{print $1; exit}')"
if [ -z "$dev" ]; then
  echo "ERROR: 未发现运行中的 Android 模拟器，请先按 ADB.md §二.0 启动 AVD" >&2
  exit 1
fi
echo "Target Emulator: $dev"
adb -s "$dev" install -r -d "app/build/outputs/apk/debug/app-debug.apk"
adb -s "$dev" shell am start -n com.example.yanji/.MainActivity
```

为空则报错退出，**不回退真机**。安装后模拟器无测试限制（截图/录屏/模拟点击滑动/自动交互/改设置/切深浅色/测试数据，截图存 `build/`）；测试数据不得注入真机或产品运行时。

**7.2 真机——仅用户明确要求「实机测试」——Bash（Git Bash）**：

```bash
adb start-server >/dev/null 2>&1
try=0; until [ "$(adb devices | grep -c '_adb-tls-connect.*device')" -gt 0 ]; do
  try=$((try+1)); [ $try -ge 15 ] && break; sleep 3
done
dev="$(adb devices | awk '/_adb-tls-connect.*device/{print $1; exit}')"
if [ -z "$dev" ]; then
  echo "ERROR: 未发现 mDNS 无线设备，请检查代理 / VPN 与手机无线调试（见 §5.5 / ADB.md §三.1）" >&2
  exit 1
fi
echo "Target Device: $dev"
adb -s "$dev" install -r -d "app/build/outputs/apk/debug/app-debug.apk"
adb -s "$dev" shell am start -n com.example.yanji/.MainActivity
```

PowerShell（作者本机手动）：`adb start-server; Start-Sleep -Seconds 2; $dev = (adb devices | Where-Object { $_ -match "adb-.*device" } | ForEach-Object { ($_ -split '\s+')[0] } | Select-Object -First 1); if (-not $dev) { Write-Error "未发现 mDNS 无线设备"; exit 1 }; Write-Host "Target Device: $dev"; adb -s $dev install -r -d "app\build\outputs\apk\debug\app-debug.apk"; adb -s $dev shell am start -n com.example.yanji/.MainActivity`

发现为空则报错退出，**不回退写死串**。安装启动即完成交付；**真机红线**：NEVER 截图/录屏/`uiautomator dump`/模拟点击滑动/改系统设置/抓库备份/清数据（除非逐项明确授权）；保护已有数据与进行中专注会话。搜不到设备/`10061 积极拒绝`/凭据失效 → [ADB.md §二.2](file:///d:/yanji/ADB.md) 标准 6 步配对。代理 7898 全局接管会拦 UDP 5353 致 mDNS 失败（[ADB.md §三.1](file:///d:/yanji/ADB.md)）；手机息屏/退桌面会休眠无线调试并重置端口，须停留在「无线调试」页。

---

## 8. 深浅色验证

- **产品代码只读**：允许读取系统 uiMode 与 `YanjiThemeMode`；NEVER 调用 `UiModeManager`/`setApplicationNightMode`/`setNightModeOverride` 改写系统模式。
- **模拟器（默认）**：自由切浅/深、截图取证、反复对比，无需授权。
- **真机（仅明确实机测试且当次授权）**：操作限 `adb shell cmd uimode night yes|no`、`adb shell settings put secure ui_night_mode 0|2` 及读取；**先记基线** `settings get secure ui_night_mode`、`cmd uimode night`、`ui_night_mode_override_on/off`，测完**还原**并如实报告；仅限深浅色，不放宽其他系统设置；能只读取证就不切换。

---

## 9. 磁盘清理

`scripts/clean-build-artifacts.sh`：默认只清 `build/` 一次性产物（零构建代价）；`--native` 额外清 node_modules 两个原生库 `build/`+`.cxx`（下次 assemble 重编 C++）；`--all` 额外清 `app/build/intermediates`+`app/.cxx`。NEVER 把 `gradle clean` 当日常手段；NEVER `rm -rf node_modules`（删除 `postinstall` 的 patch-package 产物）。`build/device-backup/` 属真机数据，任何清理跳过。ABI 已收敛 `reactNativeArchitectures=arm64-v8a,x86_64`，勿随意扩 32 位。

---

## 10. 提交与忽略

| 情形 | 动作 |
| :--- | :--- |
| 改 Room 实体/迁移 | **必须提交** `app/schemas/**` 对应 JSON，与代码同提交 |
| `local.properties` / `*.db*` / `device-backup/` / `build/` / `*.yanji-backup` / `*.backup.json` / 密钥库（`*.jks`/`*.keystore`/`*.pem`/`*.pfx`） | **绝不提交**（权威忽略清单以根 `.gitignore` 为准） |
| 日常 UI/业务改动 | §6 验证 + 模拟器测试；不碰真机；不跑备份/抓库 |
| 明确「实机测试」 | 仅覆盖安装+拉起主界面；禁一切调试操作（§7.2） |
| 任何 push 前 | §6 四项全过 |

**交付闭环**：① 不写外部知识库日志；② §6 门禁全过；③ 精准 `git add <file>`，禁 `git add .`/`git commit -a`；④ `git commit -m "<Conventional Commits 简讯>"`；⑤ `git push`（失败走 §5.5 代理）。临时报告写 `build/orca-reports/`。

---

## 11. 文档维护（更新本文时保留）

本文唯一入口，与其他手册冲突时以本文为准（代码与 ADR 除外）。易漂移事实改引用式（§5）。**任何更新不得删除**：架构原则、代理 7898 排查、mDNS 自动发现、ADB 同调用铁律、默认模拟器测试/真机按需推送/真机禁调试、模拟器无限制、系统深浅色三原则（§8）、§6 验证门禁、数据与计时安全（§3.5/§3.6/红线 3/4/5/7）、提交忽略与推送纪律（§10）。
