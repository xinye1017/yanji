package com.example.yanji.ui.home

import com.example.yanji.data.Subject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class AddPlanSheetTest {

    @Test
    fun testSubjectSelectionLabelAndShortName() {
        val category = Subject(id = "math", name = "数学一", colorHex = "#4A90E2", sortOrder = 1)
        val categoryItem = SubjectCategoryItem(category = category, color = androidx.compose.ui.graphics.Color.Blue)

        // When only category is selected
        val catSelection = SubjectSelection(categoryItem = categoryItem, subject = null)
        assertEquals("数学一", catSelection.label)
        assertEquals("数学一", catSelection.shortName)
        assertEquals("math", catSelection.actualSubject.id)

        // When sub-subject is selected
        val subSubject = Subject(id = "math_adv", name = "高等数学", colorHex = "#4A90E2", parentId = "math", sortOrder = 1)
        val subSelection = SubjectSelection(categoryItem = categoryItem, subject = subSubject)
        assertEquals("数学一 › 高等数学", subSelection.label)
        assertEquals("高等数学", subSelection.shortName)
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
        assertTrue("Must support un-timed mode", content.contains("不限时"))
        assertTrue("Must support editing mode", content.contains("editingTask"))
        assertTrue("Must support action suggestions", content.contains("getSubjectActionSuggestions"))
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
