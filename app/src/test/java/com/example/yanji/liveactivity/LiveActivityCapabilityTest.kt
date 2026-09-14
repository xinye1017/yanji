package com.example.yanji.liveactivity

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [LiveActivityCapability.tier] 的纯 JVM 契约测试。
 *
 * 只构造数据类、只读 `tier` / `isColorOsFamily` 两个纯 Kotlin 属性，
 * **不触碰 [LiveActivityCapabilityDetector]**（它依赖 android.* 运行时）。
 *
 * 存在的理由：「系统能力」与「运行时通知可用性」是两个正交维度，
 * 用真机插桩测试只能观测到当前那一台设备的那一种组合；
 * 这里把全部组合钉死，避免再次出现「以为 low API 上 tier 恒为 STANDARD_ONGOING」这类误判。
 */
class LiveActivityCapabilityTest {

    private fun capability(
        sdkInt: Int = 35,
        canPostPromotedOngoing: Boolean = false,
        notificationsEnabled: Boolean = true,
        promotedPermissionGranted: Boolean = false,
        manufacturer: String = "Google",
        brand: String = "google"
    ) = LiveActivityCapability(
        sdkInt = sdkInt,
        canPostPromotedOngoing = canPostPromotedOngoing,
        notificationsEnabled = notificationsEnabled,
        promotedPermissionGranted = promotedPermissionGranted,
        manufacturer = manufacturer,
        brand = brand
    )

    @Test
    fun notificationsDisabledAlwaysWinsRegardlessOfSystemCapability() {
        assertEquals(
            LiveActivityCapability.Tier.NOTIFICATIONS_DISABLED,
            capability(canPostPromotedOngoing = true, notificationsEnabled = false).tier
        )
        assertEquals(
            LiveActivityCapability.Tier.NOTIFICATIONS_DISABLED,
            capability(canPostPromotedOngoing = false, notificationsEnabled = false).tier
        )
    }

    @Test
    fun promotedCapabilityWithColorOsFamilyMapsToFluidCloudTier() {
        assertEquals(
            LiveActivityCapability.Tier.COLOR_OS_FLUID_CLOUD,
            capability(sdkInt = 36, canPostPromotedOngoing = true, manufacturer = "OPPO").tier
        )
        assertEquals(
            LiveActivityCapability.Tier.COLOR_OS_FLUID_CLOUD,
            capability(sdkInt = 36, canPostPromotedOngoing = true, manufacturer = "realme").tier
        )
    }

    @Test
    fun promotedCapabilityOnNonColorOsMapsToAndroidLiveUpdateTier() {
        assertEquals(
            LiveActivityCapability.Tier.ANDROID_LIVE_UPDATE,
            capability(sdkInt = 36, canPostPromotedOngoing = true, manufacturer = "Google").tier
        )
    }

    @Test
    fun noPromotedCapabilityWithNotificationsEnabledFallsBackToStandardOngoing() {
        assertEquals(
            LiveActivityCapability.Tier.STANDARD_ONGOING,
            capability(sdkInt = 35, canPostPromotedOngoing = false).tier
        )
        assertEquals(
            LiveActivityCapability.Tier.STANDARD_ONGOING,
            capability(sdkInt = 36, canPostPromotedOngoing = false).tier
        )
    }

    @Test
    fun colorOsFamilyDetectionIsManufacturerOrBrandBased() {
        assertTrue(capability(manufacturer = "OPPO").isColorOsFamily)
        assertTrue(capability(manufacturer = "OnePlus").isColorOsFamily)
        assertTrue(capability(manufacturer = "realme").isColorOsFamily)
        assertTrue(capability(manufacturer = "unknown", brand = "OPPO").isColorOsFamily)
        assertFalse(capability(manufacturer = "Google", brand = "google").isColorOsFamily)
    }

    @Test
    fun describeIncludesDiagnosticFields() {
        val text = capability(sdkInt = 35, manufacturer = "OPPO").describe()
        assertTrue(text, text.contains("sdk=35"))
        assertTrue(text, text.contains("tier=STANDARD_ONGOING"))
        assertTrue(text, text.contains("promotedOngoing=false"))
        assertTrue(text, text.contains("notifications=true"))
    }
}
