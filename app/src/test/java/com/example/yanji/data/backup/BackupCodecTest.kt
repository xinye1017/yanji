package com.example.yanji.data.backup

import com.example.yanji.data.ChatMessage
import com.example.yanji.data.ChatSender
import com.example.yanji.data.ChatSession
import com.example.yanji.data.CheckIn
import com.example.yanji.data.ExamSession
import com.example.yanji.data.FocusSession
import com.example.yanji.data.NoteEntry
import com.example.yanji.data.ScheduleBlock
import com.example.yanji.data.ScheduleBlockCompletion
import com.example.yanji.data.ScheduleDayAssignment
import com.example.yanji.data.ScheduleTemplate
import com.example.yanji.data.QuickStartPreset
import com.example.yanji.data.SessionStatus
import com.example.yanji.data.UserSettings
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Backup codec tests exercise the destructive-import boundary without touching a database.
 *
 * A valid empty backup is allowed only when the complete envelope is present. An empty or
 * partial object must never be mistaken for a deliberate "clear all local data" backup.
 */
class BackupCodecTest {

    private val epoch = 1_757_000_000_000L

    private fun sampleBackup() = YanjiBackup(
        exportedAt = epoch,
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
                startTime = epoch,
                endTime = epoch + 60_000L,
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
                startTime = epoch,
                endTime = epoch + 10_500_000L,
                score = 126.0,
                maxScore = 150.0,
                note = "中值定理失分"
            )
        ),
        noteEntries = listOf(
            NoteEntry(
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
        checkIns = listOf(CheckIn(date = "2026-09-11", checkInTime = epoch, streak = 3)),
        quickStartPresets = listOf(
            QuickStartPreset(id = "q-1", type = QuickStartPreset.TYPE_START_FOCUS, label = "开始专注", sortOrder = 0)
        ),
        scheduleTemplates = listOf(
            ScheduleTemplate(
                id = "tpl-1",
                name = "复习冲刺",
                sortOrder = 0,
                createdAt = epoch,
                updatedAt = epoch,
                blocks = listOf(
                    ScheduleBlock(
                        id = "b-1",
                        templateId = "tpl-1",
                        startMinute = 510,
                        endMinute = 630,
                        subjectIds = listOf("math"),
                        subjectLabel = "数学",
                        title = "核心知识复习"
                    ),
                    ScheduleBlock(
                        id = "b-2",
                        templateId = "tpl-1",
                        startMinute = 1200,
                        endMinute = 1230,
                        subjectIds = emptyList(),
                        subjectLabel = "复盘",
                        title = "错题整理"
                    )
                )
            )
        ),
        scheduleDayAssignments = listOf(
            ScheduleDayAssignment(date = "2026-10-29", templateId = "tpl-1", updatedAt = epoch)
        ),
        scheduleBlockCompletions = listOf(
            ScheduleBlockCompletion(date = "2026-10-29", blockId = "b-1", completedAt = epoch)
        )
    )

    private fun fullJson(backup: YanjiBackup = sampleBackup()): String = BackupCodec.encode(backup)

    private fun fullObject(backup: YanjiBackup = sampleBackup()): JsonObject =
        Json.parseToJsonElement(fullJson(backup)).jsonObject

    private fun JsonObject.without(vararg keys: String): JsonObject =
        JsonObject(filterKeys { it !in keys })

    private fun JsonObject.withArrayItem(key: String, item: kotlinx.serialization.json.JsonElement): JsonObject {
        val items = getValue(key).jsonArray
        return JsonObject(this + (key to JsonArray(items + item)))
    }

    private fun assertFailure(raw: String, messageContains: String? = null) {
        val result = BackupCodec.decode(raw)
        assertTrue("Expected rejection for: $raw", result is BackupDecodeResult.Failure)
        if (messageContains != null) {
            assertTrue(
                "Expected '$messageContains' in: ${(result as BackupDecodeResult.Failure).message}",
                result.message.contains(messageContains)
            )
        }
    }

    @Test
    fun `round trip preserves every collection and value`() {
        val original = sampleBackup()
        val decoded = BackupCodec.decode(fullJson(original))
        assertTrue(decoded is BackupDecodeResult.Success)

        val restored = (decoded as BackupDecodeResult.Success).backup
        assertEquals(original.schemaVersion, restored.schemaVersion)
        assertEquals(3600L, restored.focusSessions.single().durationSeconds)
        assertEquals(SessionStatus.COMPLETED, restored.focusSessions.single().status)
        assertEquals(126.0, restored.examSessions.single().score!!, 0.001)
        assertEquals(listOf("数学突破", "心态平和"), restored.noteEntries.single().tags)
        assertEquals(ChatSender.JUANJUAN, restored.chatMessages[1].sender)
        assertEquals("开始专注", restored.quickStartPresets.single().label)
        assertEquals("浙江大学 计算机学院", restored.settings!!.targetSchool)
        assertEquals("复习冲刺", restored.scheduleTemplates.single().name)
        assertEquals(listOf(510, 1200), restored.scheduleTemplates.single().blocks.map { it.startMinute })
        assertEquals("tpl-1", restored.scheduleDayAssignments.single().templateId)
        assertEquals("b-1", restored.scheduleBlockCompletions.single().blockId)
    }

    @Test
    fun `v4 rejects an incomplete schedule envelope`() {
        for (key in listOf(
            "scheduleTemplates",
            "scheduleDayAssignments",
            "scheduleBlockCompletions"
        )) {
            assertFailure(fullObject().without(key).toString(), "v4")
        }
    }

    @Test
    fun `v3 backup without schedule tables preserves the local timetable`() {
        val legacy = fullObject()
            .without("scheduleTemplates", "scheduleDayAssignments", "scheduleBlockCompletions")
            .let { JsonObject(it + ("schemaVersion" to JsonPrimitive(3))) }

        val result = BackupCodec.decode(legacy.toString())
        assertTrue(result is BackupDecodeResult.Success)
        val success = result as BackupDecodeResult.Success
        assertTrue(success.warnings.any { it.contains("每日时间表") })
        assertTrue(success.backup.scheduleTemplates.isEmpty())
    }

    @Test
    fun `schedule references and block structure are validated before import`() {
        val template = sampleBackup().scheduleTemplates.single()

        val badAssignment = sampleBackup().copy(
            scheduleDayAssignments = listOf(
                ScheduleDayAssignment(date = "2026-10-29", templateId = "missing", updatedAt = epoch)
            )
        )
        assertFailure(BackupCodec.encode(badAssignment), "日期指派")

        val badCompletion = sampleBackup().copy(
            scheduleBlockCompletions = listOf(
                ScheduleBlockCompletion(date = "2026-10-29", blockId = "missing", completedAt = epoch)
            )
        )
        assertFailure(BackupCodec.encode(badCompletion), "完成状态")

        val mismatchedTemplate = template.copy(
            blocks = template.blocks.map { it.copy(templateId = "other") }
        )
        assertFailure(
            BackupCodec.encode(sampleBackup().copy(scheduleTemplates = listOf(mismatchedTemplate))),
            "结构无效"
        )

        val overlapping = template.copy(
            blocks = listOf(
                template.blocks[0],
                template.blocks[0].copy(id = "b-overlap", startMinute = 600, endMinute = 660)
            )
        )
        assertFailure(
            BackupCodec.encode(sampleBackup().copy(scheduleTemplates = listOf(overlapping))),
            "重叠"
        )

        val blankName = template.copy(name = "  ")
        assertFailure(
            BackupCodec.encode(sampleBackup().copy(scheduleTemplates = listOf(blankName))),
            "结构无效"
        )

        val duplicateTemplate = sampleBackup().copy(
            scheduleTemplates = listOf(template, template.copy(name = "副本"))
        )
        assertFailure(BackupCodec.encode(duplicateTemplate), "重复的时间表模板")

        val completion = sampleBackup().scheduleBlockCompletions.single()
        val duplicateCompletion = sampleBackup().copy(
            scheduleBlockCompletions = listOf(completion, completion)
        )
        assertFailure(BackupCodec.encode(duplicateCompletion), "重复的时间段完成状态")
    }

    @Test
    fun `exported json never contains the ai api key`() {
        val json = fullJson()

        assertFalse("备份文件里不得出现 aiApiKey 字段", json.contains("aiApiKey"))
        assertFalse("备份文件里不得出现凭据明文", json.contains("sk-super-secret-should-never-appear"))
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
        val json = fullJson(sampleBackup().copy(schemaVersion = YanjiBackup.CURRENT_SCHEMA_VERSION + 1))
        assertFailure(json, "更新版本")
    }

    /**
     * 摘除成就系统后，用户手里的旧备份仍然带着 `unlockedAchievements` 字段。
     * 这些文件必须照常导入：该字段被忽略，其余记录完整恢复，且不会因此报错。
     */
    @Test
    fun `legacy backup carrying achievements still restores every other record`() {
        val legacy = JsonObject(
            fullObject() + ("unlockedAchievements" to JsonObject(
                mapOf(
                    "focus_first" to JsonPrimitive(1000L),
                    "streak_7" to JsonPrimitive(2000L)
                )
            ))
        )

        val result = BackupCodec.decode(legacy.toString())
        assertTrue("a legacy file must not be rejected for its achievement field", result is BackupDecodeResult.Success)
        val restored = (result as BackupDecodeResult.Success).backup
        assertEquals(1, restored.focusSessions.size)
        assertEquals(1, restored.examSessions.size)
        assertEquals(1, restored.noteEntries.size)
        assertEquals(1, restored.checkIns.size)
        assertEquals(1, restored.quickStartPresets.size)
        assertTrue("achievement data is simply dropped", restored.isEmpty.not())
    }

    @Test
    fun `valid v1 envelope is accepted while newer table keys are absent`() {
        val legacy = fullObject()
            .without("studyTasks", "subjects")
            .let { JsonObject(it + ("schemaVersion" to JsonPrimitive(1))) }

        val result = BackupCodec.decode(legacy.toString())
        assertTrue(result is BackupDecodeResult.Success)
        val success = result as BackupDecodeResult.Success
        assertTrue(success.warnings.any { it.contains("旧版本") })
        assertEquals(1, success.backup.focusSessions.size)
    }

    @Test
    fun `old v2 settings disclose decision model defaults before replacement`() {
        val root = fullObject()
        val oldSettings = root.getValue("settings").jsonObject
            .without("decisionProvider", "decisionModel", "decisionEndpoint")
        val legacy = JsonObject(root + mapOf(
            "schemaVersion" to JsonPrimitive(2),
            "settings" to oldSettings
        ))

        val result = BackupCodec.decode(legacy.toString()) as BackupDecodeResult.Success
        assertTrue("New exports must distinguish the added settings fields", YanjiBackup.CURRENT_SCHEMA_VERSION > 2)
        assertTrue(result.warnings.any { it.contains("决策模型") && it.contains("默认") })
        assertEquals(UserSettings().decisionModel, result.backup.settings!!.decisionModel)
    }

    @Test
    fun `current format rejects silently missing decision settings`() {
        val root = fullObject()
        val incomplete = root.getValue("settings").jsonObject.without("decisionModel")
        assertFailure(JsonObject(root + ("settings" to incomplete)).toString(), "决策模型")
    }

    @Test
    fun `decode rejects an empty unrelated or incomplete envelope`() {
        assertFailure("{}")
        assertFailure("""{"hello":"world"}""")

        val incomplete = fullObject().without("chatMessages").toString()
        assertFailure(incomplete)

        val v2MissingNewTables = fullObject().without("studyTasks", "subjects").toString()
        assertFailure(v2MissingNewTables)
    }

    @Test
    fun `decode rejects malformed and truncated json`() {
        assertFailure("{ this is not json ", "不是有效的研迹备份")

        val truncated = fullJson().dropLast(17)
        assertFailure(truncated, "不是有效的研迹备份")
    }

    @Test
    fun `decode tolerates unknown fields only inside a complete envelope`() {
        val withFutureField = JsonObject(
            fullObject() + ("someFutureField" to Json.parseToJsonElement("""{"a":1}"""))
        )
        assertTrue(BackupCodec.decode(withFutureField.toString()) is BackupDecodeResult.Success)

        assertFailure("""{"schemaVersion":2,"someFutureField":1}""")
    }

    @Test
    fun `duplicate ids and check-in dates are rejected before import`() {
        val focus = fullObject().getValue("focusSessions").jsonArray.first()
        val duplicateFocus = fullObject().withArrayItem("focusSessions", focus)
        assertFailure(duplicateFocus.toString(), "重复")

        val checkIn = fullObject().getValue("checkIns").jsonArray.first()
        val duplicateDate = fullObject().withArrayItem("checkIns", checkIn)
        assertFailure(duplicateDate.toString(), "重复")
    }

    @Test
    fun `chat messages must reference an existing session`() {
        val broken = JsonObject(
            fullObject() + (
                "chatMessages" to JsonArray(
                    listOf(
                        Json.parseToJsonElement(
                            """{"id":"m-broken","sessionId":"missing","sender":"USER","content":"x","timestamp":$epoch}"""
                        )
                    )
                )
                )
        )
        assertFailure(broken.toString(), "会话")

        val legacyInitial = JsonObject(
            fullObject() + (
                "chatMessages" to JsonArray(
                    listOf(
                        Json.parseToJsonElement(
                            """{"id":"m-initial","sessionId":"session-initial","sender":"USER","content":"x","timestamp":$epoch}"""
                        )
                    )
                )
                )
        )
        assertTrue(BackupCodec.decode(legacyInitial.toString()) is BackupDecodeResult.Success)
    }

    @Test
    fun `invalid date primitives are rejected`() {
        val invalidNote = JsonObject(
            fullObject() + (
                "journalEntries" to JsonArray(
                    listOf(
                        Json.parseToJsonElement(
                            """{"id":"j-invalid","date":"2026-02-30","title":"x","content":"x"}"""
                        )
                    )
                )
                )
        )
        assertFailure(invalidNote.toString(), "日期")
    }

    @Test
    fun `nonfinite and product-breaking negative values are rejected`() {
        val nonfinite = fullJson().replace("\"dailyGoalHours\": 10.0", "\"dailyGoalHours\": 1e999")
        assertFailure(nonfinite, "数值")

        val negativeDuration = JsonObject(
            fullObject() + (
                "focusSessions" to JsonArray(
                    listOf(
                        Json.parseToJsonElement(
                            """{"id":"fs-neg","subjectId":"math","subjectName":"数学","startTime":$epoch,"endTime":${epoch + 1},"durationSeconds":-1}"""
                        )
                    )
                )
                )
        )
        assertFailure(negativeDuration.toString(), "负数")
    }

    @Test
    fun `wall clock adjustments do not invalidate real study records`() {
        val reversedSession = JsonObject(
            fullObject() + (
                "focusSessions" to JsonArray(
                    listOf(
                        Json.parseToJsonElement(
                            """{"id":"fs-time","subjectId":"math","subjectName":"数学","startTime":${epoch + 10},"endTime":$epoch,"durationSeconds":1}"""
                        )
                    )
                )
                )
        )
        assertTrue(BackupCodec.decode(reversedSession.toString()) is BackupDecodeResult.Success)

        val reversedChat = JsonObject(
            fullObject() + (
                "chatSessions" to JsonArray(
                    listOf(
                        Json.parseToJsonElement(
                            """{"id":"s-1","title":"x","createdAt":${epoch + 10},"updatedAt":$epoch,"model":"m"}"""
                        )
                    )
                )
                )
        )
        assertTrue(BackupCodec.decode(reversedChat.toString()) is BackupDecodeResult.Success)
    }

    @Test
    fun `max import limit is the shared 64 mib authority`() {
        assertEquals(64 * 1024 * 1024, BackupCodec.MAX_IMPORT_BYTES)
    }
}
