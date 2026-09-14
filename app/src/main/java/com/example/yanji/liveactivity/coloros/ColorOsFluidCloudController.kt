package com.example.yanji.liveactivity.coloros

import android.annotation.SuppressLint
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import com.example.yanji.liveactivity.LiveActivityCapability

/**
 * ColorOS 侧能力的**诚实描述**。
 *
 * 为什么不做「ColorOS 原生流体云」的主动接入：
 * OPPO 开放平台《流体云 接入准备》(open.oppomobile.com/documentation/page/info?id=13572)
 * 明确要求接入方先提交 QPS 峰值 / 单日请求总量 / 应用包名 / 开发者 ID，并
 * **「联系 OPPO 进行确认」**；首次接入还需完成**企业认证**；卡片需要分配 `serviceId`
 * 并开发 UPK。也就是说它是**商务级接入**，不是一个可以直接 `implementation` 进来的 SDK。
 *
 * 因此本控制器：
 *  - 只做能力探测与降级决策，不做任何私有接口调用；
 *  - 不使用 Hidden API、不 Reflection 调用流体云私有方法、不 Hook SystemUI（规范 §25/§39）；
 *  - 把「标准 Android 16 Live Update」作为通往流体云的**官方支持路径**——
 *    ColorOS 16 公开宣称已完整接入原生 Android 16 Live Updates API，
 *    应用遵循该规范即可适配流体云。是否真的呈现为流体云**必须真机确认**。
 */
data class ColorOsCapability(
    val isColorOsFamily: Boolean,
    val sdkInt: Int,
    /** 标准 Android 16 Live Update 在本机是否可用。 */
    val standardLiveUpdateAvailable: Boolean,
    /** 是否已接入 OPPO 原生流体云能力。当前恒为 false，见 [nativeIntegrationNote]。 */
    val nativeFluidCloudIntegrated: Boolean = false
) {
    /** 当前实际生效的展示路径，用于日志与交付报告。 */
    val effectivePath: Path
        get() = when {
            !standardLiveUpdateAvailable -> Path.STANDARD_ONGOING_NOTIFICATION
            isColorOsFamily -> Path.STANDARD_LIVE_UPDATE_ON_COLOROS
            else -> Path.STANDARD_LIVE_UPDATE
        }

    enum class Path {
        /** 普通常驻通知 + Chronometer（Android 15 及以下、或用例被系统拒绝）。 */
        STANDARD_ONGOING_NOTIFICATION,

        /** 标准 Android 16 Live Update。 */
        STANDARD_LIVE_UPDATE,

        /** ColorOS 系 ROM 上的标准 Live Update —— 由系统侧呈现，可能映射为流体云。待真机确认。 */
        STANDARD_LIVE_UPDATE_ON_COLOROS
    }

    val nativeIntegrationNote: String
        get() = "OPPO 原生流体云（SeedlingSupportSDK / IntelligentIntent）需企业认证 + " +
            "联系 OPPO 申请 + serviceId 分配 + UPK 卡片，属商务接入，需真机验证"
}

/**
 * ColorOS / 流体云适配控制器。
 *
 * 职责刻意收窄：**只做探测与用户引导**。通知本体一律由 [StandardNotificationController] +
 * [AndroidLiveUpdateController] 产出，这里不重复实现展示逻辑。
 */
class ColorOsFluidCloudController(private val context: Context) {

    fun capability(standard: LiveActivityCapability): ColorOsCapability = ColorOsCapability(
        isColorOsFamily = standard.isColorOsFamily,
        sdkInt = standard.sdkInt,
        standardLiveUpdateAvailable = standard.canPostPromotedOngoing
    )

    /**
     * 引导用户前往本应用的通知设置（公开 API `ACTION_APP_NOTIFICATION_SETTINGS`，
     * API 26+）。用于「流体云/实时活动没出现」时的自查：通知总开关、渠道是否被关、
     * 是否已授权实时活动。
     *
     * 不使用 `ACTION_MANAGE_APP_PROMOTED_NOTIFICATIONS`：该常量**不存在于 API 36 的
     * android.jar**（实测全量 class 中搜不到），引用它会直接编译失败。
     */
    @SuppressLint("InlinedApi")
    fun notificationSettingsIntent(): Intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
        .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    /** 渠道是否仍可作为 promoted 载体（重要性不能是 MIN，且没被用户关掉）。 */
    fun ongoingChannelUsable(channelId: String): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return true
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return false
        val channel = manager.getNotificationChannel(channelId) ?: return false
        return channel.importance > NotificationManager.IMPORTANCE_MIN
    }

    /** 供诊断输出的一行摘要，真机验证时直接看 logcat。 */
    fun describe(capability: ColorOsCapability, standard: LiveActivityCapability): String = buildString {
        append("path=").append(capability.effectivePath)
        append(" colorOsFamily=").append(capability.isColorOsFamily)
        append(" nativeFluidCloud=").append(capability.nativeFluidCloudIntegrated)
        append(" ").append(standard.describe())
        append(" appNotifEnabled=").append(
            runCatching { NotificationManagerCompat.from(context).areNotificationsEnabled() }.getOrDefault(false)
        )
    }
}
