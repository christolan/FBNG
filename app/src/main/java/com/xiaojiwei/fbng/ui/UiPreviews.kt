package com.xiaojiwei.fbng.ui

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.xiaojiwei.fbng.R
import com.xiaojiwei.fbng.dat.DatCatalog
import com.xiaojiwei.fbng.dat.ProblemKind
import com.xiaojiwei.fbng.dat.RomProblem
import com.xiaojiwei.fbng.dat.SetMatch
import com.xiaojiwei.fbng.importing.ImportReport
import com.xiaojiwei.fbng.importing.InstallOutcome
import com.xiaojiwei.fbng.library.FolderScan
import com.xiaojiwei.fbng.library.LibraryGame
import com.xiaojiwei.fbng.library.MissingDependency
import com.xiaojiwei.fbng.library.LibraryFolder
import com.xiaojiwei.fbng.library.LibrarySnapshot
import com.xiaojiwei.fbng.ui.theme.FbngTheme

@Preview(name = "loading", widthDp = 400, heightDp = 800)
@Composable
private fun PreviewLoading() {
    LibraryPreview(load = CatalogLoad.Loading, snapshot = null)
}

@Preview(name = "compact empty", widthDp = 400, heightDp = 800)
@Preview(name = "compact empty large font", widthDp = 400, heightDp = 800, fontScale = 1.5f)
@Composable
private fun PreviewCompactEmpty() {
    LibraryPreview(load = ready(), snapshot = emptySnapshot())
}

@Preview(name = "medium empty", widthDp = 800, heightDp = 360)
@Composable
private fun PreviewMediumEmpty() {
    LibraryPreview(load = ready(), snapshot = emptySnapshot())
}

@Preview(name = "expanded ready", widthDp = 900, heightDp = 640)
@Preview(name = "compact ready", widthDp = 400, heightDp = 800, fontScale = 1.5f)
@Composable
private fun PreviewExpandedReady() {
    LibraryPreview(load = ready(), snapshot = readySnapshot())
}

@Preview(name = "dat error", widthDp = 400, heightDp = 800)
@Composable
private fun PreviewDatError() {
    LibraryPreview(load = CatalogLoad.Failed("内置 DAT 无法解析。"), snapshot = null)
}

@Preview(name = "compact import", widthDp = 400, heightDp = 800)
@Composable
private fun PreviewImport() {
    LibraryPreview(
        load = ready(),
        snapshot = emptySnapshot(),
        reports = listOf(mismatchReport()),
        initialSection = ShellSection.Import,
    )
}

@Preview(name = "compact folders empty", widthDp = 400, heightDp = 800)
@Preview(name = "compact folders large font", widthDp = 400, heightDp = 800, fontScale = 1.5f)
@Composable
private fun PreviewFoldersEmpty() {
    LibraryPreview(load = ready(), snapshot = emptySnapshot(), initialSection = ShellSection.Import)
}

@Preview(name = "compact folders scanning", widthDp = 400, heightDp = 800)
@Composable
private fun PreviewFoldersScanning() {
    LibraryPreview(
        load = ready(),
        snapshot = emptySnapshot(),
        initialSection = ShellSection.Import,
        busy = true,
    )
}

@Preview(name = "compact folders ready", widthDp = 400, heightDp = 800)
@Preview(name = "expanded folders ready", widthDp = 900, heightDp = 640)
@Composable
private fun PreviewFoldersReady() {
    LibraryPreview(
        load = ready(),
        snapshot = readySnapshot(),
        initialSection = ShellSection.Import,
        folders = listOf(FolderScan(previewFolder, roms = 1, bios = 1)),
    )
}

@Preview(name = "expanded folder unavailable", widthDp = 900, heightDp = 640)
@Composable
private fun PreviewFolderUnavailable() {
    LibraryPreview(
        load = ready(),
        snapshot = emptySnapshot(),
        initialSection = ShellSection.Import,
        folders = listOf(FolderScan(previewFolder, unavailable = true)),
    )
}

@Preview(name = "sync expanded", widthDp = 900, heightDp = 640)
@Composable
private fun PreviewSync() {
    LibraryPreview(load = ready(), snapshot = emptySnapshot(), initialSection = ShellSection.Sync)
}

@Preview(name = "missing core", widthDp = 400, heightDp = 800)
@Composable
private fun PreviewMissingCore() {
    FbngTheme { MissingCore(onQuit = {}) }
}

@Preview(name = "hud portrait", widthDp = 400, heightDp = 800)
@Preview(name = "hud landscape", widthDp = 800, heightDp = 360)
@Composable
private fun PreviewHud() {
    FbngTheme {
        BoxWithConstraints(Modifier.fillMaxSize().background(Color.Black)) {
            val minBar = with(LocalDensity.current) { MinHudBar.roundToPx() }
            GameHud(
                layout = layoutGameHud(
                    screenWidthPx = constraints.maxWidth,
                    screenHeightPx = constraints.maxHeight,
                    gameAspect = 4f / 3f,
                    minBarPx = minBar,
                ),
                ready = true,
                busy = false,
                hasSlot = true,
                message = stringResource(R.string.slot_ready),
                onSave = {},
                onLoad = {},
                onQuit = {},
            )
        }
    }
}

@Composable
private fun LibraryPreview(
    load: CatalogLoad,
    snapshot: LibrarySnapshot?,
    reports: List<ImportReport> = emptyList(),
    initialSection: ShellSection = ShellSection.Library,
    folders: List<FolderScan> = emptyList(),
    busy: Boolean = false,
) {
    FbngTheme {
        Box(Modifier.fillMaxSize()) {
            LibraryContent(
                load = load,
                snapshot = snapshot,
                reports = reports,
                busy = busy,
                workMessage = R.string.folder_scanning,
                readFold = { null },
                onImport = {},
                onPlay = {},
                initialSection = initialSection,
                folders = folders,
            )
        }
    }
}

private val previewFolder = LibraryFolder(Uri.parse("content://preview/tree/arcade"), "街机游戏")

private fun ready() = CatalogLoad.Ready(DatCatalog("preview", emptyList()))

private fun emptySnapshot() = LibrarySnapshot()

private fun readySnapshot() = LibrarySnapshot(listOf(
    LibraryGame("galaga", "Galaga (Namco rev. B)"),
    LibraryGame("mslug", "Metal Slug - Super Vehicle-001", listOf(MissingDependency("neogeo", true))),
))

private fun mismatchReport() = ImportReport(
    displayName = "import.zip",
    outcome = InstallOutcome.REJECTED_MISMATCH,
    match = SetMatch(
        setName = "kovsh",
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
