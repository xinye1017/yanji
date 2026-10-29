# 「研迹」ADB 模拟器测试与真机按需推送工作流指南

> 本文档是「研迹」项目针对 **Android 模拟器（默认测试环境）** 与 **真机无线调试（仅用户明确要求「实机测试」时）** 的设备部署、连接排错与实操经验的**唯一权威指南**。
> 日常开发、构建验证、UI 检查、交互测试与截图一律在模拟器完成（模拟器无测试限制）；真机只在用户明确要求实机测试时推送，手机端只允许「覆盖安装 + 调起主界面」，**禁止截图等任何调试操作**。
> 与 `AGENTS.md` §二.3 / §三 Active Constraints 冲突时，以 `AGENTS.md` 为准。

---

## 一、核心原则与不可逾越的红线（铁律 ⚠️）

### 1. 【最关键】ADB 发现与安装必须落在同一次工具调用内
- **底层机理**：在大部分 AI Agent 执行环境中，每个工具命令调用运行在**独立的临时作业对象**（Job Object）里；单条命令结束后，后台子进程（包括 `adb.exe` 服务端守护进程）会被系统自动终止与回收。
- **后果**：如果把 `adb start-server`、`adb devices`、`adb install` 拆到多次调用执行，每次都会重新启动 adb server，导致设备来不及上线，甚至误报 `device not found`。
- **铁律**：**设备发现（`start-server` + 等待）、连接校验与安装 / 启动必须写在同一个工具调用中。**
  - **PowerShell**：使用分号 `;` 串联整段命令；
  - **Git Bash**：使用 `&&` 或 `;` 串联整段脚本。

### 2. 【默认模拟器 / 真机按需】模拟器无测试限制，真机禁止调试操作
- **默认测试环境**：所有开发、构建验证、UI 检查、交互测试与截图默认在 **Android 模拟器**（`emulator-5554`）上完成。
- **模拟器无测试限制**：可自由截图、录屏、`uiautomator dump`、模拟点击 / 滑动、运行自动交互测试、修改系统设置、切换深浅色、创建 / 清理测试数据；截图仍只保存到被 Git 忽略的 `build/`。模拟器是独立测试环境，测试数据只在模拟器内创建，不得注入真机或产品运行时。
- **真机触发条件**：只有用户在当前任务中**明确说出「实机测试」**（或同义的明确指令）时，才允许把 APK 推送到手机。日常 UI / 业务改动**不得**默认推送真机，也不得把真机当作日常测试环境。
- **真机交付终点**：`install -r -d` 覆盖安装 + `am start` 调起主界面即完成本次实机交付。
- **真机操作红线**：即使用户要求实机测试，手机端也**只允许**「覆盖安装 → 调起主界面」这一交付动作。**禁止**在真实手机上截图、录屏、`uiautomator dump`、模拟点击 / 滑动、修改系统设置（深浅色、动画、权限、电池优化等）、抓取 / 导出数据库、备份、清数据或任何其他调试操作；如确需其中某项，必须由用户在该次请求中**逐项明确授权**。
- **数据保护**：任何真机操作都必须保护已有数据与进行中的专注会话。
- **系统深浅色**：产品代码不得写系统级深浅色；模拟器可自由切换验证；真机仅在明确实机测试且授权时临时切换，**先记基线、测完还原**（详见 `AGENTS.md` §二.6）。

### 3. 【无假数据与安全守卫】
- 严禁为了测试向设备注入任何 Mock 虚假数据；
- 严禁拉取或导出含有敏感凭据（如 AI API Key）的本地配置文件；
- 日常推送严禁盲目执行 `pm clear`，必须保留本地用户数据（使用 `install -r -d` 覆盖安装）。

---

## 二、模拟器默认环境与真机无线连接

### 2.0 模拟器（默认测试环境）

#### 创建 AVD
```bash
sdkmanager "system-images;android-35;google_apis;x86_64"
avdmanager create avd -n DiaryPhone -k "system-images;android-35;google_apis;x86_64" -d pixel_6
```
- `sdkmanager` / `avdmanager` 位于 `$ANDROID_HOME/cmdline-tools/latest/bin/`。
- x86_64 镜像在 Windows 上**必须有硬件加速**，否则无法启动（见下）。

