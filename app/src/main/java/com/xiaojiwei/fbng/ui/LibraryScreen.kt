package com.xiaojiwei.fbng.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.window.layout.FoldingFeature
import androidx.window.layout.WindowInfoTracker
import androidx.window.layout.WindowLayoutInfo
import com.xiaojiwei.fbng.BuildConfig
import com.xiaojiwei.fbng.R
import com.xiaojiwei.fbng.dat.ProblemKind
import com.xiaojiwei.fbng.dat.RomProblem
import com.xiaojiwei.fbng.dat.crcHex
import com.xiaojiwei.fbng.importing.ImportReport
import com.xiaojiwei.fbng.importing.InstallOutcome
import com.xiaojiwei.fbng.library.AppStorage
import com.xiaojiwei.fbng.library.InstalledSet
import com.xiaojiwei.fbng.library.LibraryScanner
import com.xiaojiwei.fbng.library.LibrarySnapshot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun LibraryScreen(
    load: CatalogLoad,
    snapshot: LibrarySnapshot?,
    reports: List<ImportReport>,
    storage: AppStorage,
    onImported: (List<ImportReport>, LibrarySnapshot) -> Unit,
    onPlay: () -> Unit,
) {
    val catalog = (load as? CatalogLoad.Ready)?.catalog
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        val loaded = catalog ?: return@rememberLauncherForActivityResult
        if (uris.isEmpty()) return@rememberLauncherForActivityResult
        scope.launch {
            busy = true
            val imported = importUris(context, uris, storage, loaded)
            val refreshed = withContext(Dispatchers.IO) { LibraryScanner(storage, loaded).scan() }
            onImported(imported, refreshed)
            busy = false
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .windowInsetsPadding(WindowInsets.safeDrawing),
    ) {
        when (load) {
            is CatalogLoad.Failed -> {
                DatError(load.message)
                return@Box
            }
            CatalogLoad.Loading -> {
                LoadingColumn(stringResource(R.string.loading_dat))
                return@Box
            }
            is CatalogLoad.Ready -> if (snapshot == null) {
                LoadingColumn(stringResource(R.string.scanning))
                return@Box
            }
        }
        val readySnapshot = snapshot

        val fold = rememberFoldSnapshot()
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val twoPanes = libraryUsesTwoPanes(maxWidth.value.toInt(), fold)
            if (twoPanes) {
                Row(
                    Modifier.fillMaxSize().padding(24.dp),
                    horizontalArrangement = Arrangement.spacedBy(28.dp),
                ) {
                    IntroColumn(
                        modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()),
                        busy = busy,
                        reports = reports,
                        onImport = { launcher.launch(ZIP_MIME) },
                    )
                    GameColumn(
                        modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()),
                        snapshot = readySnapshot,
                        onPlay = onPlay,
                    )
                }
            } else {
                Column(
                    Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp),
                ) {
                    IntroColumn(Modifier.fillMaxWidth(), busy, reports) { launcher.launch(ZIP_MIME) }
                    GameColumn(Modifier.fillMaxWidth(), readySnapshot, onPlay)
                }
            }
        }
    }
}

@Composable
private fun BoxScope.LoadingColumn(message: String) {
    Column(
        modifier = Modifier.align(Alignment.Center),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(16.dp))
        Text(message, color = MaterialTheme.colorScheme.onBackground)
    }
}

