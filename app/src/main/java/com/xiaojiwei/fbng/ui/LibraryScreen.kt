package com.xiaojiwei.fbng.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
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
import com.xiaojiwei.fbng.library.FolderScan
import com.xiaojiwei.fbng.library.LibrarySnapshot
import com.xiaojiwei.fbng.ui.theme.FbngButton
import com.xiaojiwei.fbng.ui.theme.FbngDimens

@Composable
internal fun LibraryContent(
    load: CatalogLoad,
    snapshot: LibrarySnapshot?,
    reports: List<ImportReport>,
    busy: Boolean,
    readFold: @Composable () -> FoldSnapshot?,
    onImport: () -> Unit,
    onPlay: (String) -> Unit,
    initialSection: ShellSection = ShellSection.Library,
    workMessage: Int = R.string.importing,
    folders: List<FolderScan> = emptyList(),
    folderError: Int? = null,
    launchError: Boolean = false,
    folderActions: FolderActions = FolderActions(),
) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
        contentColor = MaterialTheme.colorScheme.onBackground,
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
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
            LibraryShell(
                snapshot = snapshot,
                reports = reports,
                busy = busy,
                readFold = readFold,
                onImport = onImport,
                onPlay = onPlay,
                initialSection = initialSection,
                workMessage = workMessage,
                folders = folders,
                folderError = folderError,
                launchError = launchError,
                folderActions = folderActions,
            )
        }
    }
}

