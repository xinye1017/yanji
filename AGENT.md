# 「研迹」Coding Agent 指南与真机推送工作流

> 本文档专为后续接手的 **AI Coding Agent**（如 Claude、Gemini、Cursor、Cline、Copilot 等）编写。  
> 详细记录了项目的构建规范、设备连接方式、无线 ADB 注意事项及一键推送部署命令，确保任何 Agent 接手后均可无缝执行增量编译与真机调试。

---

## 一、项目运行环境与元信息

* **代码仓库**：`github.com/xinye1017/yanji`（私密），分支 `main`，远端 `origin`。
* **提交纪律**：`app/schemas/**` 必须随代码一起提交（迁移测试的校验依据）；**绝不提交**
  `local.properties`、`*.db*`、`build/` 下的真机备份。

* **操作系统**：Windows (PowerShell / pwsh)
* **项目路径**：`D:\AI项目\yanji`
* **应用包名**：`com.example.yanji`
* **主入口 Activity**：`com.example.yanji.MainActivity`
* **目标 SDK / 最低 SDK**：`compileSdk = 36`, `minSdk = 24`, `targetSdk = 36`
* **Android SDK 路径**：`C:\Users\dex\Android\Sdk`（配置于 `local.properties`）
* **ADB 工具路径**：`C:\Users\dex\Android\Sdk\platform-tools\adb.exe`（已配置进系统环境变量 `PATH`）
* **调试 APK 产物路径**：`app\build\outputs\apk\debug\app-debug.apk`

---

## 二、标准增量构建命令

项目已开启配置缓存（Configuration Cache）与构建缓存（Build Cache），Gradle Wrapper 版本为 **9.1.0**。增量修改后请优先使用以下命令进行快速增量编译：

```powershell
# 在项目根目录下执行增量构建
.\gradlew.bat assembleDebug

# 改动数据层 / 迁移后必须同时跑单元测试（含 Room 迁移链校验）
.\gradlew.bat :app:testDebugUnitTest
```

> **提示**：增量编译正常仅耗时 5~10 秒，构建成功后产物会自动生成到 `app\build\outputs\apk\debug\app-debug.apk`。
>
> **注意**：这两个任务都必须保持全绿。单元测试目前有 27 个用例，其中 `YanjiMigrationTest`
> 会对 Room 导出的真实 schema JSON 做结构校验 —— 它失败通常意味着「用户升级时会打不开数据库」，
> 属于必须立即修的问题，不能用跳过测试的方式绕过去。

---

## 三、真机推送工作流（无线 Wi-Fi ADB）

### 1. 设备网络信息
* **手机局域网常用 IP**：`192.168.0.105`
* **连接模式**：Android 11+ 无线调试（Wireless Debugging）

### 2. 核心机制与避坑指南（重要 ⚠️）
1. **配对端口 vs 连接端口**：
   - 手机点击「使用配对码配对设备」弹窗里的端口为 **临时配对端口**，仅用于执行 `adb pair`，配对成功后该端口立即关闭；
   - 真正的 **连接端口** 是在「无线调试」主界面开关下方显示的 5 位端口号（例如 `41689`）。
2. **端口动态变动与息屏断开**：
   - 手机（尤其是 ColorOS / HyperOS 等国产系统）若息屏、锁屏或离开「无线调试」设置页面，无线调试服务可能会被系统自动关闭或休眠；
   - 每次重新打开开关或重新进入界面，5 位端口号通常会随机刷新。推送前需提醒用户保持屏幕常亮在「无线调试」主界面，并确认当前显示的最新 5 位端口。
3. **命令行隔离性与链式执行**：
   - 在部分 Agent 的执行环境中，子进程生命周期结束可能导致 adb daemon 被重置。
   - **务必使用分号 `;` 链式执行「连接 -> 安装 -> 启动」**，以保证在同一个会话内完成操作。

---

### 3. 一键无线连接与推送命令（推荐直接运行）

**先别手忙脚乱找端口**：只要手机开着无线调试且与本机同一 WiFi，
`adb devices` 会在几秒内自动列出形如
`adb-3B1F6KE5SAR82D37-v0IbtR._adb-tls-connect._tcp` 的 mDNS 设备，直接用这个串当 serial 即可。

当用户告知当前无线调试主界面的端口号（假设为 `<PORT>`，如 `41689`）时，**直接执行以下单行链式命令**：

```powershell
adb connect 192.168.0.105:<PORT>; adb -s 192.168.0.105:<PORT> install -r -d "app\build\outputs\apk\debug\app-debug.apk"; adb -s 192.168.0.105:<PORT> shell am start -n com.example.yanji/.MainActivity
```

**执行示例（验证通过的真实日志）**：
```text
connected to 192.168.0.105:41689
Performing Streamed Install
Success
Starting: Intent { cmp=com.example.yanji/.MainActivity }
```

> ⚠️ **本机实测（2026-09-11）：adb daemon 会随调用的子进程结束被重置**，
> 跨命令调用 `adb` 会报 `no devices/emulators found`，且 `adb connect <ip>:5555` 不成立
> （除非此前手动执行过 `adb tcpip 5555`）。
> → **所有 adb 操作必须链在同一次执行里**。项目里已备好两个脚本可直接用：
> - `bash build/adb-backup.sh`：等设备 → 备份数据库（含 `-wal`/`-shm`）→ 打印当前状态
> - `bash build/adb-push.sh`：安装 → 启动 → 等迁移 → 拉回数据库 → 抓崩溃日志
>
> 备份用户数据时必须**连 `-wal` / `-shm` 一起拉**，否则读到的是过期快照。

---

### 4. 首次配对（仅当未配对或凭证失效时需要）

如果换了新电脑或手机上误删了配对设备，需要执行配对：
1. 请用户打开「无线调试」-> 点击「使用配对码配对设备」；
2. 获取 6 位配对码（例如 `837138`）和弹窗内的 IP 与配对端口（例如 `192.168.0.105:45713`）；
3. 执行配对：
   ```powershell
   adb pair 192.168.0.105:45713 837138
   ```
4. 提示 `Successfully paired` 后，让用户关闭配对弹窗，查看主界面「IP 地址和端口」处的新端口，再运行上方的**一键无线连接与推送命令**。

---

### 5. 备用方案：通过 USB 数据线推送并固定无线端口

如果无线端口频繁变动或断连，可以引导用户使用 USB 数据线：
1. 手机连上电脑 USB，开启「USB 调试」；
2. 直接安装并启动：
   ```powershell
   adb install -r -d "app\build\outputs\apk\debug\app-debug.apk"
   adb shell am start -n com.example.yanji/.MainActivity
   ```
3. **一键固定无线调试端口为 5555（推荐）**：
   在插着数据线的情况下执行：
   ```powershell
   adb tcpip 5555
   ```
   执行后即可拔掉数据线，后续无论手机如何息屏或重启设置，端口永久固定为 `5555`，直接运行：
   ```powershell
   adb connect 192.168.0.105:5555
   ```

---

## 四、常用调试与验证指令

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