@Composable
private fun DatError(message: String) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            "FBNG",
            style = MaterialTheme.typography.displaySmall,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Text(
            stringResource(R.string.dat_error_title),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.error,
        )
        Text(stringResource(R.string.dat_error_body, message), color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun IntroColumn(
    modifier: Modifier,
    busy: Boolean,
    reports: List<ImportReport>,
    onImport: () -> Unit,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            "FBNG",
            style = MaterialTheme.typography.displaySmall,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Text(
            stringResource(R.string.library_sub),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.secondary,
        )
        Text(stringResource(R.string.legal_notice), color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(stringResource(R.string.gamepad_hint), color = MaterialTheme.colorScheme.onSurfaceVariant)
        Button(
            onClick = onImport,
            enabled = !busy,
            modifier = Modifier.fillMaxWidth().height(52.dp),
        ) {
            Text(stringResource(R.string.import_button))
        }
        if (busy) {
            LinearProgressIndicator(Modifier.fillMaxWidth())
            Text(stringResource(R.string.importing))
        }
        if (reports.isNotEmpty()) {
            Text(stringResource(R.string.reports_heading), style = MaterialTheme.typography.titleMedium)
            reports.forEach { report -> ImportReportBlock(report) }
        }
        Text(
            stringResource(
                R.string.footer_core,
                BuildConfig.FBNEO_COMMIT_SHORT,
                BuildConfig.FBNEO_BUILD_DATE,
                BuildConfig.LIBRETRODROID_VERSION,
            ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun GameColumn(
    modifier: Modifier,
    snapshot: LibrarySnapshot,
    onPlay: () -> Unit,
) {
    Column(
        modifier
            .background(MaterialTheme.colorScheme.surface)
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            Modifier
                .width(36.dp)
                .height(6.dp)
                .background(MaterialTheme.colorScheme.primary),
        )
        Text(stringResource(R.string.game_title), style = MaterialTheme.typography.headlineMedium)
        Text(
            stringResource(R.string.game_english),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.secondary,
        )
        Text(stringResource(R.string.game_set), color = MaterialTheme.colorScheme.onSurfaceVariant)
        StatusLine(
            installed = snapshot.kovsh,
            expectedOk = snapshot.romReady,
            okText = stringResource(R.string.rom_ok),
            missingText = stringResource(R.string.rom_missing),
        )
        StatusLine(
            installed = snapshot.pgm,
            expectedOk = snapshot.biosReady,
            okText = stringResource(R.string.bios_ok),
            missingText = stringResource(R.string.bios_missing),
        )
        if (!snapshot.romReady) ProblemBlock(snapshot.kovsh)
        if (!snapshot.biosReady) ProblemBlock(snapshot.pgm)
        Button(
            onClick = onPlay,
            enabled = snapshot.canLaunch,
            modifier = Modifier.fillMaxWidth().height(52.dp),
        ) {
            Text(stringResource(if (snapshot.canLaunch) R.string.play else R.string.play_blocked))
        }
    }
}

@Composable
private fun StatusLine(installed: InstalledSet, expectedOk: Boolean, okText: String, missingText: String) {
    val color = when {
        expectedOk -> MaterialTheme.colorScheme.secondary
        installed.present -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Text(if (expectedOk) okText else missingText, color = color)
}

@Composable
private fun ProblemBlock(installed: InstalledSet) {
    val match = installed.match
    if (installed.unreadable) {
        Text(
            stringResource(R.string.not_a_zip, installed.expected + ".zip"),
            color = MaterialTheme.colorScheme.error,
        )
        return
    }
    if (match != null && match.isClean && match.setName != installed.expected) {
        Text(
            stringResource(
                R.string.installed_wrong,
                installed.expected + ".zip",
                match.setName,
                installed.expected,
            ),
            color = MaterialTheme.colorScheme.error,
        )
        return
    }
    if (match != null && !match.isClean) {
        Text(stringResource(R.string.mismatch_header, installed.expected + ".zip", match.setName))
        ProblemList(match.problems)
    }
}

@Composable
private fun ImportReportBlock(report: ImportReport) {
    Column(
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier.padding(vertical = 6.dp),
    ) {
        val headline = when (report.outcome) {
            InstallOutcome.INSTALLED -> stringResource(
                R.string.installed_as,
                report.installedFileName ?: report.displayName,
            )
            InstallOutcome.REJECTED_OTHER_SET -> stringResource(
                R.string.rejected_other,
                report.match?.setName ?: "?",
                report.match?.description ?: report.displayName,
            )
            InstallOutcome.REJECTED_MISMATCH -> stringResource(
                R.string.mismatch_header,
                report.displayName,
                report.match?.setName ?: "?",
            )
            InstallOutcome.UNRECOGNIZED -> stringResource(R.string.unrecognized, report.displayName)
            InstallOutcome.UNREADABLE -> stringResource(R.string.not_a_zip, report.displayName)
        }
        Text(report.displayName, style = MaterialTheme.typography.titleSmall)
        Text(
            headline,
            color = if (report.outcome == InstallOutcome.INSTALLED) {
                MaterialTheme.colorScheme.secondary
            } else {
                MaterialTheme.colorScheme.error
            },
        )
        report.match?.let { ProblemList(it.problems) }
    }
}

@Composable
internal fun ProblemList(problems: List<RomProblem>) {
    val shown = problems.take(12)
    shown.forEach { problem ->
        Text(
            problemText(problem),
            style = MaterialTheme.typography.bodySmall,
            color = if (problem.kind == ProblemKind.EXTRA) {
                MaterialTheme.colorScheme.onSurfaceVariant
            } else {
                MaterialTheme.colorScheme.error
            },
        )
    }
    if (problems.size > shown.size) {
        Text("+${problems.size - shown.size}", style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun problemText(problem: RomProblem): String = when (problem.kind) {
    ProblemKind.MISSING -> stringResource(R.string.problem_missing, problem.expectedName.orEmpty())
    ProblemKind.CRC_MISMATCH -> stringResource(
        R.string.problem_crc,
        problem.actualName ?: problem.expectedName.orEmpty(),
        crcHex(problem.actualCrc ?: 0L),
        crcHex(problem.expectedCrc ?: 0L),
    )
    ProblemKind.SIZE_MISMATCH -> stringResource(
        R.string.problem_size,
        problem.actualName ?: problem.expectedName.orEmpty(),
        (problem.actualSize ?: 0L).coerceAtMost(Int.MAX_VALUE.toLong()).toInt(),
        (problem.expectedSize ?: 0L).coerceAtMost(Int.MAX_VALUE.toLong()).toInt(),
    )
    ProblemKind.NAME_MISMATCH -> stringResource(
        R.string.problem_name,
        problem.actualName.orEmpty(),
        problem.expectedName.orEmpty(),
    )
    ProblemKind.DUPLICATE_NAME -> stringResource(R.string.problem_dupe, problem.actualName.orEmpty())
    ProblemKind.EXTRA -> stringResource(R.string.problem_extra, problem.actualName.orEmpty())
}

@Composable
internal fun rememberFoldSnapshot(): FoldSnapshot? {
    val activity = LocalContext.current.findActivity()
    val layoutInfo by WindowInfoTracker.getOrCreate(activity)
        .windowLayoutInfo(activity)
        .collectAsStateWithLifecycle(initialValue = WindowLayoutInfo(emptyList()))
    val feature = layoutInfo.displayFeatures.filterIsInstance<FoldingFeature>().firstOrNull() ?: return null
    val vertical = feature.orientation == FoldingFeature.Orientation.VERTICAL
    return FoldSnapshot(
        orientationVertical = vertical,
        separating = feature.isSeparating,
        hingeStartPx = if (vertical) feature.bounds.left else feature.bounds.top,
        hingeEndPx = if (vertical) feature.bounds.right else feature.bounds.bottom,
    )
}

private val ZIP_MIME = arrayOf(
    "application/zip",
    "application/x-zip-compressed",
    "application/octet-stream",
)