@Composable
private fun LibraryShell(
    snapshot: LibrarySnapshot,
    reports: List<ImportReport>,
    busy: Boolean,
    readFold: @Composable () -> FoldSnapshot?,
    onImport: () -> Unit,
    onPlay: (String) -> Unit,
    initialSection: ShellSection,
    workMessage: Int,
    folders: List<FolderScan>,
    folderError: Int?,
    launchError: Boolean,
    folderActions: FolderActions,
) {
    val focusManager = LocalFocusManager.current
    var sectionName by rememberSaveable { mutableStateOf(initialSection.name) }
    var savedFocus by rememberSaveable { mutableStateOf("") }
    var libraryFocus by rememberSaveable { mutableStateOf("") }
    var selectedName by rememberSaveable { mutableStateOf<String?>(null) }
    var detailsOpen by rememberSaveable { mutableStateOf(false) }
    var focusRequest by remember { mutableStateOf(0) }
    val gridState = rememberLazyGridState()
    val cardRequesters = remember { mutableMapOf<String, FocusRequester>() }
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var searchFocused by remember { mutableStateOf(false) }
    val section = ShellSection.valueOf(sectionName)
    val shellRequesters = remember {
        listOf(
            ShellTags.Play,
            ShellTags.EmptyImport,
            ShellTags.DetailBack,
            ShellTags.Search,
            ShellTags.Import,
            ShellTags.AddFolder,
            ShellTags.ScanFolders,
            ShellTags.Sync,
            ShellTags.SettingsInfo,
            ShellTags.SettingsCore,
            ShellTags.NavLibrary,
            ShellTags.NavImport,
            ShellTags.NavSync,
            ShellTags.NavSettings,
        ).associateWith { FocusRequester() }
    }
    val folderTags = folders.map { ShellTags.removeFolder(it.folder.uri) }
    val requesters = shellRequesters + folderTags.associateWith { tag ->
        key(tag) { remember { FocusRequester() } }
    }
    fun focusSaved(id: String) {
        if (section == ShellSection.Library) libraryFocus = id else savedFocus = id
    }
    fun request(id: String): Boolean {
        val requester = requesters[id] ?: cardRequesters[id] ?: return false
        return try {
            requester.requestFocus(FocusDirection.Enter)
        } catch (_: IllegalArgumentException) {
            false
        } catch (_: IllegalStateException) {
            false
        }
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val widthDp = maxWidth.value.toInt()
        val widthClass = shellWidth(widthDp)
        val rail = shellUsesNavigationRail(widthDp, readFold())
        val margin = when (widthClass) {
            ShellWidth.Compact -> FbngDimens.compactMargin
            ShellWidth.Medium -> FbngDimens.mediumMargin
            ShellWidth.Expanded -> FbngDimens.expandedMargin
        }
        val kovshTitle = stringResource(R.string.game_title)
        val games = remember(snapshot, rail, searchQuery, kovshTitle) {
            val needle = searchQuery.trim()
            if (rail || needle.isEmpty()) snapshot.games else snapshot.games.filter {
                it.name.contains(needle, ignoreCase = true) || it.description.contains(needle, ignoreCase = true) ||
                    (it.name == "kovsh" && kovshTitle.contains(needle, ignoreCase = true))
            }
        }
        val selected = games.firstOrNull { it.name == selectedName } ?: games.firstOrNull()
        val compactDetails = !rail && detailsOpen && selected != null

        fun goToImport() {
            searchFocused = false
            savedFocus = ""
            sectionName = ShellSection.Import.name
        }
        fun back() {
            when {
                searchFocused -> {
                    focusManager.clearFocus(force = true)
                    searchFocused = false
                    libraryFocus = selected?.let { ShellTags.game(it.name) } ?: ShellTags.NavLibrary
                    focusRequest++
                }
                section != ShellSection.Library -> sectionName = ShellSection.Library.name
                compactDetails -> {
                    detailsOpen = false
                    libraryFocus = ShellTags.game(checkNotNull(selected).name)
                    focusRequest++
                }
            }
        }

        BackHandler(enabled = searchFocused || section != ShellSection.Library || compactDetails, onBack = ::back)
        LaunchedEffect(section, snapshot, busy, folderTags, rail, widthClass, compactDetails, focusRequest) {
            if (busy) return@LaunchedEffect
            val preferred = when (section) {
                ShellSection.Library -> {
                    val card = selected?.let { ShellTags.game(it.name) }
                    val savedCard = games.indexOfFirst { ShellTags.game(it.name) == libraryFocus }
                    if (!compactDetails && savedCard >= 0 && libraryFocus !in cardRequesters) {
                        gridState.scrollToItem(savedCard)
                    }
                    listOfNotNull(
                        libraryFocus,
                        ShellTags.Play.takeIf { compactDetails },
                        card,
                        ShellTags.EmptyImport,
                        ShellTags.DetailBack,
                        ShellTags.NavLibrary,
                    )
                }
                ShellSection.Import -> listOf(savedFocus, if (reports.isEmpty()) ShellTags.AddFolder else ShellTags.Import, ShellTags.NavImport)
                ShellSection.Sync -> listOf(savedFocus, ShellTags.Sync)
                ShellSection.Settings -> listOf(savedFocus, ShellTags.SettingsInfo, ShellTags.SettingsCore)
            }
            val fallback = ShellTags.NavLibrary.takeIf { preferred.lastOrNull() == it }
            val targets = if (fallback == null) preferred else preferred.dropLast(1)
            repeat(4) {
                if (targets.distinct().any(::request)) return@LaunchedEffect
                withFrameNanos { }
            }
            if (fallback != null) request(fallback)
        }
        Box(Modifier.fillMaxSize().onKeyEvent { event ->
            if (event.type != KeyEventType.KeyUp || event.key != Key.ButtonB) return@onKeyEvent false
            back()
            true
        }) {
            ShellScaffold(
                section = section,
                rail = rail,
                expanded = widthClass == ShellWidth.Expanded,
                requesters = requesters,
                onSection = {
                    if (it != section) {
                        searchFocused = false
                        savedFocus = ""
                        sectionName = it.name
                    }
                },
                onFocused = ::focusSaved,
            ) {
                when (section) {
                    ShellSection.Library -> GameLibraryPage(
                        games = games,
                        selected = selected,
                        hasGames = snapshot.games.isNotEmpty(),
                        gridState = gridState,
                        cardRequesters = cardRequesters,
                        wide = rail,
                        compactDetails = compactDetails,
                        margin = margin,
                        busy = busy,
                        preparing = busy && workMessage == R.string.preparing_game,
                        launchError = launchError,
                        requesters = requesters,
                        onFocused = ::focusSaved,
                        onSelected = { selectedName = it },
                        onDetails = {
                            selectedName = it
                            detailsOpen = true
                            libraryFocus = ShellTags.Play
                            focusRequest++
                        },
                        onBack = ::back,
                        onPlay = onPlay,
                        onOpenImport = ::goToImport,
                        search = {
                            LibrarySearch(
                                query = searchQuery,
                                onQuery = { searchQuery = it },
                                onSearchFocus = { searchFocused = it },
                                onExitSearch = ::back,
                                requester = requesters.getValue(ShellTags.Search),
                                onFocused = ::focusSaved,
                            )
                        },
                    )
                    ShellSection.Import -> ImportPage(
                        reports = reports,
                        busy = busy,
                        margin = margin,
                        requesters = requesters,
                        onFocused = ::focusSaved,
                        onImport = onImport,
                        workMessage = workMessage,
                        folders = folders,
                        folderError = folderError,
                        folderActions = folderActions,
                    )
                    ShellSection.Sync -> SyncPage(
                        margin = margin,
                        requesters = requesters,
                        onFocused = ::focusSaved,
                    )
                    ShellSection.Settings -> SettingsPage(
                        margin = margin,
                        requesters = requesters,
                        onFocused = ::focusSaved,
                    )
                }
            }
        }
    }
}

