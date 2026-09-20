# 「研迹」Coding Agent 指南 · 工程规范与真机推送工作流

> 本文档是接手的 **AI Coding Agent**（Claude、Codex、Gemini、Cursor、Cline、Copilot、Antigravity 等）的操作规范与指南。
> 记录项目的核心架构与工程原则、构建方式、设备连接机制、避坑指南与真机推送工作流。
>
> **开工顺序**：先读「§一 核心架构与工程原则」与「§三 Active Constraints」确立红线；开发前查阅「§二 核心机制与避坑铁律」；需要构建部署与调试时看「§五 标准构建与真机部署流程」与「§七 真机工作流」。

---

## 一、核心架构与工程原则

- **不要为了保持向后兼容而妥协**。对于已经过时的实现路径，直接删除，而不是额外添加兼容层、回退方案（fallback）或迁移逻辑（migration）。
- **选择能够完整满足当前需求的最简单实现**。避免为了假想中的未来需求而提前设计抽象层、配置项或间接层。
- **以逐层迭代的方式构建系统**。先实现一个能够端到端正常工作的最小版本，然后在这个已经可用的产品上，一层一层增加新的能力。永远不要为了尚未完成的复杂设计，而牺牲一个已经能够正常工作的产品。
- **保持组件模块化，并清晰地进行关注点分离**。
- **如果成熟、维护良好的库能够降低整体复杂度或提高可靠性，优先使用这些现有库**。如果没有明确理由，不要自己重新实现已有的通用功能。
- **在自己编写实现或者添加新的依赖包之前，优先充分利用项目中已经存在的依赖**。在没有检查一个库的文档和类型定义之前，不要想当然地认为这个库不支持某项功能。
- **从长期角度做架构决策**。不要接受那种“现在暂时能用、以后再替换”的权宜之计。
- **在设计解决方案之前，先研究成熟产品是如何解决这个问题的**。优先采用这些产品已经验证过的设计模式和行业惯例，而不是从零开始重新发明一套方案。

---

## 二、核心机制与避坑铁律（必读 ⚠️）

> **Shell 环境声明**：本仓库 Agent 运行环境推荐为 **Git Bash (POSIX sh)**；PowerShell 命令仅适用于 Windows 本机手动交互执行。下文凡标注 `bash` / `powershell` 的示例，请按**当前会话实际 shell** 选择，不要混用两套语法。

### 1. 【最关键】ADB 发现与安装必须落在同一次工具调用内

- **现象**：在部分 Agent 执行环境中，每个工具命令调用运行在**独立的临时作业对象**里；单条命令结束后，后台子进程（包括 `adb.exe` 服务端守护进程）会被系统自动回收终止。
- **后果**：如果把 `adb start-server`、`adb devices`、`adb install` 拆到多次调用执行，每次都会重新看到 `* daemon not running; starting now at tcp:5037`，mDNS 根本来不及建立连接，甚至误报 `device not found`。
- **铁律**：**设备发现（`start-server` + 等待）与安装 / 启动必须写在同一个工具调用中。** 在该调用内，按你当前 shell 的语法串联步骤（bash 用换行或 `&&` / `;`，PowerShell 用 `;`），不要把发现与安装拆成两次调用。

> 若你的执行环境**不是**「每命令独立作业」模型（例如交互式常驻 shell），上述限制不成立；但「一次调用内完成发现 + 安装」仍是推荐做法，可避免设备掉线导致的假失败。

### 2. 【最省心】使用 mDNS 自动发现，绝不手动问端口或写死 IP

