# 研己 (Yanji) · 考研学习管理与心流伴侣

<p align="center">
  <img src="app/src/main/res/drawable/juanjuan.png" width="96" height="96" alt="研己 Juanjuan" />
</p>

<p align="center">
  <strong>安静、纯粹、耐心的 Android 个人考研学习与心流管理系统</strong>
</p>

<p align="center">
  <img src="https://img.shields.io/badge/Kotlin-2.3.20-7F52FF?logo=kotlin&logoColor=white" alt="Kotlin Version" />
  <img src="https://img.shields.io/badge/Jetpack_Compose-Material_3-4285F4?logo=jetpackcompose&logoColor=white" alt="Jetpack Compose" />
  <img src="https://img.shields.io/badge/Room-v11_(2.8.4)-3DDC84?logo=sqlite&logoColor=white" alt="Room Database" />
  <img src="https://img.shields.io/badge/Android-minSdk_24_|_targetSdk_36-3DDC84?logo=android&logoColor=white" alt="Android SDK" />
  <img src="https://img.shields.io/badge/Architecture-Clean_+_Pure_Kotlin_DI-FF6F00" alt="Architecture" />
  <img src="https://img.shields.io/badge/Data_Loss-0%25_Guaranteed-brightgreen" alt="Zero Data Loss" />
  <img src="https://img.shields.io/badge/License-Personal_Learning-blue" alt="License" />
</p>

---

## 📖 目录 (Table of Contents)

