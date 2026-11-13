package com.xiaojiwei.fbng.ui

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.click
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
import com.xiaojiwei.fbng.dat.DatCatalog
import com.xiaojiwei.fbng.library.FolderScan
import com.xiaojiwei.fbng.library.LibraryGame
import com.xiaojiwei.fbng.library.LibraryFolder
import com.xiaojiwei.fbng.library.LibrarySnapshot
import com.xiaojiwei.fbng.ui.theme.FbngTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33], qualifiers = "mdpi")
class ShellFocusTest {
    @get:Rule
    val rule = createComposeRule()

    @Test
    @Config(sdk = [33], qualifiers = "w900dp-h640dp-mdpi")
    fun dpadSelectsTheNextGameAndStartsItsSet() {
        var played: String? = null
        rule.setContent {
            FbngTheme {
                LibraryContent(
                    load = readyCatalog(),
                    snapshot = readySnapshot(),
                    busy = false,
                    readFold = { null },
                    onPlay = { played = it },
                )
            }
        }
        rule.onNodeWithTag(ShellTags.game("galaga")).assertIsFocused()
            .performKeyInput { pressKey(Key.DirectionDown) }
        rule.onNodeWithTag(ShellTags.game("mslug")).assertIsFocused()
            .performKeyInput { pressKey(Key.ButtonA) }
        rule.onNodeWithTag(ShellTags.Play).assertIsFocused().performKeyInput { pressKey(Key.ButtonA) }
        assertEquals("mslug", played)
    }

    @Test
    @Config(sdk = [33], qualifiers = "w900dp-h640dp-mdpi")
    fun gamesStayPlayableAndFocusedWhileFolderResultsArrive() {
        var snapshot by mutableStateOf(readySnapshot())
        var played: String? = null
        rule.setContent {
            FbngTheme {
                LibraryContent(
                    load = readyCatalog(),
                    snapshot = snapshot,
                    busy = true,
                    playBusy = false,
                    readFold = { null },
                    onPlay = { played = it },
                )
            }
        }
        rule.onNodeWithTag(ShellTags.game("galaga")).assertIsFocused()
            .performKeyInput { pressKey(Key.DirectionDown) }
        rule.onNodeWithTag(ShellTags.game("mslug")).assertIsFocused()
        rule.runOnIdle { snapshot = LibrarySnapshot(listOf(LibraryGame("1942", "1942")) + snapshot.games) }
        rule.onNodeWithTag(ShellTags.game("mslug")).assertIsFocused()
            .performKeyInput { pressKey(Key.ButtonA) }
        rule.onNodeWithTag(ShellTags.Play).assertIsFocused().performKeyInput { pressKey(Key.ButtonA) }
        assertEquals("mslug", played)
    }

    @Test
    @Config(sdk = [33], qualifiers = "w400dp-h800dp-mdpi")
    fun compactDetailsReturnsToTheSelectedGame() {
        rule.setContent {
            FbngTheme {
                LibraryContent(
                    load = readyCatalog(),
                    snapshot = readySnapshot(),
                    busy = false,
                    readFold = { null },
                    onPlay = {},
                )
            }
        }
        rule.onNodeWithTag(ShellTags.game("galaga")).assertIsFocused()
            .performKeyInput { pressKey(Key.DirectionDown) }
        rule.onNodeWithTag(ShellTags.game("mslug")).assertIsFocused()
            .performKeyInput { pressKey(Key.ButtonA) }
        rule.onNodeWithTag(ShellTags.Play).assertIsFocused().performKeyInput { pressKey(Key.ButtonB) }
        rule.onNodeWithTag(ShellTags.game("mslug")).assertIsFocused()
    }

    @Test
    @Config(sdk = [33], qualifiers = "w400dp-h360dp-mdpi")
    fun controllerScrollsTheRecentCoversAndRestoresFocusAfterPlay() {
        var inGame by mutableStateOf(false)
        val names = listOf("galaga", "mslug", "sf2", "kof98")
        rule.setContent {
            FbngTheme {
                val state = rememberSaveableStateHolder()
                if (!inGame) state.SaveableStateProvider("shell") {
                    LibraryContent(
                        load = readyCatalog(),
                        snapshot = LibrarySnapshot(names.map { LibraryGame(it, it) }),
                        busy = false,
                        readFold = { null },
                        onPlay = { inGame = true },
                        initialSection = ShellSection.Recent,
                        recentNames = names,
                    )
                }
            }
        }
        names.zipWithNext().forEach { (current, next) ->
            rule.onNodeWithTag(ShellTags.recent(current)).assertIsFocused()
                .performKeyInput { pressKey(Key.DirectionRight) }
            rule.onNodeWithTag(ShellTags.recent(next)).assertIsFocused()
        }
        rule.onNodeWithTag(ShellTags.recent(names.last())).performKeyInput { pressKey(Key.ButtonA) }
        rule.runOnIdle {
            assertTrue(inGame)
            inGame = false
        }
        rule.onNodeWithTag(ShellTags.recent(names.last())).assertIsFocused()
    }

