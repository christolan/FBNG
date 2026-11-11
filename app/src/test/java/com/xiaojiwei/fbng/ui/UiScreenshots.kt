package com.xiaojiwei.fbng.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.RoborazziComposeOptions
import com.github.takahirom.roborazzi.captureRoboImage
import com.github.takahirom.roborazzi.size
import com.xiaojiwei.fbng.R
import com.xiaojiwei.fbng.dat.DatCatalog
import com.xiaojiwei.fbng.dat.ProblemKind
import com.xiaojiwei.fbng.dat.RomProblem
import com.xiaojiwei.fbng.dat.SetMatch
import com.xiaojiwei.fbng.importing.ImportReport
import com.xiaojiwei.fbng.importing.InstallOutcome
import com.xiaojiwei.fbng.importing.RomInstaller
import com.xiaojiwei.fbng.library.InstalledSet
import com.xiaojiwei.fbng.library.LibrarySnapshot
import com.xiaojiwei.fbng.ui.theme.FbngTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Representative stills of the shell that exists today. mdpi keeps the files small.
 * Phone portrait is under 600dp, phone landscape is 600–840dp, and the unfolded
 * foldable is at least 840dp wide. Capture is a no-op unless record or verify is on.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33], qualifiers = "mdpi")
class UiScreenshots {
    @Test
    fun libraryPhonePortraitShowsTheBottomBar() {
        assertFalse(shellUsesNavigationRail(400, null))
        assertEquals(ShellWidth.Compact, shellWidth(400))
        capture("library_phone_portrait", 400, 800) {
            LibraryShot(
                load = readyCatalog(),
                snapshot = LibrarySnapshot(absent(RomInstaller.KOVSH), absent(RomInstaller.PGM)),
            )
        }
    }

    @Test
    fun libraryPhonePortraitShowsImportResult() {
        assertFalse(libraryUsesTwoPanes(400, null))
        capture("library_phone_portrait_import", 400, 800) {
            LibraryShot(
                load = readyCatalog(),
                snapshot = LibrarySnapshot(absent(RomInstaller.KOVSH), absent(RomInstaller.PGM)),
                reports = listOf(mismatchReport()),
            )
        }
    }

    @Test
    fun libraryPhoneLandscapeIsTwoPanes() {
        assertTrue(libraryUsesTwoPanes(800, null))
        capture("library_phone_landscape", 800, 360) {
            LibraryShot(
                load = readyCatalog(),
                snapshot = LibrarySnapshot(absent(RomInstaller.KOVSH), absent(RomInstaller.PGM)),
            )
        }
    }

    @Test
    fun libraryFoldableExpandedCanPlay() {
        assertTrue(libraryUsesTwoPanes(900, null))
        capture("library_foldable_expanded", 900, 640) {
            LibraryShot(
                load = readyCatalog(),
                snapshot = LibrarySnapshot(clean(RomInstaller.KOVSH, bios = false), clean(RomInstaller.PGM, bios = true)),
            )
        }
    }

    @Test
    fun syncExpandedIsAnEmptyState() {
        assertTrue(shellUsesNavigationRail(900, null))
        assertEquals(ShellWidth.Expanded, shellWidth(900))
        capture("shell_sync_expanded", 900, 640) {
            LibraryShot(
                load = readyCatalog(),
                snapshot = LibrarySnapshot(absent(RomInstaller.KOVSH), absent(RomInstaller.PGM)),
                initialSection = ShellSection.Sync,
            )
        }
    }

    @Test
    fun settingsCompactShowsRealInfoOnly() {
        assertFalse(shellUsesNavigationRail(400, null))
        capture("shell_settings_compact", 400, 800) {
            LibraryShot(
                load = readyCatalog(),
                snapshot = LibrarySnapshot(absent(RomInstaller.KOVSH), absent(RomInstaller.PGM)),
                initialSection = ShellSection.Settings,
            )
        }
    }

    @Test
    fun libraryDatError() {
        capture("library_dat_error", 400, 800) {
            LibraryShot(load = CatalogLoad.Failed("内置 DAT 无法解析。"), snapshot = null)
        }
    }

    @Test
    fun hudPhonePortraitUsesEndBars() {
        assertEquals(HudBand.Ends, layoutGameHud(400, 800, 4f / 3f, 56).band)
        capture("hud_phone_portrait", 400, 800) {
            FbngTheme { HudFrame(aspect = 4f / 3f, hasSlot = true, message = "") }
        }
    }

    @Test
    fun hudPhoneLandscapeUsesSideBars() {
        assertEquals(HudBand.Sides, layoutGameHud(800, 360, 4f / 3f, 56).band)
        capture("hud_phone_landscape", 800, 360) {
            FbngTheme { HudFrame(aspect = 4f / 3f, hasSlot = false, message = "") }
        }
    }

    @Test
    fun hudFoldableThinBarsOverlay() {
        val layout = layoutGameHud(900, 640, 4f / 3f, 56)
        assertEquals(HudBand.Overlay, layout.band)
        assertEquals(BarAxis.Sides, layout.axis)
        capture("hud_foldable_thin_bars", 900, 640) {
            FbngTheme {
                HudFrame(
                    aspect = 4f / 3f,
                    hasSlot = true,
                    message = stringResource(R.string.core_not_ready),
                )
            }
        }
    }

    @Test
    fun gameMissingCore() {
        capture("game_missing_core", 400, 800) {
            FbngTheme { MissingCore(onQuit = {}) }
        }
    }

    @OptIn(ExperimentalRoborazziApi::class)
    private fun capture(name: String, widthDp: Int, heightDp: Int, content: @Composable () -> Unit) {
        captureRoboImage(
            filePath = "src/test/screenshots/$name.png",
            roborazziComposeOptions = RoborazziComposeOptions {
                size(widthDp = widthDp, heightDp = heightDp)
            },
            content = content,
        )
    }
}

@Composable
private fun LibraryShot(
    load: CatalogLoad,
    snapshot: LibrarySnapshot?,
    reports: List<ImportReport> = emptyList(),
    initialSection: ShellSection = ShellSection.Library,
) {
    FbngTheme {
        LibraryContent(
            load = load,
            snapshot = snapshot,
            reports = reports,
            busy = false,
            readFold = { null },
            onImport = {},
            onPlay = {},
            initialSection = initialSection,
        )
    }
}

@Composable
private fun HudFrame(aspect: Float, hasSlot: Boolean, message: String) {
    BoxWithConstraints(Modifier.fillMaxSize().background(Color.Black)) {
        val minBar = with(LocalDensity.current) { MinHudBar.roundToPx() }
        val layout = layoutGameHud(
            screenWidthPx = constraints.maxWidth,
            screenHeightPx = constraints.maxHeight,
            gameAspect = aspect,
            minBarPx = minBar,
        )
        GameHud(
            layout = layout,
            ready = true,
            busy = false,
            hasSlot = hasSlot,
            message = message,
            onSave = {},
            onLoad = {},
            onQuit = {},
        )
    }
}

private fun readyCatalog() = CatalogLoad.Ready(DatCatalog("test", emptyList()))

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

private fun mismatchReport() = ImportReport(
    displayName = "import.zip",
    outcome = InstallOutcome.REJECTED_MISMATCH,
    match = SetMatch(
        setName = RomInstaller.KOVSH,
        description = "import.zip",
        isBios = false,
        requiredCount = 2,
        matchedOwnCount = 1,
        contentHits = 1,
        explainedCount = 1,
        problems = listOf(RomProblem(kind = ProblemKind.MISSING, expectedName = "missing.bin")),
    ),
    installedFileName = null,
)