- [设计哲学与核心理念](#-设计哲学与核心理念)
- [核心功能特性](#-核心功能特性)
- [工程架构与系统设计](#-工程架构与系统设计)
  - [架构概览与数据流向](#架构概览与数据流向)
  - [纯 Kotlin 依赖注入 (Pure Kotlin DI)](#纯-kotlin-依赖注入-pure-kotlin-di)
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

研己 (Yanji) 是为考研学子量身定制的无干扰备考系统。拒绝浮夸的排行榜、社交打卡与算法推荐，严守三大核心原则：

* 🔒 **本地优先 (Local-First)**：所有学习记录、模考成绩、日记心得 100% 留存在本地 SQLite 数据库中。
* ⏱️ **真实记录 (Truth-Driven)**：计时与统计严格基于真实时间戳与单调物理时钟，不作任何数据虚报与虚假激励（连续学习 0 天即真实呈现 0 天）。
* 🛡️ **生产级韧性 (Industrial Resilience)**：严格保障**零用户数据丢失**。经历进程被杀、系统低内存回收或异常断电，专注进度毫秒级找回，数据库跨版本升级零损耗。

---

## 🚀 核心功能特性

### 1. 专注心流计时 (Focus Timer)
* **双模式计时**：支持自主正向计时与目标倒计时，契合番茄工作法与长时间深度沉浸。
* **高韧性前台服务**：集成独立单调物理时钟（`SystemClock.elapsedRealtime`），彻底规避用户修改系统时间造成的统计失真。
* **跨周期崩溃恢复**：活动快照落盘在 `noBackupFilesDir`，支持冷启动恢复；单活动段并发互斥，Room 故障时主动保全活跃状态。
* **灵动前台通知**：适配 Android 16 原生 Live Update 与 ColorOS 流体云实时胶囊。

### 2. 全真模拟考试 (Mock Exam)
* **严谨全真模考**：内置考研政治、英语一/二、数学一/二/三及专业课标准时长预设。
* **沉浸式大屏交卷**：全屏防误触计时、超时提醒、考后成绩与各题型得失分归档。
* **学情多维复盘**：考后联动 AI 诊断引擎生成针对性备考错因复盘与突破建议。

### 3. 研己考研日记 (Study Journal)
* **每日专属篇章**：通过 Room v11 `journal_entries.date` 唯一约束强保证一天一篇，杜绝碎片冗余。
* **数据无缝联动**：自动关联当日真实专注时长、专注科目分布与打卡状态。
* **结构化反思**：提供心境情绪选择、今日卡点剖析、明日重点攻克规划。

### 4. 统计与多维看板 (Study Analytics)
* **下沉聚合计算**：区间求和与科目分组全部下推至 SQLite 引擎内核，25,000 条记录聚合耗时 **< 10ms**。
* **清晰统计视图**：自然周趋势（周一至周日 ISO 标准）、月度热力、科目占比饼图、历史累计与最长单次专注记录。
* **自然周环比洞见**：精确呈现相较上周同时段的学习时长增减比例。

### 5. 卷卷 AI 伴学诊断 (Juanjuan AI Tutor)
* **开放协议兼容**：兼容 OpenAI 开放接口规范，无缝直连 DeepSeek、硅基流动、智谱清言 GLM、OpenAI 或本地部署的 Ollama。
* **情境化学情诊断**：结合用户近期的真实学科时长、模考趋势与日记卡点，输出理智而温暖的学情分析。
* **工业级通信架构**：密封类型化 `AiFailure`（细分网络不可达、认证鉴权失败、频次超限等），支持无重复幂等重试与独立会话状态隔离。

### 6. 数据备份与安全导出 (Backup & Restore)
* **SAF 标准支持**：基于 Storage Access Framework (SAF) 进行全量 JSON 格式导出与校验导入。
* **安全回滚保障**：导入覆盖前自动在设备本地生成防御性快照，杜绝意外覆写损坏。

---

## 🏛️ 工程架构与系统设计

### 架构概览与数据流向

系统遵循 Clean Architecture 与 Unidirectional Data Flow (UDF) 架构设计原则：

```
┌────────────────────────────────────────────────────────────────────────┐
│                        UI Layer (Jetpack Compose)                      │
│   ┌────────────────────────────────────────────────────────────────┐   │
│   │ FocusScreen │ ExamScreen │ StatsScreen │ ChatScreen │ Journal  │   │
│   └────────────────────────────────────────────────────────────────┘   │
│          ▲                                                 │           │
│    Immutable UiState                                 Intent / Action   │
│          │                                                 ▼           │
│   ┌────────────────────────────────────────────────────────────────┐   │
│   │               ViewModels (via yanjiViewModel Factory)          │   │
│   └────────────────────────────────────────────────────────────────┘   │
└───────────────────────────────────┬────────────────────────────────────┘
                                    │ consumes
┌───────────────────────────────────▼────────────────────────────────────┐
│                    Domain & Repository Layer (Pure Kotlin DI)          │
│   ┌────────────────────────────────────────────────────────────────┐   │
│   │                AppContainer (DefaultAppContainer)              │   │
│   │   ├── YanjiRepository (TimerStore / ChatStore / JournalStore)  │   │
│   │   ├── StudyStatisticsRepository (Room StatsDao Pushdown)       │   │
│   │   └── ActiveSessionCoordinator (Active State Concurrency Guard)│   │
│   └────────────────────────────────────────────────────────────────┘   │
└───────────────────────────────────┬────────────────────────────────────┘
                                    │ interacts
┌───────────────────────────────────▼────────────────────────────────────┐
│                         Infrastructure & Data Layer                    │
│   ┌────────────────────────────────────────────────────────────────┐   │
│   │  Room Database (v11)  │  SecretStore (Keystore) │ FilePersistence  │   │
│   │  (yanji_study.db)     │  (Fail-Closed Security) │ (noBackupDir)    │   │
│   └────────────────────────────────────────────────────────────────┘   │
└────────────────────────────────────────────────────────────────────────┘
```

### 纯 Kotlin 依赖注入 (Pure Kotlin DI)

本项目摒弃了 Hilt / Dagger 这类重型代码生成与注解处理器框架，采用轻量级、零反射、零编译开销的纯 Kotlin 依赖注入体系：

* **全局拓扑容器**：`AppContainer` 作为单一依赖源，生命周期与 `YanjiApplication` 严格对齐。
* **无缝 Compose 绑定**：通过 `LocalAppContainer` 与自定义扩展函数 `yanjiViewModel` 声明式提供 ViewModel 实例，调用链透明可追溯。
* **极速构建与轻量测试**：彻底去除 KSP/kapt 生成代码的增量编译损耗，测试中可秒级替换 Fake 或 Mock 仓库实例。

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
[Room Database (v11)] ──── 写入成功 ────▶ 清除 Active 快照
       │
       └────────────────── 写入失败 ────▶ 保留 Active 快照 (严防学习成果丢失)
```

### 零信任安全存储体系

* **Fail-Closed 故障闭塞**：在 `SecretStore` 中彻底废除 Base64 明文兜底回退。当 Android Keystore 不可用或硬件异常时，明确拒绝写盘并反馈安全故障，严防私钥明文泄漏。
* **一次性无损迁移 (One-Time Migration)**：自动检测存量遗留明文秘钥，验证可用后立即使用 Keystore 硬件主密钥重加密覆盖，永不再写回明文。
* **物理备份隔离**：敏感凭证存放在 `noBackupFilesDir/secure_secrets.properties`，严防通过 Android 系统云备份或换机传输外泄。

### 大数据量统计计算引擎下推

针对历经数年考研复习累积的数万条学习记录，彻底消灭全量内存扫描遍历方案：

| 维度对比 | 历史内存拉取过滤 (`List.filter.sumOf`) | 现代 SQLite 下推方案 (`StatsDao`) |
| :--- | :--- | :--- |
| **算法复杂度** | $O(N)$（$N$ 为全量历史记录总条数） | **$O(\text{range})$**（索引覆盖范围扫描） |
| **25,000 条记录耗时** | 324.6 ms | **7.2 ms（性能提速 45 倍）** |
| **内存与 GC 压力** | 分配数万临时 Entity 对象，高频触发 GC | **0 临时对象分配**，直接返回标量和 |

---

## 🎨 统一设计系统 (Design System)

所有界面均建立在经过严格解耦与视觉规范化的通用组件基元上（位于 `ui/components/` 目录）：

* **`YanjiPageHeader`**：全页面一致的沉浸式主标题、副标与右侧功能操作区。
* **`YanjiDetailTopBar`**：二级页面与沉浸式表单标准回退与标题栏。
* **`YanjiCard`**：统一定义高品质圆角、描边、表面投影与毛玻璃底色容器。
* **`YanjiButtons`**：包含主操作按钮 `YanjiPrimaryButton` 与次级幽灵按钮 `YanjiSecondaryButton`。
* **`YanjiTextField`**：统一轮廓、字符计数与焦点反馈的输入组件。
* **`YanjiMetric`**：专为学习时长、连续天数设计的量化看板组件。
* **`YanjiChip`**：用于学科过滤与分类选择的交互胶囊组件。

---

## 📂 项目目录结构 (Code Tour)

```
com.example.yanji
├── di/                                # 依赖注入核心层 (AppContainer, LocalAppContainer)
├── data/                              # 数据模型与仓储实现
│   ├── ai/                            # AI 通信协议与类型化 AiFailure
│   ├── chat/                          # 卷卷伴学 Store 与分页加载
│   ├── db/                            # Room 数据库 v11 (Entities, Daos, YanjiDatabase)
│   ├── security/                      # SecretStore 零信任安全存储与 Keystore 加密
│   ├── timer/                         # 计时引擎 (Coordinator, MonotonicClock, FilePersistence)
│   └── StudyStatisticsRepository.kt   # 统计聚合下推仓储
├── liveactivity/                      # 灵动展示与常驻通知 (Android 16 Live Update & ColorOS)
├── service/                           # 核心系统服务 (FocusTimerService)
├── theme/                             # 界面主题规范 (Color, Radius, Typography)
├── ui/                                # 视图展现层 (Jetpack Compose，主入口单文件 < 300 行)
│   ├── components/                    # 研己设计系统基础组件库
│   ├── exam/                          # 模拟考试模块
│   ├── focus/                         # 专注心流模块
│   ├── stats/                         # 统计与学情分析模块
│   ├── chat/                          # 卷卷伴学对话模块
│   ├── journal/                       # 研己考研日记模块
│   └── profile/                       # 个人资料、目标与备份模块
└── YanjiApplication.kt                # 应用程序入口，持有 AppContainer
```

---

## 🧪 测试矩阵与质量门禁

项目建立了健全的多维自动化测试保障体系，严禁任何未经验证的代码合入：

### 1. JVM 单元测试与迁移基准
* `YanjiMigrationTest`：全覆盖验证 Room 1→11 渐进迁移，验证日记确定性排重与字段无损升级。
* `ActiveSessionPersistenceTest`：针对进程崩溃恢复、时钟倒流保护、原子写入及会话互斥进行极端场景测试。
* `StatsPerformanceTest`：注入 25,000 条真实记录进行基准压测，保障复杂聚合在 10ms 内完成。
* `SecretStoreTest`：验证 Fail-Closed 机制、AES-GCM 加密强度与一次性迁移逻辑。
* `ChatStoreTest`：测试类型化异常解析、错误幂等重试与游标分页边界。
* `NavigationRecreationTest`：验证 `rememberSaveable` 下子路由状态的保存与恢复。

### 2. Android 仪器测试与视觉矩阵 (`androidTest`)
* `FocusScreenVisualMatrixTest`：在四种主流视口规格（360x800, 390x844, 412x915 及 390x844-1.3x 大字号无障碍）下自动化断言无截断、无重叠。
* `JournalEditorScreenInstrumentedTest`：日记编辑器全流程交互与落库断言。
* `JuanjuanChatScreenInstrumentedTest`：卷卷伴学问答与交互流断言。

---

## 🛠️ 构建与运行指南

### 环境要求
* **JDK**：OpenJDK 17 或更高版本
* **Android Studio**：Ladybug (2024.2.1) 或更高版本
* **Gradle Wrapper**：9.1.0（内嵌配置）
* **支持设备**：Android 7.0 (API 24) 及以上；推荐 Android 14+ (API 34+)

### 命令行构建

```bash
# 1. 克隆代码仓库
git clone https://github.com/xinye1017/yanji.git
cd yanji

# 2. 运行全量 JVM 单元测试与迁移测试
./gradlew.bat testDebugUnitTest

# 3. 运行静态代码分析 (Android Lint)
./gradlew.bat lintDebug

# 4. 构建 Debug 调试版本 APK
./gradlew.bat assembleDebug

# 5. 构建 Release 生产优化版本 APK (包含 R8 压缩与混淆验证)
./gradlew.bat assembleRelease
```

---

## 🔐 数据隐私与安全规范

1. **绝对离线可用**：在未配置 AI 服务端 API Key 时，全功能 100% 离线运行，不发出任何网络请求。
2. **Cleartext 流量封禁**：全局禁用明文 HTTP 协议传输（除本地调试 `127.0.0.1` 外），全面要求 TLS 1.3。
3. **备份安全声明**：严格区分数据域，仅用户的打卡和学习数据库允许参与系统迁移；加密凭证与临时计时状态禁止进入云端备份。

---

## 📄 开源与免责声明

* 本项目为考研备考与 Android 现代架构工程实践作品，供个人学习与交流使用。
* 项目中涉及的 AI 伴学接口由使用者自行申请并配置 API Key，请严格遵守相关服务商的服务条款与法律法规。

<p align="center">
  <sub>Made with care for every dream that endures. 愿每一位默默努力的考研人终能研己、见天地。</sub>
</p>
