package com.xiaojiwei.fbng.library

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class LibraryBrowseTest {
    @Test
    fun groupsByDisplayTitleAndKeepsNumericOrderWithinAGroup() {
        val games = listOf(
            LibraryGame("mslug10", "Metal Slug 10"),
            LibraryGame("number", "1942"),
            LibraryGame("kovsh", "Knights of Valour Super Heroes"),
            LibraryGame("galaga", "Galaga"),
            LibraryGame("mslug2", "Metal Slug 2"),
        )
        val titles = mapOf("mslug10" to "合金弹头 10", "mslug2" to "合金弹头 2", "kovsh" to "《三国战纪：风云再起》")
        val results = LibraryResults(indexLibrary(games, titles))

        assertEquals(listOf('G', 'H', 'S', '#'), results.groups.keys.toList())
        assertEquals(listOf("mslug2", "mslug10"), results.groups.getValue('H').map { it.game.name })
        assertEquals(mapOf("galaga" to 1, "mslug2" to 3, "mslug10" to 4, "kovsh" to 6, "number" to 8), results.rowByName)
        assertEquals("Galaga", results.byName.getValue("galaga").title)

        val entry = results.byName.getValue("mslug2")
        assertTrue(listOf("弹头", "METAL SLUG", "mslug2").all(entry::matches))
    }
}
