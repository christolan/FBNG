package com.xiaojiwei.fbng.ui

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performScrollToIndex
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
class SaveSlotsDialogTest {
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
                    onToggleFavorite = {}, onBack = {}, activeSlot = 1,
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
        rule.onNode(hasText("删除快照") and hasClickAction() and hasAnyAncestor(isDialog()))
            .assertIsDisplayed().performClick()
        awaitSnapshotFocus("snapshot-2")
        resume.assertIsEnabled().performClick()
        rule.runOnIdle { assertEquals(1, resumedSlot) }
    }

    private fun awaitSnapshotFocus(tag: String, request: Boolean = false) {
        val node = rule.onNodeWithTag(tag)
        rule.waitUntil("$tag appears", 5_000) { rule.onAllNodes(hasTestTag(tag)).fetchSemanticsNodes().isNotEmpty() }
        if (request) node.requestFocus()
        rule.waitUntil("$tag is focused", 5_000) { rule.onAllNodes(hasTestTag(tag) and isFocused()).fetchSemanticsNodes().isNotEmpty() }
    }

    @Test
    fun clearAllReturnsToItsButtonOnCancelAndRemovesAllSlotsOnConfirmation() {
        val store = SaveStateStore(folder.newFolder("clear"))
        store.write(byteArrayOf(1), coreCommit = "core")
        store.createManualSlot(byteArrayOf(2), coreCommit = "core")
        rule.setContent {
            var revision by remember { mutableStateOf(0) }
            FbngTheme {
                SaveSlotsDialog(
                    store = store,
                    mode = SaveSlotMode.Save,
                    busy = false,
                    message = "",
                    revision = revision,
                    onChoose = {},
                    onCreate = {},
                    onDelete = {},
                    onDismiss = {},
                    onClear = {
                        store.clear()
                        revision++
                    },
                )
            }
        }
        val clear = rule.onNodeWithText("清除全部存档")
        rule.waitUntil(5_000) { runCatching { clear.assertIsEnabled(); true }.getOrDefault(false) }
        clear.performClick()
        rule.onNodeWithText("取消").assertIsFocused().performKeyInput { pressKey(Key.ButtonB) }
        clear.assertIsFocused().performKeyInput { pressKey(Key.ButtonA) }
        rule.onNodeWithText("取消").assertIsFocused().performKeyInput { pressKey(Key.DirectionRight) }
        rule.onNode(hasText("清除全部存档") and isFocused()).performKeyInput { pressKey(Key.ButtonA) }
        assertFalse(store.hasSlot())
        assertFalse(store.hasSlot(1))
    }

    @Test
    fun newSlotStaysAvailableAfterSavingAndWhileScrollingUntilFull() {
        val store = SaveStateStore(folder.newFolder("slots"))
        var overwrittenSlot: Int? = null
        rule.setContent {
            var revision by remember { mutableStateOf(0) }
            var busy by remember { mutableStateOf(false) }
            val scope = rememberCoroutineScope()
            FbngTheme {
                SaveSlotsDialog(
                    store = store,
                    mode = SaveSlotMode.Save,
                    busy = busy,
                    message = "",
                    revision = revision,
                    onChoose = { overwrittenSlot = it },
                    onDelete = {},
                    onCreate = {
                        busy = true
                        scope.launch {
                            delay(100)
                            store.createManualSlot(byteArrayOf(1), coreCommit = "core")
                            revision++
                            busy = false
                        }
                    },
                    onDismiss = {},
                )
            }
        }

        repeat(10) { index ->
            rule.waitUntil(5_000) {
                rule.onAllNodes(hasText("新建槽位") and hasClickAction() and isFocused()).fetchSemanticsNodes().size == 1
            }
            if (index == 8) {
                rule.onNode(SemanticsMatcher.keyIsDefined(SemanticsProperties.HorizontalScrollAxisRange))
                    .performScrollToIndex(7)
            }
            val create = rule.onNodeWithText("新建槽位").assertIsDisplayed()
            if (index == 0) create.performClick()
            else create.assertIsFocused().performKeyInput { pressKey(Key.ButtonA) }
        }

        rule.waitUntil(5_000) {
            rule.onAllNodes(hasText("已达 10 个手动槽位上限，可覆盖已有存档。"))
                .fetchSemanticsNodes().size == 1
        }
        rule.onNodeWithText("新建槽位").assertIsDisplayed().assertIsNotEnabled()
        rule.onNodeWithText("已达上限").assertIsDisplayed()
        assertEquals((0..10).toList(), store.listSlots().map { it.id })
        val focusedSlot = isFocused() and hasText("槽位 10")
        rule.waitUntil(5_000) { rule.onAllNodes(focusedSlot).fetchSemanticsNodes().size == 1 }
        rule.onNode(focusedSlot).performKeyInput { pressKey(Key.ButtonA) }
        rule.onNodeWithText("覆盖存档").performClick()
        assertEquals(10, overwrittenSlot)
    }

    @Test
    fun deletingSlotsRequiresConfirmationAndRestoresFocus() {
        val store = SaveStateStore(folder.newFolder("delete"))
        store.write(byteArrayOf(0), coreCommit = "core")
        store.write(byteArrayOf(1), coreCommit = "core", id = 1)
        rule.setContent {
            var revision by remember { mutableStateOf(0) }
            FbngTheme {
                SaveSlotsDialog(
                    store = store,
                    mode = SaveSlotMode.Load,
                    busy = false,
                    message = "",
                    revision = revision,
                    onChoose = {},
                    onCreate = {},
                    onDelete = { store.delete(it); revision++ },
                    onDismiss = {},
                )
            }
        }
        val auto = hasText("自动存档") and hasClickAction()
        rule.waitUntil(5_000) { rule.onAllNodes(auto and isFocused()).fetchSemanticsNodes().size == 1 }
        rule.onNode(auto).performKeyInput { pressKey(Key.DirectionRight) }
        rule.onNodeWithText("槽位 1").assertIsFocused().performKeyInput { pressKey(Key.DirectionDown) }
        val deleteManual = rule.onNodeWithContentDescription("删除「槽位 1」存档")
        val cancel = hasText("取消") and isFocused()
        deleteManual.assertIsFocused().performKeyInput { pressKey(Key.ButtonA) }
        rule.waitUntil(5_000) { rule.onAllNodes(cancel).fetchSemanticsNodes().size == 1 }
        rule.onNode(cancel).performKeyInput { pressKey(Key.ButtonB) }
        assertTrue(store.hasSlot(1))
        deleteManual.assertIsFocused().performKeyInput { pressKey(Key.ButtonA) }
        rule.waitUntil(5_000) { rule.onAllNodes(cancel).fetchSemanticsNodes().size == 1 }
        rule.onNode(cancel).performKeyInput { pressKey(Key.DirectionRight) }
        val confirmation = hasText("删除存档") and hasClickAction() and SemanticsMatcher.keyNotDefined(SemanticsProperties.ContentDescription)
        rule.onNode(confirmation).assertIsFocused().performKeyInput { pressKey(Key.ButtonA) }
        rule.waitUntil(5_000) { rule.onAllNodes(auto and isFocused()).fetchSemanticsNodes().size == 1 }
        assertFalse(store.hasSlot(1))
        rule.onNode(auto).performKeyInput { pressKey(Key.DirectionDown) }
        rule.onNodeWithContentDescription("删除「自动存档」存档").assertIsFocused()
            .performKeyInput { pressKey(Key.ButtonA) }
        rule.onNode(confirmation).performClick()
        rule.waitUntil(5_000) {
            rule.onAllNodes(hasText("关闭") and isFocused()).fetchSemanticsNodes().size == 1
        }
        assertFalse(store.hasSlot())
    }
}
