package com.xiaojiwei.fbng.game

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class SaveStateStoreTest {
    @get:Rule
    val folder = TemporaryFolder()

    @Test
    fun slotSurvivesANewStoreInstance() {
        val directory = folder.newFolder("states")
        val first = SaveStateStore(directory)
        assertFalse(first.hasSlot())
        assertNull(first.read())

        val payload = byteArrayOf(1, 2, 3, 4, 5)
        first.write(payload, coreCommit = "77016b4dff6c76beba08d24ea1c61ed2750bc0a7", savedAtEpochMs = 100L)

        val second = SaveStateStore(File(directory.path))
        assertTrue(second.hasSlot())
        val slot = second.read()!!
        assertTrue(payload.contentEquals(slot.bytes))
        assertEquals("77016b4dff6c76beba08d24ea1c61ed2750bc0a7", slot.coreCommit)
        assertEquals(100L, slot.savedAtEpochMs)
    }
}
