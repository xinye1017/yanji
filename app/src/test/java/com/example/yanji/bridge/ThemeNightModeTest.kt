package com.example.yanji.bridge

import android.content.res.Configuration
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 「跟随系统」的深浅色判定口径。
 *
 * 这层逻辑单点落在 [isNightMode]：只认夜间位，不认设备形态。
 * 判定对象是 `Configuration`，调用方负责传入**系统全局**那份
 * （`Resources.getSystem().configuration`）而非应用级那份。
 */
class ThemeNightModeTest {

    private fun configWithNightFlag(flag: Int): Configuration =
        Configuration().apply { uiMode = flag }

    @Test
    fun `night yes resolves dark`() {
        assertTrue(
            isNightMode(
                configWithNightFlag(
                    Configuration.UI_MODE_NIGHT_YES or Configuration.UI_MODE_TYPE_NORMAL
                )
            )
        )
    }

    @Test
    fun `night no resolves light`() {
        assertFalse(
            isNightMode(
                configWithNightFlag(
                    Configuration.UI_MODE_NIGHT_NO or Configuration.UI_MODE_TYPE_NORMAL
                )
            )
        )
    }

    @Test
    fun `undefined night falls back to light`() {
        assertFalse(
            isNightMode(
                configWithNightFlag(
                    Configuration.UI_MODE_NIGHT_UNDEFINED or Configuration.UI_MODE_TYPE_NORMAL
                )
            )
        )
    }

    @Test
    fun `device type does not change the verdict`() {
        // 车机 / 电视同样可以是夜间；主题跟随的是深浅色，不是设备形态。
        assertTrue(
            isNightMode(
                configWithNightFlag(
                    Configuration.UI_MODE_NIGHT_YES or Configuration.UI_MODE_TYPE_CAR
                )
            )
        )
        assertFalse(
            isNightMode(
                configWithNightFlag(
                    Configuration.UI_MODE_NIGHT_NO or Configuration.UI_MODE_TYPE_TELEVISION
                )
            )
        )
    }
}
