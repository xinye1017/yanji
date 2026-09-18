package com.example.yanji.data.achievement

import com.example.yanji.data.AchievementRarity
import org.junit.Assert.*
import org.junit.Test

class AchievementUnlockFlowTest {

    @Test
    fun testAchievementCatalogFind() {
        val def = AchievementCatalog.find("journey_focus_first")
        assertNotNull("journey_focus_first should exist in catalog", def)
        assertEquals("研途启程", def?.title)
        assertTrue(def?.description?.isNotEmpty() == true)

        val nonExistent = AchievementCatalog.find("non_existent_xyz")
        assertNull("Non-existent achievement should return null", nonExistent)
    }

    @Test
    fun testAllDefinitionsHaveValidMetadata() {
        for (def in AchievementCatalog.definitions) {
            assertTrue("ID must not be blank: ${def.id}", def.id.isNotBlank())
            assertTrue("Title must not be blank: ${def.id}", def.title.isNotBlank())
            assertTrue("Description must not be blank: ${def.id}", def.description.isNotBlank())
            assertTrue("Target must be positive: ${def.id}", def.target > 0)
            assertNotNull("Rarity must be specified: ${def.id}", def.rarity)
        }
    }
}
