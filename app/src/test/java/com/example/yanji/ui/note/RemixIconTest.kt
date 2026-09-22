package com.example.yanji.ui.note

import com.example.yanji.ui.icons.RemixIcons
import org.junit.Assert.assertNotNull
import org.junit.Test

class RemixIconTest {

    @Test
    fun testAllRemixIconsParseValidly() {
        assertNotNull(RemixIcons.ArrowLeftLine)
        assertNotNull(RemixIcons.EyeLine)
        assertNotNull(RemixIcons.EditLine)
        assertNotNull(RemixIcons.CheckLine)
        assertNotNull(RemixIcons.TimeLine)
        assertNotNull(RemixIcons.ArrowGoBackLine)
        assertNotNull(RemixIcons.ArrowGoForwardLine)
        assertNotNull(RemixIcons.CheckboxLine)
        assertNotNull(RemixIcons.CheckboxFill)
        assertNotNull(RemixIcons.ListUnordered)
        assertNotNull(RemixIcons.ListOrdered)
        assertNotNull(RemixIcons.Bold)
        assertNotNull(RemixIcons.Heading)
        assertNotNull(RemixIcons.DoubleQuotesL)
        assertNotNull(RemixIcons.Italic)
        assertNotNull(RemixIcons.Underline)
        assertNotNull(RemixIcons.Strikethrough)
        assertNotNull(RemixIcons.CodeLine)
        assertNotNull(RemixIcons.Separator)
        assertNotNull(RemixIcons.Link)
        assertNotNull(RemixIcons.ArrowLeftSLine)
        assertNotNull(RemixIcons.ArrowRightSLine)
    }
}