@Composable
private fun LibrarySearch(
    query: String,
    onQuery: (String) -> Unit,
    onSearchFocus: (Boolean) -> Unit,
    onExitSearch: () -> Unit,
    requester: FocusRequester,
    onFocused: (String) -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val focusManager = LocalFocusManager.current
    OutlinedTextField(
        value = query,
        onValueChange = onQuery,
        modifier = Modifier
            .fillMaxWidth()
            .onFocusChanged {
                onSearchFocus(it.isFocused)
                if (it.isFocused) onFocused(ShellTags.Search)
            }
            .onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyUp &&
                    (event.key == Key.ButtonB || event.key == Key.Back || event.key == Key.Escape)
                ) {
                    onExitSearch()
                    true
                } else {
                    false
                }
            }
            .testTag(ShellTags.Search)
            .focusRequester(requester),
        singleLine = true,
        textStyle = MaterialTheme.typography.bodyLarge,
        label = { Text(stringResource(R.string.search_label)) },
        keyboardOptions = KeyboardOptions(imeAction = androidx.compose.ui.text.input.ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
        shape = RoundedCornerShape(FbngDimens.cardRadius),
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = scheme.onSurface,
            unfocusedTextColor = scheme.onSurface,
            focusedBorderColor = scheme.primary,
            unfocusedBorderColor = scheme.outline,
            cursorColor = scheme.primary,
            focusedLabelColor = scheme.secondary,
            unfocusedLabelColor = scheme.onSurfaceVariant,
            focusedContainerColor = scheme.surface,
            unfocusedContainerColor = scheme.surface,
        ),
    )
}

@Composable
private fun ImportPage(
    reports: List<ImportReport>,
    busy: Boolean,
    margin: Dp,
    requesters: Map<String, FocusRequester>,
    onFocused: (String) -> Unit,
    onImport: () -> Unit,
    workMessage: Int,
    folders: List<FolderScan>,
    folderError: Int?,
    folderActions: FolderActions,
) {
    val scheme = MaterialTheme.colorScheme
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(margin),
        verticalArrangement = Arrangement.spacedBy(FbngDimens.space16),
    ) {
        Text(
            stringResource(R.string.import_title),
            style = MaterialTheme.typography.headlineSmall,
            color = scheme.onBackground,
        )
        FolderManagement(
            folders = folders,
            busy = busy,
            workMessage = workMessage,
            error = folderError,
            requesters = requesters,
            onFocused = onFocused,
            actions = folderActions,
        )
        Text(
            stringResource(R.string.import_body),
            style = MaterialTheme.typography.bodyLarge,
            color = scheme.onSurface,
        )
        Text(
            stringResource(R.string.legal_notice),
            style = MaterialTheme.typography.bodyLarge,
            color = scheme.onSurfaceVariant,
        )
        FbngButton(
            onClick = onImport,
            enabled = !busy,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = FbngDimens.buttonHeight)
                .onFocusChanged { if (it.isFocused) onFocused(ShellTags.Import) }
                .testTag(ShellTags.Import)
                .focusRequester(requesters.getValue(ShellTags.Import)),
        ) {
            Text(stringResource(R.string.import_button))
        }
        if (reports.isNotEmpty()) {
            Text(
                stringResource(R.string.reports_heading),
                style = MaterialTheme.typography.titleMedium,
                color = scheme.onBackground,
            )
            reports.forEach { report -> ImportReportBlock(report) }
        }
    }
}