#### 硬件加速（AEHD）
```bash
sdkmanager "extras;google;Android_Emulator_Hypervisor_Driver"
# 以管理员权限执行：
"$ANDROID_HOME/extras/google/Android_Emulator_Hypervisor_Driver/silent_install.bat"
emulator-check accel
```
- 成功输出：`AEHD (version 2.2) is installed and usable.`
- 未安装时报 `x86_64 emulation currently requires hardware acceleration!`；**不要**用 `-accel off` 绕过（会挂起或段错误）。

#### 启动模拟器
```bash
"$ANDROID_HOME/emulator/emulator.exe" -avd DiaryPhone \
  -no-window -no-audio -no-boot-anim \
  -no-snapshot-load -no-snapshot-save \
  -gpu host -cores 6 -memory 4096
```
- **`-no-window`**：本机 emulator 包只提供无头 QEMU 可执行文件，带窗口启动会报 `can't find the emulator executable`；画面用 scrcpy 投屏（见下）。
- **`-gpu host`**：走宿主 GPU；`swiftshader_indirect` 只有约 16–20 fps。
- **`-no-snapshot-load`**：避免陈旧 Quick Boot 快照导致设备长时间 `offline`。
- 启动后等待开机完成：
  ```bash
  adb wait-for-device shell getprop sys.boot_completed   # 返回 1 即开机完成
  ```

#### 720p 渲染与动画收敛（提升帧率）
```bash
adb -s emulator-5554 shell wm size 720x1600
adb -s emulator-5554 shell wm density 320
adb -s emulator-5554 shell settings put global window_animation_scale 0
adb -s emulator-5554 shell settings put global transition_animation_scale 0
adb -s emulator-5554 shell settings put global animator_duration_scale 0
```
- 720p + host GPU + 6 核实测约 30 fps；继续降到 540p 无明显收益，不必再降。

#### scrcpy 投屏（模拟器画面）
```bash
winget install --id Genymobile.scrcpy -e
scrcpy -s emulator-5554 --window-title "Yanji Emulator" --stay-awake \
  --max-fps=60 --max-size=720 --video-bit-rate=8M --no-audio
```

#### 模拟器特性
- 通过本地回环 `emulator-5554` 连接，**不受**网络代理、mDNS 组播、息屏休眠影响（见 §三）。
- 安装 / 启动与真机相同，使用 `install -r -d` 保留数据。
- 模拟器上**无测试限制**：截图、录屏、交互、自动测试、改设置、切深浅色、建 / 清测试数据均可自由执行。

---

> **以下 §2.1–§2.3 无线连接流程仅在用户明确要求「实机测试」时使用。日常任务不得执行。**

### 2.1 模式 A：mDNS 动态自动发现（最省心模式）
Android 11+ 原生支持基于 TLS 的 mDNS 广播。只要手机与电脑在同一 Wi-Fi，且手机未休眠无线调试：
1. 运行 `adb start-server` 并等待 2~3 秒；
2. ADB 会自动嗅探并注册形如 `adb-<SERIAL>-<HASH>._adb-tls-connect._tcp` 的设备序列号；
3. 直接使用该动态设备号执行安装，无需手动连接 IP 与端口。

---

### 2.2 模式 B：配对与手动连接（断连、凭证过期或切换网络时的标准 6 步法）

当由于网络变动、代理干扰导致 mDNS 无法发现设备，或设备状态呈现 `offline` / `unauthorized` 时，按以下经过实机验证的标准 6 步流程操作：

#### 步骤 1：打开手机无线调试
- 进入手机：**「设置 → 系统与更新 → 开发者选项 → 无线调试」**；
- 确保无线调试开关处于**开启**状态。

#### 步骤 2：获取配对参数
- 点击页面内的 **「使用配对码配对设备」** 弹窗；
- 记下弹出的：
  - **WLAN IP 地址**（例如 `192.168.0.103`）；
  - **5 位临时配对端口**（例如 `33013`，注意：每次弹出都随机变动）；
  - **6 位配对码**（例如 `369078`）。

