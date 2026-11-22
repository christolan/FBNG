package com.xiaojiwei.fbng.ui

import android.content.Context
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
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
import kotlinx.coroutines.launch
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
    fun launchPageStartsOnlyAfterConfirmingTheSelectedFixedSlot() {
        val game = LibraryGame("slot_test", "Slot test")
        val context = ApplicationProvider.getApplicationContext<Context>()
        val stateDir = AppStorage(context.filesDir).stateDir(game.name)
        val store = SaveStateStore(stateDir)
        store.write(byteArrayOf(1), coreCommit = "core")
        store.createManualSlot(byteArrayOf(2), coreCommit = "core")
        val launches = mutableListOf<Int?>()
        try {
            rule.setContent {
                FbngTheme {
                    GameLaunchPage(
                        entry = LibraryEntry(game, game.description, 'S'),
                        cover = null, fold = null, favorite = false,
                        busy = false, launchError = false,
                        onPlay = { launches.add(it) },
                        onToggleFavorite = {}, onConfigureFolders = {}, onBack = {},
                    )
                }
            }
            rule.waitUntil(5_000) { rule.onAllNodes(hasTestTag("progress-slot-3")).fetchSemanticsNodes().size == 1 }
            rule.onNodeWithTag(ShellTags.Play).assertIsFocused()
            rule.onNodeWithTag("progress-slot-3").performClick()
            rule.runOnIdle { assertTrue(launches.isEmpty()) }
            rule.onNodeWithTag(ShellTags.Play).performClick()
            rule.runOnIdle { assertEquals(listOf<Int?>(3), launches) }
            rule.onNodeWithTag("progress-slot-1").performClick()
            rule.onNodeWithTag(ShellTags.Play).performClick()
            rule.runOnIdle { assertEquals(listOf<Int?>(3, 1), launches) }
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
                    LibraryEntry(game, game.description, 'L'), null, null, false, store,
                    busy = false, ready = true, message = "", onPrimary = { launchedSlot = it },
                    onToggleFavorite = {}, onBack = {},
                )
            }
        }
        rule.onNodeWithTag("snapshots-tab").performClick()
        rule.waitUntil(5_000) { rule.onAllNodes(hasTestTag("retry-snapshots")).fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithTag(ShellTags.Play).assertIsEnabled().performClick()
        rule.runOnIdle { assertEquals(1, launchedSlot) }
        File(directory, "snapshots").delete()
        rule.onNodeWithTag("retry-snapshots").performClick()
        rule.waitUntil(5_000) {
            rule.onAllNodes(hasText("还没有快照。游玩时暂停即可生成。")).fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test
    fun pauseCanResumeWhenSlotAndSnapshotListingsFail() {
        val store = GameProgressStore(folder.newFile("unreadable-progress"))
        val game = LibraryGame("pause", "Pause")
        var resumedSlot: Int? = null
        rule.setContent {
            FbngTheme {
                GameProgressPage(
                    LibraryEntry(game, game.description, 'P'), null, null, false, store,
                    busy = false, ready = true, message = "", onPrimary = { resumedSlot = it },
                    onToggleFavorite = {}, onBack = {}, activeSlot = 2,
                )
            }
        }
        rule.waitUntil(5_000) { rule.onAllNodes(hasTestTag("retry-slots")).fetchSemanticsNodes().isNotEmpty() }
        val resume = rule.onNodeWithTag(ShellTags.Pause).assertIsFocused().assertIsEnabled()
        val resumeBounds = resume.getUnclippedBoundsInRoot()
        rule.onNodeWithTag("snapshots-tab").performClick()
        rule.waitUntil(5_000) { rule.onAllNodes(hasTestTag("retry-snapshots")).fetchSemanticsNodes().isNotEmpty() }
        assertEquals(resumeBounds, resume.getUnclippedBoundsInRoot())
        resume.assertIsEnabled().performClick()
        rule.runOnIdle { assertEquals(2, resumedSlot) }
    }

    @Test
    @Config(sdk = [33], qualifiers = "w540dp-h320dp-land-mdpi")
    fun pauseKeepsPrimaryAndSnapshotActionsVisibleAndRestoresCardFocus() {
        val directory = folder.newFolder("snapshots")
        val store = GameProgressStore(directory)
        repeat(7) { index ->
            val savedAt = 1_700_000_000_000L + index * 2_000L
            val id = store.createSnapshot(SavedSlot(byteArrayOf(1), "", savedAt), null, 1)
            assertTrue(File(directory, "snapshots/slot$id.save").setLastModified(savedAt))
        }
        val game = LibraryGame("pause", "Pause")
        var resumedSlot: Int? = null
        rule.setContent {
            FbngTheme {
                GameProgressPage(
                    LibraryEntry(game, game.description, 'P'), null, null, false, store,
                    busy = false, ready = true, message = "", onPrimary = { resumedSlot = it },
                    onToggleFavorite = {}, onBack = { resumedSlot = 1 }, activeSlot = 1,
                    onCreateSnapshot = { error("unexpected snapshot creation") }, onQuit = {},
                )
            }
        }
        val resume = rule.onNodeWithTag(ShellTags.Pause).assertIsFocused()
        val resumeBounds = resume.getUnclippedBoundsInRoot()
        resume.performKeyInput { pressKey(Key.ButtonR1) }
        rule.onNodeWithTag("snapshots-tab").assertIsFocused()
        awaitSnapshotFocus("snapshot-6", request = true)
        rule.onNodeWithTag("snapshot-6").performKeyInput { pressKey(Key.DirectionRight) }
        awaitSnapshotFocus("snapshot-3")
        rule.onNodeWithTag("snapshot-3").performKeyInput { pressKey(Key.DirectionRight) }
        awaitSnapshotFocus("snapshot-1")
        assertEquals(resumeBounds, resume.getUnclippedBoundsInRoot())
        for (tag in listOf("create-snapshot", "apply-snapshot", "delete-snapshot", "snapshot-prev")) {
            val action = rule.onNodeWithTag(tag).assertIsDisplayed().getUnclippedBoundsInRoot()
            assertTrue(action.bottom <= 320.dp)
        }
        rule.onNodeWithTag("delete-snapshot").performClick()
        val confirm = rule.onNode(hasText("删除快照") and hasClickAction() and hasAnyAncestor(isDialog()))
        rule.onNodeWithText("取消").assertIsFocused().performKeyInput { pressKey(Key.ButtonB) }
        rule.onNodeWithTag("delete-snapshot").assertIsFocused().performKeyInput { pressKey(Key.ButtonA) }
        confirm.assertIsDisplayed().performClick()
        awaitSnapshotFocus("snapshot-2")
        rule.onNodeWithTag("snapshot-2").performKeyInput { pressKey(Key.ButtonB) }
        rule.runOnIdle { assertEquals(1, resumedSlot) }
    }

    private fun awaitSnapshotFocus(tag: String, request: Boolean = false) {
        val node = rule.onNodeWithTag(tag)
        rule.waitUntil("$tag appears", 5_000) { rule.onAllNodes(hasTestTag(tag)).fetchSemanticsNodes().isNotEmpty() }
        if (request) node.requestFocus()
        rule.waitUntil("$tag is focused", 5_000) { rule.onAllNodes(hasTestTag(tag) and isFocused()).fetchSemanticsNodes().isNotEmpty() }
    }
}
