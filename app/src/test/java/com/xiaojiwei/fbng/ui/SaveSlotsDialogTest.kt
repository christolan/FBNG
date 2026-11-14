package com.xiaojiwei.fbng.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.pressKey
import com.xiaojiwei.fbng.game.SaveStateStore
import com.xiaojiwei.fbng.ui.theme.FbngTheme
import org.junit.Assert.assertEquals
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
    fun newSlotStaysAvailableAfterSavingAndWhileScrollingUntilFull() {
        val store = SaveStateStore(folder.newFolder("slots"))
        var overwrittenSlot: Int? = null
        rule.setContent {
            var revision by remember { mutableStateOf(0) }
            FbngTheme {
                SaveSlotsDialog(
                    store = store,
                    mode = SaveSlotMode.Save,
                    busy = false,
                    message = "",
                    revision = revision,
                    onChoose = { overwrittenSlot = it },
                    onCreate = {
                        store.createManualSlot(byteArrayOf(1), coreCommit = "core")
                        revision++
                    },
                    onDismiss = {},
                )
            }
        }

        repeat(10) { index ->
            rule.waitUntil(5_000) {
                rule.onAllNodes(hasText("新建槽位") and hasClickAction()).fetchSemanticsNodes().size == 1
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
        assertEquals((0..10).toList(), store.listSlots().map { it.id })
        val focusedSlot = isFocused() and hasText("槽位 ", substring = true)
        rule.waitUntil(5_000) { rule.onAllNodes(focusedSlot).fetchSemanticsNodes().size == 1 }
        rule.onNode(focusedSlot).performKeyInput { pressKey(Key.ButtonA) }
        rule.onNodeWithText("覆盖存档").performClick()
        check(overwrittenSlot in 1..10)
    }
}