#### 步骤 3：在终端执行配对命令
执行命令（用真实 IP、配对端口和配对码替换）：
```powershell
adb pair 192.168.0.103:33013 369078
```
成功后控制台将输出：
`Successfully paired to 192.168.0.103:33013 [guid=adb-...]`

#### 步骤 4：关闭手机配对弹窗，返回无线调试主界面
- **关键操作**：配对成功后，**必须在手机上点击关闭/确定关闭配对弹窗**，返回到「无线调试」主页面。

#### 步骤 5：读取主调试端口（避坑重点 ⚠️）
- **核心认知**：步骤 2 弹出的配对端口（如 `33013`）**仅供配对握手一次性使用**，并非后续 ADB 连接的调试端口！
- 在「无线调试」主页面中，查看 **“IP 地址和端口”** 标签下显示的 **另一个 5 位端口号**（例如 `192.168.0.103:38201`，此处的 `38201` 才是真实调试端口）。

#### 步骤 6：执行正式连接与双向通信验证
执行连接并在同一次调用中校验状态：
```powershell
adb connect 192.168.0.103:38201; Start-Sleep -Seconds 1; adb devices -l
```
若控制台显示：
`connected to 192.168.0.103:38201`
且设备状态为 `device`，即代表双向加密调试通道已完全建立！

---

### 2.3 模式 C：USB 数据线快速转接备用方案
若局域网多播隔离严重导致无线广播受阻：
1. 用 USB 数据线将手机连接电脑并允许 USB 调试；
2. 执行端口固定：
   ```powershell
   adb tcpip 5555
   ```
3. 拔出 USB 数据线，直接无线连接：
   ```powershell
   adb connect <手机IP>:5555
   ```

---

## 三、高频异常排错与避坑指南

> 本节只针对**真机无线调试**；模拟器通过本地回环连接，不受代理、mDNS 与息屏影响。

### 1. 为什么忽然搜不到设备 / mDNS 广播丢失？
- **排查 1（最常见！）：电脑或手机是否开启了科学上网/代理软件？**
  - **机理**：代理软件（Clash Verge、Mihomo、v2rayN 等）开启 **TUN 虚拟网卡模式** 或 **全局接管** 时，会无差别接管网卡路由并**阻断局域网 UDP 5353 组播流量**，直接导致 mDNS 组播发现机制失效。
  - **经验**：本机的 GitHub 访问常走本地代理端口 `7898`。一旦发现 mDNS 搜不到设备，**第一时间检查代理软件是否开启了 TUN 模式**，临时关闭 TUN 或将局域网段设为直连。
- **排查 2：手机是否息屏或退出了设置页面？**
  - **机理**：国内定制系统（OPPO ColorOS 等）出于极致省电考虑，只要手机息屏或切出「无线调试」页面，后台系统便会将无线调试服务置入休眠状态，甚至直接关闭端口。
  - **经验**：在调试和推送期间，**务必提醒用户解锁手机并停留在「无线调试」页面内**。

### 2. 报错 `由于目标计算机积极拒绝，无法连接。 (10061)`
- **原因**：目标 IP 上对应的 5 位调试端口已经失效（手机重开了无线调试、或者重新进入过页面导致系统重新分配了新的随机端口）。
- **解法**：查看手机「无线调试」主页面上当前显示的“IP 地址和端口”，使用更新后的最新端口号执行 `adb connect`。

### 3. 为什么设备连上几秒后又报 `not found`？
- **原因**：工具调用被拆成了多条命令，上一条结束后 ADB Server 随 Agent 进程池生命周期被回收，造成“伪掉线”。
- **解法**：严格遵守 §一.1，将 `adb start-server`、等待、`devices` 过滤、`install` 与 `am start` 写在同一个工具调用中。

### 4. 模拟器启动即退出 / 长时间 `offline`
- `x86_64 emulation currently requires hardware acceleration!` → 安装 AEHD（§2.0）。
- `can't find the emulator executable` → 缺少带窗口的 emulator 二进制，改用 `-no-window` + scrcpy。
- 长时间 `offline`、QEMU CPU 时间几乎为 0 → 陈旧快照，加 `-no-snapshot-load` 重启。