- **现象**：手机无线调试端口在每次进入界面或重开开关时**随机变动**，局域网 IP 也会随 DHCP 变动。
- **解法**：Android 11+ 无线调试自带 TLS mDNS 广播。只要手机与电脑在同一 Wi-Fi 且开着无线调试，`adb start-server` 后等待约 2 秒，ADB 就会自动发现形如 `adb-<SERIAL>-<HASH>._adb-tls-connect._tcp` 的设备序列号。
- **铁律**：**直接使用 mDNS 动态发现的设备串，不要向用户索要 5 位端口号，也不要用 `adb connect <ip>:<port>` 去碰运气。** 若动态发现为空，应**显式报错并停止**，而不是回退到某个写死的设备串（**文档与脚本中不得出现任何写死的序列号 / IP / 端口**）。

### 3. 【最效率】日常功能开发不要去跑备份、抓数据或自行截屏验证

- **陷阱 1（冗余备份与查库）**：曾见 Agent 每次改代码后都去跑复杂备份脚本、拉取 SQLite、用 `sqlite3` 检查 `user_version`、检查 `no_backup` 凭据——严重浪费时间且极易因 Windows 路径格式或环境缺失中断。
- **陷阱 2（自行截屏验证）**：曾见 Agent 推送后自行执行 `adb screencap` 导出截图甚至做交叉视觉比对——不仅消耗大量时间和 Token，还会打扰用户真机使用。
- **正道**：
  - **绝大多数日常 UI / 功能 / 业务逻辑修改**：只需编译 APK → 单次推送安装 → 调起主界面即可完成交付。**UI 与视觉体验由用户在真机上亲自查看，AI 严禁自行截屏验证**（除非用户在对话中明确要求截屏）。
  - **只有且仅有改动了数据库实体（Room Schema）并需验证迁移链时**：才先跑 `.\gradlew.bat :app:testDebugUnitTest` 验证迁移测试，必要时才备份数据。

### 4. 【高频排错】为什么有时设备搜不到？

- **排查 1：电脑或手机是否开启了 VPN / 科学上网 / 本地代理软件？**（最常见原因！）
  VPN 或本地代理会接管网卡路由并拦截局域网 UDP 5353 组播流量，导致 mDNS 发现失败。
  本项目的 GitHub 访问依赖**本机代理端口 `7898`**；一旦该代理进程处于全局 / TUN 模式，它会同时拦截 5353 组播，**表现为「设备忽然搜不到」，而根因其实在代理**。
  搜不到设备时，**第一时间确认代理 / VPN 是否处于全局接管状态**，必要时临时关闭或改为「仅代理指定域名」后再重试。
- **排查 2：手机是否息屏或切出了设置界面？**
  国产系统（OPPO ColorOS 等）为省电，息屏或退到桌面后可能将无线调试休眠。请提醒用户解锁手机并保持在「无线调试」设置页面。

---

## 三、Active Constraints（红线清单 · 一页速查）

以下为**不可协商**的硬约束，违反即视为交付失败：

