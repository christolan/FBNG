package com.xiaojiwei.fbng.ui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
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
import com.xiaojiwei.fbng.library.AppStorage
import com.xiaojiwei.fbng.library.InstalledSet
import com.xiaojiwei.fbng.library.LibraryScanner
import com.xiaojiwei.fbng.library.LibrarySnapshot
import com.xiaojiwei.fbng.ui.theme.FbngButton
import com.xiaojiwei.fbng.ui.theme.FbngDimens
import com.xiaojiwei.fbng.ui.theme.fbngFocusTarget
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

    LibraryContent(
        load = load,
        snapshot = snapshot,
        reports = reports,
        busy = busy,
        readFold = { rememberFoldSnapshot() },
        onImport = { launcher.launch(ZIP_MIME) },
        onPlay = onPlay,
    )
}

@Composable
internal fun LibraryContent(
    load: CatalogLoad,
    snapshot: LibrarySnapshot?,
    reports: List<ImportReport>,
    busy: Boolean,
    readFold: @Composable () -> FoldSnapshot?,
    onImport: () -> Unit,
    onPlay: () -> Unit,
    initialSection: ShellSection = ShellSection.Library,
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
    onPlay: () -> Unit,
    initialSection: ShellSection,
) {
    val focusManager = LocalFocusManager.current
    var sectionName by rememberSaveable { mutableStateOf(initialSection.name) }
    var savedFocus by rememberSaveable { mutableStateOf("") }
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var searchFocused by remember { mutableStateOf(false) }
    val section = ShellSection.valueOf(sectionName)
    val requesters = remember {
        listOf(
            ShellTags.Play,
            ShellTags.Card,
            ShellTags.Search,
            ShellTags.Import,
            ShellTags.Sync,
            ShellTags.SettingsInfo,
            ShellTags.SettingsCore,
            ShellTags.NavLibrary,
            ShellTags.NavImport,
            ShellTags.NavSync,
            ShellTags.NavSettings,
        ).associateWith { FocusRequester() }
    }
    fun focusSaved(id: String) {
        savedFocus = id
    }
    fun request(id: String): Boolean {
        val requester = requesters[id] ?: return false
        return try {
            requester.requestFocus(FocusDirection.Enter)
        } catch (_: IllegalArgumentException) {
            false
        } catch (_: IllegalStateException) {
            false
        }
    }
    fun focusSection(target: ShellSection, canLaunch: Boolean): Boolean {
        val preferred = listOf(savedFocus) + when (target) {
            ShellSection.Library -> listOf(
                // Search sits at the top of the narrow library. Focusing the cover
                // first scrolls that header off the screen.
                if (canLaunch) ShellTags.Play else ShellTags.Search,
                ShellTags.Card,
                ShellTags.Play,
            )
            ShellSection.Import -> listOf(ShellTags.Import)
            ShellSection.Sync -> listOf(ShellTags.Sync)
            ShellSection.Settings -> listOf(ShellTags.SettingsInfo, ShellTags.SettingsCore)
        }
        return preferred.distinct().any { request(it) }
    }
    fun focusLibraryPrimary() {
        val id = if (snapshot.canLaunch) ShellTags.Play else ShellTags.Card
        if (!request(id)) request(ShellTags.NavLibrary)
        savedFocus = id
    }

    LaunchedEffect(reports) {
        if (reports.isNotEmpty()) sectionName = ShellSection.Import.name
    }
    LaunchedEffect(section, snapshot.canLaunch) {
        // The focus target is not attached on the first frame. Retry across a few frames.
        repeat(4) {
            if (focusSection(section, snapshot.canLaunch)) return@LaunchedEffect
            withFrameNanos { }
        }
    }

    BackHandler(enabled = searchFocused || section != ShellSection.Library) {
        if (searchFocused) {
            focusManager.clearFocus(force = true)
            focusLibraryPrimary()
        } else {
            sectionName = ShellSection.Library.name
        }
    }

    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .onKeyEvent { event ->
                if (event.type != KeyEventType.KeyUp || event.key != Key.ButtonB) return@onKeyEvent false
                if (searchFocused) {
                    focusManager.clearFocus(force = true)
                    focusLibraryPrimary()
                } else if (section != ShellSection.Library) {
                    sectionName = ShellSection.Library.name
                }
                true
            },
    ) {
        val widthDp = maxWidth.value.toInt()
        val widthClass = shellWidth(widthDp)
        val rail = shellUsesNavigationRail(widthDp, readFold())
        val margin = when (widthClass) {
            ShellWidth.Compact -> FbngDimens.compactMargin
            ShellWidth.Medium -> FbngDimens.mediumMargin
            ShellWidth.Expanded -> FbngDimens.expandedMargin
        }
        val shortWindow = maxHeight < FbngDimens.shortWindow
        ShellScaffold(
            section = section,
            rail = rail,
            expanded = widthClass == ShellWidth.Expanded,
            requesters = requesters,
            onSection = { sectionName = it.name },
            onFocused = ::focusSaved,
        ) {
            when (section) {
                ShellSection.Library -> LibraryPage(
                    snapshot = snapshot,
                    query = searchQuery,
                    onQuery = { searchQuery = it },
                    onSearchFocus = { searchFocused = it },
                    onExitSearch = {
                        focusManager.clearFocus(force = true)
                        focusLibraryPrimary()
                    },
                    showSearch = !rail,
                    shortWindow = shortWindow,
                    margin = margin,
                    requesters = requesters,
                    onFocused = ::focusSaved,
                    onPlay = onPlay,
                    onOpenImport = { sectionName = ShellSection.Import.name },
                )
                ShellSection.Import -> ImportPage(
                    reports = reports,
                    busy = busy,
                    margin = margin,
                    requesters = requesters,
                    onFocused = ::focusSaved,
                    onImport = onImport,
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

@Composable
private fun LibraryPage(
    snapshot: LibrarySnapshot,
    query: String,
    onQuery: (String) -> Unit,
    onSearchFocus: (Boolean) -> Unit,
    onExitSearch: () -> Unit,
    showSearch: Boolean,
    shortWindow: Boolean,
    margin: Dp,
    requesters: Map<String, FocusRequester>,
    onFocused: (String) -> Unit,
    onPlay: () -> Unit,
    onOpenImport: () -> Unit,
) {
    val title = stringResource(R.string.game_title)
    val english = stringResource(R.string.game_english)
    val needle = query.trim()
    val matches = needle.isEmpty() ||
        title.contains(needle, ignoreCase = true) ||
        english.contains(needle, ignoreCase = true) ||
        snapshot.kovsh.expected.contains(needle, ignoreCase = true)
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(
                horizontal = margin,
                vertical = if (shortWindow) FbngDimens.space8 else margin,
            ),
        verticalArrangement = Arrangement.spacedBy(if (shortWindow) FbngDimens.space8 else FbngDimens.space16),
    ) {
        if (showSearch) {
            Column(verticalArrangement = Arrangement.spacedBy(FbngDimens.space8)) {
                Text(
                    stringResource(R.string.app_name),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.secondary,
                )
                Text(
                    stringResource(R.string.shell_library),
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onBackground,
                )
            }
            LibrarySearch(
                query = query,
                onQuery = onQuery,
                onSearchFocus = onSearchFocus,
                onExitSearch = onExitSearch,
                requester = requesters.getValue(ShellTags.Search),
                onFocused = onFocused,
            )
        }
        GameBanner(
            snapshot = snapshot,
            shortWindow = shortWindow,
            detailed = !showSearch && !shortWindow,
            playRequester = requesters.getValue(ShellTags.Play),
            onFocused = onFocused,
            onPlay = onPlay,
        )
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                stringResource(R.string.all_games),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                stringResource(R.string.library_count),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (matches) {
            CoverGrid(
                snapshot = snapshot,
                requester = requesters.getValue(ShellTags.Card),
                onFocused = onFocused,
                onPlay = onPlay,
                onOpenImport = onOpenImport,
                down = if (showSearch) requesters.getValue(ShellTags.NavLibrary) else null,
                shortWindow = shortWindow,
            )
        } else {
            Text(
                stringResource(R.string.search_empty),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
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
private fun GameBanner(
    snapshot: LibrarySnapshot,
    shortWindow: Boolean,
    detailed: Boolean,
    playRequester: FocusRequester,
    onFocused: (String) -> Unit,
    onPlay: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(FbngDimens.panelRadius))
            .background(scheme.surface)
            .padding(if (shortWindow) FbngDimens.space12 else FbngDimens.space16),
        verticalArrangement = Arrangement.spacedBy(FbngDimens.space8),
    ) {
        if (shortWindow) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(FbngDimens.space16),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(FbngDimens.space4)) {
                    Text(
                        stringResource(R.string.featured_label),
                        style = MaterialTheme.typography.labelLarge,
                        color = scheme.secondary,
                    )
                    Text(
                        stringResource(R.string.game_title),
                        style = MaterialTheme.typography.titleLarge,
                        color = scheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        shortStatus(snapshot),
                        style = MaterialTheme.typography.bodyLarge,
                        color = scheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                PlayButton(
                    snapshot = snapshot,
                    requester = playRequester,
                    onFocused = onFocused,
                    onPlay = onPlay,
                    modifier = Modifier.widthIn(min = FbngDimens.cardMin),
                )
            }
        } else if (!detailed) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(FbngDimens.space12),
            ) {
                Column(
                    Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(FbngDimens.space8),
                ) {
                    Text(
                        stringResource(R.string.featured_label),
                        style = MaterialTheme.typography.labelLarge,
                        color = scheme.secondary,
                    )
                    Text(
                        stringResource(R.string.game_title),
                        style = MaterialTheme.typography.titleLarge,
                        color = scheme.onSurface,
                    )
                    Text(
                        shortStatus(snapshot),
                        style = MaterialTheme.typography.bodyLarge,
                        color = scheme.onSurfaceVariant,
                    )
                    PlayButton(
                        snapshot = snapshot,
                        requester = playRequester,
                        onFocused = onFocused,
                        onPlay = onPlay,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                Poster(Modifier.width(FbngDimens.railWidthMedium / 2).heightIn(min = FbngDimens.cardMin))
            }
        } else {
            Text(
                stringResource(R.string.featured_label),
                style = MaterialTheme.typography.labelLarge,
                color = scheme.secondary,
            )
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(FbngDimens.space16),
            ) {
                Column(
                    Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(FbngDimens.space8),
                ) {
                    Text(
                        stringResource(R.string.game_title),
                        style = MaterialTheme.typography.headlineSmall,
                        color = scheme.onSurface,
                    )
                    Text(
                        stringResource(R.string.game_english),
                        style = MaterialTheme.typography.titleMedium,
                        color = scheme.secondary,
                    )
                    Text(
                        stringResource(R.string.game_set),
                        style = MaterialTheme.typography.bodyLarge,
                        color = scheme.onSurfaceVariant,
                    )
                    Text(
                        stringResource(R.string.banner_body),
                        style = MaterialTheme.typography.bodyLarge,
                        color = scheme.onSurface,
                    )
                    ReadinessDetail(snapshot)
                    PlayButton(
                        snapshot = snapshot,
                        requester = playRequester,
                        onFocused = onFocused,
                        onPlay = onPlay,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                Poster(Modifier.width(FbngDimens.cardMin).heightIn(min = FbngDimens.cardMin))
            }
            Text(
                stringResource(R.string.library_page),
                modifier = Modifier.align(Alignment.End),
                style = MaterialTheme.typography.bodyLarge,
                color = scheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun Poster(modifier: Modifier) {
    val scheme = MaterialTheme.colorScheme
    Box(
        modifier
            .clip(RoundedCornerShape(FbngDimens.cardRadius))
            .background(scheme.surfaceContainerHigh),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier.padding(FbngDimens.space12),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(FbngDimens.space8),
        ) {
            Text(
                stringResource(R.string.poster_label),
                style = MaterialTheme.typography.labelLarge,
                color = scheme.secondary,
                textAlign = TextAlign.Center,
            )
            Text(
                stringResource(R.string.game_title),
                style = MaterialTheme.typography.titleMedium,
                color = scheme.onSurface,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun PlayButton(
    snapshot: LibrarySnapshot,
    requester: FocusRequester,
    onFocused: (String) -> Unit,
    onPlay: () -> Unit,
    modifier: Modifier,
) {
    FbngButton(
        onClick = onPlay,
        enabled = snapshot.canLaunch,
        modifier = modifier
            .heightIn(min = FbngDimens.buttonHeight)
            .onFocusChanged { if (it.isFocused) onFocused(ShellTags.Play) }
            .testTag(ShellTags.Play)
            .focusRequester(requester),
    ) {
        Text(
            stringResource(if (snapshot.canLaunch) R.string.play else R.string.play_blocked),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun CoverGrid(
    snapshot: LibrarySnapshot,
    requester: FocusRequester,
    onFocused: (String) -> Unit,
    onPlay: () -> Unit,
    onOpenImport: () -> Unit,
    down: FocusRequester?,
    shortWindow: Boolean,
) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val gap = FbngDimens.space16
        val columns = ((maxWidth + gap) / (FbngDimens.cardMin + gap)).toInt().coerceAtLeast(1)
        val cell = (maxWidth - gap * (columns - 1)) / columns
        CoverCard(
            snapshot = snapshot,
            requester = requester,
            onFocused = onFocused,
            onClick = { if (snapshot.canLaunch) onPlay() else onOpenImport() },
            modifier = Modifier.width(cell),
            down = down,
            shortWindow = shortWindow,
        )
    }
}

@Composable
private fun CoverCard(
    snapshot: LibrarySnapshot,
    requester: FocusRequester,
    onFocused: (String) -> Unit,
    onClick: () -> Unit,
    modifier: Modifier,
    down: FocusRequester?,
    shortWindow: Boolean,
) {
    val scheme = MaterialTheme.colorScheme
    Column(
        modifier
            .onFocusChanged { if (it.isFocused) onFocused(ShellTags.Card) }
            .testTag(ShellTags.Card)
            .then(
                if (down == null) {
                    Modifier
                } else {
                    Modifier.onPreviewKeyEvent { event ->
                        if (event.type == KeyEventType.KeyDown && event.key == Key.DirectionDown) {
                            down.requestFocus(FocusDirection.Down)
                            true
                        } else {
                            false
                        }
                    }.focusProperties { this.down = down }
                },
            )
            .focusRequester(requester)
            .fbngFocusTarget(
                onClick = onClick,
                container = scheme.surface,
                large = true,
            )
            .padding(FbngDimens.space12),
        verticalArrangement = Arrangement.spacedBy(FbngDimens.space8),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(if (shortWindow) FbngDimens.space48 else FbngDimens.cardMin / 2)
                .clip(RoundedCornerShape(FbngDimens.controlRadius))
                .background(scheme.surfaceContainerHigh),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                stringResource(R.string.poster_label),
                style = MaterialTheme.typography.titleMedium,
                color = scheme.onSurface,
                textAlign = TextAlign.Center,
            )
        }
        Text(
            stringResource(R.string.game_title),
            style = MaterialTheme.typography.titleMedium,
            color = scheme.onSurface,
            maxLines = if (shortWindow) 1 else 2,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            stringResource(R.string.game_set),
            style = MaterialTheme.typography.bodyLarge,
            color = scheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            shortStatus(snapshot),
            style = MaterialTheme.typography.bodyLarge,
            color = if (snapshot.canLaunch) scheme.secondary else scheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ReadinessDetail(snapshot: LibrarySnapshot) {
    if (needsDetail(snapshot.kovsh, snapshot.romReady)) {
        StatusLine(
            installed = snapshot.kovsh,
            expectedOk = snapshot.romReady,
            okText = stringResource(R.string.rom_ok),
            missingText = stringResource(R.string.rom_missing),
        )
        if (!snapshot.romReady) ProblemBlock(snapshot.kovsh)
    } else {
        Text(
            if (snapshot.romReady) stringResource(R.string.card_ready) else stringResource(R.string.card_needs_rom),
            style = MaterialTheme.typography.bodyLarge,
            color = if (snapshot.romReady) {
                MaterialTheme.colorScheme.secondary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
    }
    if (needsDetail(snapshot.pgm, snapshot.biosReady)) {
        StatusLine(
            installed = snapshot.pgm,
            expectedOk = snapshot.biosReady,
            okText = stringResource(R.string.bios_ok),
            missingText = stringResource(R.string.bios_missing),
        )
        if (!snapshot.biosReady) ProblemBlock(snapshot.pgm)
    } else if (!snapshot.biosReady) {
        Text(
            stringResource(R.string.card_needs_bios),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun needsDetail(installed: InstalledSet, ready: Boolean): Boolean {
    if (ready) return true
    if (installed.unreadable) return true
    return installed.present
}

@Composable
private fun shortStatus(snapshot: LibrarySnapshot): String = when {
    snapshot.canLaunch -> stringResource(R.string.card_ready)
    snapshot.kovsh.unreadable || snapshot.pgm.unreadable -> stringResource(R.string.card_blocked)
    snapshot.kovsh.present && !snapshot.romReady -> stringResource(R.string.card_blocked)
    snapshot.pgm.present && !snapshot.biosReady -> stringResource(R.string.card_blocked)
    !snapshot.romReady -> stringResource(R.string.card_needs_rom)
    else -> stringResource(R.string.card_needs_bios)
}

@Composable
private fun ImportPage(
    reports: List<ImportReport>,
    busy: Boolean,
    margin: Dp,
    requesters: Map<String, FocusRequester>,
    onFocused: (String) -> Unit,
    onImport: () -> Unit,
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
        if (busy) {
            LinearProgressIndicator(
                Modifier.fillMaxWidth(),
                color = scheme.primary,
                trackColor = scheme.secondaryContainer,
            )
            Text(
                stringResource(R.string.importing),
                style = MaterialTheme.typography.bodyLarge,
                color = scheme.onBackground,
            )
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
private fun StatusLine(installed: InstalledSet, expectedOk: Boolean, okText: String, missingText: String) {
    val color = when {
        expectedOk -> MaterialTheme.colorScheme.secondary
        installed.present -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Text(
        if (expectedOk) okText else missingText,
        color = color,
        style = MaterialTheme.typography.bodyLarge,
    )
}

@Composable
private fun ProblemBlock(installed: InstalledSet) {
    val match = installed.match
    if (installed.unreadable) {
        Text(
            stringResource(R.string.not_a_zip, installed.expected + ".zip"),
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodyLarge,
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
            style = MaterialTheme.typography.bodyLarge,
        )
        return
    }
    if (match != null && !match.isClean) {
        Text(
            stringResource(R.string.mismatch_header, installed.expected + ".zip", match.setName),
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodyLarge,
        )
        ProblemList(match.problems)
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

private val ZIP_MIME = arrayOf(
    "application/zip",
    "application/x-zip-compressed",
    "application/octet-stream",
)
