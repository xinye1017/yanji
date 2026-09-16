# 「研迹」Coding Agent 指南与真机推送工作流

> 本文档专为接手的 **AI Coding Agent**（如 Claude、Gemini、Cursor、Cline、Copilot、Antigravity 等）编写。  
> 详细记录了项目的构建规范、设备连接机制、避坑指南及一键真机推送命令。  
> **请务必先通读「核心机制与避坑铁律」，避免因环境差异或误解指令而走入备份/排错等无关弯路！**

---

## 一、核心机制与避坑铁律（必读 ⚠️）

### 1. 【最关键】单行链式执行（Single-Line Chaining）
- **现象**：在 Agent 执行环境（Windows PowerShell）中，每个工具命令调用都运行在独立的临时作业对象中。当单个命令执行完毕时，后台子进程（包括 `adb.exe` 服务端守护进程）会被系统自动回收终止！
- **后果**：如果把 `adb start-server`、`adb devices`、`adb install` 分开在多次调用中执行，每次都会看到 `* daemon not running; starting now at tcp:5037`，导致 mDNS 根本来不及建立连接，甚至误报 `device not found`。
- **铁律**：**所有 ADB 操作必须在同一次 PowerShell 调用中，用分号 `;` 链式连成一行执行！**

### 2. 【最省心】使用 mDNS 自动发现，绝不手动问端口或写死 IP
- **现象**：手机的无线调试端口在每次进入界面或重开开关时是**随机变动**的（例如 37699、41689），局域网 IP 也会随 DHCP 变动（例如 192.168.0.105 或 192.168.0.109）。
- **解法**：Android 11+ 无线调试自带 TLS mDNS 广播。只要手机与电脑在同一 Wi-Fi 下且开着无线调试，`adb start-server` 后只需等待 2 秒，ADB 就会自动发现并配对形如 `adb-3B1F6KE5SAR82D37-v0IbtR._adb-tls-connect._tcp` 的设备序列号。
- **铁律**：**直接使用 mDNS 设备串，千万不要向用户索要 5 位端口号，也不要用 `adb connect 192.168.0.xxx:xxxx` 去碰运气！**

### 3. 【最效率】日常功能开发不要去跑备份或抓数据
- **陷阱**：曾有 Agent 每次修改代码后都去跑复杂的 bash 备份脚本、拉取 SQLite 数据库、用 `sqlite3` 检查 `user_version`、检查 `no_backup` 凭据——这严重浪费时间且极易因 Windows 路径格式或环境缺失中断。
- **正道**：
  - **95% 的日常 UI、功能、业务逻辑修改**：只需编译 APK -> 执行单行推送安装 -> 启动查看效果即可！
  - **只有且仅有改动了数据库实体（Room Schema）并需要验证迁移链时**：才先执行 `.\gradlew.bat :app:testDebugUnitTest` 验证迁移测试，必要时才备份数据。

### 4. 【高频排错】为什么有时设备搜不到？
- **排查 1：电脑或手机是否开启了 VPN / 科学上网软件？**（最常见原因！）
  VPN 软件会接管网卡路由并拦截局域网 UDP 5353 组播流量，导致 mDNS 发现失败。如果搜不到设备，第一时间询问用户是否开着 VPN，请其先关闭。
- **排查 2：手机是否息屏或切出了设置界面？**
  国产系统（OPPO ColorOS 等）为了省电，息屏或退到桌面后可能会将无线调试休眠。请提醒用户解锁手机并保持在「无线调试」设置页面。

---

## 二、项目运行环境与元信息

### Portable workflow（所有开发者 / CI）

* 所有脚本必须从 `scripts/` 自身位置推导仓库根目录，不得依赖固定盘符或用户名。
* Android SDK 通过 `ANDROID_HOME` / `ANDROID_SDK_ROOT` 或 `local.properties` 发现；ADB 默认从 `PATH` 查找，也可用 `ADB=/path/to/adb` 覆盖。
* `scripts/adb-push.sh` 支持 `YANJI_APK`、`YANJI_DEVICE_BACKUP_DIR`、`YANJI_PACKAGE` 覆盖默认值。
* 默认 APK 与设备备份路径分别是 `<repo>/app/build/outputs/apk/debug/app-debug.apk` 与 `<repo>/build/device-backup`。