---

## 四、极速构建与部署工作流（默认模拟器）

### 第一步：极速构建 Debug APK（30 秒增量）
- **铁律**：严禁日常执行 `clean`（会清空缓存导致 3 分钟全量冷编译）；严禁执行 `build`（会触发全量扫描与测试）。
- **只执行**：
  - **PowerShell**：`.\gradlew.bat assembleDebug`
  - **Git Bash**：`./gradlew.bat assembleDebug`

---

### 第二步：安装到模拟器并启动（默认，所有日常测试；单次调用完整执行）

#### PowerShell
```powershell
adb start-server; Start-Sleep -Seconds 2; $dev = (adb devices | Where-Object { $_ -match "^emulator-\d+\s+device" } | ForEach-Object { ($_ -split '\s+')[0] } | Select-Object -First 1); if (-not $dev) { Write-Error "未发现运行中的 Android 模拟器，请先按 ADB.md §2.0 启动 AVD"; exit 1 }; Write-Host "Target Emulator: $dev"; adb -s $dev install -r -d "app\build\outputs\apk\debug\app-debug.apk"; adb -s $dev shell am start -n com.example.yanji/.MainActivity
```

#### Git Bash
```bash
adb start-server >/dev/null 2>&1
dev="$(adb devices | awk '/^emulator-[0-9]+[[:space:]]+device$/{print $1; exit}')"
if [ -z "$dev" ]; then
  echo "ERROR: 未发现运行中的 Android 模拟器，请先按 ADB.md §2.0 启动 AVD" >&2
  exit 1
fi
echo "Target Emulator: $dev"
adb -s "$dev" install -r -d "app/build/outputs/apk/debug/app-debug.apk"
adb -s "$dev" shell am start -n com.example.yanji/.MainActivity
```

> ✅ 模拟器安装启动后可自由截图、录屏、模拟点击 / 滑动、运行自动交互测试、修改系统设置、切换深浅色、创建 / 清理测试数据；截图保存到 `build/`。

---

### 第三步（条件执行）：仅在用户明确要求「实机测试」时推送到手机

> ⚠️ **日常任务不得执行本节**。只有用户在当前任务中明确说出「实机测试」或同义指令时才允许推送手机。

#### PowerShell
```powershell
adb start-server; Start-Sleep -Seconds 2; $dev = (adb devices | Where-Object { $_ -match "adb-.*device" } | ForEach-Object { ($_ -split '\s+')[0] } | Select-Object -First 1); if (-not $dev) { Write-Error "未发现 mDNS 无线设备，请检查代理 / VPN 与手机无线调试（见 §三.1）"; exit 1 }; Write-Host "Target Device: $dev"; adb -s $dev install -r -d "app\build\outputs\apk\debug\app-debug.apk"; adb -s $dev shell am start -n com.example.yanji/.MainActivity
```

#### Git Bash
```bash
adb start-server >/dev/null 2>&1
try=0; until [ "$(adb devices | grep -c '_adb-tls-connect.*device')" -gt 0 ]; do
  try=$((try+1)); [ $try -ge 15 ] && break; sleep 3
done
dev="$(adb devices | awk '/_adb-tls-connect.*device/{print $1; exit}')"
if [ -z "$dev" ]; then
  echo "ERROR: 未发现 mDNS 无线设备，请检查代理 / VPN 与手机无线调试（见 §三.1）" >&2
  exit 1
fi
echo "Target Device: $dev"
adb -s "$dev" install -r -d "app/build/outputs/apk/debug/app-debug.apk"
adb -s "$dev" shell am start -n com.example.yanji/.MainActivity
```

> ⚠️ **真机红线**：安装启动后即完成本次实机交付；**禁止**在手机上截图、录屏、`uiautomator dump`、模拟点击 / 滑动、修改系统设置、抓取 / 导出数据库、备份或清数据。必须保护已有数据与进行中的专注会话；如确需其中某项，必须由用户在该次请求中逐项明确授权。

---

## 五、常用调试与排错指令速查

### 5.1 模拟器（默认，无测试限制）

