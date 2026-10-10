# 「研迹」ADB 无线调试与真机工作流指南

> 本文档是「研迹」项目针对 Android 真机无线调试（Wireless Debugging）、ADB 部署推送、连接排错与实操经验的**唯一权威指南**。
> 统一收录并升级了原 `AGENTS.md` 中关于 ADB 的核心规范、实测经验与排错流程。

---

## 一、核心原则与不可逾越的红线（铁律 ⚠️）

### 1. 【最关键】ADB 发现与安装必须落在同一次工具调用内
- **底层机理**：在大部分 AI Agent 执行环境中，每个工具命令调用运行在**独立的临时作业对象**（Job Object）里；单条命令结束后，后台子进程（包括 `adb.exe` 服务端守护进程）会被系统自动终止与回收。
- **后果**：如果把 `adb start-server`、`adb devices`、`adb install` 拆到多次调用执行，每次都会重新启动 adb server，导致 mDNS 根本来不及建立连接，甚至误报 `device not found`。
- **铁律**：**设备发现（`start-server` + 等待）、连接校验与安装 / 启动必须写在同一个工具调用中。**
  - **PowerShell**：使用分号 `;` 串联整段命令；
  - **Git Bash**：使用 `&&` 或 `;` 串联整段脚本。

### 2. 【真机验证】允许截图，交互测试需用户授权
- **日常推送终点**：**「APK 安装成功且主入口 Activity 正常拉起（`am start` 无启动崩溃）」**。
- **允许截图**：AI 可以使用 `screencap` 或测试框架截图验证布局与视觉效果；截图只保存到被 Git 忽略的 `build/`。
- **交互授权**：未经用户授权，不得模拟点击、滑动或运行自动交互测试。用户明确要求真机测试时，可在该任务范围内执行，保护已有数据和专注会话。
- **系统深浅色**：产品代码不得写系统级深浅色；为主题跟随验证可临时用 ADB 切换系统浅色 / 深色，**先记基线、测完还原**（详见 AGENTS.md §二.6）。

### 3. 【无假数据与安全守卫】
- 严禁为了测试向设备注入任何 Mock 虚假数据；
- 严禁拉取或导出含有敏感凭据（如 AI API Key）的本地配置文件；
- 日常推送严禁盲目执行 `pm clear`，必须保留本地用户数据（使用 `install -r -d` 覆盖安装）。

---

## 二、真机无线连接全流程与实操经验

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
- **解法**：严格遵守§一.1，将 `adb start-server`、等待、`devices` 过滤、`install` 与 `am start` 写在同一个工具调用中。

---

## 四、极速构建与一键推送工作流

### 第一步：极速构建 Debug APK（30 秒增量）
- **铁律**：严禁日常执行 `clean`（会清空缓存导致 3 分钟全量冷编译）；严禁执行 `build`（会触发全量扫描与测试）。
- **只执行**：
  - **PowerShell**：`.\gradlew.bat assembleDebug`
  - **Git Bash**：`./gradlew.bat assembleDebug`

---

### 第二步：一键真机推送安装与调起（单次调用完整执行）

#### PowerShell（Windows 推荐执行脚本）
```powershell
adb start-server; Start-Sleep -Seconds 2; $dev = (adb devices | Where-Object { $_ -match "(adb-.*|_adb-tls-connect.*|192\.168\..*)\s+device" } | ForEach-Object { ($_ -split '\s+')[0] } | Select-Object -First 1); if (-not $dev) { Write-Error "未发现在线设备，请确认无线调试与网络环境（见 ADB.md）"; exit 1 }; Write-Host "Target Device: $dev"; adb -s $dev install -r -d "app\build\outputs\apk\debug\app-debug.apk"; adb -s $dev shell am start -n com.example.yanji/.MainActivity
```

#### Git Bash（POSIX 推荐执行脚本）
```bash
adb start-server >/dev/null 2>&1
try=0; until [ "$(adb devices | grep -E -c '(_adb-tls-connect|adb-.*|192\.168\.).*device')" -gt 0 ]; do
  try=$((try+1)); [ $try -ge 10 ] && break; sleep 2
done
dev="$(adb devices | awk '/(_adb-tls-connect|adb-.*|192\.168\.).*device/{print $1; exit}')"
if [ -z "$dev" ]; then
  echo "ERROR: 未发现有效无线调试设备，请检查手机无线调试页面与网络代理（见 ADB.md）" >&2
  exit 1
fi
echo "Target Device: $dev"
adb -s "$dev" install -r -d "app/build/outputs/apk/debug/app-debug.apk"
adb -s "$dev" shell am start -n com.example.yanji/.MainActivity
```

---

## 五、常用真机调试与排错指令速查

| 任务 | 指令 | 说明 |
| :--- | :--- | :--- |
| **查看所有已连接设备** | `adb devices -l` | 验证设备是否在线（必须呈现 `device`，非 `offline`） |
| **设备配对** | `adb pair <ip>:<pair_port> <code>` | 首次配对或凭证失效时配对握手 |
| **手动连接设备** | `adb connect <ip>:<connect_port>` | 配对完成后连接主调试端口 |
| **断开所有无线连接** | `adb disconnect` | 清理失效连接 |
| **重启 ADB 服务** | `adb kill-server; adb start-server` | 解决 ADB 服务死锁或残留假死 |
| **检查应用是否存活** | `adb shell pidof com.example.yanji` | 查看应用主进程 PID |
| **调起主界面（交付终点）** | `adb shell am start -n com.example.yanji/.MainActivity` | 推送安装后的标准启动命令 |
| **强制停止应用** | `adb shell am force-stop com.example.yanji` | 结束应用进程 |
| **查看实时崩溃堆栈** | `adb logcat *:E` | 仅在启动即崩或严重异常时抓取日志排查 |
| **查看核心业务日志** | `adb logcat -s YanjiAI:V FocusTimer:V RoomDatabase:V` | 过滤应用业务日志 |
| **导出屏幕截图（允许验证）** | `adb shell screencap -p /sdcard/s.png; adb pull /sdcard/s.png build/s.png` | 截图仅保存到被 Git 忽略的 `build/`，不得修改系统配色 |

---

## 六、真机机型参考与历史事实

以下参数只记录仓库作者当前真机特征，供调试与分辨率考量参考，不得写入自动化测试死逻辑：
- **机型**：一加 / OPPO 系列（`PKB110`）
- **系统环境**：ColorOS / Android 14+
- **屏幕分辨率**：逻辑分辨率 **1256×2760**（非标准 1080×2400）
- **本地代理端口**：本机代理常驻端口 `7898`（开启 TUN 模式时会阻断 mDNS 广播，需特别警惕）
