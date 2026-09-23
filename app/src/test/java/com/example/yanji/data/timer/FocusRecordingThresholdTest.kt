package com.example.yanji.data.timer

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 「不足 1 分钟的专注不予记录」这条业务规则的守卫。
 *
 * 它原本由 ActiveFocusContent 的「完成」按钮禁用 + 提示文案在 UI 层表达，
 * 倒计时版式重做后规则只剩 TimerStore 落库出口一处；而插桩用例只看得见按钮，
 * 于是这条保证随文案一起失去了覆盖。规则本身在此钉住。
 */
class FocusRecordingThresholdTest {

    @Test
    fun `focus under one minute is not recorded`() {
        assertFalse(TimerStore.isRecordableFocus(0L))
        assertFalse(TimerStore.isRecordableFocus(59L))
    }

    @Test
    fun `focus of one minute or more is recorded`() {
        assertTrue(TimerStore.isRecordableFocus(60L))
        assertTrue(TimerStore.isRecordableFocus(3_600L))
    }
}
