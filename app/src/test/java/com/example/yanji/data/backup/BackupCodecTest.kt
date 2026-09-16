package com.example.yanji.data.backup

import com.example.yanji.data.ChatMessage
import com.example.yanji.data.ChatSender
import com.example.yanji.data.ChatSession
import com.example.yanji.data.ExamSession
import com.example.yanji.data.FocusSession
import com.example.yanji.data.JournalEntry
import com.example.yanji.data.QuickStartPreset
import com.example.yanji.data.SessionStatus
import com.example.yanji.data.UserSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 备份编解码的纯 JVM 测试。
 *
 * 这里最关键的两条断言是：
 *  1. **导出的 JSON 里不可能出现 API Key** —— 凭据泄露到用户可以随手转发/上传的
 *     备份文件里，是最难挽回的一类事故；
 *  2. **更高版本的备份必须被拒绝** —— 旧 App 硬读新格式会静默丢字段。
 */
class BackupCodecTest {

    private fun sampleBackup() = YanjiBackup(
        exportedAt = 1_757_000_000_000L,
        appVersionName = "1.0",
        settings = UserSettingsBackup.fromDomain(
            UserSettings(
                targetExamDate = "2026-12-20",
                targetSchool = "浙江大学 计算机学院",
                targetMajor = "电子信息 (085400)",
                dailyGoalHours = 10.0f,
                validStudyThresholdMinutes = 30,
                defaultSubjectId = "math",
                aiProvider = "DeepSeek",
                aiBaseUrl = "https://api.deepseek.com/v1",
                aiApiKey = "sk-super-secret-should-never-appear",
                aiModel = "deepseek-chat"
            )
        ),
        focusSessions = listOf(
            FocusSession(
                id = "fs-1",
                subjectId = "math_advanced",
                subjectName = "高等数学",
                startTime = 1_757_000_000_000L,
                endTime = 1_757_000_060_000L,
                durationSeconds = 3600,
                pausedDurationSeconds = 0,
                pauseCount = 0,
                mode = "正向计时",
                note = "二重积分",
                status = SessionStatus.COMPLETED
            )
        ),
        examSessions = listOf(
            ExamSession(
                id = "es-1",
                subjectId = "math",
                subjectName = "数学一 全真模拟",
                plannedDurationSeconds = 10800,
                actualDurationSeconds = 10500,
                score = 126.0,
                maxScore = 150.0,
                note = "中值定理失分"
            )
        ),
        journalEntries = listOf(
            JournalEntry(
                id = "j-1",
                date = "2026-09-05",
                title = "渐入佳境",
                content = "今天状态很稳定",
                moodScore = 5,
                tags = listOf("数学突破", "心态平和")
            )
        ),
        chatSessions = listOf(ChatSession(id = "s-1", title = "数学草稿复盘")),
        chatMessages = listOf(
            ChatMessage(id = "m-1", sessionId = "s-1", sender = ChatSender.USER, content = "草稿写乱了怎么办"),
            ChatMessage(id = "m-2", sessionId = "s-1", sender = ChatSender.JUANJUAN, content = "试试十字四折法")
        ),
        checkIns = emptyList(),
        unlockedAchievements = mapOf("focus_first" to 1_757_000_000_000L, "streak_7" to 1_757_100_000_000L),
        quickStartPresets = listOf(
            QuickStartPreset(id = "q-1", type = QuickStartPreset.TYPE_START_FOCUS, label = "开始专注", sortOrder = 0)
        )
    )

    @Test
    fun `round trip preserves every collection and value`() {
        val original = sampleBackup()
        val json = BackupCodec.encode(original)

        val decoded = BackupCodec.decode(json)
        assertTrue("应当解码成功", decoded is BackupDecodeResult.Success)

        val restored = (decoded as BackupDecodeResult.Success).backup
        assertEquals(original.schemaVersion, restored.schemaVersion)
        assertEquals(1, restored.focusSessions.size)
        assertEquals(3600L, restored.focusSessions.first().durationSeconds)
        assertEquals(SessionStatus.COMPLETED, restored.focusSessions.first().status)
        assertEquals("正向计时", restored.focusSessions.first().mode)
        assertEquals(126.0, restored.examSessions.first().score!!, 0.001)
        assertEquals("高等数学", restored.focusSessions.first().subjectName)
        assertEquals(listOf("数学突破", "心态平和"), restored.journalEntries.first().tags)
        assertEquals(2, restored.chatMessages.size)
        assertEquals(ChatSender.JUANJUAN, restored.chatMessages[1].sender)
        assertEquals(2, restored.unlockedAchievements.size)
        assertEquals("开始专注", restored.quickStartPresets.first().label)
        assertEquals("浙江大学 计算机学院", restored.settings!!.targetSchool)
    }