| 任务 | 指令 | 说明 |
| :--- | :--- | :--- |
| **查看所有已连接设备** | `adb devices -l` | 模拟器序列号形如 `emulator-5554` |
| **启动 AVD** | `emulator.exe -avd DiaryPhone -no-window -gpu host -cores 6 -memory 4096` | 详见 §2.0 |
| **检查硬件加速** | `emulator-check accel` | 应输出 AEHD 可用 |
| **调起主界面** | `adb shell am start -n com.example.yanji/.MainActivity` | 安装后的标准启动命令 |
| **强制停止应用** | `adb shell am force-stop com.example.yanji` | 结束应用进程 |
| **截图（模拟器）** | `adb shell screencap -p /sdcard/s.png; adb pull /sdcard/s.png build/s.png` | 仅保存到被 Git 忽略的 `build/` |
| **录屏（模拟器）** | `adb shell screenrecord /sdcard/r.mp4` | 结束后 `adb pull` 到 `build/` |
| **UI 层级（模拟器）** | `adb shell uiautomator dump /sdcard/ui.xml; adb pull /sdcard/ui.xml build/ui.xml` | 用于定位控件 |
| **模拟点击 / 滑动（模拟器）** | `adb shell input tap X Y` / `adb shell input swipe ...` | 无授权限制 |
| **切换深浅色（模拟器）** | `adb shell cmd uimode night yes\|no` | 仅模拟器自由使用 |
| **查看实时崩溃堆栈** | `adb logcat *:E` | 启动即崩或严重异常时排查 |
| **查看核心业务日志** | `adb logcat -s YanjiAI:V FocusTimer:V RoomDatabase:V` | 过滤应用业务日志 |

### 5.2 真机（仅用户明确要求「实机测试」时）

| 任务 | 指令 | 说明 |
| :--- | :--- | :--- |
| **查看所有已连接设备** | `adb devices -l` | 验证设备是否在线（必须呈现 `device`，非 `offline`） |
| **设备配对** | `adb pair <ip>:<pair_port> <code>` | 首次配对或凭证失效时配对握手 |
| **手动连接设备** | `adb connect <ip>:<connect_port>` | 配对完成后连接主调试端口 |
| **断开所有无线连接** | `adb disconnect` | 清理失效连接 |
| **重启 ADB 服务** | `adb kill-server; adb start-server` | 解决 ADB 服务死锁或残留假死 |
| **覆盖安装（交付动作）** | `adb -s <dev> install -r -d app/build/outputs/apk/debug/app-debug.apk` | 保留用户数据 |
| **调起主界面（交付终点）** | `adb -s <dev> shell am start -n com.example.yanji/.MainActivity` | 推送安装后的标准启动命令 |

> ⚠️ 真机表格**仅限**上表动作；截图、录屏、`uiautomator`、模拟交互、改系统设置、抓库 / 备份 / 清数据一律禁止，除非用户逐项明确授权。

---

## 六、本机设备参考事实（仅描述作者机器）

以下参数只记录仓库作者当前设备特征，供调试与分辨率考量参考，不得写入自动化测试死逻辑：

### 模拟器（默认测试设备）
- **AVD**：`DiaryPhone`，系统镜像 `android-35 google_apis x86_64`（Android 15）
- **序列号**：`emulator-5554`
- **加速**：AEHD 2.2
- **渲染**：`-gpu host`，`wm size 720x1600`，`wm density 320`
- **投屏**：scrcpy 5.0.1，`--max-size=720 --max-fps=60 --video-bit-rate=8M --no-audio`

### 真机（仅明确要求实机测试时）
- **机型**：一加 / OPPO 系列（`PKB110`）
- **系统环境**：ColorOS / Android 14+
- **屏幕分辨率**：逻辑分辨率 **1256×2760**（非标准 1080×2400）
- **注意**：真机默认禁止截图 / `uiautomator` / 模拟交互，坐标类信息仅供获授权后的调试参考

### 网络
- **本地代理端口**：本机代理常驻端口 `7898`（开启 TUN 模式时会阻断 mDNS 广播，需特别警惕；仅影响真机无线调试）