    @Test
    @Config(sdk = [33], qualifiers = "w400dp-h800dp-mdpi")
    fun pagingKeepsTheHeaderFixedAndGameCardsTheSameSize() {
        val games = (1..20).map { index ->
            LibraryGame("game$index", if (index == 1) "Arcade 1" else "Arcade $index with a very long title")
        }
        rule.setContent {
            FbngTheme {
                LibraryContent(
                    load = readyCatalog(),
                    snapshot = LibrarySnapshot(games),
                    busy = false,
                    readFold = { null },
                    onPlay = {},
                )
            }
        }
        val title = rule.onNode(hasText("游戏库") and SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading))
            .assertIsDisplayed()
        val titleBounds = title.getUnclippedBoundsInRoot()
        val first = rule.onNodeWithTag(ShellTags.game(games[0].name))
        val firstBounds = first.getUnclippedBoundsInRoot()
        val secondBounds = rule.onNodeWithTag(ShellTags.game(games[1].name)).getUnclippedBoundsInRoot()
        assertEquals(firstBounds.right - firstBounds.left, secondBounds.right - secondBounds.left)
        assertEquals(firstBounds.bottom - firstBounds.top, secondBounds.bottom - secondBounds.top)
        val nextPage = rule.onNodeWithTag(ShellTags.NextPage).getUnclippedBoundsInRoot()
        assertTrue(nextPage.bottom < firstBounds.top)
        rule.onNodeWithTag(ShellTags.JumpToLetter).assertIsDisplayed()
        rule.onNodeWithTag(ShellTags.LibraryFilter).assertIsDisplayed()
        rule.onNodeWithTag(ShellTags.game(games[0].name)).assertIsFocused()
            .performKeyInput { pressKey(Key.ButtonR1) }
        rule.onNodeWithTag(ShellTags.game(games[0].name)).assertDoesNotExist()
        assertEquals(titleBounds, title.getUnclippedBoundsInRoot())
    }

    @Test
    @Config(sdk = [33], qualifiers = "w320dp-h360dp-mdpi")
    fun compactTabsAndSubpagesUseTheSameHeaderBounds() {
        rule.setContent {
            FbngTheme {
                LibraryContent(
                    load = readyCatalog(),
                    snapshot = readySnapshot(),
                    busy = false,
                    readFold = { null },
                    onPlay = {},
                    initialSection = ShellSection.Favorites,
                )
            }
        }
        val header = rule.onNodeWithTag(ShellTags.Header)
        val initial = header.getUnclippedBoundsInRoot()
        rule.onNodeWithTag(ShellTags.NavRecent).performClick()
        assertEquals(initial, header.getUnclippedBoundsInRoot())
        rule.onNodeWithTag(ShellTags.NavLibrary).performClick()
        assertEquals(initial, header.getUnclippedBoundsInRoot())
        rule.onNode(hasText("游戏库") and SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading)).assertIsDisplayed()
        listOf(ShellTags.JumpToLetter, ShellTags.LibraryFilter, ShellTags.PreviousPage, ShellTags.NextPage).forEach { tag ->
            val control = rule.onNodeWithTag(tag).assertIsDisplayed().getUnclippedBoundsInRoot()
            assertTrue(control.left >= initial.left && control.right <= initial.right)
            assertTrue(control.top >= initial.top && control.bottom <= initial.bottom)
        }
        rule.onNode(SemanticsMatcher.keyIsDefined(SemanticsProperties.EditableText)).assertDoesNotExist()
        rule.onNodeWithTag(ShellTags.game("galaga")).performClick()
        assertEquals(initial, header.getUnclippedBoundsInRoot())
        rule.onNodeWithTag(ShellTags.NavSettings).performClick()
        assertEquals(initial, header.getUnclippedBoundsInRoot())
        rule.onNodeWithTag(ShellTags.OpenImport).performClick()
        assertEquals(initial, header.getUnclippedBoundsInRoot())
        rule.onNodeWithTag(ShellTags.SettingsBack).performClick()
        rule.onNodeWithTag(ShellTags.OpenSync).performClick()
        assertEquals(initial, header.getUnclippedBoundsInRoot())
    }

    @Test
    @Config(sdk = [33], qualifiers = "w800dp-h320dp-land-mdpi")
    fun everyLetterKeyIsVisibleInLandscapeAndButtonBReturnsToTheFilter() {
        rule.setContent {
            FbngTheme {
                LibraryContent(
                    load = readyCatalog(),
                    snapshot = readySnapshot(),
                    busy = false,
                    readFold = { null },
                    onPlay = {},
                )
            }
        }
        val filter = rule.onNodeWithTag(ShellTags.JumpToLetter)
        filter.requestFocus()
        filter.performKeyInput { pressKey(Key.ButtonA) }
        rule.onNodeWithTag(ShellTags.letter('A')).assertIsFocused()
        "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789._-".forEach { character ->
            rule.onNodeWithTag(ShellTags.letter(character)).assertIsDisplayed()
        }
        listOf("退格", "清空", "完成").forEach { rule.onNodeWithText(it).assertIsDisplayed() }
        rule.onNodeWithTag(ShellTags.letter('Q')).performTouchInput { click() }
        rule.onNodeWithText("0 款游戏").assertIsDisplayed()
        rule.onNodeWithText("清空").performClick()
        rule.onNodeWithTag(ShellTags.letter('M')).performClick()
        rule.onNodeWithTag(ShellTags.letter('S')).performClick()
        rule.onNodeWithTag(ShellTags.letter('A')).performKeyInput { pressKey(Key.ButtonB) }
        filter.assertIsFocused()
        rule.onNodeWithTag(ShellTags.game("mslug")).assertIsDisplayed()
        rule.onNodeWithTag(ShellTags.game("galaga")).assertDoesNotExist()
    }

    @Test
    @Config(sdk = [33], qualifiers = "w400dp-h800dp-mdpi")
    fun buttonBReturnsFromImportToItsSettingsEntry() {
        rule.setContent {
            FbngTheme {
                LibraryContent(
                    load = readyCatalog(),
                    snapshot = LibrarySnapshot(),
                    busy = false,
                    readFold = { null },
                    onPlay = {},
                    initialSection = ShellSection.Import,
                )
            }
        }
        rule.onNodeWithText("设置/导入").assertIsDisplayed()
        rule.onNodeWithText("导入 zip").assertDoesNotExist()
        rule.onNodeWithTag(ShellTags.AddFolder).assertIsFocused()
        rule.onNodeWithTag(ShellTags.AddFolder).performKeyInput { pressKey(Key.ButtonB) }
        rule.onNodeWithTag(ShellTags.OpenImport).assertIsFocused()
    }

    @Test
    @Config(sdk = [33], qualifiers = "w400dp-h800dp-mdpi")
    fun dpadReachesFolderControlsAndRemovalRestoresFocus() {
        val uri = Uri.parse("content://test/tree/arcade")
        val remove = ShellTags.removeFolder(uri)
        rule.setContent {
            var folders by remember { mutableStateOf(listOf(FolderScan(LibraryFolder(uri, "街机"), roms = 1))) }
            FbngTheme {
                LibraryContent(
                    load = readyCatalog(),
                    snapshot = LibrarySnapshot(),
                    busy = false,
                    readFold = { null },
                    onPlay = {},
                    initialSection = ShellSection.Import,
                    folders = folders,
                    folderActions = FolderActions(remove = { removed ->
                        folders = folders.filterNot { it.folder.uri == removed }
                    }),
                )
            }
        }
        rule.onNodeWithTag(ShellTags.AddFolder).assertIsFocused().performKeyInput { pressKey(Key.DirectionDown) }
        rule.onNodeWithTag(ShellTags.ScanFolders).assertIsFocused().performKeyInput { pressKey(Key.DirectionDown) }
        rule.onNodeWithTag(remove).assertIsFocused().performKeyInput { pressKey(Key.ButtonA) }
        rule.onNodeWithTag(remove).assertDoesNotExist()
        rule.onNodeWithTag(ShellTags.AddFolder).assertIsFocused()
    }

    @Test
    @Config(sdk = [33], qualifiers = "w400dp-h800dp-mdpi")
    fun missingCoreStartsOnQuitAndMovesUpToTheMessage() {
        rule.setContent {
            FbngTheme { MissingCore(onQuit = {}) }
        }
        rule.onNodeWithTag(ShellTags.Quit).assertIsFocused()
        rule.onNodeWithTag(ShellTags.Quit).performKeyInput { pressKey(Key.DirectionUp) }
        rule.onNodeWithTag(ShellTags.CoreMissing).assertIsFocused()
    }

    @Test
    @Config(sdk = [33], qualifiers = "w800dp-h480dp-mdpi")
    fun dpadDownMovesBetweenHudActions() {
        rule.setContent {
            FbngTheme {
                Box(Modifier.fillMaxSize().background(Color.Black)) {
                    GameHud(
                        layout = GameHudLayout(
                            band = HudBand.Sides,
                            axis = BarAxis.Sides,
                            barPx = 220,
                            gameLeftPx = 220,
                            gameTopPx = 0,
                            gameWidthPx = 360,
                            gameHeightPx = 480,
                            aspectUsed = 4f / 3f,
                            usedFallback = false,
                        ),
                        ready = true,
                        busy = false,
                        hasSlot = true,
                        message = "",
                        onSave = {},
                        onLoad = {},
                        onQuit = {},
                    )
                }
            }
        }
        rule.onNodeWithTag(ShellTags.Save).requestFocus()
        rule.waitForIdle()
        rule.onNodeWithTag(ShellTags.Save).assertIsFocused()
        rule.onNodeWithTag(ShellTags.Save).performKeyInput { pressKey(Key.DirectionDown) }
        rule.onNodeWithTag(ShellTags.Load).assertIsFocused()
    }
}

private fun readyCatalog() = CatalogLoad.Ready(DatCatalog("test", emptyList()))

private fun readySnapshot() = LibrarySnapshot(listOf(
    LibraryGame("galaga", "Galaga"),
    LibraryGame("mslug", "Metal Slug"),
))
