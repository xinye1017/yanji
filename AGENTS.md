# 「研迹」Coding Agent 指南 · 工程规范与真机推送工作流

> 本文档是接手的 **AI Coding Agent**（Claude、Codex、Gemini、Cursor、Cline、Copilot、Antigravity 等）的操作规范与指南。
> 记录项目的核心架构与工程原则、构建方式、设备连接机制、避坑指南与真机推送工作流。
>
> **开工顺序**：先读「§一 核心架构与工程原则」与「§三 Active Constraints」确立红线；开发前查阅「§二 核心机制与避坑铁律」；日常构建部署看「§五 标准构建与真机部署流程」；真机无线连接、配对与排错查阅 [ADB.md](file:///d:/yanji/ADB.md)。

---

## 一、核心架构与工程原则

### 当前运行架构：React Native + Kotlin（Yanji 2.0）

**默认按此架构开展工作，不再从历史 Compose 页面推断当前 UI 技术栈。** 只有任务涉及架构升级，或下列权威文件出现变化时，才重新核对相关层。截图与旧文档用于参考，运行代码和构建配置决定事实。

| 层 | 当前职责 | 权威入口 |
| :--- | :--- | :--- |
| UI | React Native + React + TypeScript，页面、组件、导航与视觉动画 | `package.json`、`App.tsx`、`src/screens/**`、`src/components/**`、`src/navigation/**` |
| JavaScript 引擎 | Hermes，由 Android 宿主加载 | `YanjiApplication.kt`、`gradle/libs.versions.toml` |
| 渲染架构 | Fabric / New Architecture，Bridgeless 宿主 | `gradle.properties`、`YanjiApplication.kt`、`MainActivity.kt` |
| 原生业务 | Kotlin：计时、通知、数据查询与业务写入；RN 通过桥接调用 | `app/src/main/java/com/example/yanji/bridge/**`、`src/bridge/**`、原生业务包 |
| 持久化 | Room 是唯一业务事实源；RN 不另建数据库或计时事实 | `app/src/main/java/com/example/yanji/data/db/YanjiDatabase.kt` |
| 当前 UI 主题 | RN 语义 Token 与主题 Provider；导航玻璃材质集中在独立 Token 中 | `src/theme/tokens.ts`、`src/theme/ThemeProvider.tsx`、`src/theme/liquidGlass.ts` |

React Native 与 React 的版本以 `package.json` 为准，原生构件版本以 Version Catalog 为准，避免在指南中复制易漂移数值。历史 Compose 页面及其材质实现不属于当前运行 UI，不能直接拿来修复 RN 页面。

**原生库接入方式**：本项目是 Android Add-to-App，`:app` 未应用 React Native app Gradle 插件；第三方库按既有流程手工链接。新增含 Fabric 组件的库需同步更新 `settings.gradle.kts`、`app/build.gradle.kts` 的依赖与 codegen 任务、`YanjiApplication.kt` 的 Package 注册，以及 `app/src/main/jni/{CMakeLists.txt,OnLoad.cpp}` 的 C++ 编译与组件描述符注册。不能仅安装 npm 包后就宣称接入完成。

**前端交付顺序**：先执行 `npm run typecheck` 与必要的针对性检查，再执行 `npm run bundle:android` 生成 APK 所用 JS bundle，最后执行 `gradlew.bat assembleDebug`。单独 Gradle 编译不会自动刷新 JS bundle；不得把旧 bundle 的安装误报为新 UI 已交付。

**原生构建并发**：Gradle worker 数由 `gradle.properties` 限制；各 Android 模块的 CMake/Ninja 编译任务池由根 `build.gradle.kts` 统一限制。不得为了提速覆盖这些限制，避免多 ABI 构建同时启动数十个 `clang++` 导致内存耗尽。

### 工程原则

- **不要为了保持向后兼容而妥协**。对于已经过时的实现路径，直接删除，而不是额外添加兼容层、回退方案（fallback）或迁移逻辑（migration）。
- **选择能够完整满足当前需求的最简单实现**。避免为了假想中的未来需求而提前设计抽象层、配置项或间接层。
- **以逐层迭代的方式构建系统**。先实现一个能够端到端正常工作的最小版本，然后在这个已经可用的产品上，一层一层增加新的能力。永远不要为了尚未完成的复杂设计，而牺牲一个已经能够正常工作的产品。
- **保持组件模块化，并清晰地进行关注点分离**。
- **如果成熟、维护良好的库能够降低整体复杂度或提高可靠性，优先使用这些现有库**。如果没有明确理由，不要自己重新实现已有的通用功能。
- **在自己编写实现或者添加新的依赖包之前，优先充分利用项目中已经存在的依赖**。在没有检查一个库的文档和类型定义之前，不要想当然地认为这个库不支持某项功能。
- **从长期角度做架构决策**。不要接受那种“现在暂时能用、以后再替换”的权宜之计。
- **在设计解决方案之前，先研究成熟产品是如何解决这个问题的**。优先采用这些产品已经验证过的设计模式和行业惯例，而不是从零开始重新发明一套方案。
- **修改完成后执行 Git 提交与推送（无需保存外部知识库日志）**：每次执行修改完成后，**不需要**向个人知识库记录工作日志；必须执行 `git commit` 与 `git push` 推送至远端仓库。提交时必须附带简略、概括性的提交信息（如遵循 Conventional Commits 风格，用一两句话清晰概括改动要点），且注意隔离无关在途修改。

---

## 二、核心机制与避坑铁律（必读 ⚠️）

> **Shell 环境声明**：本仓库 Agent 运行环境推荐为 **Git Bash (POSIX sh)**；PowerShell 命令仅适用于 Windows 本机手动交互执行。下文凡标注 `bash` / `powershell` 的示例，请按**当前会话实际 shell** 选择，不要混用两套语法。

### 1. 【最关键】ADB 发现与安装必须落在同一次工具调用内

- **底层原因**：在部分 Agent 执行环境中，每个工具命令调用运行在独立的临时作业对象里；单条命令结束后，后台子进程（包括 `adb.exe` 服务端守护进程）会被系统自动回收终止，拆分调用会导致连接建立中断或误报 `device not found`。
- **铁律**：**设备发现（`start-server` + 等待）与安装 / 启动必须写在同一个工具调用中。** 详细机理与多环境脚本参见 [ADB.md §一.1](file:///d:/yanji/ADB.md)。

### 2. 【最省心】优先 mDNS 自动发现，断连配对走标准流程

- **机制**：优先通过 Android 11+ TLS mDNS 动态广播发现设备序列号，不写死任何动态端口或 IP。
- **断连处理**：若网络变动或凭据过期，按 [ADB.md §二.2](file:///d:/yanji/ADB.md) 的标准 6 步法进行配对（`adb pair`）与主端口连接（`adb connect`）。

### 3. 【真机验证】允许截图，交互测试需用户授权

- **真机交付终点**：编译 APK → 单次推送安装（`install -r -d`）→ 调起主界面（`am start`）即宣告交付完成。
- **允许截图验证**：AI 可以使用 `screencap` 或测试框架截图检查布局与视觉效果，截图只保存到被 Git 忽略的 `build/` 目录。
- **交互授权**：未经用户授权，不得模拟点击、滑动或运行自动交互测试。用户明确要求真机测试时，可以在该任务范围内执行，必须保护已有数据和专注会话（详见 [ADB.md §一.2](file:///d:/yanji/ADB.md)）。

### 4. 【高频排错】网络代理与息屏休眠排查

- **代理拦截（最常见）**：本机 GitHub 访问依赖**代理端口 `7898`**；一旦开启全局/TUN 模式会拦截局域网 UDP 5353 组播导致 mDNS 发现失败。排查步骤详见 [ADB.md §三.1](file:///d:/yanji/ADB.md)。
- **页面休眠**：手机息屏或退到桌面后系统会将无线调试休眠并重置端口，调试时需保持在「无线调试」设置页面内。

### 5. 【编译提速】Gradle 极速构建与避坑原则（30 秒 vs 2 分钟）

- **痛点现象**：部分 Agent 或开发者构建耗时常常超过 2~3 分钟，甚至出现进程假死、超时无响应。
- **根因与避坑铁律**：
  1. **精准指定 Task，严禁盲目执行 `build`**：
     - 日常推送只需生成 Debug 安装包，执行 `assembleDebug`。
     - **严禁执行 `gradlew build`**：`build` 任务会触发完整的单元测试套件、全仓库 Lint 扫描、各构建变体校验，耗时漫长且容易因非阻塞性 warning 挂起或中断。
  2. **严禁日常构建盲目 `clean`**：
     - **切勿习惯性添加 `clean`**（如 `gradlew clean assembleDebug`）。
     - `clean` 会暴力清空 Gradle Configuration Cache、Kotlin/KSP 增量缓存及 DEX 产物，导致原本 30 秒的增量编译（如 31/38 任务 `UP-TO-DATE`）变成 2~3 分钟的 100% 全量冷编译。
     - 仅当修改了 Room Entity 实体需重新生成 Schema 或出现无法自愈的注解生成错乱时，才针对性 clean。
  3. **利用常驻 Gradle Daemon 与 Configuration Cache**：
     - 保留常驻 Gradle Daemon，**严禁添加 `--no-daemon`**。
     - 本仓库已启用 Configuration Cache，预热后无需重复解析 Gradle 依赖配置。
  4. **Windows 平台脚本后缀与执行方式**：
     - Windows 无论在 PowerShell 还是 Git Bash 下，均必须显式调用 `gradlew.bat`（PowerShell 下 `.\gradlew.bat assembleDebug`，Git Bash 下 `./gradlew.bat assembleDebug`）。
     - 若省略 `.bat` 直接调用无后缀的 `gradlew`，在 Windows 非 POSIX 环境下易被错误关联，甚至出现管道挂起假死。

### 6. 【铁律】严禁改动设备的系统浅色 / 深色模式

- **红线**：研迹的编译、推送、安装、启动、调试、真机验证全过程中，**严禁以任何方式修改用户手机的系统配色方案**（系统默认的浅色 / 深色模式）：
  - 禁 ADB 层切换：`adb shell cmd uimode night yes|no`、`adb shell settings put secure ui_night_mode …` 及同类 `settings`/`am` 命令一律不得执行；
  - 禁代码层切换：不得在应用中调用 `UiModeManager` / `setApplicationNightMode` / `setNightModeOverride` 等 API 写**系统级**模式（研迹主题只允许**读取**系统 uiMode 与应用内偏好 `YanjiThemeMode`，见 `theme/Theme.kt`）；
  - 禁要求用户为配合验证去改系统模式：深浅色观感问题一律由用户自行切换（优先用研迹应用内主题，无需动系统）后在对话中反馈，AI 依据用户描述的模式排查。
- **原因**：真机是用户日常使用设备，系统配色属于用户个人设置，不因任何调试目的而被 Agent 改动。

---

## 三、Active Constraints（红线清单 · 一页速查）

以下为**不可协商**的硬约束，违反即视为交付失败：

1. **保护在途修改与隔离**：开工前必须先执行 `git status --short`；未明确要求修改的已修改/在途文件一律不得随意改写、还原或清空，保持专注修改目标文件。
2. **禁破坏性 Git 操作**：禁止执行 `git checkout -- .`、`git reset --hard`、`git clean -fd`、`git stash`，严禁使用全仓库 formatter 覆盖无关文件。
3. **零假数据**：严禁在运行时注入任何虚假业务演示数据 / Mock；Room 是唯一业务持久化事实源，空表是合法状态（`scripts/check-runtime-fixtures.sh` 实行零容忍检查）。
4. **凭据零泄漏**：AI API Key 绝不写入 Room、SharedPreferences、日志、Prompt、测试 fixture 或备份；Keystore 异常时**拒绝降级为明文**（Fail-Closed）。
5. **迁移安全**：严禁 `fallbackToDestructiveMigration()`；迁移必须手写 `migrate(connection: SQLiteConnection)`；严禁 `ALTER TABLE DROP COLUMN`（兼容 Android 7.0 / SQLite < 3.35）；`app/schemas/**` 必须随 Entity 变更同步提交。
6. **UI 工程底线与设计自由**：品牌气质和页面信息架构参考 `DESIGN.md`，但不得把历史色值、固定字号、圆角半径、零边框、无玻璃效果等作为禁止创新的红线。UI Agent 可重设计整个视觉系统（颜色、字体、层级、圆角、材质、卡片、动画），但需统一使用和更新语义 Design Tokens，确保深浅色、触控目标、对比度与无障碍可用。视觉改造必须同步更新 `src/theme/**`、相应组件及其测试契约，避免硬编码四散。**系统级深浅色设置禁止改动**。现有 Compose `Modifier.hazeEffect` 若仍被使用，必须显式设置 `backgroundColor` 以规避已知崩溃；此条是技术安全约束，不是材质设计禁令。
7. **计时韧性**：计时物理事实必须基于单调物理时钟（`SystemClock.elapsedRealtime`）；前台常驻通知交系统 Chronometer 驱动，**严禁每秒 `notify()`**。
8. **事实不复制**：SDK / 版本 / 颜色 / 设备网络参数一律引用权威源（§四），不在文档硬编码。
9. **ADB 步骤单次调用完整执行**：执行 ADB 相关操作时必须整段放在同一次工具调用内（见 §二.1 与 §五），避免后台子进程回收导致断连。
10. **无证据不宣称通过**：严格区分「编译成功」「测试执行成功」「真机推送成功」；未运行项必须显式标注，环境故障不得伪装成产品缺陷。
11. **截图与真机交互授权**：日常推送以「APK 安装成功且主入口 Activity 正常拉起（无启动崩溃）」为交付终点；允许 AI 截图验证，截图保存到 `build/`。未经用户授权不得模拟操作真机；用户明确要求真机测试后，可执行任务内的自动交互测试，保护已有数据和专注会话。系统配色仍严禁改动。
12. **修改完成必须 Git 提交与推送**：任务修改完成并验证后，**不需要**在个人知识库记录工作日志；必须针对目标改动文件执行 `git commit` 与 `git push`，并在提交时附加简略、概括性的提交信息（严禁提交未要求修改的在途文件）。
13. **严禁改动设备系统配色**：编译 / 推送 / 调试 / 真机验证全程，严禁以任何方式（ADB `cmd uimode night`、`settings put ui_night_mode`、应用内 `UiModeManager` API、要求用户改系统模式等）修改真机系统的浅色 / 深色模式；研迹主题只读系统 uiMode 与应用内 `YanjiThemeMode`，深浅色验证由用户自行切换后反馈（见 §二.6）。

---

## 四、项目事实、环境与权威层次

### 4.1 Portable workflow（所有开发者 / CI）

* 所有脚本必须从 `scripts/` 自身位置推导仓库根目录，**不得依赖固定盘符或用户名**。
* Android SDK 通过 `ANDROID_HOME` / `ANDROID_SDK_ROOT` 或 `local.properties` 发现；ADB 默认从 `PATH` 查找，也可用 `ADB=/path/to/adb` 覆盖。
* `scripts/adb-push.sh` 支持 `YANJI_APK`、`YANJI_DEVICE_BACKUP_DIR`、`YANJI_PACKAGE` 覆盖默认值。
* 默认 APK 与设备备份路径分别是 `<repo>/app/build/outputs/apk/debug/app-debug.apk` 与 `<repo>/build/device-backup`。

### 4.2 事实的唯一来源（避免数值漂移）

**不要在本文档复制 SDK / 版本 / 颜色数值**，以免与代码漂移。以下为权威来源：

| 事实 | 权威来源 | 备注 |
| :--- | :--- | :--- |
| `compileSdk` / `minSdk` / `targetSdk` / `versionCode` / `versionName` | `app/build.gradle.kts`、`README.md` 顶部 `project-facts` 注释 | 由 `scripts/check-project-facts.sh` 在 CI 校验 |
| Room schema 版本与库版本 | `app/src/main/java/com/example/yanji/data/db/YanjiDatabase.kt`、`gradle/libs.versions.toml` | 同上由 CI 守卫 |
| 依赖库版本（Kotlin / Compose BOM / Room / KSP 等） | `gradle/libs.versions.toml` | 统一走 Version Catalog |
| 包名 / namespace / 主入口 Activity | `app/build.gradle.kts`、`app/src/main/AndroidManifest.xml` | — |
| 当前 React Native 设计 Token | `src/theme/tokens.ts`、`src/theme/ThemeProvider.tsx` | 本轮视觉重构允许设计评审后修改，并同步更新相关测试；Kotlin 原生 UI 仍参考其原生主题 |
| 设备网络参数（序列号 / IP / 端口） | mDNS 动态广播发现，或 `$ANDROID_SERIAL` | 动态过滤 `_adb-tls-connect` |
| 废弃功能（`QuickStartPreset`）行为 | `docs/adr/0001-retain-quick-start-compatibility-tombstone.md` | 兼容墓碑，禁止运行时读写 |

需要数值时按需读取，例如：

```bash
grep -E 'compileSdk|minSdk|targetSdk|versionName' app/build.gradle.kts
```

### 4.3 权威层次（冲突时的裁决顺序）

当不同文档互相矛盾时，按以下**由高到低**的顺序裁决：

```text
Level 0  机器执行守卫与代码现状（最高权威）
         Git 脏工作区 / app/build.gradle.kts / YanjiDatabase.kt /
         CI 守卫脚本（scripts/check-*.sh）/ verification-metadata.xml
Level 1  Agent 核心指南 —— AGENTS.md（本文）
Level 2  架构决策记录 —— docs/adr/**（分歧时无条件服从 ADR）、docs/audits/**
Level 3  项目门面与验证事实 —— README.md（受 check-project-facts.sh 保护的字段）
Level 4  当前运行 UI 的设计 Token 与组件代码 —— `src/theme/**`、`src/components/**`、`src/screens/**`；`DESIGN.md` 提供可迭代的创作方向，历史 Compose 主题仅作迁移参考
Level 5  长期沉淀事实 —— .workbuddy/memory/MEMORY.md（经验证的环境与平台坑）
Level 6  历史阶段性留档（仅供回溯，禁止作为改动依据）—— 开发手册/**、历史 Prompt 模板、旧重构报告
```

**裁决准则**：① 代码与构建配置永远拥有最高真实性；② ADR 具有裁决性法律地位，历史文档与 ADR 分歧时**无条件服从 ADR**；③ 禁止把历史 Prompt / 留档当作既定事实。

### 4.4 Owner-specific workflow（仓库作者当前设备，仅供参考）

以下路径、设备型号与无线调试提示**只描述作者机器**，不得复制进可执行脚本或 CI：

- **代码仓库**：`github.com/xinye1017/yanji`，分支 `main`，远端 `origin`
- **操作系统**：Windows（Git Bash / PowerShell）
- **项目路径**：`D:\yanji`
- **应用包名**：`com.example.yanji`；主入口 `com.example.yanji.MainActivity`
- **Android SDK 路径**：`C:\Users\dex\Android\Sdk`（配置于 `local.properties`）
- **ADB 工具路径**：`C:\Users\dex\Android\Sdk\platform-tools\adb.exe`（已配置进系统环境变量 `PATH`）
- **调试 APK 产物路径**：`app\build\outputs\apk\debug\app-debug.apk`
- **真机分辨率**：`PKB110` 逻辑分辨率为 **1256×2760**（**不是** 1080×2400），点击坐标以 `uiautomator dump` 结果为准
- **网络代理**：本机 GitHub 访问走代理端口 `7898`；该代理若全局接管会干扰 ADB mDNS（见 §二.4）

> **设备串 / IP / 端口一律不写入本文档**：连接参数每次随机漂移，一律通过 mDNS 动态发现取得（§二.2）。

---

## 五、标准构建与真机部署流程（日常两步走）

### 第一步：编译构建 APK

**原则**：严禁 `clean`，严禁 `build`；只执行 `assembleDebug` 利用增量与配置缓存，30 秒内完成构建（详见 §二.5）。

- **Bash (Git Bash，本仓库 Agent 默认)**：
  ```bash
  ./gradlew.bat assembleDebug
  ```
- **PowerShell (Windows 本机手动执行)**：
  ```powershell
  .\gradlew.bat assembleDebug
  ```

### 第二步：一键真机推送安装与启动

**要求：整条命令在同一次工具调用内执行，不得把发现与安装拆分。**

**Bash（Git Bash，本仓库 Agent 默认）**：

```bash
adb start-server >/dev/null 2>&1
try=0; until [ "$(adb devices | grep -c '_adb-tls-connect.*device')" -gt 0 ]; do
  try=$((try+1)); [ $try -ge 15 ] && break; sleep 3
done
dev="$(adb devices | awk '/_adb-tls-connect.*device/{print $1; exit}')"
if [ -z "$dev" ]; then
  echo "ERROR: 未发现 mDNS 无线设备，请检查代理 / VPN 与手机无线调试（见 §二.4）" >&2
  exit 1
fi
echo "Target Device: $dev"
adb -s "$dev" install -r -d "app/build/outputs/apk/debug/app-debug.apk"
adb -s "$dev" shell am start -n com.example.yanji/.MainActivity
```

**PowerShell（作者本机手动执行时）**：

```powershell
adb start-server; Start-Sleep -Seconds 2; $dev = (adb devices | Where-Object { $_ -match "adb-.*device" } | ForEach-Object { ($_ -split '\s+')[0] } | Select-Object -First 1); if (-not $dev) { Write-Error "未发现 mDNS 无线设备，请检查代理 / VPN 与手机无线调试（见 §二.4）"; exit 1 }; Write-Host "Target Device: $dev"; adb -s $dev install -r -d "app\build\outputs\apk\debug\app-debug.apk"; adb -s $dev shell am start -n com.example.yanji/.MainActivity
```

> **核心逻辑**：① 启动 ADB Server；② 等待约 2 秒让 mDNS 广播发现无线调试手机；③ 动态抓取当前在线 `adb-.*` 无线设备序列号（**为空则明确报错退出，不回退写死串**）；④ 保留本地用户数据覆盖安装（`-r -d`）；⑤ 立即调起主界面。
>
> ⚠️ **注意**：主界面拉起后即完成日常推送流程。允许截图检查；用户明确要求真机测试时，可继续执行任务范围内的验证，保护已有数据与专注会话。

---

## 六、真机无线调试与 ADB 工作流（权威规范见 ADB.md）

> ⚠️ **关于 ADB 无线调试全流程、断连配对 6 步法、网络代理（端口 7898）排错、常用调试指令表与真机交互红线，已全部迁移并整合至独立权威文档 [ADB.md](file:///d:/yanji/ADB.md)。**

### 核心纪律与准则摘要：
1. **同调用执行铁律**：`adb start-server`、设备发现、安装与启动必须写在**同一次工具调用内**执行，严禁拆分；
2. **交付终点**：编译 APK → 单次覆盖安装（`install -r -d`） → 调起主界面（`am start`）即宣告交付完成；
3. **截图与交互授权**：允许 AI 截图验证，截图保存到 `build/`；模拟操作与自动交互测试需用户明确授权，并保护已有数据与专注会话；
4. **配对与排错**：遇到搜不到设备、提示 `10061 积极拒绝` 或凭证失效时，严格遵循 [ADB.md §二.2](file:///d:/yanji/ADB.md) 的标准 6 步法处理。
5. **严禁改动设备系统配色**：推送与调试全程，严禁通过 ADB（`cmd uimode night` / `settings put ui_night_mode`）或代码改动真机系统的浅色 / 深色模式；深浅色验证由用户自行切换后反馈（见 §二.6）。

---

## 七、提交与忽略决策表

| 情形 | 动作 |
| :--- | :--- |
| 改动 Room 实体 / 迁移 | **必须提交** `app/schemas/**` 对应 JSON（迁移测试的校验依据），且与代码同一提交 |
| `local.properties` | **绝不提交**（含本机 SDK 路径） |
| `*.db` / `*.db-wal` / `*.db-shm` / `device-backup/` | **绝不提交**（真机数据，`.gitignore` 已覆盖） |
| `build/`（含真机备份、截图、临时报告） | **绝不提交**（`.gitignore` 已覆盖；临时报告统一写 `build/orca-reports/`） |
| `*.yanji-backup` / `*.backup.json` / 密钥库（`*.jks` / `*.keystore` / `*.pem` / `*.pfx`） | **绝不提交** |
| 日常 UI / 业务改动 | 编译 + 推送启动；**不跑**备份 / 抓库；允许截图，自动交互测试需用户授权 |
| 仅有的两个例外：改动 Room Schema 需验证迁移链 | 才先跑 `:app:testDebugUnitTest` 迁移测试，必要时才备份 |

> 权威忽略清单以仓库根 `.gitignore` 为准；本表为速查。

### 7.1 每次修改完成后的 Git 提交与推送闭环

每次任务执行修改完成后，需遵循以下交付闭环：
1. **无需外部知识库留档**：**不需要**保存工作日志到个人知识库。
2. **精准暂存目标文件**：严禁无脑 `git add .` 或 `git commit -a`；必须精准指定本次任务实际改动的文件（如 `git add <file>`），严格保护其他在途修改。
3. **附加简略概括的提交信息**：执行 `git commit -m "<简略概括的提交信息>"`，提交信息应简明扼要概括核心改动（如 `fix: 修复...`、`feat: 新增...`、`docs: 更新...`）。
4. **推送到远端**：执行 `git push` 推送至远端分支。

---

## 八、文档地图（权威层次）

| 目录 / 文件 | 用途 | 优先级 |
| :--- | :--- | :--- |
| `AGENTS.md`（本文） | Agent 核心指南与工程规范总入口 | **开工必读** |
| `ADB.md` | 真机无线调试、配对排错与 ADB 指令唯一权威指南 | **真机连接必读** |
| `OPPO流体云适配.md` | ColorOS 17 / OPPO 流体云深度适配与避坑指南 | **实时活动与流体云必读** |
| `README.md` | 产品 / 架构 / 构建门禁门面；`project-facts` 由 CI 校验 | 事实来源 |
| `docs/adr/**` | 架构决策记录（ADR） | **决策分歧时以 ADR 为准** |
| `docs/audits/**` | 安全 / CI / 后端审计报告 | 历史证据 |
| `DESIGN.md`、`docs/design/**`、`FrontEnd.md` | 设计与前端资料 | **以新版 `DESIGN.md` 为创作方向，其他旧版文档仅供回溯，不可将其固定参数当作新 UI 红线** |
| `docs/migration/**` | 仓库加固 / 迁移计划 | 规划参考 |
| `.workbuddy/memory/MEMORY.md` | 长期沉淀的环境与平台坑 | 经验事实（Level 5） |
| `开发手册/**` | 阶段性重构报告、技术栈、架构状态 | **历史留档（Level 6，禁止作为改动依据）**；`技术栈.md` / `架构与能力状态.md` 可能仍在 v11 阶段，以代码与 `gradle/libs.versions.toml` 为准 |
| `build/orca-reports/**` | 临时分析/设计报告（**不入库**） | 任务产物 |

---

## 九、文档维护原则

1. **唯一入口**：本文是仓库主要的 Agent 操作指南与工程规范；其他开发手册与本文冲突时以本文（除代码与 ADR 外）为准。
2. **修订原则**：只保留清晰可落地的规范与原则，不堆砌冗余报告；易漂移的事实一律改为引用式（§四）。
3. **保留清单**：任何更新都不得删除 —— 核心架构原则、代理端口 `7898` 网络排查、mDNS 自动发现、ADB 同次调用铁律、截图允许及交互授权原则、严禁改动设备系统深浅色模式、必要的数据与计时安全、提交 / 忽略与 Git 推送纪律。**不要将旧卡片风格、固定配色、零边框或历史圆角规格升级为不可变规范**。
