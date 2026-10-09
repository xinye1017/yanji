# 「研迹」ColorOS 17 / OPPO 流体云深度适配指南

> 本文档总结了「研迹」在 **ColorOS 17（基于 Android 16 / SDK 36）** 平台接入与深度打磨 **流体云（Fluid Cloud / 灵动岛胶囊 / 锁屏实时卡片）** 的一线工程实践与避坑经验。
> 涵盖系统底层机制剖析、Android 16 官方 Live Update 规范落地、官方应用反编译对比、高频排错与避坑铁律。

---

## 一、ColorOS 17 流体云的两种形态与底层架构

在 ColorOS 17 中，出现在状态栏胶囊、锁屏顶部及通知中心的「流体云卡片」实际上存在两种完全不同的底层实现形态：

```
                    ┌────────────────────────────────────────────────────────┐
                    │                   ColorOS 17 流体云展示层                │
                    │      (状态栏灵动岛胶囊 / 锁屏毛玻璃卡片 / 通知中心置顶)     │
                    └───────────────────────────┬────────────────────────────┘
                                                │
                 ┌──────────────────────────────┴──────────────────────────────┐
                 ▼                                                             ▼
    【形态 A：系统内置特权卡片】                                    【形态 B：标准 Live Update 通知】
   Pantanal Seedling Widget 卡片                               Promoted Ongoing Notification
 ──────────────────────────────────                          ──────────────────────────────────
 • 适用方：系统自带应用（时钟、录音、日历等）                    • 适用方：第三方通用应用（研迹、音乐、外卖等）
 • 机制：OPPO 专有潘塔纳尔（Pantanal）卡片引擎                 • 机制：Android 16 原生规范（API 36）
 • 布局：自定义 XML（如 ConstraintLayout、                    • 布局：SystemUI 统一通知模板渲染
   COUIShadowCardView、COUITintImageView）                   • 按钮：底部 Action Bar 标准横向排布
 • 按钮：可在 XML 中手写固定在右侧的圆形按钮                    • 规范性：严格受 hasPromotableCharacteristics 校验
 • 接入成本：需系统签名或 OPPO 泛在服务平台注册审核              • 接入成本：纯原生 API，无需厂商私有 SDK，零权限申请
```

### 1. 官方时钟反编译实录（为什么官方时钟按钮在右边？）
在对 ColorOS 17 官方时钟（`com.coloros.alarmclock`）的 `Clock.apk` 反编译分析中，我们提取到了其布局资源文件 `floating_view_timer_land_horizontal_fluid_cloud.xml`：
- **卡片容器**：`com.coui.appcompat.cardView.COUIShadowCardView`（OPPO 专有控件）；
- **内部视图**：手写 `ConstraintLayout`，左侧放置沙漏 `ImageView`，中间放置放大时间的 `TextView` 与 `计时` 标签；
- **右侧按钮**：直接放置了两个 `COUITintImageView`（对应暂停 `timer_pause` 与退出 `timer_exit` 的圆形按钮），并通过约束固定在卡片右边缘；
- **更新通道**：由 `com.oplus.pantanal.seedling.update.SeedlingUpdateManager` 统一调度。
- **结论**：官方时钟并不是通过系统标准通知（Notification）承载的，而是作为系统特权组件注册的**专属小部件（Widget）**。

### 2. 第三方应用的唯一最佳路径：Android 16 标准 Live Update
- ColorOS 17 全面接入并尊重 Android 16（API 36）的 **Promoted Ongoing Notifications（提升常驻通知）** 规范。
- 只要第三方 App 的常驻前台服务通知满足官方规范，ColorOS 17 的 SystemUI 就会**全自动接管**，并自动提升为状态栏动态小胶囊和锁屏毛玻璃流体云。
- **核心优势**：无需接入任何厂商私有闭源 SDK，无需复杂的平台账号资质审核，天然跨设备（在原生 Android 16、ColorOS 17、HyperOS 等兼容系统中均可自动生效）。

---

## 二、标准 Live Update 适配规范与代码落地