1. **保护在途修改与隔离**：开工前必须先执行 `git status --short`；未明确要求修改的已修改/在途文件一律不得随意改写、还原或清空，保持专注修改目标文件。
2. **禁破坏性 Git 操作**：禁止执行 `git checkout -- .`、`git reset --hard`、`git clean -fd`、`git stash`，严禁使用全仓库 formatter 覆盖无关文件。
3. **零假数据**：严禁在运行时注入任何虚假业务演示数据 / Mock；Room 是唯一业务持久化事实源，空表是合法状态（`scripts/check-runtime-fixtures.sh` 实行零容忍检查）。
4. **凭据零泄漏**：AI API Key 绝不写入 Room、SharedPreferences、日志、Prompt、测试 fixture 或备份；Keystore 异常时**拒绝降级为明文**（Fail-Closed）。
5. **迁移安全**：严禁 `fallbackToDestructiveMigration()`；迁移必须手写 `migrate(connection: SQLiteConnection)`；严禁 `ALTER TABLE DROP COLUMN`（兼容 Android 7.0 / SQLite < 3.35）；`app/schemas/**` 必须随 Entity 变更同步提交。
6. **UI 设计红线**：`ui/` 层 0 容忍裸 `Color(0x...)` 字面量与静态亮色 Token；裸圆角受棘轮上限约束；暗色背景用 Midnight Blue 系列，**禁止纯黑 `#000000`**；`Modifier.hazeEffect` 必须显式设 `backgroundColor` 垫底（否则真机启动崩溃）。
7. **计时韧性**：计时物理事实必须基于单调物理时钟（`SystemClock.elapsedRealtime`）；前台常驻通知交系统 Chronometer 驱动，**严禁每秒 `notify()`**。
8. **事实不复制**：SDK / 版本 / 颜色 / 设备网络参数一律引用权威源（§四），不在文档硬编码。
9. **ADB 步骤单次调用完整执行**：执行 ADB 相关操作时必须整段放在同一次工具调用内（见 §二.1 与 §五），避免后台子进程回收导致断连。
10. **无证据不宣称通过**：严格区分「编译成功」「测试执行成功」「真机推送成功」；未运行项必须显式标注，环境故障不得伪装成产品缺陷。
11. **严禁自行截屏验证**：真机验证以「APK 安装成功且主入口 Activity 正常拉起（无启动崩溃）」为交付终点。**严禁 AI 自行使用 `screencap` 抓取屏幕截图或跑深浅色交叉比对**；视觉呈现与交互效果由用户在真机上肉眼确认。除非用户在当前提示中明确要求截屏，否则绝不主动执行截屏。

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
| 设计 Token 颜色数值 | `app/src/main/java/com/example/yanji/theme/**` | 只引用语义 Token 名，不复制 Hex |
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
Level 4  设计系统活体代码 —— app/src/main/java/.../theme/*.kt 优先于 DESIGN.md / FrontEnd.md / docs/design/
Level 5  长期沉淀事实 —— .workbuddy/memory/MEMORY.md（经验证的环境与平台坑）
Level 6  历史阶段性留档（仅供回溯，禁止作为改动依据）—— 开发手册/**、历史 Prompt 模板、旧重构报告
```

**裁决准则**：① 代码与构建配置永远拥有最高真实性；② ADR 具有裁决性法律地位，历史文档与 ADR 分歧时**无条件服从 ADR**；③ 禁止把历史 Prompt / 留档当作既定事实。

### 4.4 Owner-specific workflow（仓库作者当前设备，仅供参考）

以下路径、设备型号与无线调试提示**只描述作者机器**，不得复制进可执行脚本或 CI：

- **代码仓库**：`github.com/xinye1017/yanji`，分支 `main`，远端 `origin`
- **操作系统**：Windows（Git Bash / PowerShell）
- **项目路径**：`D:\AI项目\yanji`
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

在项目根目录下执行（Git Bash 也请使用 `./gradlew.bat`；若遇 `ClassNotFoundException: GradleWrapperMain` 则改用 `.bat`）：

```bash
./gradlew.bat assembleDebug
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
> ⚠️ **注意**：主界面拉起后即完成推送流程，**不要继续自行截屏验证**，直接将界面留给用户在真机上体验与确认。

---

## 六、异常排查与备用方案

### 1. 搜不到设备时的快速检查清单

1. 确认电脑与手机连接在**同一个 Wi-Fi**（5G / 2.4G 局域网互通）；
2. 确认电脑和手机均**关闭了 VPN / 科学上网 / 全局代理**——特别检查本机代理端口 `7898` 是否处于全局或 TUN 接管模式（见 §二.4）；
3. 确认手机亮屏，处于「设置 → 系统与更新 → 开发者选项 → 无线调试」页面内。

### 2. 重新配对（首次使用或凭证失效时）

若设备列表出现 `offline` 或未授权，需要重新配对：

1. 打开手机「无线调试 → 使用配对码配对设备」；
2. 记下弹出的 **IP、5 位配对端口、6 位配对码**（**每次随机，用完即弃，不写入任何脚本或文档**）；
3. 运行配对命令（**用真实值替换 `<ip>`、`<pair_port>`、`<code>`**）：
   ```bash
   adb pair <ip>:<pair_port> <code>
   ```
4. 配对成功后关闭弹窗，返回主页面继续第五节第二步的推送流程。

### 3. USB 数据线备用方案（固定端口）

如果局域网多播隔离严重导致 mDNS 无法工作：

1. 用 USB 数据线连接手机并开启「USB 调试」；
2. 固定无线调试端口（示例端口仅为约定值，可按需替换）：
   ```bash
   adb tcpip <port>
   ```
3. 拔掉数据线，之后只要手机处于同一 Wi-Fi，即可用**手机当前 IP**（以系统设置或 `adb devices -l` 为准）连接：
   ```bash
   adb connect <ip>:<port>
   ```

---

## 七、真机工作流：常用调试与 UI 定位（严禁自行截屏）

### 1. 常用调试与验证指令

| 任务 | 命令行 |
| :--- | :--- |
| **查看当前连接设备** | `adb devices -l` |
| **检查应用是否在运行** | `adb shell pidof com.example.yanji` |
| **启动应用** | `adb shell am start -n com.example.yanji/.MainActivity` |
| **强制停止应用** | `adb shell am force-stop com.example.yanji` |
| **查看应用实时崩溃与异常** | `adb logcat *:E` |
| **查看特定业务日志** | `adb logcat -s FocusTimer:V RoomDatabase:V StudyStats:V` |
| **清除应用所有本地数据（慎用）** | `adb shell pm clear com.example.yanji` |
| **屏幕截图导出（仅限用户要求）** | `adb exec-out screencap -p > screenshot.png` |

> 执行上述命令时同样遵守 §二.1：若环境会回收子进程，请把相关步骤放在同一次调用内。

### 2. 设备会间歇性掉线 —— 必须带等待重试

无线调试的 mDNS 广播并不稳定（实测同一轮操作中会先 `device`、随后 `no devices/emulators found`），且 `adb` server 可能随每次工具调用被回收。**等待设备上线的循环与安装/启动写在同一次调用内**，见 §五 第二步的 Bash 块（`adb start-server` → 轮询 `_adb-tls-connect.*device` → 为空则报错退出）。

确认在线后再执行 `install` / `shell` / `pull`。安装 debug APK 走无线约需十几秒，不要因为前一条命令报 `not found` 就判定失败——重新发现设备后重试即可。

### 3. 定位控件：uiautomator dump + 坐标点击

`uiautomator dump` 的 XML 是**单行**的，因此 `grep '…' | grep -o 'bounds="…"'` 会把整个文件的 bounds 全部抓出、无法与控件配对。用 `scripts/dump_ui.py` 解析最省事：

```bash
adb shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1
adb pull /sdcard/ui.xml build/ui.xml
python scripts/dump_ui.py build/ui.xml          # 输出「可见文本 / 描述 -> 中心坐标」
adb shell input tap <x> <y>
```

- dump **只包含当前视口内**的元素；目标不在屏内时先滚动再 dump：
  `adb shell input swipe <x1> <y1> <x2> <y2> <duration_ms>`
- Compose 的底部 Tab 走 `content-desc`（如 `我的`、`统计`），正文走 `text`。
- 真机逻辑分辨率见 §4.4 Owner-specific 小节；点击坐标按 dump 结果来，不要凭机型假设。

### 4. 截图（仅限用户明确要求时，严禁自主执行）

**铁律：AI 绝对不要在真机部署后自行截屏验证。** 真实 UI 渲染与使用体验完全由用户在真机上直观确认。

仅当**用户明确在提示词中指示截屏**（例如「帮我截个图」、「截屏看看效果」）时，才允许执行截图导出：

优先 `screencap` 落盘再 `pull`（`exec-out … > file.png` 在 Git Bash 下可能被改动行尾）：

```bash
adb shell screencap -p /sdcard/s.png && adb pull /sdcard/s.png build/s.png
```

截系统栏时注意：**系统提示横幅（如「已连接到无线调试」）会盖住状态栏**，等几秒让横幅消失再截。

### 5. 主题与深色模式：严禁自行跑深浅色截图比对

- **历史做法纠偏**：旧规范曾要求通过脚本强制切换系统深色模式并截屏交叉比对，已被证明严重浪费时间、消耗上下文并干扰真机正常使用。
- **现行规范**：改动涉及**系统栏、主题、暗色模式**等视觉效果时，AI 确保代码符合设计规范、编译通过并推送到手机启动即可。**严禁 AI 自行执行 `cmd uimode night` 切换系统模式并跑截图比对**。
- **用户主导验证**：深浅色切换及视觉效果由用户在真机上操作核验；若用户明确要求截取深浅色截图对比，才可在用户要求下配合执行。

---

## 八、提交与忽略决策表

| 情形 | 动作 |
| :--- | :--- |
| 改动 Room 实体 / 迁移 | **必须提交** `app/schemas/**` 对应 JSON（迁移测试的校验依据），且与代码同一提交 |
| `local.properties` | **绝不提交**（含本机 SDK 路径） |
| `*.db` / `*.db-wal` / `*.db-shm` / `device-backup/` | **绝不提交**（真机数据，`.gitignore` 已覆盖） |
| `build/`（含真机备份、截图、临时报告） | **绝不提交**（`.gitignore` 已覆盖；临时报告统一写 `build/orca-reports/`） |
| `*.yanji-backup` / `*.backup.json` / 密钥库（`*.jks` / `*.keystore` / `*.pem` / `*.pfx`） | **绝不提交** |
| 日常 UI / 业务改动 | 只编译 + 推送启动；**不跑**备份 / 抓库，**不自行截屏** |
| 仅有的两个例外：改动 Room Schema 需验证迁移链 | 才先跑 `:app:testDebugUnitTest` 迁移测试，必要时才备份 |

> 权威忽略清单以仓库根 `.gitignore` 为准；本表为速查。

---

## 九、文档地图（权威层次）

| 目录 / 文件 | 用途 | 优先级 |
| :--- | :--- | :--- |
| `AGENTS.md`（本文） | Agent 核心指南与真机工作流入口 | **开工必读** |
| `README.md` | 产品 / 架构 / 构建门禁门面；`project-facts` 由 CI 校验 | 事实来源 |
| `docs/adr/**` | 架构决策记录（ADR） | **决策分歧时以 ADR 为准** |
| `docs/audits/**` | 安全 / CI / 后端审计报告 | 历史证据 |
| `docs/design/**`、`DESIGN.md`、`FrontEnd.md` | 设计系统与前端规范 | UI 参考（注意：暗色口径以活体 theme 代码为准） |
| `docs/migration/**` | 仓库加固 / 迁移计划 | 规划参考 |
| `.workbuddy/memory/MEMORY.md` | 长期沉淀的环境与平台坑 | 经验事实（Level 5） |
| `开发手册/**` | 阶段性重构报告、技术栈、架构状态 | **历史留档（Level 6，禁止作为改动依据）**；`技术栈.md` / `架构与能力状态.md` 可能仍在 v11 阶段，以代码与 `gradle/libs.versions.toml` 为准 |
| `build/orca-reports/**` | 临时分析/设计报告（**不入库**） | 任务产物 |

---

## 十、文档维护原则

1. **唯一入口**：本文是仓库主要的 Agent 操作指南与工程规范；其他开发手册与本文冲突时以本文（除代码与 ADR 外）为准。
2. **修订原则**：只保留清晰可落地的规范与原则，不堆砌冗余报告；易漂移的事实一律改为引用式（§四）。
3. **保留清单**：任何更新都不得删除 —— 核心架构原则、代理端口 `7898` 网络排查、mDNS 自动发现、ADB 同次调用铁律、严禁自行截屏验证原则、提交 / 忽略纪律。
