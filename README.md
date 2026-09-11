# 研迹 (Yanji)

一款仅供个人使用的 Android 考研日记与学习管理应用。安静、稳定、耐心的备考伴侣。

> 核心原则：**Local First** —— 数据全部存储在本机；**真实记录** —— 计时基于真实时间戳，绝不伪造统计；**低干扰** —— 不做社交、排行榜与过度激励。

## 功能

- **专注计时**：正向计时 / 倒计时，前台 Service + 单调时钟（`SystemClock.elapsedRealtime`），进程内业务层驱动落库，不依赖页面存活
- **模拟考试**：全真严格计时、考后成绩与复盘归档
- **考研日记**：每日一篇，自动关联当日真实学习时长
- **每日打卡**：连续天数如实呈现（0 天就是 0 天）
- **学习统计**：周/月/全部趋势图、科目分布、 streak 与单次最长专注
- **卷卷伴学（AI）**：兼容 OpenAI 格式的可配置后端（DeepSeek / 硅基流动 / GLM / OpenAI / 本地 Ollama），基于真实学习数据做阶段学情诊断
- **数据备份**：JSON 导出/导入（SAF），整表替换前自动留本机快照

## 隐私与安全

- 学习数据保存在本机 Room 数据库（`yanji_study.db`）
- **AI API Key 不入库**：Android Keystore 加密后存放于 no-backup 目录
- 系统自动备份只覆盖数据库域，凭据永不进入备份
- 全局禁用明文 HTTP（仅放行回环地址）；未配置 Key 时完全离线

## 技术栈

| 项 | 版本/说明 |
| :--- | :--- |
| Kotlin / JVM | 2.3.20 / Java 17 |
| UI | Jetpack Compose（Material 3） |
| 数据库 | Room 2.8.4（v9，含迁移链 1→9 与 JVM 迁移测试） |
| 构建 | Gradle Wrapper 9.1.0 · AGP 9.0.1 |
| minSdk / targetSdk | 24 / 36 |

### 架构

```
Compose Screens
   └─ Feature ViewModels（不可变 UiState + 构造注入）
        ├─ YanjiRepository（委托层）
        │    ├─ TimerStore / ChatStore / JournalStore
        │    ├─ CheckInStore / QuickStartPresetStore
        │    ├─ AiClient + AiProtocol
        │    └─ BackupCodec / BackupTransfer
        └─ StudyStatisticsRepository（时长/科目聚合）
             └─ Room (v9) — 单一事实来源

FocusTimerService ──时间事实──▶ ActiveSessionCoordinator ──完成/放弃──▶ TimerStore 落库
```

计时语义：倒计时归零/主动结束 → `COMPLETED` 落库；放弃 → 不落库；专注 < 60 秒不记录。

## 构建

```bash
# 本地构建（Windows 下首次需 Android SDK；SDK 路径由 local.properties 指定）
./gradlew.bat :app:assembleDebug

# JVM 单元测试（含 Room 全链迁移测试，无需设备）
./gradlew.bat :app:testDebugUnitTest

# 插桩测试（API 34+ 模拟器或真机）
./gradlew.bat :app:connectedDebugAndroidTest
```

CI（GitHub Actions）运行单元测试 + Debug/Release 构建 + API 34 模拟器插桩测试。

## 更多文档

- [`开发手册/架构与能力状态.md`](开发手册/架构与能力状态.md) —— 能力状态表、数据不变量、迁移矩阵、剩余工作

## 许可

个人项目，仅供学习交流。