    @Test
    fun `exported json never contains the ai api key`() {
        val json = BackupCodec.encode(sampleBackup())

        assertFalse("备份文件里不得出现 aiApiKey 字段", json.contains("aiApiKey"))
        assertFalse("备份文件里不得出现凭据明文", json.contains("sk-super-secret-should-never-appear"))
        // 非敏感设置必须保留，否则恢复后要用户重新配置
        assertTrue(json.contains("https://api.deepseek.com/v1"))
        assertTrue(json.contains("deepseek-chat"))
    }

    @Test
    fun `settings backup drops api key when converting from domain model`() {
        val settings = UserSettings(aiApiKey = "sk-abc123")
        val backup = UserSettingsBackup.fromDomain(settings)
        assertEquals("", backup.toDomain().aiApiKey)
    }

    @Test
    fun `decode rejects a backup from a newer schema version`() {
        val json = BackupCodec.encode(
            sampleBackup().copy(schemaVersion = YanjiBackup.CURRENT_SCHEMA_VERSION + 1)
        )

        val result = BackupCodec.decode(json)
        assertTrue(result is BackupDecodeResult.Failure)
        val message = (result as BackupDecodeResult.Failure).message
        assertTrue("应提示版本过高：$message", message.contains("更新版本"))
    }

    @Test
    fun `decode accepts an older schema version with a warning`() {
        val json = """
            {
              "schemaVersion": 0,
              "focusSessions": [],
              "examSessions": [],
              "journalEntries": []
            }
        """.trimIndent()

        val result = BackupCodec.decode(json, currentSchemaVersion = YanjiBackup.CURRENT_SCHEMA_VERSION)
        assertTrue(result is BackupDecodeResult.Failure)

        val older = """
            {
              "schemaVersion": 1,
              "exportedAt": 1,
              "focusSessions": [{"id":"fs-1","subjectId":"math","subjectName":"数学","startTime":1,"endTime":2,"durationSeconds":1}]
            }
        """.trimIndent()
        val ok = BackupCodec.decode(older, currentSchemaVersion = 2)
        assertTrue(ok is BackupDecodeResult.Success)
        val success = ok as BackupDecodeResult.Success
        assertTrue("应给出旧版本提示", success.warnings.any { it.contains("旧版本") })
        assertEquals(1, success.backup.focusSessions.size)
    }

    @Test
    fun `decode tolerates unknown fields from future minor versions`() {
        val json = """
            {
              "schemaVersion": 1,
              "exportedAt": 1,
              "someFutureField": {"a":1},
              "focusSessions": []
            }
        """.trimIndent()

        val result = BackupCodec.decode(json)
        assertTrue(result is BackupDecodeResult.Success)
    }

    @Test
    fun `decode rejects malformed json with a readable message`() {
        val result = BackupCodec.decode("{ this is not json ")
        assertTrue(result is BackupDecodeResult.Failure)
        assertTrue((result as BackupDecodeResult.Failure).message.contains("不是有效的研迹备份"))
    }

    @Test
    fun `decode rejects blank input`() {
        val result = BackupCodec.decode("   ")
        assertTrue(result is BackupDecodeResult.Failure)
    }

    @Test
    fun `decode rejects oversized input before parsing`() {
        // 远超 64 MB 上限：即便内容看起来像一个合法前缀，也必须在解析前被拒绝。
        val huge = "{\"schemaVersion\":1,\"focusSessions\":[" + "0,".repeat(40_000_000) + "0]}"
        assertTrue(huge.length > 64L * 1024 * 1024)
        val result = BackupCodec.decode(huge)
        assertTrue(result is BackupDecodeResult.Failure)
        assertTrue((result as BackupDecodeResult.Failure).message.contains("过大"))
    }

    @Test
    fun `decode warns when the backup carries no data`() {
        val json = BackupCodec.encode(YanjiBackup(exportedAt = 1L))

        val result = BackupCodec.decode(json)
        assertTrue(result is BackupDecodeResult.Success)
        val success = result as BackupDecodeResult.Success
        assertTrue(success.backup.isEmpty)
        assertTrue("应提示会清空本机记录", success.warnings.any { it.contains("清空") })
    }

    @Test
    fun `encode stamps the current schema version`() {
        val json = BackupCodec.encode(YanjiBackup(exportedAt = 1L))
        assertTrue(json.contains("\"schemaVersion\": ${YanjiBackup.CURRENT_SCHEMA_VERSION}"))
    }
}
