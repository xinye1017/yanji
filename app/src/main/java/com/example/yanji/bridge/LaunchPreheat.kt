package com.example.yanji.bridge

import android.os.Bundle
import android.os.SystemClock
import android.util.Log
import com.example.yanji.data.DailyStudySnapshot
import com.example.yanji.data.UserSettings
import com.example.yanji.data.YanjiRepository
import com.example.yanji.data.YanjiTime
import com.facebook.react.bridge.Arguments
import com.facebook.react.bridge.WritableMap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * 冷启动预热：在 React Native 首帧之前，把今天要用的真实数据读好，交给启动委托
 * （`MainActivity` 里的 `YanjiActivityDelegate.getLaunchOptions()`）当
 * initialProperties 送进 JS。
 *
 * 为什么需要它：首页（`TodayScreen`）的每次渲染都从 `useSyncExternalStore` 取快照，
 * 而快照只能由过桥的 Room 读回填。冷启动时缓存是空的，首帧必然渲染成
 * 「正在读取今日记录…」，等桥回来再跳一次——用户看到的是一次莫名的加载。
 * 预热把这个读提前到宿主启动阶段，首帧直接就是有数据的页面。
 *
 * 铁律：
 *  - 预热读的是**同一份 Room 数据**（[com.example.yanji.data.DailySnapshotReader]），
 *    不引入任何演示数据 / Mock；读失败就安静退回原有的过桥读取路径。
 *  - `getLaunchOptions()` 由宿主同步调用，所以只做**有界**等待，绝不无限期阻塞
 *    主线程；等不到也不影响正确性，只是退回首帧加载。
 */
object LaunchPreheat {

    private const val TAG = "YanjiLaunchPreheat"

    /**
     * 有界等待上限。
     *
     * 等待期间系统启动窗口仍然显示，用户看到的是「启动」而不是空页面，所以这个预算换来的是
     * 首帧直接有数据。首启动要连建库带回灌设置与学科，实测在几百毫秒量级；上限取 1s，
     * 即使异常慢也远低于 ANR 阈值，超时后只是退回原来的首帧加载。
     */
    private const val AWAIT_TIMEOUT_MS = 1_000L

    /** 预热自身的硬上限：超时就当预热失败，启动画面与首帧都不再等它。 */
    private const val SETTLE_TIMEOUT_MS = 2_000L

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val settled = CountDownLatch(1)
    private val startedAt = SystemClock.elapsedRealtime()

    @Volatile
    private var properties: Bundle? = null

    /** 首帧载荷；预热失败或超时为 null，JS 侧照原路径过桥读取。 */
    val launchProperties: Bundle?
        get() = properties

    /** 预热是否已结束（成功与失败都算结束）。启动画面据此判断能否退出。 */
    val hasSettled: Boolean
        get() = settled.count == 0L

    /** 在 `Application.onCreate` 里调用；内部切 IO，不阻塞主线程。 */
    fun start() {
        scope.launch { preheat() }
    }

    /**
     * 有界等待预热结束，供 Activity 在 `super.onCreate()` 前调用。
     *
     * 等待期间系统启动窗口仍然显示，用户看到的是「启动」而不是「空页面」。
     */
    fun awaitSettled(timeoutMs: Long = AWAIT_TIMEOUT_MS) {
        settled.await(timeoutMs, TimeUnit.MILLISECONDS)
    }

    private suspend fun preheat() {
        try {
            // 硬上限：预热只允许把启动画面扣住这么久，绝不无限期拖住首帧。
            withTimeout(SETTLE_TIMEOUT_MS) { readLaunchProperties() }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            Log.w(TAG, "Launch preheat failed; the shell falls back to bridged reads", error)
        } finally {
            Log.i(
                TAG,
                "settled in ${SystemClock.elapsedRealtime() - startedAt}ms, " +
                    "snapshot=${properties != null}"
            )
            settled.countDown()
        }
    }

    private suspend fun readLaunchProperties() {
        val repository = YanjiRepository.getInstance()
        // 设置与学科都是异步回灌的；不等它们，送过桥的就是默认值而不是用户配置。
        repository.awaitHydration()

        val date = YanjiTime.todayIso()
        val settings = repository.settings.value
        properties = Bundle().apply {
            putString("date", date)
            putBundle("dailySnapshot", Arguments.toBundle(dailySnapshotMap(repository.getDailySnapshot(date))))
            putBundle("settings", Arguments.toBundle(settingsMap(repository, settings)))
            putBundle("examCountdown", Arguments.toBundle(examCountdownMap(settings.targetExamDate)))
        }
    }
}

/**
 * 今日快照载荷。与 `YanjiDataModule.getDailySnapshot` 逐字段一致，
 * 预热与桥接调用共用同一份装配，杜绝两份口径。
 */
