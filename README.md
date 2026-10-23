# 研迹 (Yanji) · 考研学习管理与心流伴侣

<!-- project-facts: compileSdk=36 minSdk=24 targetSdk=36 versionCode=2 versionName=1.0.0 roomSchema=25 roomLibrary=2.8.4 -->

<p align="center">
  <img src="app/src/main/res/drawable-nodpi/juanjuan.png" width="96" height="96" alt="研迹 Juanjuan" />
</p>

<p align="center">
  <strong>安静、纯粹、耐心的 Android 个人考研学习与心流管理系统</strong>
</p>

<p align="center">
  <img src="https://img.shields.io/badge/Kotlin-2.3.20-7F52FF?logo=kotlin&logoColor=white" alt="Kotlin Version" />
  <img src="https://img.shields.io/badge/React_Native-Android-61DAFB?logo=react&logoColor=black" alt="React Native UI" />
  <img src="https://img.shields.io/badge/Room-v25_(2.8.4)-3DDC84?logo=sqlite&logoColor=white" alt="Room v25" />
  <img src="https://img.shields.io/badge/Android-minSdk_24_|_targetSdk_36-3DDC84?logo=android&logoColor=white" alt="Android SDK" />
  <img src="https://img.shields.io/badge/Architecture-React_Native_+_Kotlin_Bridge-FF6F00" alt="Architecture" />
  <img src="https://img.shields.io/badge/Restore-Room_Transaction-brightgreen" alt="Transactional Restore" />
  <img src="https://img.shields.io/badge/License-Personal_Learning-blue" alt="License" />
</p>

---

## 📖 目录 (Table of Contents)