@Composable
private fun SyncPage(
    margin: Dp,
    requesters: Map<String, FocusRequester>,
    onFocused: (String) -> Unit,
) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(margin),
        verticalArrangement = Arrangement.spacedBy(FbngDimens.space16),
    ) {
        Text(
            stringResource(R.string.sync_title),
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onBackground,
        )
        FocusableMessage(
            tag = ShellTags.Sync,
            requester = requesters.getValue(ShellTags.Sync),
            onFocused = onFocused,
            container = MaterialTheme.colorScheme.surface,
        ) {
            Text(
                stringResource(R.string.sync_body),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

@Composable
private fun SettingsPage(
    margin: Dp,
    requesters: Map<String, FocusRequester>,
    onFocused: (String) -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(margin),
        verticalArrangement = Arrangement.spacedBy(FbngDimens.space16),
    ) {
        Text(
            stringResource(R.string.settings_title),
            style = MaterialTheme.typography.headlineSmall,
            color = scheme.onBackground,
        )
        FocusableMessage(
            tag = ShellTags.SettingsInfo,
            requester = requesters.getValue(ShellTags.SettingsInfo),
            onFocused = onFocused,
            container = scheme.surface,
        ) {
            Text(
                stringResource(R.string.settings_body),
                style = MaterialTheme.typography.bodyLarge,
                color = scheme.onSurface,
            )
            Text(
                stringResource(R.string.gamepad_hint),
                style = MaterialTheme.typography.bodyLarge,
                color = scheme.onSurfaceVariant,
            )
            Text(
                stringResource(R.string.legal_notice),
                style = MaterialTheme.typography.bodyLarge,
                color = scheme.onSurfaceVariant,
            )
        }
        FocusableMessage(
            tag = ShellTags.SettingsCore,
            requester = requesters.getValue(ShellTags.SettingsCore),
            onFocused = onFocused,
            container = scheme.surface,
        ) {
            Text(
                stringResource(
                    R.string.footer_core,
                    BuildConfig.FBNEO_COMMIT_SHORT,
                    BuildConfig.FBNEO_BUILD_DATE,
                    BuildConfig.LIBRETRODROID_VERSION,
                ),
                style = MaterialTheme.typography.bodyLarge,
                color = scheme.onSurface,
            )
        }
    }
}

@Composable
private fun BoxScope.LoadingColumn(message: String) {
    val requester = remember { FocusRequester() }
    LaunchedEffect(message) { requester.tryFocus() }
    Column(
        modifier = Modifier
            .align(Alignment.Center)
            .focusRequester(requester)
            .focusable(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(FbngDimens.space16))
        Text(
            message,
            color = MaterialTheme.colorScheme.onBackground,
            style = MaterialTheme.typography.bodyLarge,
        )
    }
}

@Composable
private fun DatError(message: String) {
    val requester = remember { FocusRequester() }
    LaunchedEffect(message) { requester.tryFocus() }
    PlainFocusBlock(
        tag = ShellTags.DatError,
        requester = requester,
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(FbngDimens.space24),
    ) {
        Text(
            stringResource(R.string.app_name),
            style = MaterialTheme.typography.displaySmall,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Text(
            stringResource(R.string.dat_error_title),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.error,
        )
        Text(
            stringResource(R.string.dat_error_body, message),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun ImportReportBlock(report: ImportReport) {
    val scheme = MaterialTheme.colorScheme
    Column(
        verticalArrangement = Arrangement.spacedBy(FbngDimens.space4),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(FbngDimens.cardRadius))
            .background(scheme.surface)
            .padding(FbngDimens.space16),
    ) {
        val headline = when (report.outcome) {
            InstallOutcome.INSTALLED -> stringResource(
                R.string.installed_as,
                report.installedFileName ?: report.displayName,
            )
            InstallOutcome.AMBIGUOUS -> stringResource(R.string.import_ambiguous, report.displayName)
            InstallOutcome.REJECTED_MISMATCH -> stringResource(
                R.string.mismatch_header,
                report.displayName,
                report.match?.setName ?: "?",
            )
            InstallOutcome.UNRECOGNIZED -> stringResource(R.string.unrecognized, report.displayName)
            InstallOutcome.UNREADABLE -> stringResource(R.string.not_a_zip, report.displayName)
        }
        Text(
            report.displayName,
            style = MaterialTheme.typography.titleMedium,
            color = scheme.onSurface,
        )
        Text(
            headline,
            style = MaterialTheme.typography.bodyLarge,
            color = if (report.outcome == InstallOutcome.INSTALLED) scheme.secondary else scheme.error,
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
            style = MaterialTheme.typography.bodyLarge,
            color = if (problem.kind == ProblemKind.EXTRA) {
                MaterialTheme.colorScheme.onSurfaceVariant
            } else {
                MaterialTheme.colorScheme.error
            },
        )
    }
    if (problems.size > shown.size) {
        Text(
            "+${problems.size - shown.size}",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
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

private fun FocusRequester.tryFocus() {
    try {
        requestFocus()
    } catch (_: IllegalArgumentException) {
    } catch (_: IllegalStateException) {
    }
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
