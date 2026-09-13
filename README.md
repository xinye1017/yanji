# 研迹 (Yanji)

一款仅供个人使用的 Android 考研日记与学习管理应用。安静、稳定、耐心的备考伴侣。

> 核心原则：**Local First** —— 数据全部存储在本机；**真实记录** —— 计时基于真实时间戳，绝不伪造统计；**低干扰** —— 不做社交、排行榜与过度激励。

## 功能

- **专注计时**：正向计时 / 倒计时，前台 Service + 单调时钟（`SystemClock.elapsedRealtime`），进程死亡快照落盘与恢复（`noBackupFilesDir`），完成/放弃均具备数据一致性保障。
- **模拟考试**：全真严格计时、考后成绩与复盘归档，支持 AI 深度学情诊断。
- **考研日记**：每日一篇（`journal_entries.date` 唯一索引），自动关联当日真实学习时长，支持困难卡点与明日计划。
- **每日打卡**：连续天数如实呈现（0 天就是 0 天）。
- **学习统计**：DAO 层下沉聚合查询（$O(\text{week size})$，25,000 条记录 < 10ms），周/月/全部趋势图、科目分布、自然周环比（较上周）、连续天数与单次最长专注。
- **卷卷伴学（AI）**：兼容 OpenAI 格式的可配置后端（DeepSeek / 硅基流动 / GLM / OpenAI / 本地 Ollama），基于真实学习数据做阶段学情诊断，支持 typed `AiFailure` 细分错误与重试机制。
- **数据备份**：JSON 导出/导入（SAF），整表替换前自动留本机快照。

## 隐私与安全

- 学习数据保存在本机 Room 数据库（`yanji_study.db`，v11）。
- **AI API Key 不入库**：Android Keystore 加密（`SecretStore`，Fail-Closed 机制，移除明文降级，历史明文仅支持一次性迁移，Key 损坏提示明确错误），存放在应用 no-backup 目录。
- 系统自动备份只覆盖数据库域，凭据与进行中的活动计时快照永不进入备份。
- 全局禁用明文 HTTP（仅放行回环地址）；未配置 Key 时完全离线。

## 技术栈与工程规范

| 项 | 版本/说明 |
| :--- | :--- |
| Kotlin / JVM | 2.3.20 / Java 17 |
| UI 架构 | Jetpack Compose（Material 3），标准化 UI 组件库（`ui/components/`） |
| 架构设计 | 纯 Kotlin `AppContainer` 依赖注入 + `yanjiViewModel` 工厂，模块化分解大页面（全部 < 300 行） |
| 导航机制 | 自定义栈状态持久化（`@Serializable` + `YanjiSubScreenStackSaver` 跨进程死亡恢复） |
| 数据库 | Room 2.8.4（v11，含迁移链 1→11 与 JVM 迁移测试，严禁破坏性回退） |
| 构建与质量 | Gradle Wrapper 9.1.0 · AGP 9.0.1 · Android Lint · desugar_jdk_libs 2.1.5 |
| minSdk / targetSdk | 24 / 36 |

### 架构图

```
Compose Screens (< 300 行 / 分离 Content、Components、Dialogs)
   └─ Feature ViewModels（不可变 UiState + yanjiViewModel 注入）
        ├─ AppContainer (轻量纯 Kotlin DI，零反射、零代码生成)
        │    ├─ YanjiRepository (委托与聚合)
        │    │    ├─ TimerStore / ChatStore / JournalStore
        │    │    ├─ CheckInStore / QuickStartPresetStore
        │    │    ├─ AiClient + AiProtocol
        │    │    └─ BackupCodec / BackupTransfer
        │    ├─ StudyStatisticsRepository (Room DAO 下沉聚合)
        │    └─ ActiveSessionCoordinator (进程内单活动会话互斥)
        └─ Room (v11) — 单一事实来源 (journal_entries.date 唯一索引)

FocusTimerService ──时间事实──▶ ActiveSessionCoordinator ──完成/放弃──▶ TimerStore 落库
                                      │
                         FileTimerSessionPersistence (noBackupFilesDir)
```

## 测试体系

1. **JVM 单元测试**：
   - `YanjiMigrationTest`：验证 1→11 全量数据库迁移，验证幂等性与日记去重。
   - `ActiveSessionPersistenceTest`：验证进程崩溃恢复、时钟倒流保护、快照原子更新与单会话互斥。
   - `SecretStoreTest`：验证 Keystore 强加密、Fail-Closed、一次性明文迁移。
   - `ChatStoreTest`：验证 typed `AiFailure`、会话隔离重试、分页边界。
   - `StatsPerformanceTest`：25,000 行级大规模数据集 Room 聚合性能基准（< 10ms）。
   - `NavigationRecreationTest`：验证子页面序列化与进程重建恢复。
2. **UI 与视觉矩阵测试**（`app/src/androidTest`）：
   - `ActiveFocusContentInstrumentedTest`：专注主流程交互与倒计时准确性。
   - `JournalEditorScreenInstrumentedTest`：日记心境选择、任务规划与保存交互。
   - `JuanjuanChatScreenInstrumentedTest`：卷卷欢迎卡片、快捷提问、输入栏动作分发。
   - `FocusScreenVisualMatrixTest`：多分辨率标准视口（360x800, 390x844, 412x915 及 1.3x 大字号）布局与无障碍验证。

## 构建与运行

```bash
# 本地构建 Debug APK
./gradlew.bat :app:assembleDebug

# 运行全量 JVM 单元测试与基准测试
./gradlew.bat :app:testDebugUnitTest

# 运行 Android Lint 静态分析
./gradlew.bat :app:lintDebug

# 编译插桩与 UI 测试
./gradlew.bat :app:compileDebugAndroidTestKotlin
```

CI（GitHub Actions）流水线已配置：单元测试 + 数据库迁移基准 + Android Lint + Debug/Release R8 打包 + API 34 插桩测试。

## 许可

个人项目，仅供学习交流。