- [设计哲学与核心理念](#-设计哲学与核心理念)
- [核心功能特性](#-核心功能特性)
- [工程架构与系统设计](#-工程架构与系统设计)
  - [架构概览与数据流向](#架构概览与数据流向)
  - [React Native 与 Kotlin 原生桥接](#react-native-与-kotlin-原生桥接)
  - [高韧性计时引擎与进程死亡恢复](#高韧性计时引擎与进程死亡恢复)
  - [零信任安全存储体系](#零信任安全存储体系)
  - [大数据量统计计算引擎下推](#大数据量统计计算引擎下推)
- [统一设计系统 (Design System)](#-统一设计系统-design-system)
- [项目目录结构 (Code Tour)](#-项目目录结构-code-tour)
- [测试矩阵与质量门禁](#-测试矩阵与质量门禁)
- [构建与运行指南](#-构建与运行指南)
- [数据隐私与安全规范](#-数据隐私与安全规范)
- [开源与免责声明](#-开源与免责声明)

---

## 💡 设计哲学与核心理念

研迹 (Yanji) 是为考研学子量身定制的无干扰备考系统。拒绝浮夸的排行榜、社交打卡与算法推荐，严守三大核心原则：

* 🔒 **本地优先 (Local-First)**：学习记录、模考成绩和日记由设备上的 SQLite 数据库保存；应用不提供自建云同步，但 Android 可按系统设置备份或迁移学习数据库。
* ⏱️ **真实记录 (Truth-Driven)**：计时与统计严格基于真实时间戳与单调物理时钟，不作任何数据虚报与虚假激励（连续学习 0 天即真实呈现 0 天）。
* 🛡️ **可恢复性 (Resilience)**：活动计时快照独立持久化，数据库升级使用显式 Room Migration，JSON 恢复在单个 Room 事务中提交；异常恢复仍以自动化测试覆盖的边界为准。

---

## 🚀 核心功能特性

### 1. 专注心流计时 (Focus Timer)
* **双模式计时**：支持自主正向计时与目标倒计时，契合番茄工作法与长时间深度沉浸。
* **高韧性前台服务**：集成独立单调物理时钟（`SystemClock.elapsedRealtime`），彻底规避用户修改系统时间造成的统计失真。
* **跨周期崩溃恢复**：活动快照落盘在 `noBackupFilesDir`，支持冷启动恢复；单活动段并发互斥，Room 故障时主动保全活跃状态。
* **系统计时通知**：前台服务使用系统 Chronometer 展示时间并提供暂停、继续与结束操作；支持时会请求 Android Promoted Ongoing 展示，其他设备自动降级为普通常驻通知。

### 2. 全真模拟考试 (Mock Exam)
* **严谨全真模考**：内置考研政治、英语一/二、数学一/二/三及专业课标准时长预设。
* **沉浸式大屏交卷**：全屏防误触计时、超时提醒、考后成绩与各题型得失分归档。
* **学情多维复盘**：考后联动 AI 诊断引擎生成针对性备考错因复盘与突破建议。

### 3. 研迹考研日记 (Study Journal)
* **每日专属篇章**：本地数据库支持草稿暂存与预览/编辑模式分流；一天可记录多篇随笔，按日期分组呈现；向右滑收藏、向左滑删除。
* **数据无缝联动**：自动关联当日真实专注时长、专注科目分布与打卡状态。
* **结构化反思**：提供心境情绪选择、今日卡点剖析、明日重点攻克规划。

### 4. 统计与多维看板 (Study Analytics)
* **下沉聚合计算**：区间求和与科目分组全部下推至 SQLite 引擎内核，25,000 条记录聚合耗时 **< 10ms**。
* **清晰统计视图**：自然周趋势（周一至周日 ISO 标准）、月度热力、科目占比饼图、历史累计与最长单次专注记录。
* **自然周环比洞见**：精确呈现相较上周同时段的学习时长增减比例。

### 5. AI 学情分析与建议 (Study Analysis)
* **开放协议兼容**：兼容 OpenAI 开放接口规范，无缝直连 DeepSeek、硅基流动、智谱清言 GLM、OpenAI 或本地部署的 Ollama。
* **有依据的阶段分析**：基于已完成的专注、模考与已保存随笔，对比前后等长周期，展示分析依据、趋势限制与未来三天建议；模考或随笔单独有记录也可生成。
* **明确的失败反馈**：认证、额度、网络、超时和结果格式错误在页面显示；旧版聊天记录仍保留在本机数据库与备份中，升级不删除历史数据。

### 6. 数据备份与安全导出 (Backup & Restore)
* **手动导出与恢复**：设置页「数据管理」通过 Android 系统文件选择器保存和选择 JSON 备份，无需申请全盘存储权限；文件写入、关闭完成后才显示导出成功。
* **预览后确认覆盖**：选中的备份先校验格式、版本与记录信息，再显示导出时间、各类记录数量和覆盖范围。确认恢复会替换专注、模考、随笔、学习任务、打卡、成就、旧版聊天记录及快捷预设，备份中为空的类别会清空本机对应数据；有设置或非空学科列表时才替换它们。
* **事务化恢复**：专注或模考尚未结束（包括暂停、正在完成）时拒绝恢复；恢复期间也不能启动新会话。导入前必须成功保存本机快照，整表替换在单个 Room 事务内提交，写入失败整体回滚。格式不合法、版本不兼容或快照保存失败均不改动原有数据。
* **凭据隔离**：API 密钥不进入备份，恢复也不会覆盖本机凭据；完整导出与恢复核对在独立测试数据库执行。
* **格式与大小**：当前备份格式 v3 包含决策模型的普通设置，恢复旧版设置前会提示缺失项使用默认值；文件和导入前安全快照均以 UTF-8 的 64 MiB 为上限，超过时拒绝操作。

---

## 🏛️ 工程架构与系统设计

### 架构概览与数据流向

UI 由 React Native 与 TypeScript 实现，Kotlin 宿主提供计时、数据和主题原生能力：

```
┌────────────────────────────────────────────────────────────────┐
│ React Native UI: App.tsx, screens, shared components, navigation │
│ ThemeProvider resolves the app preference and system theme      │
└───────────────────────────────┬────────────────────────────────┘
                                │ typed calls and subscriptions
┌───────────────────────────────▼────────────────────────────────┐
│ src/bridge/index.ts  ⇄  YanjiPackage                            │
│ Kotlin modules: timer, data, theme, backup                      │
└───────────────────────────────┬────────────────────────────────┘
                                │
┌───────────────────────────────▼────────────────────────────────┐
│ Kotlin services and repositories                                │
│ Room database (v22), timer state, Android Keystore credentials  │
└────────────────────────────────────────────────────────────────┘
```

### React Native 与 Kotlin 原生桥接

`App.tsx` 组合主题、安全区和导航 Provider，并挂载常驻的主页面。页面通过 `src/bridge/index.ts` 调用原生能力；Android 的 `YanjiPackage` 注册计时、数据、主题和备份模块，模块再访问 Kotlin 服务、仓储与 Room。`YanjiApplication` 初始化 `YanjiRepository`，Activity 不再持有全局依赖容器或重复初始化仓储。

订阅式数据由原生桥接模块推送给 RN；界面消费桥接层提供的当前状态，并通过同一入口执行写入与计时操作。

### 高韧性计时引擎与进程死亡恢复

针对 Android 后台低内存杀死 (Low-Memory Killer) 与系统清理机制，建立了双重保险体系：

```
[UI: Focus / Exam Screen]
       │  (1) startSession(type, subject, targetDuration)
       ▼
[ActiveSessionCoordinator] ──── (2) saveActiveSession ────▶ [FileTimerSessionPersistence]
       │                                                      (noBackupFilesDir 原子落盘)
       │  (3) startForegroundService(MonotonicClock)
       ▼
[FocusTimerService]
       │
       ▼ (运行中突遭系统杀进程 Process Death)
       ▼
[用户冷启动恢复 App] ──── loadActiveSession ────▶ 恢复计时态 (基于 elapsedRealtime 计算真实流逝)
       │
       │  (4) completeSession()
       ▼
[Room Database (v22)] ──── 写入成功 ────▶ 清除 Active 快照
       │
       └────────────────── 写入失败 ────▶ 保留 Active 快照 (严防学习成果丢失)
```

### 零信任安全存储体系

* **Fail-Closed 故障闭塞**：在 `SecretStore` 中彻底废除 Base64 明文兜底回退。当 Android Keystore 不可用或硬件异常时，明确拒绝写盘并反馈安全故障，严防私钥明文泄漏。
* **一次性无损迁移 (One-Time Migration)**：自动检测存量遗留明文秘钥，验证可用后立即使用 Keystore 硬件主密钥重加密覆盖，永不再写回明文。
* **物理备份隔离**：敏感凭证存放在 `noBackupFilesDir/yanji_ai_credential`，不参与 Android 系统云备份、设备迁移或用户 JSON 备份。

### 大数据量统计计算引擎下推

针对历经数年考研复习累积的数万条学习记录，彻底消灭全量内存扫描遍历方案：

| 维度对比 | 历史内存拉取过滤 (`List.filter.sumOf`) | 现代 SQLite 下推方案 (`StatsDao`) |
| :--- | :--- | :--- |
| **算法复杂度** | $O(N)$（$N$ 为全量历史记录总条数） | **$O(\text{range})$**（索引覆盖范围扫描） |
| **25,000 条记录基线** | 随历史总量线性增长 | JVM 基准要求区间查询、分组与求和各自 **< 10 ms** |
| **内存与 GC 压力** | 需要实例化并过滤全量 Entity | 聚合结果由 SQLite 直接返回 |

---

## 🎨 统一设计系统 (Design System)

React Native 设计系统由 `src/theme/tokens.ts`、`src/theme/ThemeProvider.tsx` 与共享组件共同实现。Token 集中提供深浅主题颜色、圆角、间距、排版、触控、动效和阴影角色；`src/components/YanjiUI.tsx` 提供页面使用的基础组件，`src/theme/liquidGlass.ts` 与 `src/navigation/BottomTabBar.tsx` 管理导航栏材质和布局。

---

## 📂 项目目录结构 (Code Tour)

```
src/
├── bridge/                            # React Native 到 Kotlin 的类型化桥接
├── components/                        # RN 页面共享组件
├── navigation/                        # 主导航、页面状态和底栏
├── screens/                           # 今日、专注、复盘与设置页面
├── state/                             # RN 侧状态订阅与快照消费
└── theme/                             # 语义 Token、主题 Provider、导航材质

app/src/main/java/com/example/yanji/
├── bridge/                            # YanjiPackage 与计时、数据、主题、备份模块
├── data/                              # Room、仓储、AI、备份与计时状态
├── liveactivity/                      # Android 实时活动与通知能力
├── service/                           # FocusTimerService
├── YanjiApplication.kt                # 初始化仓储并创建 React Native 宿主
└── MainActivity.kt                    # React Native 主 Activity
```

---

## 🧪 测试矩阵与质量门禁

项目通过 GitHub Actions 执行以下自动化验证；仓库管理员仍需在分支保护中把 `Required production baseline` 设为 required check：

### 1. JVM 单元测试与迁移基准
* `YanjiMigrationTest`：从手写 v1 数据库验证完整迁移链，并用当前导出 schema 校验终态结构与历史数据保留。
* `ActiveSessionPersistenceTest`：针对进程崩溃恢复、时钟倒流保护、原子写入及会话互斥进行极端场景测试。
* `StatsPerformanceTest`：注入 25,000 条真实记录进行基准压测，保障复杂聚合在 10ms 内完成。
* `SecretStoreTest`：验证 Fail-Closed 机制、AES-GCM 加密强度与一次性迁移逻辑。
* `StudyDiagnosticsTest`：验证等长周期比较、模考独立数据源与草稿排除。

### 2. Android 仪器测试 (`androidTest`)
当前仪器测试覆盖应用数据初始化、备份恢复、RN 导航布局、实时活动能力和 RN 原生桥接快照。

### 3. CI 与发布矩阵
* Push / PR：RN 依赖安装、TypeScript 检查、RN 测试与 Android bundle；JVM 单测、Lint、Debug、未签名 Release 和完整仪器测试。
* 每周：最低支持 API 24 与 target API 36 回归。
* `v<versionName>` Tag：复用完整质量门，并在 API 24/34/36 全绿后才进入生产签名与 GitHub Release；产物同时附带 SHA-256 与构建追溯信息。

---

## 🛠️ 构建与运行指南

### 环境要求
* **Node.js / npm**：使用 CI 中 `actions/setup-node` 固定的同一 Node 版本（`.github/workflows/android-quality.yml` 是该要求的唯一权威来源，避免在文档里复制易漂移的版本号），并通过锁文件安装依赖。仓库内的 `.ts` 源文件由 Node 内置类型剥离加载，每次导入都会打印 `MODULE_TYPELESS_PACKAGE_JSON` 警告；这是预期噪音（`package.json` 有意不声明 `"type": "module"`），不影响任何退出码
* **JDK**：OpenJDK 17 或更高版本
* **Android Studio**：Ladybug (2024.2.1) 或更高版本
* **Gradle Wrapper**：9.1.0（内嵌配置）
* **支持设备**：Android 7.0 (API 24) 及以上；推荐 Android 14+ (API 34+)

### 命令行构建

```bash
# 1. 克隆代码仓库
git clone https://github.com/xinye1017/yanji.git
cd yanji

# 2. 安装 RN 依赖并验证前端
npm ci
npm run typecheck
npm test
npm run bundle:android

# 3. 运行全量 JVM 单元测试与迁移测试
./gradlew.bat testDebugUnitTest

# 4. 运行静态代码分析 (Android Lint)
./gradlew.bat lintDebug

# 5. 构建 Debug 调试版本 APK
./gradlew.bat assembleDebug

# 6. 构建 Release 生产优化版本 APK (包含 R8 压缩与混淆验证)
./gradlew.bat assembleRelease
```

---

## 🔐 数据隐私与安全规范

1. **本地优先**：学习与日记功能不依赖研迹自建服务器；只有用户配置并主动使用 AI 功能时，相关请求才会发往用户选择的服务商。
2. **传输边界**：为支持局域网本地大模型（如同一 WiFi 下的 Ollama、LM Studio、vLLM 或私有网关），应用放行明文 HTTP 流量；访问公网 AI 服务商时建议使用 HTTPS，实际 TLS 版本由设备与所选服务商协商。AI 请求仅在用户配置并主动触发分析时才会发出，凭证不随请求外泄、也不进入任何备份。
3. **OS 备份契约**：Room 学习数据库允许 Android 按用户的系统设置进行云备份和设备迁移。AI 凭证与活动计时快照位于 `noBackupFilesDir`，不会进入 OS 备份；用户导出的 JSON 同样不含 API Key。

---

## 📄 开源与免责声明

* 本项目为考研备考与 Android 现代架构工程实践作品，供个人学习与交流使用。
* 项目中涉及的 AI 分析接口由使用者自行申请并配置 API Key，请严格遵守相关服务商的服务条款与法律法规。

<p align="center">
  <sub>Made with care for every dream that endures. 愿每一位默默努力的考研人，都能留下属于自己的研迹、见天地。</sub>
</p>