### Owner-specific workflow（仓库作者当前设备，仅供参考）

以下路径、设备型号和无线调试提示只描述作者机器，不得复制进可执行脚本或 CI：

* **代码仓库**：`github.com/xinye1017/yanji`，分支 `main`，远端 `origin`
* **提交纪律**：`app/schemas/**` 必须随代码一起提交（迁移测试的校验依据）；**绝不提交** `local.properties`、`*.db*`、`build/` 下的真机备份。
* **操作系统**：Windows (PowerShell / pwsh)
* **项目路径**：`D:\AI项目\yanji`
* **应用包名**：`com.example.yanji`
* **主入口 Activity**：`com.example.yanji.MainActivity`
* **目标 SDK / 最低 SDK**：`compileSdk = 36`, `minSdk = 24`, `targetSdk = 36`
* **Android SDK 路径**：`C:\Users\dex\Android\Sdk`（配置于 `local.properties`）
* **ADB 工具路径**：`C:\Users\dex\Android\Sdk\platform-tools\adb.exe`（已配置进系统环境变量 `PATH`）
* **调试 APK 产物路径**：`app\build\outputs\apk\debug\app-debug.apk`

---

## 三、标准构建与真机部署流程（日常两步走）

### 第一步：编译构建 APK
在项目根目录下执行编译：
```powershell
.\gradlew.bat assembleDebug
```

### 第二步：一键真机推送安装与启动（黄金单行命令）
在 PowerShell 中运行（必须整条单行执行，不得拆分）：
```powershell
adb start-server; Start-Sleep -Seconds 2; $dev = (adb devices | Where-Object { $_ -match "adb-.*device" } | ForEach-Object { ($_ -split '\s+')[0] } | Select-Object -First 1); if (-not $dev) { $dev = "adb-3B1F6KE5SAR82D37-v0IbtR._adb-tls-connect._tcp" }; Write-Host "Target Device: $dev"; adb -s $dev install -r -d "app\build\outputs\apk\debug\app-debug.apk"; adb -s $dev shell am start -n com.example.yanji/.MainActivity
```

> **该单行命令核心逻辑**：
> 1. 启动 ADB Server；
> 2. 等待 2 秒让 mDNS 自动广播发现无线调试手机；
> 3. 动态抓取当前在线的 `adb-.*` 无线设备序列号（若抓取为空则回退到默认设备串）；
> 4. 保留本地用户数据覆盖安装（`-r -d`）；
> 5. 立即调起主界面。

---

## 四、异常排查与备用方案

### 1. 搜不到设备时的快速检查清单
1. 确认电脑与手机连接在同一个 Wi-Fi（5G / 2.4G 局域网互通）；
2. 确认电脑和手机均**关闭了任何 VPN / 代理软件**；
3. 确认手机亮屏，处于「设置 -> 系统与更新 -> 开发者选项 -> 无线调试」页面内。

### 2. 重新配对（首次使用或凭证失效时）
如果设备列表中出现 `offline` 或未授权，需要重新配对：
1. 打开手机「无线调试 -> 使用配对码配对设备」；
2. 记下弹出的 IP、5位配对端口以及 6位配对码；
3. 运行配对单行命令（示例）：
   ```powershell
   adb start-server; adb pair 192.168.0.109:45713 837138
   ```
4. 配对成功后，关闭配对弹窗，返回主页面即可继续使用第三节的黄金单行命令。

### 3. USB 数据线备用方案（一劳永逸固定端口）
如果局域网多播隔离严重导致 mDNS 无法工作：
1. 用 USB 数据线将手机连接至电脑并开启「USB 调试」；
2. 运行命令将手机无线调试端口固定为 `5555`：
   ```powershell
   adb tcpip 5555
   ```
