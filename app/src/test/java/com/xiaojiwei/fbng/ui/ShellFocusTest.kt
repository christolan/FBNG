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
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.xiaojiwei.fbng.dat.DatCatalog
import com.xiaojiwei.fbng.game.GamepadBridge
import com.xiaojiwei.fbng.library.FolderScan
import com.xiaojiwei.fbng.library.LibraryGame
import com.xiaojiwei.fbng.library.LibraryFolder
import com.xiaojiwei.fbng.library.LibrarySnapshot
import com.xiaojiwei.fbng.library.MissingDependency
import com.xiaojiwei.fbng.ui.theme.CornerRadiusOption
import com.xiaojiwei.fbng.ui.theme.FbngTheme
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
        val rail = listOf(ShellTags.NavFavorites, ShellTags.NavRecent, ShellTags.NavLibrary, ShellTags.NavSettings)
            .map { rule.onNodeWithTag(it).getUnclippedBoundsInRoot() }
        val heights = rail.map { (it.bottom - it.top).value }
        assertTrue(heights.max() - heights.min() <= 1f)
        val header = rule.onNodeWithTag(ShellTags.Header).getUnclippedBoundsInRoot()
        assertEquals(8.dp, rail.first().top - header.top)
        assertTrue(rail.last().bottom > rail.first().top + 64.dp * 4)
        rule.onNodeWithTag(ShellTags.game("galaga")).assertIsFocused()
            .performKeyInput { pressKey(Key.DirectionDown) }
        rule.onNodeWithTag(ShellTags.game("mslug")).assertIsFocused()
            .performKeyInput { pressKey(Key.ButtonA) }
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
                    onPlay = {},
                    initialSection = ShellSection.Favorites,
                    favoriteNames = favorites,
                    onToggleFavorite = { favorites = favorites - it },
                )
            }
        }
        rule.onNodeWithTag(ShellTags.favorite("galaga")).assertIsFocused()
            .performKeyInput { pressKey(Key.DirectionDown) }
        rule.onNodeWithContentDescription("取消收藏「Galaga」").assertIsFocused()
            .performKeyInput { pressKey(Key.ButtonA) }
        rule.onNodeWithTag(ShellTags.favorite("galaga")).assertDoesNotExist()
        rule.onNodeWithTag(ShellTags.favorite("mslug")).assertIsFocused()
        rule.onNodeWithContentDescription("取消收藏「Metal Slug」").performTouchInput { click() }
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
    @Config(sdk = [33], qualifiers = "w400dp-h800dp-mdpi")
    fun recentPagesFillTwoRowsAndRestoreTheGameAfterReordering() {
        var height by mutableStateOf(800.dp)
        var inGame by mutableStateOf(false)
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
                            onPlay = { inGame = true },
                            initialSection = ShellSection.Recent,
                            recentNames = recentNames,
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
        assertEquals(header.left, first.left)
        assertEquals(header.right, fourth.right)
        assertTrue(fourth.top > first.top)
        card(5).assertDoesNotExist()

        card(4).requestFocus()
        rule.runOnIdle { height = 280.dp }
        card(4).assertIsFocused()
        assertEquals(card(1).getUnclippedBoundsInRoot().top, card(4).getUnclippedBoundsInRoot().top)
        rule.runOnIdle { height = 800.dp }
        card(4).assertIsFocused()

        card(1).requestFocus().performKeyInput { pressKey(Key.DirectionRight) }
        card(2).assertIsFocused().performKeyInput { pressKey(Key.ButtonR1) }
        rule.onNodeWithContentDescription("第 2 / 2 页").assertIsDisplayed()
        assertEquals(first, card(5).getUnclippedBoundsInRoot())
        card(6).assertIsFocused().performKeyInput { pressKey(Key.ButtonL1) }
        card(2).assertIsFocused()
        rule.onNodeWithTag(ShellTags.NextPage).performTouchInput { click() }
        card(6).assertIsFocused().performKeyInput { pressKey(Key.ButtonA) }
        rule.runOnIdle {
            assertTrue(inGame)
            recentNames = listOf("game6") + recentNames.dropLast(1)
            inGame = false
        }
        rule.onNodeWithContentDescription("第 1 / 2 页").assertIsDisplayed()
        card(6).assertIsFocused()
    }

    @Test
    @Config(sdk = [33], qualifiers = "w900dp-h400dp-mdpi")
    fun favoritePagesFillOneRowAndRemovingTheLastPageRestoresFocus() {
        var favorites by mutableStateOf((1..5).map { "game$it" }.toSet())
        val games = LibrarySnapshot(favorites.map { LibraryGame(it, it) })
        rule.setContent {
            FbngTheme {
                LibraryContent(
                    load = readyCatalog(),
                    snapshot = games,
                    busy = false,
                    readFold = { null },
                    onPlay = {},
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
        card(5).assertDoesNotExist()
        card(4).requestFocus().performKeyInput { pressKey(Key.ButtonR1) }
        card(5).assertIsFocused()
        rule.onNodeWithContentDescription("第 2 / 2 页").assertIsDisplayed()
        rule.onNodeWithContentDescription("取消收藏「game5」").performTouchInput { click() }
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
        games.take(3).forEach { game ->
            rule.onNodeWithTag(ShellTags.game(game.name)).assertIsFocused()
                .performKeyInput { pressKey(Key.DirectionDown) }
        }
        rule.onNodeWithTag(ShellTags.game(games[3].name)).assertIsFocused()
            .performKeyInput { pressKey(Key.ButtonR1) }
        rule.onNodeWithTag(ShellTags.game(games[0].name)).assertDoesNotExist()
        rule.onNodeWithTag(ShellTags.game(games[13].name)).assertIsFocused()
        rule.onNodeWithTag(ShellTags.NextPage).requestFocus().performKeyInput { pressKey(Key.ButtonA) }
        val lastPageFirst = rule.onNodeWithTag(ShellTags.game(games[20].name)).assertIsFocused()
        assertEquals(firstBounds, lastPageFirst.getUnclippedBoundsInRoot())
        lastPageFirst.performKeyInput { pressKey(Key.ButtonL1) }
        rule.onNodeWithTag(ShellTags.game(games[10].name)).assertIsFocused()
        assertEquals(titleBounds, title.getUnclippedBoundsInRoot())
    }

    @Test
    @Config(sdk = [33], qualifiers = "w540dp-h320dp-land-mdpi")
    fun shortLibraryFitsTenCardsWithoutScrollingAndKeepsDetailsFocus() {
        val games = (1..10).map { LibraryGame("game$it", "Game $it") }
        rule.setContent {
            FbngTheme {
                LibraryContent(
                    load = readyCatalog(),
                    snapshot = LibrarySnapshot(games),
                    busy = false,
                    readFold = { null },
                    onPlay = {},
                    initialSection = ShellSection.Library,
                )
            }
        }
        val header = rule.onNodeWithTag(ShellTags.Header)
        val headerBounds = header.getUnclippedBoundsInRoot()
        val nav = rule.onNodeWithTag(ShellTags.NavSettings).getUnclippedBoundsInRoot()
        val cardBounds = games.map { game ->
            rule.onNodeWithTag(ShellTags.game(game.name)).assertIsDisplayed().getUnclippedBoundsInRoot()
        }
        assertTrue(cardBounds.all { it.top >= headerBounds.bottom && it.bottom <= nav.top })
        rule.onNode(SemanticsMatcher.keyIsDefined(SemanticsProperties.VerticalScrollAxisRange)).assertDoesNotExist()
        games.take(5).forEachIndexed { index, game ->
            val card = rule.onNodeWithTag(ShellTags.game(game.name)).assertIsFocused().assertIsDisplayed()
            val title = rule.onNodeWithText(game.description, useUnmergedTree = true)
                .assertIsDisplayed().getUnclippedBoundsInRoot()
            val file = rule.onNodeWithText("${game.name}.zip", useUnmergedTree = true)
                .assertIsDisplayed().getUnclippedBoundsInRoot()
            assertTrue(title.right <= file.left)
            assertEquals(cardBounds[index], card.getUnclippedBoundsInRoot())
            if (index < 4) card.performKeyInput { pressKey(Key.DirectionDown) }
        }
        assertEquals(headerBounds, header.getUnclippedBoundsInRoot())
        rule.onNodeWithTag(ShellTags.game(games[4].name)).performKeyInput { pressKey(Key.ButtonA) }
        rule.onNodeWithTag(ShellTags.Play).assertIsFocused().performKeyInput { pressKey(Key.ButtonB) }
        rule.onNodeWithTag(ShellTags.game(games[4].name)).assertIsFocused().assertIsDisplayed()
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
    @Config(sdk = [33], qualifiers = "w320dp-h540dp-port-mdpi")
    fun letterPickerKeepsItsSideMarginsAndAllKeysInACompactWindow() {
        rule.setContent {
            FbngTheme {
                LibraryQueryDialog(query = "", resultCount = 2, onQuery = {}, onDismiss = {})
            }
        }
        val panel = rule.onNode(SemanticsMatcher.keyIsDefined(SemanticsProperties.PaneTitle))
            .getUnclippedBoundsInRoot()
        assertEquals(16.dp, panel.left)
        assertEquals(304.dp, panel.right)
        "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789._-".forEach { character ->
            rule.onNodeWithTag(ShellTags.letter(character)).assertIsDisplayed()
        }
        listOf("退格", "清空", "完成").forEach { rule.onNodeWithText(it).assertIsDisplayed() }
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
                    onPlay = {},
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
            assertEquals(List(5) { pixels }, radii.map { it.topStart.toPx(Size(96f, 48f), Density(1f)) })
        }

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
    fun pauseMenuRestoresTheSlotActionAndBackResumes() {
        var resumed = 0
        var exited = 0
        rule.setContent {
            var panelOpen by remember { mutableStateOf(false) }
            FbngTheme {
                GamePausePage(
                    ready = true,
                    busy = false,
                    hasSlot = true,
                    dialogOpen = panelOpen,
                    message = "",
                    failed = false,
                    onResume = { resumed++ },
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
                        readFold = { null }, onPlay = {},
                    )
                }
            }
        }
        rule.onNodeWithTag(ShellTags.NavLibrary).requestFocus()
            .assertIsFocused().performKeyInput { pressKey(Key.ButtonY) }
        rule.onNodeWithTag(ShellTags.letter('A')).assertIsFocused()
            .performKeyInput { pressKey(Key.ButtonB) }
        rule.onNodeWithTag(ShellTags.JumpToLetter).assertIsFocused()
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
                    onPlay = {},
                    initialSection = ShellSection.Recent,
                    recentNames = listOf("galaga"),
                    returnFromGame = returnFromGame,
                    onReturnRestored = { returnFromGame = null },
                )
            }
        }
        rule.onNodeWithTag(ShellTags.recent("galaga")).assertIsFocused()
            .performKeyInput { pressKey(Key.ButtonA) }
        rule.onNodeWithTag(ShellTags.Play).assertIsFocused()
            .performKeyInput { pressKey(Key.ButtonB) }
        rule.onNodeWithTag(ShellTags.recent("galaga")).assertIsFocused()

        rule.onNodeWithTag(ShellTags.NavLibrary).performClick()
        rule.onNodeWithTag(ShellTags.game("mslug")).requestFocus()
            .performKeyInput { pressKey(Key.ButtonA) }
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
