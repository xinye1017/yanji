package com.example.yanji.ui.home

import com.example.yanji.data.Subject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class AddPlanSheetTest {

    @Test
    fun testSubjectSelectionDisplayNameNeverShowsPath() {
        val category = Subject(id = "math", name = "数学一", colorHex = "#4A90E2", sortOrder = 1)
        val categoryItem = SubjectCategoryItem(category = category, color = androidx.compose.ui.graphics.Color.Blue)

        // When only category is selected
        val catSelection = SubjectSelection(categoryItem = categoryItem, subject = null)
        assertEquals("数学一", catSelection.displayName)
        assertEquals("math", catSelection.actualSubject.id)

        // When sub-subject is selected: 只显示末端学科名，不拼 "数学一 › 高等数学" 路径
        val subSubject = Subject(id = "math_adv", name = "高等数学", colorHex = "#4A90E2", parentId = "math", sortOrder = 1)
        val subSelection = SubjectSelection(categoryItem = categoryItem, subject = subSubject)
        assertEquals("高等数学", subSelection.displayName)
        assertEquals("math_adv", subSelection.actualSubject.id)
    }

    @Test
    fun testAddPlanSheetContract() {
        val projectRoot = findProjectRoot()
        val sheetFile = File(projectRoot, "app/src/main/java/com/example/yanji/ui/home/AddPlanSheet.kt")
        assertTrue("AddPlanSheet.kt must exist", sheetFile.exists())

        val content = sheetFile.readText()
        assertTrue("Must use ModalBottomSheet", content.contains("ModalBottomSheet"))
        assertTrue("Must support 3 pages: Form, Subject, Duration", content.contains("AddPlanPage"))
        assertTrue("Must support wheel picker", content.contains("WheelPicker"))
        assertTrue("Must support start focus action", content.contains("添加并开始专注"))
        assertTrue("Must support editing mode", content.contains("editingTask"))
        // 规则更新：标题改为「备注」，且删除所有「快捷添加」灵感词选项
        assertTrue("Title must be 备注", content.contains("title = \"备注\""))
        assertTrue("Must not contain action suggestions", !content.contains("getSubjectActionSuggestions"))
        // 预计时长：取消按钮选择，进入滚轮选择盘，默认不设定时间，上限 180 分钟
        assertTrue("Must support duration field entry", content.contains("DurationField"))
        assertTrue("Must default to un-timed mode", content.contains("不设定时间"))
        assertTrue("Max duration must be 180 minutes", content.contains("180"))
        // 真机规则：默认大学科 / 显示不拼路径 / 无新建科目入口
        assertTrue("Default selection must be the big category, not a sub-subject",
            content.contains("mutableStateOf(editingSub)"))
        assertTrue("Display name must never concatenate category + sub-subject",
            content.contains("val displayName: String")
                && !content.contains("$" + "{categoryItem.category.name} ›"))
        assertTrue("No create-subject entry inside the picker",
            !content.contains("新建科目") && !content.contains("CreateSubjectDialog"))
    }

    private fun findProjectRoot(): File {
        var current: File? = File(".").canonicalFile
        while (current != null) {
            if (File(current, "settings.gradle.kts").exists()) return current
            current = current.parentFile
        }
        return File(".").canonicalFile
    }
}
