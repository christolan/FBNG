package com.xiaojiwei.fbng.ui

import android.content.Context
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.click
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.xiaojiwei.fbng.game.SaveStateStore
import com.xiaojiwei.fbng.game.GameProgressStore
import com.xiaojiwei.fbng.game.SavedSlot
import com.xiaojiwei.fbng.library.AppStorage
import com.xiaojiwei.fbng.library.LibraryEntry
import com.xiaojiwei.fbng.library.LibraryGame
import com.xiaojiwei.fbng.ui.theme.FbngTheme
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33], qualifiers = "w800dp-h480dp-land-mdpi")
class GameProgressPageTest {
    @get:Rule
    val rule = createComposeRule()

    @get:Rule
    val folder = TemporaryFolder()

    @Test
    fun launchPageStartsDirectlyFromTheFocusedOrTappedSlot() {
        val game = LibraryGame("slot_test", "Slot test")
        val context = ApplicationProvider.getApplicationContext<Context>()
        val stateDir = AppStorage(context.filesDir).stateDir(game.name)
        val store = SaveStateStore(stateDir)
        store.write(byteArrayOf(1), coreCommit = "core")
        GameProgressStore(stateDir).saveSlot(2, SavedSlot(byteArrayOf(2), "core", 1), null)
        val launches = mutableListOf<Int?>()
        try {
            rule.setContent {
                FbngTheme {
                    GameLaunchPage(
                        entry = LibraryEntry(game, game.description, 'S'),
                        preview = GamePreviewArtwork(), fold = null, favorite = false,
                        busy = false, launchError = false,
                        onPlay = { launches.add(it) },
                        onToggleFavorite = {}, onConfigureFolders = {}, onBack = {},
                    )
                }
            }
            awaitFocus("progress-slot-1")
            rule.onNodeWithTag("progress-slot-1").performKeyInput { pressKey(Key.DirectionDown) }
            awaitFocus("progress-slot-2")
            rule.onNodeWithTag("delete-progress-slot-2").assertDoesNotExist()
            rule.onNodeWithTag("progress-slot-2").performKeyInput { pressKey(Key.ButtonX) }
            rule.onNode(hasText("取消") and hasClickAction() and hasAnyAncestor(isDialog()))
                .assertIsFocused().performKeyInput { pressKey(Key.ButtonB) }
            awaitFocus("progress-slot-2")
            rule.runOnIdle { assertTrue(launches.isEmpty()) }
            rule.onNodeWithTag("progress-slot-2").performKeyInput { pressKey(Key.ButtonA) }
            rule.runOnIdle { assertEquals(listOf<Int?>(2), launches) }
            rule.onNodeWithTag("progress-slot-4").performClick()
            rule.runOnIdle { assertEquals(listOf<Int?>(2, 4), launches) }
        } finally {
            stateDir.deleteRecursively()
        }
    }

