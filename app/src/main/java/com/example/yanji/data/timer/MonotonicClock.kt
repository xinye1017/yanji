package com.example.yanji.data.timer

import android.os.SystemClock

import java.io.File

/**
 * 单调时钟抽象。
 *
 * 为什么不用 `System.currentTimeMillis()`：
 * 挂钟时间会被用户改时间、NTP 校时、时区调整前后跳变，用它算"专注了多久"会得到荒谬结果。
 * [SystemClock.elapsedRealtime] 是单调的、且包含设备 deep sleep，是计时间隔的正确基准
 * （Android 官方推荐用法）。
 *
 * 抽成接口是为了让计时状态机可以在纯 JVM 单元测试里用假时钟驱动，
 * 不必依赖 Android 运行时，也不必真的等待。
 */
interface MonotonicClock {
    fun nowMs(): Long
    fun currentBootId(): String = "boot_default"
}

object SystemMonotonicClock : MonotonicClock {
    override fun nowMs(): Long = SystemClock.elapsedRealtime()

    private val cachedBootId: String by lazy {
        runCatching {
            File("/proc/sys/kernel/random/boot_id").readText().trim()
        }.getOrNull()?.takeIf { it.isNotBlank() }
            ?: "boot_${System.currentTimeMillis() - SystemClock.elapsedRealtime()}"
    }

    override fun currentBootId(): String = cachedBootId
}
