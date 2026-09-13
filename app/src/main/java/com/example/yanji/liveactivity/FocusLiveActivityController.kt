package com.example.yanji.liveactivity

import android.app.Notification
import android.content.Context
import android.util.Log
import androidx.core.app.NotificationManagerCompat
import com.example.yanji.data.timer.FocusLiveState
import com.example.yanji.data.timer.Finished
import com.example.yanji.liveactivity.coloros.ColorOsFluidCloudController

/**
 * 常驻展示层的**唯一入口**。
 *
 * `FocusTimerService` 只跟它打交道，不关心 ColorOS SDK、Android 版本判断、
 * 通知模板细节或能力探测（规范 §32）。
 *
 * 三级降级（规范 §34），任何一级失败都不影响计时准确性与落库：
 * ```
 * ColorOS 系 + Promoted Ongoing 可用 → 标准 Live Update（由系统映射为流体云，待真机确认）
 *              Android 16 + 可用     → 标准 Live Update
 *              其余                  → 普通常驻通知 + 系统 Chronometer
 * ```
 *
 * **只在语义转换时被调用**（START / PAUSE / RESUME / FINISH / DISCARD）。
 * 运行期间的每秒推进由系统 Chronometer 负责，这里没有 tick 路径（规范 §19）。
 */
class FocusLiveActivityController(private val context: Context) {

    private val standard = StandardNotificationController(context)
    private val liveUpdate = AndroidLiveUpdateController(context)
    private val colorOs = ColorOsFluidCloudController(context)

    @Volatile
    private var detected: LiveActivityCapability? = null

    /** 能力探测结果。未初始化前返回保守值（不假设任何增强能力可用）。 */
    val capability: LiveActivityCapability
        get() = detected ?: LiveActivityCapability(
            sdkInt = android.os.Build.VERSION.SDK_INT,
            canPostPromotedOngoing = false,
            notificationsEnabled = true,
            promotedPermissionGranted = false,
            manufacturer = android.os.Build.MANUFACTURER.orEmpty(),
            brand = android.os.Build.BRAND.orEmpty()
        )

    /** 创建渠道 + 探测能力 + 打一行可核验的日志。Service `onCreate` 调用一次即可。 */
    fun initialize() {
        standard.createChannel()
        val capability = LiveActivityCapabilityDetector.detect(context)
        detected = capability
        val colorOsCapability = colorOs.capability(capability)
        Log.i(TAG, "capability: " + colorOs.describe(colorOsCapability, capability))
        if (!colorOs.ongoingChannelUsable(FocusNotificationSpecs.CHANNEL_ID)) {
            Log.w(TAG, "常驻渠道不可用（被关闭或重要性为 MIN），将无法提升为 Live Update")
        }
    }

    /**
     * 依据统一状态构建常驻通知。
     * @return null 表示当前没有需要常驻展示的活动会话。
     */
    fun buildOngoing(state: FocusLiveState, kind: FocusKind = FocusKind.FOCUS): Notification? {
        val spec = FocusNotificationSpecs.ongoing(state, System.currentTimeMillis(), kind) ?: return null
        val decorated = liveUpdate.decorate(standard.builderFor(spec), spec, capability)
        if (decorated.promotionRequested) {
            Log.i(
                TAG,
                "ongoing 已申请 promoted：promotable=${decorated.hasPromotableCharacteristics} tier=${capability.tier}"
            )
        }
        return decorated.notification
    }

    /** 语义变化时更新常驻通知（暂停 / 恢复 / 换科目）。 */
    fun updateOngoing(state: FocusLiveState, kind: FocusKind = FocusKind.FOCUS) {
        val notification = buildOngoing(state, kind) ?: return
        notify(FocusNotificationSpecs.ONGOING_NOTIFICATION_ID, notification)
    }

    /**
     * 结束：**立刻**撤掉 ongoing Live Update，再按需发一条普通完成通知。
     * 顺序不能反——否则会出现「完成通知已到、常驻胶囊还挂着」的假状态（规范 §22）。
     */
    fun finish(state: FocusLiveState, kind: FocusKind = FocusKind.FOCUS) {
        cancelOngoing()
        val spec = FocusNotificationSpecs.completion(state, kind) ?: return
        notify(spec.notificationId, standard.build(spec))
        if (state is Finished) {
            Log.i(TAG, "完成通知已发出：${state.subject} ${state.elapsedSeconds}s")
        }
    }

    fun cancelOngoing() {
        runCatching {
            NotificationManagerCompat.from(context).cancel(FocusNotificationSpecs.ONGOING_NOTIFICATION_ID)
        }
    }

    private fun notify(id: Int, notification: Notification) {
        val manager = NotificationManagerCompat.from(context)
        // 显式的运行期守卫，而不是只靠 @SuppressLint：用户在计时过程中随时可以
        // 撤销通知权限，此时下发会抛 SecurityException，而计时与落库都不应该受影响。
        if (!manager.areNotificationsEnabled()) {
            Log.w(TAG, "通知已被用户关闭，跳过一次下发（计时与落库不受影响）")
            return
        }
        try {
            manager.notify(id, notification)
        } catch (e: SecurityException) {
            // POST_NOTIFICATIONS 被运行中撤销。展示能力的失败绝不升级成计时失败。
            Log.w(TAG, "通知下发被系统拒绝：${e.message}")
        }
    }

    companion object {
        private const val TAG = "YanjiLiveActivity"
    }
}