    @Test
    fun snapshotListingFailureKeepsLaunchAvailable() {
        val directory = folder.newFolder("unreadable-snapshots")
        File(directory, "snapshots").writeText("not a directory")
        val store = GameProgressStore(directory)
        val game = LibraryGame("launch", "Launch")
        var launchedSlot: Int? = null
        rule.setContent {
            FbngTheme {
                GameProgressPage(
                    LibraryEntry(game, game.description, 'L'), GamePreviewArtwork(), null, false, store,
                    busy = false, ready = true, message = "", onPrimary = { launchedSlot = it },
                    onToggleFavorite = {}, onBack = {},
                )
            }
        }
        rule.onNodeWithTag("snapshots-tab").performTouchInput { click() }
        rule.waitUntil(5_000) { rule.onAllNodes(hasTestTag("retry-snapshots")).fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithTag("retry-snapshots").performKeyInput { pressKey(Key.ButtonL1) }
        awaitFocus("progress-slot-1")
        rule.onNodeWithTag("progress-slot-1").assertIsEnabled().performClick()
        rule.runOnIdle { assertEquals(1, launchedSlot) }
        rule.onNodeWithTag("progress-slot-1").assertIsFocused().performKeyInput { pressKey(Key.ButtonR1) }
        rule.waitUntil(5_000) { rule.onAllNodes(hasTestTag("retry-snapshots")).fetchSemanticsNodes().isNotEmpty() }
        File(directory, "snapshots").delete()
        rule.onNodeWithTag("retry-snapshots").performClick()
        rule.waitUntil(5_000) {
            rule.onAllNodes(hasText("还没有快照")).fetchSemanticsNodes().isNotEmpty()
        }
        awaitFocus(ShellTags.ToggleFavorite)
        rule.onNodeWithTag("snapshots-tab").assertIsSelected()
        rule.onNodeWithTag(ShellTags.ToggleFavorite).performKeyInput { pressKey(Key.DirectionLeft) }
        rule.onNode(isFocused() and (hasTestTag("slots-tab") or hasTestTag("snapshots-tab") or hasTestTag(ShellTags.DetailBack)))
            .assertDoesNotExist()
        rule.onNodeWithTag(ShellTags.ToggleFavorite).assertIsFocused().performKeyInput { pressKey(Key.ButtonL1) }
        awaitFocus("progress-slot-1")
    }

    @Test
    fun pauseCanResumeWhenSlotAndSnapshotListingsFail() {
        val store = GameProgressStore(folder.newFile("unreadable-progress"))
        val game = LibraryGame("pause", "Pause")
        var resumedSlot: Int? = null
        rule.setContent {
            FbngTheme {
                GameProgressPage(
                    LibraryEntry(game, game.description, 'P'), GamePreviewArtwork(), null, false, store,
                    busy = false, ready = true, message = "", onPrimary = { resumedSlot = it },
                    onToggleFavorite = {}, onBack = { resumedSlot = 2 }, activeSlot = 2,
                )
            }
        }
        rule.waitUntil(5_000) { rule.onAllNodes(hasTestTag("retry-slots")).fetchSemanticsNodes().isNotEmpty() }
        val resume = rule.onNodeWithTag(ShellTags.Pause).assertIsEnabled()
        val resumeBounds = resume.getUnclippedBoundsInRoot()
        rule.onNodeWithTag("snapshots-tab").performClick()
        rule.waitUntil(5_000) { rule.onAllNodes(hasTestTag("retry-snapshots")).fetchSemanticsNodes().isNotEmpty() }
        assertEquals(resumeBounds, resume.getUnclippedBoundsInRoot())
        resume.assertIsEnabled().performClick()
        rule.runOnIdle { assertEquals(2, resumedSlot) }
    }

    @Test
    @Config(sdk = [33], qualifiers = "w540dp-h320dp-land-mdpi")
    fun pauseScrollsSnapshotRowsAndRestoresFocusAfterSnapshotActions() {
        val directory = folder.newFolder("snapshots")
        val store = GameProgressStore(directory)
        repeat(7) { index ->
            val savedAt = 1_700_000_000_000L + index * 2_000L
            val id = store.createSnapshot(SavedSlot(byteArrayOf(1), "", savedAt), null, 1)
            assertTrue(File(directory, "snapshots/slot$id.save").setLastModified(savedAt))
        }
        store.saveSlot(1, SavedSlot(byteArrayOf(2), "", 1), null)
        val game = LibraryGame("pause", "Pause")
        var resumed = false
        val applied = mutableListOf<Pair<Int, Int>>()
        rule.setContent {
            FbngTheme {
                GameProgressPage(
                    LibraryEntry(game, game.description, 'P'), GamePreviewArtwork(), null, false, store,
                    busy = false, ready = true, message = "", onPrimary = {},
                    onToggleFavorite = {}, onBack = { resumed = true }, activeSlot = 1,
                    onCreateSnapshot = { error("unexpected snapshot creation") }, onQuit = {},
                    onApplySnapshot = { id, slot -> applied.add(id to slot) },
                )
            }
        }
        awaitFocus("progress-slot-1")
        val resume = rule.onNodeWithTag(ShellTags.Pause)
        val resumeBounds = resume.getUnclippedBoundsInRoot()
        rule.onNodeWithTag("progress-slot-1").performKeyInput { pressKey(Key.ButtonR1) }
        awaitFocus("snapshot-7")
        rule.onNodeWithTag("create-snapshot").assertIsDisplayed()
        rule.onNode(hasTestTag("snapshot-prev") or hasTestTag("snapshot-next")).assertDoesNotExist()
        for (id in 4..7) {
            val item = rule.onNodeWithTag("snapshot-$id").assertIsDisplayed().getUnclippedBoundsInRoot()
            rule.onNodeWithTag("delete-snapshot-$id").assertDoesNotExist()
            assertTrue(item.bottom <= 320.dp)
        }

        rule.onNodeWithTag("snapshot-7").performKeyInput { pressKey(Key.DirectionDown) }
        awaitFocus("snapshot-6")
        for (id in 6 downTo 3) {
            rule.onNodeWithTag("snapshot-$id").performKeyInput { pressKey(Key.DirectionDown) }
            awaitFocus("snapshot-${id - 1}")
        }
        rule.onNodeWithTag("snapshot-2").assertIsDisplayed()
        rule.onNodeWithTag("snapshot-list").performTouchInput { swipeDown() }
        rule.onNodeWithTag("snapshot-7").assertIsDisplayed()
        rule.onNodeWithTag("slots-tab").performClick()
        awaitFocus("progress-slot-1")
        rule.onNodeWithTag("progress-slot-1").performKeyInput { pressKey(Key.ButtonR1) }
        awaitFocus("snapshot-2")
        assertEquals(resumeBounds, resume.getUnclippedBoundsInRoot())
        rule.onNodeWithTag("snapshot-2").performKeyInput { pressKey(Key.ButtonX) }
        rule.onNode(hasText("取消") and hasClickAction() and hasAnyAncestor(isDialog())).assertIsFocused().performKeyInput { pressKey(Key.ButtonB) }
        awaitFocus("snapshot-2")
        rule.onNodeWithTag("snapshot-2").performKeyInput { pressKey(Key.ButtonX) }
        rule.onNode(hasText("删除快照") and hasClickAction() and hasAnyAncestor(isDialog())).performClick()
        awaitFocus("snapshot-1")

        rule.onNodeWithTag("snapshot-1").performKeyInput { pressKey(Key.ButtonA) }
        awaitFocus("snapshot-target-1")
        rule.onNodeWithTag("snapshot-target-1").performKeyInput { pressKey(Key.ButtonA) }
        rule.waitUntil(5_000) { rule.onAllNodes(hasText("覆盖存档 1？")).fetchSemanticsNodes().isNotEmpty() }
        rule.onNode(hasText("取消") and hasClickAction() and hasAnyAncestor(isDialog())).assertIsFocused().performKeyInput { pressKey(Key.ButtonB) }
        awaitFocus("snapshot-target-1")
        rule.runOnIdle { assertTrue(applied.isEmpty()) }
        rule.onNodeWithTag("snapshot-target-4").performClick()
        awaitFocus("progress-slot-4")
        rule.runOnIdle { assertEquals(listOf(1 to 4), applied) }
        rule.onNodeWithTag("progress-slot-4").performKeyInput { pressKey(Key.ButtonB) }
        rule.runOnIdle { assertTrue(resumed) }
    }

    private fun awaitFocus(tag: String) {
        rule.waitUntil("$tag is focused", 5_000) {
            rule.onAllNodes(hasTestTag(tag) and isFocused()).fetchSemanticsNodes().isNotEmpty()
        }
    }
}