在「研迹」中，流体云的核心实现集中于两层：
1. **语义描述层**：[`FocusNotificationSpec.kt`](file:///d:/yanji/app/src/main/java/com/example/yanji/liveactivity/FocusNotificationSpec.kt)（纯 Kotlin 状态抽象，不依赖 Android API，便于 JVM 单测覆盖）；
2. **控制器层**：[`StandardNotificationController.kt`](file:///d:/yanji/app/src/main/java/com/example/yanji/liveactivity/StandardNotificationController.kt) 与 [`AndroidLiveUpdateController.kt`](file:///d:/yanji/app/src/main/java/com/example/yanji/liveactivity/AndroidLiveUpdateController.kt)。

### 1. 关键 API 配置

```kotlin
val builder = NotificationCompat.Builder(context, CHANNEL_ID)
    .setSmallIcon(R.drawable.ic_stat_focus)      // 单色矢量小图标（必须透明底，供状态栏显示）
    .setContentTitle(spec.title)
    .setContentText(spec.contentText)
    .setOngoing(true)                            // 必须为常驻前台通知
    .setOnlyAlertOnce(true)                      // 避免更新时重复响铃或振动
    .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
    .setCategory(NotificationCompat.CATEGORY_STOPWATCH) // 标记分类为秒表/计时器
    .setPriority(NotificationCompat.PRIORITY_LOW)

// 1. 请求提升为 Promoted Ongoing（写入 android.requestPromotedOngoing）
builder.setRequestPromotedOngoing(true)

// 2. 配置胶囊收起时的极简数字文本（状态栏灵动岛小胶囊展示）
spec.shortCriticalText?.let { builder.setShortCriticalText(it) }

// 3. 校验合格性（Android 16 原生校验）
val notification = builder.build()
val promotable = NotificationCompat.hasPromotableCharacteristics(notification)
```

### 2. 渠道（NotificationChannel）配置要点
- **渠道重要性**：必须设为 `NotificationManager.IMPORTANCE_LOW`（或更高）；
  - ⚠️ **禁忌**：严禁设为 `IMPORTANCE_MIN`。MIN 级别会被系统直接归入静默折叠区，导致失去提升资格。
- **Badge 与锁屏**：建议显式设置 `setShowBadge(false)`，流体云会以更轻量的形态展示。

---

## 三、四大核心避坑铁律与排错经验

### 1. 【系统时钟驱动】严禁 Service 每秒 notify()，全权交由 Chronometer
- **机制原理**：
  - 流体云卡片在锁屏与状态栏上的数字跳动，**全部由 SystemUI 的内部时钟控件自动重绘**。
  - Service 只需要在开始或状态变化时计算好绝对基准墙钟时间戳（`referenceWallClockMs`），并通过 `setWhen()` 传入。
- **正向计时配置**：
  ```kotlin
  builder.setUsesChronometer(true)
  builder.setChronometerCountDown(false)
  builder.setWhen(nowWallClockMs - state.elapsedSeconds * 1000L) // 物理锚点
  builder.setShowWhen(true)
  ```
- **倒计时配置**：
  ```kotlin
  builder.setUsesChronometer(true)
  builder.setChronometerCountDown(true)
  builder.setWhen(nowWallClockMs + state.remainingSeconds * 1000L) // 预计结束点
  builder.setShowWhen(true)
  ```
- **暂停时的冻结机制**：
  - 当用户暂停时，**必须显式关闭 Chronometer**（`builder.setUsesChronometer(false)`、`builder.setShowWhen(false)`）。
  - 将已定格的时钟格式化为纯文本写入标题或内容（如 `背单词  44:51`），并同步更新 `shortCriticalText`。
  - 如果暂停时不关闭 Chronometer，SystemUI 会继续往前走字，导致显示的计时与实际有效学习时长产生巨大偏差。

### 2. 【进度条陷阱】流体云中的进度条静止问题
- **现象**：开启计时后，流体云卡片上出现了一条 100% 满的静态进度条，或者倒计时进度条卡在 0% 永远不动。
- **根因**：
  - SystemUI 内置的 Chronometer 只能自动刷新时间文本，**无法自动插值推进 Android 标准通知的 ProgressBar**；
  - 遵循 AGENTS.md §三.7，Service 绝不能每秒触发一次 `notify()` 去更新进度，因此进度条必然停留在下发时刻的静态数值上。
- **解决方案**：
  - 针对计时器/秒表场景，果断关闭通知进度条：`builder.setProgress(0, 0, false)`，且不挂载 `NotificationCompat.ProgressStyle`。
  - 关闭后，ColorOS 17 流体云会自动优化卡片版式，去掉了僵死进度条，展示更紧凑优雅的纯数字卡片。

### 3. 【Action 按钮规范】为什么不能做成官方时钟那样的右侧圆形按钮？
- **现象**：官方时钟的暂停/退出是卡片右侧的小圆形图标按钮，而第三方应用的按钮横向分布在卡片最底行。
- **底层成因**：
  - 官方时钟是特权的小组件布局（`floating_view_timer_land_horizontal_fluid_cloud.xml`），右侧直接硬编码了两个 `COUITintImageView`；
  - 第三方应用遵循 Android 16 标准通知规范，所有的 `NotificationCompat.Action` 在系统模板中都被定义在底部的 Action Bar。
- **必须提供标准矢量图标**：
  - 初始版本因传入 `addAction(0, "继续", intent)`，图标 ID 为 0，导致系统判定无图标，只能回退为大药丸文本按钮。
  - 改为传入标准矢量图标（`R.drawable.ic_pause`、`R.drawable.ic_play_arrow`、`R.drawable.ic_close`），按钮内部即可正确绘制图形，整体质感大幅提升。
- **⚠️ 绝对禁忌：切勿尝试自定义 RemoteViews！**
  - Android 16 的 `hasPromotableCharacteristics()` 对 Promoted 通知有严格的模板限制。**一旦使用自定义 `RemoteViews`，自检直接失败，系统会剥夺通知的流体云/灵动岛提升资格，退化为普通后台通知**。

### 4. 【排版去重】文案与标题的精简原则
- **问题**：在最初的流体云适配中，暂停时标题展示科目，内容展示“已暂停”，副标题（subText）也挂着“已暂停”，造成流体云上出现重复的“已暂停 · 已暂停”。
- **优化规则**：
  - 暂停态将 `subText` 设为 `null`；
  - 标题合并为 `$displaySubject  ${formatFocusClock(frozen)}`（中间采用固定双空格，不使用易产生视觉杂乱的 `·` 分隔符）；
  - `contentText` 保留单一明确的状态指示（如 `已暂停` 或 `保持专注`）。

---

## 四、真机流体云快速调试命令集

在进行流体云真机调试时，无需依赖截图或盲猜，通过 ADB 指令可实时查看系统对当前通知的判定状态：

### 1. 查看通知的完整内部属性与 Promoted 状态
```powershell
# 获取当前通知详情（包含 flags, actions, extras）
adb shell "cmd notification get '0|com.example.yanji|1001|null|10707'"
```
**关键观察字段**：
- `flags`：必须包含 `FLAG_ONGOING_EVENT` 和 `FLAG_PROMOTED_ONGOING`；
- `android.requestPromotedOngoing`：必须为 `Boolean (true)`；
- `android.shortCriticalText`：必须为当前胶囊文本（如 `44:51`）；
- `android.showChronometer`：运行态为 `true`，暂停态为 `false`；
- `actions`：检查各 action 是否带有正确的图标资源索引。

### 2. 检索流体云通知统计与通道状态
```powershell
adb shell dumpsys notification --noredact | Select-String -Pattern "com.example.yanji" -Context 0,30
```

---

## 五、总结与最佳实践对照表

| 维度 | 推荐做法 | 绝对禁忌（红线） |
| :--- | :--- | :--- |
| **通知构建** | 基于 Android 16 标准 API (`setRequestPromotedOngoing`) | 严禁使用自定义 `RemoteViews`（会导致被剥夺流体云资格） |
| **时钟走动** |交由系统 `Chronometer` 自动驱动 | 严禁 Service 每一秒调用一次 `notify()` 刷新 |
| **暂停处理** | 关闭 Chronometer，将定格时间转成字符串写入 | 严禁暂停时保留 Chronometer（会导致时间跑偏） |
| **进度条** | 计时场景下关闭 (`setProgress(0, 0, false)`) | 避免在不主动 tick 的常驻通知中开启静态进度条 |
| **操作按钮** | 提供规范的 24dp 纯白矢量 Drawable 图标 | 严禁传入 `icon = 0`（会导致系统降级为纯文本排布） |
| **渠道级别** | 必须设为 `NotificationManager.IMPORTANCE_LOW` | 严禁设为 `IMPORTANCE_MIN`（会被直接静默折叠） |
| **通知分类** | 显式标记 `CATEGORY_STOPWATCH` | 避免使用未分类或 `CATEGORY_STATUS` 等低优分类 |