internal fun dailySnapshotMap(snapshot: DailyStudySnapshot): WritableMap =
    Arguments.createMap().apply {
        putString("date", snapshot.date)
        putString("formattedDate", snapshot.formattedDate)
        putDouble("totalDurationSeconds", snapshot.totalDurationSeconds.toDouble())
        putInt("focusCount", snapshot.focusCount)
        putInt("focusParticipationCount", snapshot.focusParticipationCount)
        putInt("examCount", snapshot.examCount)
        putArray(
            "sessions",
            Arguments.createArray().apply {
                snapshot.sessions.forEach { item ->
                    pushMap(Arguments.createMap().apply {
                        putString("id", item.id)
                        putString("title", item.title)
                        putString("subjectId", item.subjectId)
                        putString("subjectName", item.subjectName)
                        putString("subjectColor", item.subjectColor)
                        putDouble("startTime", item.startTime.toDouble())
                        putDouble("endTime", item.endTime.toDouble())
                        putDouble("durationSeconds", item.durationSeconds.toDouble())
                        putBoolean("isExam", item.isExam)
                        putString("note", item.note)
                        putInt("pauseCount", item.pauseCount)
                        putString("mode", item.mode)
                        // 未挂计划时必须是 null（与 note.sessionId 同一写法）。
                        if (item.taskId == null) putNull("taskId") else putString("taskId", item.taskId)
                    })
                }
            }
        )
        putArray(
            "tasks",
            Arguments.createArray().apply {
                snapshot.tasks.forEach { task ->
                    pushMap(taskMap(task, snapshot.taskActualSeconds[task.id]))
                }
            }
        )
        putArray(
            "notes",
            Arguments.createArray().apply {
                snapshot.notes.forEach { note ->
                    pushMap(noteMap(BridgeMappers.noteFields(note)))
                }
            }
        )
    }

/** 设置载荷。与 `YanjiDataModule.getUserSettings` 逐字段一致。 */
internal fun settingsMap(repository: YanjiRepository, settings: UserSettings): WritableMap {
    val fields = BridgeMappers.settingsFields(settings)
    return Arguments.createMap().apply {
        putString("examDate", fields.examDate)
        putString("targetSchool", fields.targetSchool)
        putString("targetMajor", fields.targetMajor)
        putDouble("dailyGoalHours", settings.dailyGoalHours.toDouble())
        putString("themePreference", fields.themePreference)
        putString("decisionProvider", fields.decisionProvider)
        putString("decisionModel", fields.decisionModel)
        putString("decisionEndpoint", fields.decisionEndpoint)
        putBoolean("hasDecisionApiKey", repository.hasDecisionApiKey())
    }
}

/**
 * 考研倒计时载荷。考试日期早于今天时夹紧为 0，不返回负值。
 *
 * 日期缺失或无法解析时如实回报 0，不做猜测回落。
 */
internal fun examCountdownMap(examDateRaw: String): WritableMap {
    if (examDateRaw.isBlank()) {
        return Arguments.createMap().apply {
            putString("examDate", "")
            putInt("daysRemaining", 0)
        }
    }
    val examDate = YanjiTime.parseIsoDate(examDateRaw)
        ?: return Arguments.createMap().apply {
            putString("examDate", examDateRaw)
            putInt("daysRemaining", 0)
        }
    val today = YanjiTime.today()
    val days = java.time.temporal.ChronoUnit.DAYS.between(today, examDate).coerceAtLeast(0)
    return Arguments.createMap().apply {
        putString("examDate", examDateRaw)
        putInt("daysRemaining", days.toInt())
    }
}

/**
 * 只装配 `WritableMap`；契约语义全部在 [BridgeMappers] 里，可在 JVM 单测逐字段断言。
 */
internal fun taskMap(
    task: com.example.yanji.data.StudyTask,
    actualSeconds: Long? = null
): WritableMap {
    val fields = BridgeMappers.taskFields(task, actualSeconds)
    return Arguments.createMap().apply {
        putString("id", fields.id)
        putString("date", fields.date)
        putString("subjectId", fields.subjectId)
        putString("subjectName", fields.subjectName)
        putString("subjectColor", fields.subjectColor)
        putString("title", fields.title)
        putInt("plannedMinutes", fields.plannedMinutes)
        // 真实累计分钟数（秒 / 60），不再是恒为 0 的占位值。
        putInt("actualMinutes", fields.actualMinutes)
        putBoolean("completed", fields.completed)
        if (fields.carriedOverToId == null) putNull("carriedOverToId")
        else putString("carriedOverToId", fields.carriedOverToId)
        putDouble("createdAt", fields.createdAt)
    }
}

internal fun noteMap(fields: BridgeMappers.NoteFields): WritableMap =
    Arguments.createMap().apply {
        putString("id", fields.id)
        putString("date", fields.date)
        putDouble("timestamp", fields.timestamp)
        putString("content", fields.content)
        putBoolean("isFavorite", fields.isFavorite)
        // 未绑定时必须是 null —— 契约 `isValidNoteEntry` 对每条 NoteEntry 载荷生效。
        // 显式 putNull（与 emitStateChanged 的写法一致），绝不退化成空串。
        if (fields.sessionId == null) putNull("sessionId") else putString("sessionId", fields.sessionId)
        putString("category", fields.category)
        putBoolean("isResolved", fields.isResolved)
        if (fields.resolvedAt == null) putNull("resolvedAt") else putDouble("resolvedAt", fields.resolvedAt)
    }
