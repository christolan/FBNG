package com.xiaojiwei.fbng.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
import com.xiaojiwei.fbng.dat.DatCatalog
import com.xiaojiwei.fbng.dat.SetMatch
import com.xiaojiwei.fbng.importing.RomInstaller
import com.xiaojiwei.fbng.library.InstalledSet
import com.xiaojiwei.fbng.library.LibrarySnapshot
import com.xiaojiwei.fbng.ui.theme.FbngTheme
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
    fun dpadDownMovesFromPlayToTheCover() {
        rule.setContent {
            FbngTheme {
                LibraryContent(
                    load = readyCatalog(),
                    snapshot = readySnapshot(),
                    reports = emptyList(),
                    busy = false,
                    readFold = { null },
                    onImport = {},
                    onPlay = {},
                )
            }
        }
        rule.onNodeWithTag(ShellTags.Play).assertIsFocused()
        rule.onNodeWithTag(ShellTags.Play).performKeyInput { pressKey(Key.DirectionDown) }
        rule.onNodeWithTag(ShellTags.Card).assertIsFocused()
    }

    @Test
    @Config(sdk = [33], qualifiers = "w400dp-h800dp-mdpi")
    fun dpadDownMovesFromTheCoverToTheBottomBar() {
        rule.setContent {
            FbngTheme {
                LibraryContent(
                    load = readyCatalog(),
                    snapshot = emptySnapshot(),
                    reports = emptyList(),
                    busy = false,
                    readFold = { null },
                    onImport = {},
                    onPlay = {},
                )
            }
        }
        rule.onNodeWithTag(ShellTags.Search).assertIsFocused()
        rule.onNodeWithTag(ShellTags.Card).requestFocus()
        rule.onNodeWithTag(ShellTags.Card).performKeyInput { pressKey(Key.DirectionDown) }
        rule.onNodeWithTag(ShellTags.NavLibrary).assertIsFocused()
    }

    @Test
    @Config(sdk = [33], qualifiers = "w900dp-h640dp-mdpi")
    fun buttonAStartsTheGameWhenTheSetIsReady() {
        var played = false
        rule.setContent {
            FbngTheme {
                LibraryContent(
                    load = readyCatalog(),
                    snapshot = readySnapshot(),
                    reports = emptyList(),
                    busy = false,
                    readFold = { null },
                    onImport = {},
                    onPlay = { played = true },
                )
            }
        }
        rule.onNodeWithTag(ShellTags.Play).assertIsFocused()
        rule.onNodeWithTag(ShellTags.Play).performKeyInput { pressKey(Key.ButtonA) }
        assertTrue(played)
    }

    @Test
    @Config(sdk = [33], qualifiers = "w400dp-h800dp-mdpi")
    fun buttonBLeavesTheSearchFieldWithoutLeavingTheLibrary() {
        rule.setContent {
            FbngTheme {
                LibraryContent(
                    load = readyCatalog(),
                    snapshot = emptySnapshot(),
                    reports = emptyList(),
                    busy = false,
                    readFold = { null },
                    onImport = {},
                    onPlay = {},
                )
            }
        }
        rule.onNodeWithTag(ShellTags.Search).performTextInput("zzzz")
        rule.onNodeWithTag(ShellTags.Search).performKeyInput { pressKey(Key.ButtonB) }
        rule.onNodeWithTag(ShellTags.NavLibrary).assertIsFocused()
        rule.onNodeWithTag(ShellTags.Card).assertDoesNotExist()
    }

    @Test
    @Config(sdk = [33], qualifiers = "w400dp-h800dp-mdpi")
    fun buttonBReturnsFromImportToTheLibrary() {
        rule.setContent {
            FbngTheme {
                LibraryContent(
                    load = readyCatalog(),
                    snapshot = emptySnapshot(),
                    reports = emptyList(),
                    busy = false,
                    readFold = { null },
                    onImport = {},
                    onPlay = {},
                    initialSection = ShellSection.Import,
                )
            }
        }
        rule.onNodeWithTag(ShellTags.Import).assertIsFocused()
        rule.onNodeWithTag(ShellTags.Import).performKeyInput { pressKey(Key.ButtonB) }
        rule.onNodeWithTag(ShellTags.Search).assertIsFocused()
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

private fun emptySnapshot() = LibrarySnapshot(absent(RomInstaller.KOVSH), absent(RomInstaller.PGM))

private fun readySnapshot() = LibrarySnapshot(
    clean(RomInstaller.KOVSH, bios = false),
    clean(RomInstaller.PGM, bios = true),
)

private fun absent(name: String) = InstalledSet(name, present = false, match = null, unreadable = false)

private fun clean(name: String, bios: Boolean) = InstalledSet(
    expected = name,
    present = true,
    match = SetMatch(
        setName = name,
        description = name,
        isBios = bios,
        requiredCount = 1,
        matchedOwnCount = 1,
        contentHits = 1,
        explainedCount = 1,
        problems = emptyList(),
    ),
    unreadable = false,
)
