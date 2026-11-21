package com.xiaojiwei.fbng.ui

import android.content.Context
import android.net.Uri
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.click
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.xiaojiwei.fbng.FbngApplication
import com.xiaojiwei.fbng.dat.DatCatalog
import com.xiaojiwei.fbng.game.GamepadBridge
import com.xiaojiwei.fbng.library.FolderScan
import com.xiaojiwei.fbng.library.LibraryGame
import com.xiaojiwei.fbng.library.LibraryFolder
import com.xiaojiwei.fbng.library.LibrarySnapshot
import com.xiaojiwei.fbng.library.MissingDependency
import com.xiaojiwei.fbng.library.indexLibrary
import com.xiaojiwei.fbng.ui.theme.CornerRadiusOption
import com.xiaojiwei.fbng.ui.theme.FbngTheme
import com.xiaojiwei.fbng.update.UpdateState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowDialog

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33], qualifiers = "mdpi")
class ShellFocusTest {
    @get:Rule
    val rule = createComposeRule()

    private fun awaitGamePage() {
        rule.waitUntil(5_000) {
            runCatching { rule.onNodeWithTag(ShellTags.Play).assertIsFocused(); true }.getOrDefault(false)
        }
    }

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
                    onPlay = { entry, _ -> played = entry.game.name },
                )
            }
        }
        val rail = listOf(ShellTags.NavFavorites, ShellTags.NavRecent, ShellTags.NavLibrary, ShellTags.NavSettings)
            .map { rule.onNodeWithTag(it).getUnclippedBoundsInRoot() }
        val heights = rail.map { (it.bottom - it.top).value }
        assertTrue(heights.max() - heights.min() <= 1f)
        val header = rule.onNodeWithTag(ShellTags.Header).getUnclippedBoundsInRoot()
        assertEquals(8.dp, rail.first().top - header.top)
        assertTrue(rail.zipWithNext().all { (left, right) -> left.right < right.left && left.top == right.top })
        rule.onNodeWithTag(ShellTags.game("galaga")).assertIsFocused()
            .performKeyInput { pressKey(Key.DirectionRight) }
        rule.onNodeWithTag(ShellTags.game("mslug")).assertIsFocused()
            .performKeyInput { pressKey(Key.ButtonA) }
        awaitGamePage()
        rule.onNodeWithTag(ShellTags.Play).assertIsFocused().performKeyInput { pressKey(Key.ButtonA) }
        assertEquals("mslug", played)
    }

    @Test
    @Config(sdk = [33], qualifiers = "w400dp-h360dp-mdpi")
    fun favoritesCanBeRemovedWithTheGamepadAndByTouch() {
        var favorites by mutableStateOf(setOf("galaga", "mslug"))
        rule.setContent {
            FbngTheme {
                LibraryContent(
                    load = readyCatalog(),
                    snapshot = readySnapshot(),
                    busy = false,
                    readFold = { null },
                    onPlay = { _, _ -> },
                    initialSection = ShellSection.Favorites,
                    favoriteNames = favorites,
                    onToggleFavorite = { favorites = favorites - it },
                )
            }
        }
        rule.onNodeWithText("★ 已收藏").assertDoesNotExist()
        rule.onNodeWithTag(ShellTags.favorite("galaga")).assertIsFocused()
            .performKeyInput { pressKey(Key.ButtonA) }
        awaitGamePage()
        rule.onNodeWithTag(ShellTags.ToggleFavorite).requestFocus().assertIsFocused()
            .performKeyInput { pressKey(Key.ButtonA) }
        rule.onNodeWithText("☆ 收藏").assertIsFocused().performKeyInput { pressKey(Key.ButtonB) }
        rule.onNodeWithTag(ShellTags.favorite("galaga")).assertDoesNotExist()
        rule.onNodeWithTag(ShellTags.favorite("mslug")).assertIsFocused().performTouchInput { click() }
        awaitGamePage()
        rule.onNodeWithTag(ShellTags.ToggleFavorite).performTouchInput { click() }
        rule.onNodeWithTag(ShellTags.DetailBack).performTouchInput { click() }
        rule.onNodeWithTag(ShellTags.OpenLibrary).assertIsFocused()
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
                    onPlay = { entry, _ -> played = entry.game.name },
                )
            }
        }
        rule.onNodeWithTag(ShellTags.game("galaga")).assertIsFocused()
            .performKeyInput { pressKey(Key.DirectionRight) }
        rule.onNodeWithTag(ShellTags.game("mslug")).assertIsFocused()
        rule.runOnIdle { snapshot = LibrarySnapshot(listOf(LibraryGame("1942", "1942")) + snapshot.games) }
        rule.onNodeWithTag(ShellTags.game("mslug")).assertIsFocused()
            .performKeyInput { pressKey(Key.ButtonA) }
        awaitGamePage()
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
                    onPlay = { _, _ -> },
                )
            }
        }
        rule.onNodeWithTag(ShellTags.game("galaga")).assertIsFocused()
            .performKeyInput { pressKey(Key.DirectionRight) }
        rule.onNodeWithTag(ShellTags.game("mslug")).assertIsFocused()
            .performKeyInput { pressKey(Key.ButtonA) }
        awaitGamePage()
        rule.onNodeWithTag(ShellTags.Play).assertIsFocused().performKeyInput { pressKey(Key.ButtonB) }
        rule.onNodeWithTag(ShellTags.game("mslug")).assertIsFocused()
    }

    @Test
    @Config(sdk = [33], qualifiers = "w400dp-h800dp-mdpi")
    fun recentPagesUseAListAndRestoreTheGameAfterReordering() {
        var height by mutableStateOf(800.dp)
        var inGame by mutableStateOf(false)
        var returnFromGame by mutableStateOf<String?>(null)
        var recentNames by mutableStateOf((1..6).map { "game$it" })
        val games = LibrarySnapshot(recentNames.map { LibraryGame(it, it) })
        rule.setContent {
            FbngTheme {
                val state = rememberSaveableStateHolder()
                Box(Modifier.height(height)) {
                    if (!inGame) state.SaveableStateProvider("shell") {
                        LibraryContent(
                            load = readyCatalog(),
                            snapshot = games,
                            busy = false,
                            readFold = { null },
                            onPlay = { _, _ -> inGame = true },
                            initialSection = ShellSection.Recent,
                            recentNames = recentNames,
                            returnFromGame = returnFromGame,
                            onReturnRestored = { returnFromGame = null },
                        )
                    }
                }
            }
        }
        fun card(index: Int) = rule.onNodeWithTag(ShellTags.recent("game$index"))

        rule.onNodeWithContentDescription("第 1 / 2 页").assertIsDisplayed()
        val first = card(1).getUnclippedBoundsInRoot()
        val fourth = card(4).getUnclippedBoundsInRoot()
        val header = rule.onNodeWithTag(ShellTags.Header).getUnclippedBoundsInRoot()
        assertEquals(header.left + 8.dp, first.left)
        assertEquals(first.right, fourth.right)
        assertTrue(fourth.top > first.top)
        card(5).assertIsDisplayed()
        card(6).assertDoesNotExist()

        card(4).requestFocus()
        rule.runOnIdle { height = 280.dp }
        card(4).assertIsFocused()
        assertTrue(card(1).getUnclippedBoundsInRoot().top < card(4).getUnclippedBoundsInRoot().top)
        rule.runOnIdle { height = 800.dp }
        card(4).assertIsFocused()

        card(1).requestFocus().performKeyInput { pressKey(Key.DirectionDown) }
        card(2).assertIsFocused().performKeyInput { pressKey(Key.DirectionRight) }
        rule.onNodeWithContentDescription("第 2 / 2 页").assertIsDisplayed()
        assertEquals(first, card(6).getUnclippedBoundsInRoot())
        card(6).assertIsFocused().performKeyInput { pressKey(Key.DirectionLeft) }
        card(1).assertIsFocused()
        rule.onNodeWithTag(ShellTags.NextPage).performTouchInput { click() }
        card(6).assertIsFocused().performKeyInput { pressKey(Key.ButtonA) }
        rule.runOnIdle { assertTrue(!inGame) }
        awaitGamePage()
        rule.onNodeWithTag(ShellTags.Play).assertIsFocused().performKeyInput { pressKey(Key.ButtonA) }
        rule.runOnIdle {
            assertTrue(inGame)
            recentNames = listOf("game6") + recentNames.dropLast(1)
            returnFromGame = "game6"
            inGame = false
        }
        rule.onNodeWithContentDescription("第 1 / 2 页").assertIsDisplayed()
        card(6).assertIsFocused()
    }

    @Test
    @Config(sdk = [33], qualifiers = "w900dp-h400dp-mdpi")
    fun favoriteGalleryMovesAcrossCardsAndRestoresFocusAfterRemoval() {
        var favorites by mutableStateOf((1..5).map { "game$it" }.toSet())
        val games = LibrarySnapshot(favorites.map { LibraryGame(it, it) })
        rule.setContent {
            FbngTheme {
                LibraryContent(
                    load = readyCatalog(),
                    snapshot = games,
                    busy = false,
                    readFold = { null },
                    onPlay = { _, _ -> },
                    initialSection = ShellSection.Favorites,
                    favoriteNames = favorites,
                    onToggleFavorite = { favorites = favorites - it },
                )
            }
        }
        fun card(index: Int) = rule.onNodeWithTag(ShellTags.favorite("game$index"))

        val first = card(1).getUnclippedBoundsInRoot()
        val fourth = card(4).getUnclippedBoundsInRoot()
        val header = rule.onNodeWithTag(ShellTags.Header).getUnclippedBoundsInRoot()
        assertEquals(header.left, first.left)
        assertEquals(header.right, fourth.right)
        assertEquals(first.top, fourth.top)
        assertEquals(first.bottom, fourth.bottom)
        card(5).assertDoesNotExist()
        (1..4).forEach { card(it).assertIsFocused().performKeyInput { pressKey(Key.DirectionRight) } }
        card(5).assertIsFocused().performKeyInput { pressKey(Key.DirectionLeft) }
        card(4).assertIsFocused().performKeyInput { pressKey(Key.DirectionRight) }
        card(5).assertIsFocused()
        rule.onNodeWithContentDescription("第 2 / 2 页").assertIsDisplayed()
        card(5).performTouchInput { click() }
        rule.onNodeWithTag(ShellTags.ToggleFavorite).performTouchInput { click() }
        rule.onNodeWithTag(ShellTags.DetailBack).performTouchInput { click() }
        rule.onNodeWithContentDescription("第 1 / 1 页").assertIsDisplayed()
        card(4).assertIsFocused()
    }

    @Test
    @Config(sdk = [33], qualifiers = "w400dp-h800dp-mdpi")
    fun pagingKeepsTheHeaderFixedAndGameCardsTheSameSize() {
        val games = (1..22).map { index ->
            LibraryGame("game$index", if (index == 1) "Arcade 1" else "Arcade $index with a very long title")
        }
        rule.setContent {
            FbngTheme {
                LibraryContent(
                    load = readyCatalog(),
                    snapshot = LibrarySnapshot(games),
                    busy = false,
                    readFold = { null },
                    onPlay = { _, _ -> },
                )
            }
        }
        val title = rule.onNodeWithTag(ShellTags.HeaderTitle).assertIsDisplayed()
        val titleBounds = title.getUnclippedBoundsInRoot()
        val first = rule.onNodeWithTag(ShellTags.game(games[0].name))
        val firstBounds = first.getUnclippedBoundsInRoot()
        val secondBounds = rule.onNodeWithTag(ShellTags.game(games[1].name)).getUnclippedBoundsInRoot()
        assertEquals(firstBounds.right - firstBounds.left, secondBounds.right - secondBounds.left)
        assertTrue(kotlin.math.abs((firstBounds.bottom - firstBounds.top - (secondBounds.bottom - secondBounds.top)).value) <= 1f)
        val nextPage = rule.onNodeWithTag(ShellTags.NextPage).getUnclippedBoundsInRoot()
        assertTrue(nextPage.top > firstBounds.bottom)
        rule.onNodeWithTag(ShellTags.JumpToLetter).assertIsDisplayed()
        rule.onNodeWithTag(ShellTags.LibraryFilter).assertIsDisplayed()
        fun card(index: Int) = rule.onNodeWithTag(ShellTags.game(games[index].name))
        card(0).assertIsFocused().performKeyInput { pressKey(Key.DirectionDown) }
        card(2).assertIsFocused().performKeyInput { pressKey(Key.DirectionRight) }
        card(3).assertIsFocused().performKeyInput { pressKey(Key.DirectionRight) }
        card(0).assertDoesNotExist()
        card(10).assertIsFocused().performKeyInput { pressKey(Key.DirectionLeft) }
        card(3).assertIsFocused()
        repeat(2) {
            rule.onNodeWithTag(ShellTags.NextPage).requestFocus().performKeyInput { pressKey(Key.ButtonA) }
        }
        card(18).assertIsFocused()
        assertEquals(firstBounds, card(16).getUnclippedBoundsInRoot())
        card(18).performKeyInput { pressKey(Key.DirectionLeft) }
        val restoredRow = card(11).assertIsFocused()
        assertEquals(titleBounds, title.getUnclippedBoundsInRoot())
        restoredRow.performKeyInput { pressKey(Key.ButtonL1) }
        rule.onNodeWithTag(ShellTags.NavRecent).assertIsSelected()
        rule.onNodeWithTag(ShellTags.OpenLibrary).assertIsFocused().performKeyInput { pressKey(Key.ButtonR1) }
        rule.onNodeWithTag(ShellTags.NavLibrary).assertIsSelected()
        restoredRow.assertIsFocused()
    }

    @Test
    @Config(sdk = [33], qualifiers = "w540dp-h480dp-land-mdpi")
    fun shortLibraryPaginatesWithReadableCardsAndKeepsDetailsFocus() {
        val games = (1..10).map { LibraryGame("game$it", "Game $it") }
        var height by mutableStateOf(320.dp)
        rule.setContent {
            FbngTheme {
                Box(Modifier.height(height)) {
                    LibraryContent(
                        load = readyCatalog(),
                        snapshot = LibrarySnapshot(games),
                        busy = false,
                        readFold = { null },
                        onPlay = { _, _ -> },
                        initialSection = ShellSection.Library,
                    )
                }
            }
        }
        val header = rule.onNodeWithTag(ShellTags.Header)
        val headerBounds = header.getUnclippedBoundsInRoot()
        val nav = rule.onNodeWithTag(ShellTags.NavSettings).getUnclippedBoundsInRoot()
        val cardBounds = games.take(4).map { game ->
            rule.onNodeWithTag(ShellTags.game(game.name)).assertIsDisplayed().getUnclippedBoundsInRoot()
        }
        assertTrue(cardBounds.all { it.top >= headerBounds.bottom && it.top > nav.bottom })
        assertTrue(cardBounds.all { it.bottom - it.top >= 32.dp })
        rule.onNodeWithTag(ShellTags.game(games[6].name)).assertDoesNotExist()
        rule.onNodeWithContentDescription("第 1 / 2 页").assertIsDisplayed()
        rule.onNode(SemanticsMatcher.keyIsDefined(SemanticsProperties.VerticalScrollAxisRange)).assertDoesNotExist()
        games.take(4).forEachIndexed { index, game ->
            val card = rule.onNodeWithTag(ShellTags.game(game.name)).assertIsDisplayed()
            rule.onAllNodesWithText(game.description, useUnmergedTree = true)[0].assertIsDisplayed()
            rule.onAllNodesWithText("${game.name}.zip", useUnmergedTree = true)[0].assertIsDisplayed()
            assertEquals(cardBounds[index], card.getUnclippedBoundsInRoot())
        }
        assertEquals(headerBounds, header.getUnclippedBoundsInRoot())
        fun card(index: Int) = rule.onNodeWithTag(ShellTags.game(games[index].name))
        card(0).assertIsFocused().performKeyInput { pressKey(Key.DirectionDown) }
        card(2).assertIsFocused().performKeyInput { pressKey(Key.DirectionDown) }
        card(4).assertIsFocused().performKeyInput { pressKey(Key.DirectionRight) }
        card(5).assertIsFocused().performKeyInput { pressKey(Key.DirectionRight) }
        val target = card(8).assertIsFocused()
        target.performKeyInput { pressKey(Key.ButtonA) }
        awaitGamePage()
        rule.onNodeWithText("${games[8].name}.zip").assertIsDisplayed()
        rule.onNodeWithTag(ShellTags.Play).assertIsFocused().performKeyInput { pressKey(Key.ButtonB) }
        target.assertIsFocused().assertIsDisplayed()
        rule.runOnIdle { height = 480.dp }
        rule.onNodeWithContentDescription("第 2 / 2 页").assertIsDisplayed()
        target.assertIsFocused().assertIsDisplayed()
    }

    @Test
    @Config(sdk = [33], qualifiers = "w320dp-h360dp-mdpi")
    fun narrowLibraryWrapsControlsAndSettingsSharesTheTopBar() {
        rule.setContent {
            FbngTheme {
                LibraryContent(
                    load = readyCatalog(),
                    snapshot = readySnapshot(),
                    busy = false,
                    readFold = { null },
                    onPlay = { _, _ -> },
                    initialSection = ShellSection.Favorites,
                )
            }
        }
        val header = rule.onNodeWithTag(ShellTags.Header)
        val initial = header.getUnclippedBoundsInRoot()
        val title = rule.onNodeWithTag(ShellTags.HeaderTitle)
        rule.onNodeWithTag(ShellTags.NavRecent).performClick()
        assertEquals(initial, header.getUnclippedBoundsInRoot())
        rule.onNodeWithTag(ShellTags.NavLibrary).performClick()
        val letter = rule.onNodeWithTag(ShellTags.JumpToLetter)
        val status = rule.onNodeWithTag(ShellTags.LibraryFilter)
        val tab = rule.onNodeWithTag(ShellTags.NavSettings).getUnclippedBoundsInRoot()
        assertTrue(status.getUnclippedBoundsInRoot().top >= tab.bottom)
        assertTrue(letter.getUnclippedBoundsInRoot().left > status.getUnclippedBoundsInRoot().right)
        rule.onNodeWithContentDescription("第 1 / 1 页").assertHasNoClickAction()
        status.requestFocus().performKeyInput { pressKey(Key.DirectionRight) }
        letter.assertIsFocused()
        rule.onNode(SemanticsMatcher.keyIsDefined(SemanticsProperties.EditableText)).assertDoesNotExist()
        rule.onNodeWithTag(ShellTags.game("galaga")).performClick()
        rule.onNodeWithTag(ShellTags.NavSettings).assertDoesNotExist()
        val detailBack = rule.onNodeWithTag(ShellTags.DetailBack)
        assertEquals(8.dp, title.getUnclippedBoundsInRoot().left - detailBack.getUnclippedBoundsInRoot().right)
        detailBack.performClick()
        rule.onNodeWithTag(ShellTags.game("galaga")).assertIsFocused().performKeyInput { pressKey(Key.ButtonR1) }
        rule.onNodeWithTag(ShellTags.NavSettings).assertIsSelected()
        assertEquals(initial, header.getUnclippedBoundsInRoot())
        rule.onNodeWithTag(ShellTags.OpenImport).assertIsFocused().performKeyInput { pressKey(Key.ButtonL1) }
        rule.onNodeWithTag(ShellTags.NavLibrary).assertIsSelected()
        rule.onNodeWithTag(ShellTags.game("galaga")).assertIsFocused()
    }

    @Test
    @Config(sdk = [33], qualifiers = "w800dp-h320dp-land-mdpi")
    fun everyLetterKeyIsVisibleInLandscapeAndButtonBReturnsToTheGame() {
        rule.setContent {
            FbngTheme {
                LibraryContent(
                    load = readyCatalog(),
                    snapshot = readySnapshot(),
                    busy = false,
                    readFold = { null },
                    onPlay = { _, _ -> },
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
        rule.onNodeWithContentDescription("退格").assertIsDisplayed()
        listOf("清空", "完成").forEach { rule.onNodeWithText(it).assertIsDisplayed() }
        val panel = rule.onNode(SemanticsMatcher.keyIsDefined(SemanticsProperties.PaneTitle))
            .getUnclippedBoundsInRoot()
        assertEquals(800.dp, panel.right - panel.left)
        val firstRow = rule.onNodeWithTag(ShellTags.letter('A')).getUnclippedBoundsInRoot()
        val secondRow = rule.onNodeWithTag(ShellTags.letter('K')).getUnclippedBoundsInRoot()
        val thirdRow = rule.onNodeWithTag(ShellTags.letter('U')).getUnclippedBoundsInRoot()
        val fourthRow = rule.onNodeWithTag(ShellTags.letter('4')).getUnclippedBoundsInRoot()
        assertTrue(firstRow.top < secondRow.top && secondRow.top < thirdRow.top && thirdRow.top < fourthRow.top)
        rule.onNodeWithTag(ShellTags.letter('Q')).performTouchInput { click() }
        rule.onNodeWithTag(ShellTags.game("galaga")).assertIsDisplayed()
        rule.onNodeWithTag(ShellTags.letter('A')).performKeyInput { pressKey(Key.ButtonB) }
        rule.onNodeWithTag(ShellTags.game("galaga")).assertIsFocused()
        filter.performClick()
        rule.onNodeWithTag(ShellTags.letter('A')).assertIsDisplayed()
        rule.onNodeWithTag(ShellTags.letter('Q')).performClick()
        rule.onNodeWithText("完成").performClick()
        rule.onNodeWithTag(ShellTags.EditSearch).assertIsFocused().performClick()
        rule.onNodeWithText("清空").performClick()
        rule.onNodeWithTag(ShellTags.letter('M')).performClick()
        rule.onNodeWithTag(ShellTags.letter('S')).performClick()
        rule.onNodeWithTag(ShellTags.game("mslug")).assertDoesNotExist()
        rule.onNodeWithText("完成").performClick()
        rule.onNodeWithTag(ShellTags.game("mslug")).assertIsFocused()
        rule.onNodeWithTag(ShellTags.game("mslug")).assertIsDisplayed()
        rule.onNodeWithTag(ShellTags.game("galaga")).assertDoesNotExist()
    }

    @Test
    @Config(sdk = [33], qualifiers = "w320dp-h540dp-port-mdpi")
    fun letterPickerKeepsItsSideMarginsAndAllKeysInACompactWindow() {
        rule.setContent {
            FbngTheme {
                LibraryQueryDialog(query = "", onConfirm = {}, onDismiss = {})
            }
        }
        val header = rule.onNodeWithTag(ShellTags.Header).getUnclippedBoundsInRoot()
        assertEquals(24.dp, header.left)
        assertEquals(296.dp, header.right)
        "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789._-".forEach { character ->
            rule.onNodeWithTag(ShellTags.letter(character)).assertIsDisplayed()
        }
        rule.onNodeWithContentDescription("退格").assertIsDisplayed()
        listOf("清空", "完成").forEach { rule.onNodeWithText(it).assertIsDisplayed() }
    }

    @Test
    @Config(sdk = [33], qualifiers = "w320dp-h640dp-mdpi")
    fun cornerRadiusButtonsUpdateAllThemeShapesAndPersistTheSingleChoice() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appearance = AppearancePreferences(context)
        lateinit var shapes: Shapes
        rule.setContent {
            var option by remember { mutableStateOf(appearance.readCornerRadius()) }
            FbngTheme(option) {
                val currentShapes = MaterialTheme.shapes
                SideEffect { shapes = currentShapes }
                LibraryContent(
                    load = readyCatalog(),
                    snapshot = LibrarySnapshot(),
                    busy = false,
                    readFold = { null },
                    onPlay = { _, _ -> },
                    initialSection = ShellSection.Settings,
                    cornerRadius = option,
                    onCornerRadiusChange = {
                        appearance.writeCornerRadius(it)
                        option = it
                    },
                )
            }
        }
        fun assertRadius(pixels: Float) = rule.runOnIdle {
            val radii = with(shapes) { listOf(extraSmall, small, medium, large, extraLarge) }
            assertEquals(listOf(pixels, pixels, pixels, pixels + 8f, pixels + 8f), radii.map { it.topStart.toPx(Size(96f, 48f), Density(1f)) })
        }

        rule.onNodeWithText("外观").requestFocus().performKeyInput { pressKey(Key.DirectionRight) }
        val smaller = rule.onNodeWithTag(ShellTags.cornerRadius(CornerRadiusOption.Smaller))
        val default = rule.onNodeWithTag(ShellTags.cornerRadius(CornerRadiusOption.Default))
        val larger = rule.onNodeWithTag(ShellTags.cornerRadius(CornerRadiusOption.Larger))
        smaller.assertIsNotSelected()
        default.assertIsSelected().requestFocus()
        larger.assertIsNotSelected()
        assertRadius(12f)

        default.assertIsFocused().performKeyInput { pressKey(Key.DirectionRight) }
        larger.assertIsFocused().assertIsNotSelected().performKeyInput { pressKey(Key.ButtonA) }
        larger.assertIsFocused().assertIsSelected()
        default.assertIsNotSelected()
        assertRadius(16f)
        assertEquals(CornerRadiusOption.Larger, AppearancePreferences(context).readCornerRadius())

        smaller.performTouchInput { click() }
        smaller.assertIsSelected()
        larger.assertIsNotSelected()
        assertRadius(8f)
        assertEquals(CornerRadiusOption.Smaller, AppearancePreferences(context).readCornerRadius())
    }

    @Test
    @Config(sdk = [33], qualifiers = "w900dp-h480dp-land-mdpi")
    fun dpadReachesManualUpdatesInAbout() {
        val updater = ApplicationProvider.getApplicationContext<FbngApplication>().appUpdate
        rule.setContent {
            FbngTheme {
                LibraryContent(
                    load = readyCatalog(),
                    snapshot = readySnapshot(),
                    busy = false,
                    readFold = { null },
                    onPlay = { _, _ -> },
                    initialSection = ShellSection.Settings,
                    appUpdate = UpdateUiState(updater, UpdateState.Current, canInstall = true),
                )
            }
        }
        val about = rule.onNodeWithText("关于")
        about.requestFocus().performKeyInput { pressKey(Key.DirectionRight) }
        rule.onNodeWithText("应用更新").assertIsDisplayed()
        rule.onNodeWithText("检查更新").assertIsDisplayed()
        rule.onNodeWithTag(ShellTags.AppUpdate).assertIsFocused()
            .performKeyInput { pressKey(Key.ButtonB) }
        about.assertIsFocused()
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
                    onPlay = { _, _ -> },
                    initialSection = ShellSection.Import,
                )
            }
        }
        rule.onNodeWithText("游戏目录").assertIsDisplayed()
        rule.onNodeWithText("导入 zip").assertDoesNotExist()
        rule.onNodeWithTag(ShellTags.AddFolder).assertIsFocused()
        rule.onNodeWithTag(ShellTags.AddFolder).performKeyInput { pressKey(Key.ButtonB) }
        rule.onNodeWithTag(ShellTags.OpenImport).assertIsFocused()
    }

    @Test
    @Config(sdk = [33], qualifiers = "w400dp-h800dp-mdpi")
    fun dpadReachesFolderControlsAndRemovalRestoresFocus() {
        val uris = (1..3).map { Uri.parse("content://test/tree/arcade$it") }
        val remove = uris.map(ShellTags::removeFolder)
        rule.setContent {
            var folders by remember {
                mutableStateOf(uris.mapIndexed { index, uri -> FolderScan(LibraryFolder(uri, "街机 ${index + 1}"), roms = 1) })
            }
            FbngTheme {
                LibraryContent(
                    load = readyCatalog(),
                    snapshot = LibrarySnapshot(),
                    busy = false,
                    readFold = { null },
                    onPlay = { _, _ -> },
                    initialSection = ShellSection.Import,
                    folders = folders,
                    folderActions = FolderActions(remove = { removed ->
                        folders = folders.filterNot { it.folder.uri == removed }
                    }),
                )
            }
        }
        rule.onNodeWithTag(ShellTags.NavSettings).assertIsSelected()
        rule.onNodeWithTag(remove[0]).assertIsFocused()
        rule.onNodeWithTag(remove[2]).requestFocus().performKeyInput { pressKey(Key.DirectionDown) }
        rule.onNodeWithTag(ShellTags.AddFolder).assertIsFocused().performKeyInput { pressKey(Key.DirectionRight) }
        rule.onNodeWithTag(ShellTags.ScanFolders).assertIsFocused()
        rule.onNodeWithTag(remove[1]).requestFocus().performKeyInput { pressKey(Key.ButtonA) }
        rule.onNodeWithText("取消").assertIsFocused().performKeyInput { pressKey(Key.ButtonB) }
        rule.onNodeWithTag(remove[1]).assertIsFocused().performKeyInput { pressKey(Key.ButtonA) }
        rule.onNodeWithText("移除文件夹").performClick()
        rule.onNodeWithTag(remove[1]).assertDoesNotExist()
        rule.onNodeWithTag(remove[2]).assertIsFocused().performKeyInput { pressKey(Key.ButtonA) }
        rule.onNodeWithText("移除文件夹").performClick()
        rule.onNodeWithTag(remove[0]).assertIsFocused().performKeyInput { pressKey(Key.ButtonA) }
        rule.onNodeWithText("移除文件夹").performClick()
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
    fun pauseMenuRestoresTheSlotActionAndBackResumes() {
        var resumed = 0
        var exited = 0
        rule.setContent {
            var panelOpen by remember { mutableStateOf(false) }
            FbngTheme {
                GameMenuPage(
                    entry = indexLibrary(readySnapshot().games, emptyMap()).first(),
                    cover = null,
                    fold = null,
                    favorite = false,
                    ready = true,
                    busy = false,
                    dialogOpen = panelOpen,
                    message = "",
                    onPrimary = { resumed++ },
                    onBack = { resumed++ },
                    onToggleFavorite = {},
                    onLoad = {},
                    onReset = {},
                    onQuit = { exited++ },
                    onSave = { panelOpen = true },
                )
                if (panelOpen) {
                    SideEffect { panelOpen = false }
                }
            }
        }
        rule.onNodeWithTag(ShellTags.Pause).assertIsFocused()
            .performKeyInput { pressKey(Key.DirectionDown) }
        rule.onNodeWithTag(ShellTags.Load).assertIsFocused()
            .performKeyInput { pressKey(Key.DirectionDown) }
        rule.onNodeWithTag(ShellTags.Save).assertIsFocused()
            .performKeyInput { pressKey(Key.ButtonA) }
        rule.onNodeWithTag(ShellTags.Save).assertIsFocused()
            .performKeyInput { pressKey(Key.ButtonB) }
        assertEquals(1, resumed)
        assertEquals(0, exited)
    }

    @Test
    @Config(sdk = [33], qualifiers = "w800dp-h480dp-mdpi")
    fun searchShortcutAcceptsJoystickInTheDialogAndDoneFocusesTheResult() {
        rule.setContent {
            val context = ApplicationProvider.getApplicationContext<Context>()
            val input = remember { GamepadInput(context, GamepadBridge()) }
            CompositionLocalProvider(LocalGamepadInput provides input) {
                FbngTheme {
                    LibraryContent(
                        load = readyCatalog(), snapshot = readySnapshot(), busy = false,
                        readFold = { null }, onPlay = { _, _ -> },
                    )
                }
            }
        }
        rule.onNodeWithTag(ShellTags.NavLibrary).requestFocus()
            .assertIsFocused().performKeyInput { pressKey(Key.ButtonY) }
        rule.onNodeWithTag(ShellTags.letter('A')).assertIsFocused()
            .performKeyInput { pressKey(Key.ButtonB) }
        rule.onNodeWithTag(ShellTags.game("galaga")).assertIsFocused()
            .performKeyInput { pressKey(Key.ButtonY) }
        rule.onNodeWithTag(ShellTags.letter('A')).assertIsFocused()
        rule.runOnIdle {
            val dialog = checkNotNull(ShadowDialog.getLatestDialog())
            val motion = controllerMotion(120, hatX = 1f)
            try {
                assertTrue(checkNotNull(dialog.window).callback.dispatchGenericMotionEvent(motion))
            } finally {
                motion.recycle()
            }
        }
        rule.onNodeWithTag(ShellTags.letter('B')).assertIsFocused()
        rule.onNodeWithTag(ShellTags.letter('M')).performClick()
        rule.onNodeWithText("完成").performClick()
        rule.onNodeWithTag(ShellTags.game("mslug")).assertIsFocused()
    }

    @Test
    @Config(sdk = [33], qualifiers = "w800dp-h480dp-mdpi")
    fun unavailableDetailsReturnToTheirShelfAndPlayingReturnsToTheLibraryCard() {
        var returnFromGame by mutableStateOf<String?>(null)
        rule.setContent {
            FbngTheme {
                LibraryContent(
                    load = readyCatalog(),
                    snapshot = LibrarySnapshot(listOf(
                        LibraryGame("galaga", "Galaga", listOf(MissingDependency("parent", false))),
                        LibraryGame("mslug", "Metal Slug"),
                    )),
                    busy = false,
                    readFold = { null },
                    onPlay = { _, _ -> },
                    initialSection = ShellSection.Recent,
                    recentNames = listOf("galaga"),
                    returnFromGame = returnFromGame,
                    onReturnRestored = { returnFromGame = null },
                )
            }
        }
        rule.onNodeWithTag(ShellTags.recent("galaga")).assertIsFocused()
            .performKeyInput { pressKey(Key.ButtonA) }
        awaitGamePage()
        rule.onNodeWithTag(ShellTags.Play).assertIsFocused()
            .performKeyInput { pressKey(Key.ButtonB) }
        rule.onNodeWithTag(ShellTags.recent("galaga")).assertIsFocused()

        rule.onNodeWithTag(ShellTags.NavLibrary).performClick()
        rule.onNodeWithTag(ShellTags.game("mslug")).requestFocus()
            .performKeyInput { pressKey(Key.ButtonA) }
        awaitGamePage()
        rule.onNodeWithTag(ShellTags.Play).assertIsFocused()
        rule.runOnIdle { returnFromGame = "mslug" }
        rule.onNodeWithTag(ShellTags.game("mslug")).assertIsFocused()
    }

}

private fun readyCatalog() = CatalogLoad.Ready(DatCatalog("test", emptyList()))

private fun readySnapshot() = LibrarySnapshot(listOf(
    LibraryGame("galaga", "Galaga"),
    LibraryGame("mslug", "Metal Slug"),
))
