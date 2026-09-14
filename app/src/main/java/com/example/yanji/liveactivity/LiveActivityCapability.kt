package com.example.yanji.liveactivity

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationManagerCompat

/**
 * 设备在「常驻活动展示」上**真实具备**的能力。
 *
 * 判定原则（规范 §26）：
 *  - 以运行时 API 探测为准（[NotificationManagerCompat.canPostPromotedNotifications]），
 *    它已经把「系统是否支持 + 用户是否授权 + 用户是否在设置里关掉」合并成一个结论；
 *  - `Build.MANUFACTURER` **只作为辅助信息**，绝不作为唯一判据；
 *  - 任何一项探测抛异常（例如 Android 16 初版 ROM 缺少 36.1 的 framework 方法
 *    导致 `NoSuchMethodError`）都降级为「不可用」，而不是崩溃。
 */
data class LiveActivityCapability(
    val sdkInt: Int,
    /** Android 16 Promoted Ongoing 是否真正可用。 */
    val canPostPromotedOngoing: Boolean,
    /** 用户在系统里是否允许本应用发通知。关掉时一切降级，但计时不受影响。 */
    val notificationsEnabled: Boolean,
    /** 是否已声明并授予 POST_PROMOTED_NOTIFICATIONS。仅作诊断输出。 */
    val promotedPermissionGranted: Boolean,
    val manufacturer: String,
    val brand: String
) {
    /** 厂商是否属于 ColorOS 系（OPPO / 一加 / realme）。辅助信息，不构成能力判断。 */
    val isColorOsFamily: Boolean
        get() = manufacturer.equals("OPPO", ignoreCase = true) ||
            manufacturer.equals("OnePlus", ignoreCase = true) ||
            manufacturer.equals("realme", ignoreCase = true) ||
            brand.equals("OPPO", ignoreCase = true) ||
            brand.equals("OnePlus", ignoreCase = true) ||
            brand.equals("realme", ignoreCase = true)

    /**
     * 最终生效的展示层级。三级降级，越靠前越优先（规范 §34）。
     *
     * 注意 [Tier.COLOR_OS_FLUID_CLOUD] 并不代表本应用调用了任何 OPPO 私有接口——
     * 它表示「ColorOS 系 ROM + 标准 Promoted Ongoing 可用」，即由系统侧把标准
     * Live Update 呈现为流体云。是否真的出现流体云必须真机确认。
     */
    val tier: Tier
        get() = when {
            !notificationsEnabled -> Tier.NOTIFICATIONS_DISABLED
            canPostPromotedOngoing && isColorOsFamily -> Tier.COLOR_OS_FLUID_CLOUD
            canPostPromotedOngoing -> Tier.ANDROID_LIVE_UPDATE
            else -> Tier.STANDARD_ONGOING
        }

    enum class Tier { COLOR_OS_FLUID_CLOUD, ANDROID_LIVE_UPDATE, STANDARD_ONGOING, NOTIFICATIONS_DISABLED }

    /** 供日志与交付报告使用的一行摘要。 */
    fun describe(): String = buildString {
        append("sdk=").append(sdkInt)
        append(" tier=").append(tier)
        append(" promotedOngoing=").append(canPostPromotedOngoing)
        append(" promotedPerm=").append(promotedPermissionGranted)
        append(" notifications=").append(notificationsEnabled)
        append(" brand=").append(manufacturer).append('/').append(brand)
    }
}

/** 运行时能力探测。可注入假值做单测。 */
object LiveActivityCapabilityDetector {

    /**
     * `POST_PROMOTED_NOTIFICATIONS` 在 API 36 的 `android.jar` 里**不存在**
     * （已实测：全量 class 中搜不到该常量，它随 Android 16 QPR1 / API 36.1 才落地），
     * 因此这里用字面量而不是 `Manifest.permission.*` 常量，否则无法编译。
     */
    const val PERMISSION_POST_PROMOTED_NOTIFICATIONS = "android.permission.POST_PROMOTED_NOTIFICATIONS"

    fun detect(context: Context): LiveActivityCapability {
        val sdk = Build.VERSION.SDK_INT
        val notificationsEnabled = runCatching {
            NotificationManagerCompat.from(context).areNotificationsEnabled()
        }.getOrDefault(true)

        // androidx 内部已按 SDK_INT >= 36 守卫；这里再包一层，覆盖 ROM 缺少该方法的机型。
        val canPostPromoted = if (sdk >= 36) {
            runCatching {
                NotificationManagerCompat.from(context).canPostPromotedNotifications()
            }.getOrDefault(false)
        } else {
            false
        }

        val promotedPermission = runCatching {
            context.checkSelfPermission(PERMISSION_POST_PROMOTED_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
        }.getOrDefault(false)

        // 保证类型可见性：老系统上 NotificationManager 服务一定存在，仅用于探活。
        runCatching { context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager }

        return LiveActivityCapability(
            sdkInt = sdk,
            canPostPromotedOngoing = canPostPromoted,
            notificationsEnabled = notificationsEnabled,
            promotedPermissionGranted = promotedPermission,
            manufacturer = Build.MANUFACTURER.orEmpty(),
            brand = Build.BRAND.orEmpty()
        )
    }

    /** 单一事实来源：通知渠道重要性达不到 IMPORTANCE_LOW 时 promoted 必然失败。 */
    fun isChannelPromotable(importance: Int): Boolean = importance > NotificationManager.IMPORTANCE_MIN

    /** 供 UI 提示使用：是否需要引导用户去系统里打开通知。 */
    fun notificationPermissionState(context: Context): Boolean = runCatching {
        context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    }.getOrDefault(true)
}