3. 拔掉 USB 数据线，之后只要手机处于同一 Wi-Fi，即可直接使用固定 IP 连接：
   ```powershell
   adb connect 192.168.0.109:5555
   ```

---

## 五、常用调试与验证指令

| 任务 | 命令行 |
| :--- | :--- |
| **查看当前连接设备** | `adb devices -l` |
| **检查应用是否在运行** | `adb shell pidof com.example.yanji` |
| **启动应用** | `adb shell am start -n com.example.yanji/.MainActivity` |
| **强制停止应用** | `adb shell am force-stop com.example.yanji` |
| **查看应用实时崩溃与异常** | `adb logcat *:E` |
| **查看特定业务日志** | `adb logcat -s FocusTimer:V RoomDatabase:V StudyStats:V` |
| **清除应用所有本地数据（慎用）** | `adb shell pm clear com.example.yanji` |
| **屏幕截图导出** | `adb exec-out screencap -p > screenshot.png` |

---

## 六、真机 UI 定位与截图验证（视觉 / 主题类改动）

改动涉及**系统栏、主题、暗色模式**这类视觉效果时，只看代码不够，必须在真机上截图比对。

### 1. 设备会间歇性掉线 —— 必须带等待重试

无线调试的 mDNS 广播并不稳定（实测同一轮操作中会先 `device`、随后 `no devices/emulators found`），
且 `adb` server 会随每次工具调用被回收。**把所有步骤放在同一次调用内，并在开头等待设备上线**：

```bash
adb start-server >/dev/null 2>&1
try=0; until [ "$(adb devices | grep -c '_adb-tls-connect.*device')" -gt 0 ]; do
  try=$((try+1)); [ $try -ge 15 ] && break; sleep 3
done
```
确认在线后再执行 `install` / `shell` / `pull`。安装 33 MB 的 debug APK 走无线约需十几秒，
不要因为前一条命令报 `not found` 就判定失败——重新发现设备后重试即可。

### 2. 定位控件：uiautomator dump + 坐标点击

`uiautomator dump` 的 XML 是**单行**的，因此 `grep '…' | grep -o 'bounds="…"'` 会把整个文件的
bounds 全部抓出来、无法与控件配对。用 `scripts/dump_ui.py` 解析最省事：

```bash
adb shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1
adb pull /sdcard/ui.xml build/ui.xml
python scripts/dump_ui.py build/ui.xml          # 输出「可见文本/描述 -> 中心坐标」
adb shell input tap <x> <y>
```

- dump **只包含当前视口内**的元素；目标不在屏内时先滚动再 dump：
  `adb shell input swipe 628 2200 628 700 400`
- Compose 的底部 Tab 走 `content-desc`（如 `我的`、`统计`），正文走 `text`。
- 真机 `PKB110` 逻辑分辨率是 **1256×2760**（不是 1080×2400），点击坐标按 dump 结果来。

### 3. 截图

优先 `screencap` 落盘再 `pull`（`exec-out … > file.png` 在 Git Bash 下可能被改动行尾）：

```bash
adb shell screencap -p /sdcard/s.png && adb pull /sdcard/s.png build/s.png
```

截系统栏时注意：**系统提示横幅（如「已连接到无线调试」）会盖住状态栏**，等几秒让横幅消失再截。

### 4. 验证「App 主题 × 系统深色模式」的交叉组合

App 主题可被用户显式覆盖系统，所以只测一种组合不够。系统模式可临时强制、**用完务必还原为原值**：

```bash
adb shell cmd uimode night       # 先读原值（本机为 auto）
adb shell cmd uimode night yes   # 强制系统深色
adb shell cmd uimode night auto  # 还原
```

判定标准：**状态栏底色与图标色必须互为对比色**；且 App 显式设为 LIGHT / DARK 时，
结果**不应**随系统模式变化。两张截图字节完全一致，即是「不受系统影响」的有力证据。
