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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.hideFromAccessibility
import androidx.compose.ui.semantics.semantics
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
import com.xiaojiwei.fbng.library.LibraryResults
import com.xiaojiwei.fbng.library.LibrarySnapshot
import com.xiaojiwei.fbng.library.indexLibrary
import com.xiaojiwei.fbng.ui.theme.FbngButton
import com.xiaojiwei.fbng.ui.theme.FbngButtonKind
import com.xiaojiwei.fbng.ui.theme.FbngDimens

private enum class LibraryPicker { Letters, Status }

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
    playBusy: Boolean = busy,
    preparingGame: Boolean = busy && workMessage == R.string.preparing_game,
    favoriteNames: Set<String> = emptySet(),
    onToggleFavorite: (String) -> Unit = {},
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
            val localizedTitles = when (load) {
                is CatalogLoad.Failed -> {
                    DatError(load.message)
                    return@Box
                }
                CatalogLoad.Loading -> {
                    LoadingColumn(stringResource(R.string.loading_dat))
                    return@Box
                }
                is CatalogLoad.Ready -> {
                    if (snapshot == null) {
                        LoadingColumn(stringResource(R.string.scanning))
                        return@Box
                    }
                    load.localizedTitles
                }
            }
            LibraryShell(
                snapshot = snapshot,
                localizedTitles = localizedTitles,
                reports = reports,
                busy = busy,
                playBusy = playBusy,
                preparingGame = preparingGame,
                readFold = readFold,
                onImport = onImport,
                onPlay = onPlay,
                initialSection = initialSection,
                workMessage = workMessage,
                folders = folders,
                folderError = folderError,
                launchError = launchError,
                folderActions = folderActions,
                favoriteNames = favoriteNames,
                onToggleFavorite = onToggleFavorite,
            )
        }
    }
}

@Composable
private fun LibraryShell(
    snapshot: LibrarySnapshot,
    localizedTitles: Map<String, String>,
    reports: List<ImportReport>,
    busy: Boolean,
    playBusy: Boolean,
    preparingGame: Boolean,
    readFold: @Composable () -> FoldSnapshot?,
    onImport: () -> Unit,
    onPlay: (String) -> Unit,
    initialSection: ShellSection,
    workMessage: Int,
    folders: List<FolderScan>,
    folderError: Int?,
    launchError: Boolean,
    folderActions: FolderActions,
    favoriteNames: Set<String>,
    onToggleFavorite: (String) -> Unit,
) {
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    var sectionName by rememberSaveable { mutableStateOf(initialSection.name) }
    var savedFocus by rememberSaveable { mutableStateOf("") }
    var libraryFocus by rememberSaveable { mutableStateOf("") }
    var favoritesFocus by rememberSaveable { mutableStateOf("") }
    var navFocus by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedName by rememberSaveable { mutableStateOf<String?>(null) }
    var detailsOpen by rememberSaveable { mutableStateOf(false) }
    var playableOnly by rememberSaveable { mutableStateOf(false) }
    var picker by rememberSaveable { mutableStateOf<LibraryPicker?>(null) }
    var focusedLetter by rememberSaveable { mutableStateOf('#') }
    var pickerReturnFocus by rememberSaveable { mutableStateOf<String?>(null) }
    var jumpToName by rememberSaveable { mutableStateOf<String?>(null) }
    var focusRequest by remember { mutableStateOf(0) }
    val listState = rememberLazyListState()
    val favoritesGridState = rememberLazyGridState()
    val cardRequesters = remember { mutableMapOf<String, FocusRequester>() }
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var searchFocused by remember { mutableStateOf(false) }
    val section = ShellSection.valueOf(sectionName)
    val shellRequesters = remember {
        listOf(
            ShellTags.Play,
            ShellTags.ToggleFavorite,
            ShellTags.OpenLibrary,
            ShellTags.EmptyImport,
            ShellTags.DetailBack,
            ShellTags.Search,
            ShellTags.ClearSearch,
            ShellTags.JumpToLetter,
            ShellTags.LibraryFilter,
            ShellTags.Import,
            ShellTags.AddFolder,
            ShellTags.ScanFolders,
            ShellTags.Sync,
            ShellTags.OpenImport,
            ShellTags.OpenSync,
            ShellTags.SettingsBack,
            ShellTags.SettingsInfo,
            ShellTags.SettingsCore,
            ShellTags.NavFavorites,
            ShellTags.NavLibrary,
            ShellTags.NavSettings,
        ).associateWith { FocusRequester() }
    }
    val folderTags = folders.map { ShellTags.removeFolder(it.folder.uri) }
    val requesters = shellRequesters + folderTags.associateWith { tag ->
        key(tag) { remember { FocusRequester() } }
    }
    fun focusSaved(id: String) {
        navFocus = null
        when (section) {
            ShellSection.Library -> libraryFocus = id
            ShellSection.Favorites -> favoritesFocus = id
            else -> savedFocus = id
        }
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
        val gameTitles = remember(localizedTitles, kovshTitle) { localizedTitles + ("kovsh" to kovshTitle) }
        val entries = remember(snapshot.games, gameTitles) { indexLibrary(snapshot.games, gameTitles) }
        val favoriteEntries = remember(entries, favoriteNames) { entries.filter { it.game.name in favoriteNames } }
        val favoriteCards = remember(favoriteEntries) { favoriteEntries.map { ShellTags.favorite(it.game.name) } }
        val needle = if (rail) "" else searchQuery.trim()
        val matchingEntries = remember(entries, needle) {
            if (needle.isEmpty()) entries else entries.filter { it.matches(needle) }
        }
        val results = remember(matchingEntries, playableOnly) {
            LibraryResults(if (playableOnly) matchingEntries.filter { it.game.canLaunch } else matchingEntries)
        }
        val selected = results.byName[selectedName] ?: results.entries.firstOrNull()
        val compactDetails = !rail && detailsOpen && selected != null
        val libraryActionFocused = libraryFocus == ShellTags.Play || libraryFocus == ShellTags.ToggleFavorite
        val wideDetailsFocused = rail && section == ShellSection.Library && navFocus == null && libraryActionFocused
        val pickerOpen = section == ShellSection.Library && picker != null && entries.isNotEmpty()
        val inlineLetters = pickerOpen && rail && picker == LibraryPicker.Letters

        fun returnToList(name: String? = selected?.game?.name) {
            libraryFocus = name?.let(ShellTags::game)
                ?: if (!rail && searchQuery.isNotEmpty()) ShellTags.ClearSearch else ShellTags.LibraryFilter
            focusRequest++
        }

        fun closePicker() {
            libraryFocus = pickerReturnFocus ?: ShellTags.LibraryFilter
            pickerReturnFocus = null
            picker = null
            focusRequest++
        }

        fun showPicker(target: LibraryPicker) {
            if (pickerOpen) {
                if (picker == target) closePicker()
                return
            }
            keyboard?.hide()
            pickerReturnFocus = when (target) {
                LibraryPicker.Letters -> selected?.game?.name?.let(ShellTags::game) ?: ShellTags.JumpToLetter
                LibraryPicker.Status -> ShellTags.LibraryFilter
            }
            if (target == LibraryPicker.Letters) focusedLetter = selected?.letter ?: '#'
            picker = target
        }

        fun chooseLetter(letter: Char) {
            val name = results.groups[letter]?.firstOrNull()?.game?.name
            closePicker()
            if (name != null) {
                selectedName = name
                jumpToName = name
                returnToList(name)
            }
        }

        val letterPicker: (@Composable () -> Unit)? = if (pickerOpen && picker == LibraryPicker.Letters) {
            {
                LibraryLetterPicker(
                    lettersWithResults = results.groups.keys,
                    focusedLetter = focusedLetter,
                    onLetterFocused = { focusedLetter = it },
                    onDismiss = ::closePicker,
                    onChoose = ::chooseLetter,
                    inline = inlineLetters,
                )
            }
        } else null

        LaunchedEffect(picker, entries.isEmpty(), results.groups.isEmpty()) {
            if (picker != null && (entries.isEmpty() || (picker == LibraryPicker.Letters && results.groups.isEmpty()))) {
                closePicker()
            }
        }

        fun openSection(destination: ShellSection) {
            if (destination == section) return
            if (pickerOpen) closePicker()
            if (searchFocused) keyboard?.hide()
            searchFocused = false
            navFocus = null
            savedFocus = when {
                destination == ShellSection.Settings && section == ShellSection.Import -> ShellTags.OpenImport
                destination == ShellSection.Settings && section == ShellSection.Sync -> ShellTags.OpenSync
                else -> ""
            }
            sectionName = destination.name
        }

        fun back() {
            when {
                pickerOpen -> closePicker()
                section == ShellSection.Library && searchFocused -> {
                    keyboard?.hide()
                    focusManager.clearFocus(force = true)
                    searchFocused = false
                    returnToList()
                }
                section == ShellSection.Library && compactDetails -> {
                    detailsOpen = false
                    returnToList(selected.game.name)
                }
                wideDetailsFocused -> returnToList()
                section == ShellSection.Import || section == ShellSection.Sync -> openSection(ShellSection.Settings)
                section != ShellSection.Favorites -> openSection(ShellSection.Favorites)
            }
        }

        BackHandler(
            enabled = section != ShellSection.Favorites,
            onBack = ::back,
        )
        val tab = when (section) {
            ShellSection.Import, ShellSection.Sync -> ShellSection.Settings
            else -> section
        }
        val tabTag = when (section) {
            ShellSection.Favorites -> ShellTags.NavFavorites
            ShellSection.Library -> ShellTags.NavLibrary
            ShellSection.Settings, ShellSection.Import, ShellSection.Sync -> ShellTags.NavSettings
        }
        val libraryFocusBusy = section == ShellSection.Library && playBusy
        val importBusy = section == ShellSection.Import && busy
        val focusedGameName = ShellTags.gameName(libraryFocus)
        val savedRowExists = focusedGameName in results.rowByName
        LaunchedEffect(
            section, results.entries.isNotEmpty(), savedRowExists, libraryFocusBusy, importBusy, folderTags, rail,
            widthClass, compactDetails, pickerOpen, focusRequest,
            favoriteCards.takeIf { section == ShellSection.Favorites },
        ) {
            if (libraryFocusBusy || pickerOpen) return@LaunchedEffect
            if (navFocus?.let(::request) == true) return@LaunchedEffect
            val preferred = when (section) {
                ShellSection.Favorites -> {
                    val card = favoritesFocus.takeIf { it in favoriteCards } ?: favoriteCards.firstOrNull()
                    if (card != null &&
                        favoritesGridState.layoutInfo.visibleItemsInfo.none { it.key == card }
                    ) {
                        favoritesGridState.scrollToItem(favoriteCards.indexOf(card))
                    }
                    listOfNotNull(
                        card,
                        ShellTags.OpenLibrary.takeIf { favoriteEntries.isEmpty() },
                    )
                }
                ShellSection.Library -> {
                    val name = focusedGameName.takeIf { savedRowExists } ?: selected?.game?.name
                    val card = name?.let(ShellTags::game)
                    if (!compactDetails && name != null) {
                        val index = results.rowByName.getValue(name)
                        val rowVisible = listState.layoutInfo.visibleItemsInfo.any { it.key == card }
                        val restoreRow = focusedGameName != null || (libraryActionFocused && !rail)
                        if (jumpToName == name) {
                            // Each group starts with its heading immediately before its first game.
                            listState.scrollToItem(index - 1)
                        } else if (!rowVisible && restoreRow) {
                            listState.scrollToItem(index)
                        }
                    }
                    jumpToName = null
                    listOfNotNull(
                        libraryFocus,
                        ShellTags.Play.takeIf { compactDetails },
                        card,
                        ShellTags.ClearSearch.takeIf { !rail && searchQuery.isNotEmpty() },
                        ShellTags.LibraryFilter,
                        ShellTags.EmptyImport,
                        ShellTags.DetailBack,
                    )
                }
                ShellSection.Import -> listOf(
                    savedFocus,
                    if (reports.isEmpty()) ShellTags.AddFolder else ShellTags.Import,
                    ShellTags.SettingsBack,
                )
                ShellSection.Sync -> listOf(savedFocus, ShellTags.SettingsBack, ShellTags.Sync)
                ShellSection.Settings -> listOf(savedFocus, ShellTags.OpenImport, ShellTags.OpenSync)
            }
            val targets = preferred.distinct()
            repeat(4) {
                if (targets.any(::request)) return@LaunchedEffect
                withFrameNanos { }
            }
            request(tabTag)
        }
        Box(Modifier.fillMaxSize().onKeyEvent { event ->
            if (event.type != KeyEventType.KeyUp ||
                (event.key != Key.ButtonB && !(pickerOpen && event.key == Key.Escape))
            ) return@onKeyEvent false
            back()
            true
        }) {
            ShellScaffold(
                section = tab,
                rail = rail,
                expanded = widthClass == ShellWidth.Expanded,
                requesters = requesters,
                onSection = ::openSection,
                onNavFocused = { navFocus = it },
                modifier = if (pickerOpen && !inlineLetters) {
                    Modifier.semantics { hideFromAccessibility() }
                } else Modifier,
            ) {
                when (section) {
                    ShellSection.Favorites -> FavoritesPage(
                        entries = favoriteEntries,
                        hasFavorites = favoriteNames.isNotEmpty(),
                        scanning = workMessage == R.string.folder_scanning && busy,
                        preparing = preparingGame,
                        launchError = launchError,
                        margin = margin,
                        gridState = favoritesGridState,
                        cardRequesters = cardRequesters,
                        libraryRequester = requesters.getValue(ShellTags.OpenLibrary),
                        onFocused = ::focusSaved,
                        onOpenLibrary = { openSection(ShellSection.Library) },
                        onChoose = { entry ->
                            val name = entry.game.name
                            favoritesFocus = ShellTags.favorite(name)
                            if (entry.game.canLaunch) {
                                onPlay(name)
                            } else {
                                if (name !in results.byName) {
                                    searchQuery = ""
                                    playableOnly = false
                                }
                                selectedName = name
                                detailsOpen = !rail
                                libraryFocus = ShellTags.Play
                                openSection(ShellSection.Library)
                            }
                        },
                    )
                    ShellSection.Library -> GameLibraryPage(
                        results = results,
                        selected = selected,
                        totalGames = entries.size,
                        playableOnly = playableOnly,
                        emptyMessage = if (playableOnly && matchingEntries.isNotEmpty()) {
                            R.string.library_no_playable
                        } else {
                            R.string.search_empty
                        },
                        listState = listState,
                        cardRequesters = cardRequesters,
                        wide = rail,
                        compactDetails = compactDetails,
                        margin = margin,
                        busy = playBusy,
                        preparing = preparingGame,
                        launchError = launchError,
                        favoriteNames = favoriteNames,
                        onToggleFavorite = onToggleFavorite,
                        requesters = requesters,
                        onFocused = ::focusSaved,
                        onSelected = { selectedName = it },
                        onDetails = {
                            if (pickerOpen) closePicker()
                            selectedName = it
                            detailsOpen = !rail
                            libraryFocus = ShellTags.Play
                            focusRequest++
                        },
                        onBack = ::back,
                        onPlay = {
                            if (pickerOpen) closePicker()
                            onPlay(it)
                        },
                        onOpenImport = { openSection(ShellSection.Import) },
                        onOpenLetters = { showPicker(LibraryPicker.Letters) },
                        onOpenFilter = { showPicker(LibraryPicker.Status) },
                        letterPicker = letterPicker.takeIf { inlineLetters },
                        search = {
                            LibrarySearch(
                                query = searchQuery,
                                onQuery = { searchQuery = it },
                                onSearchFocus = { searchFocused = it },
                                onExitSearch = ::back,
                                onClear = {
                                    searchQuery = ""
                                    returnToList(selected?.game?.name ?: selectedName)
                                },
                                requester = requesters.getValue(ShellTags.Search),
                                clearRequester = requesters.getValue(ShellTags.ClearSearch),
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
                        onBack = ::back,
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
                        onBack = ::back,
                    )
                    ShellSection.Settings -> SettingsPage(
                        margin = margin,
                        requesters = requesters,
                        onFocused = ::focusSaved,
                        onOpenImport = { openSection(ShellSection.Import) },
                        onOpenSync = { openSection(ShellSection.Sync) },
                    )
                }
            }
            if (pickerOpen && !inlineLetters) {
                if (picker == LibraryPicker.Status) {
                    LibraryStatusPicker(playableOnly, ::closePicker) {
                        playableOnly = it
                        closePicker()
                    }
                } else {
                    letterPicker?.invoke()
                }
            }
        }
    }
}

@Composable
private fun ImportPage(
    reports: List<ImportReport>,
    busy: Boolean,
    margin: Dp,
    requesters: Map<String, FocusRequester>,
    onFocused: (String) -> Unit,
    onBack: () -> Unit,
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
        SettingsBack(requesters, onFocused, onBack)
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
    onBack: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(margin),
        verticalArrangement = Arrangement.spacedBy(FbngDimens.space16),
    ) {
        SettingsBack(requesters, onFocused, onBack)
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
private fun SettingsBack(
    requesters: Map<String, FocusRequester>,
    onFocused: (String) -> Unit,
    onBack: () -> Unit,
) {
    FbngButton(
        onClick = onBack,
        modifier = Modifier.libraryFocus(ShellTags.SettingsBack, requesters.getValue(ShellTags.SettingsBack), onFocused),
        kind = FbngButtonKind.Neutral,
    ) {
        Text(stringResource(R.string.settings_back))
    }
}

@Composable
private fun SettingsPage(
    margin: Dp,
    requesters: Map<String, FocusRequester>,
    onFocused: (String) -> Unit,
    onOpenImport: () -> Unit,
    onOpenSync: () -> Unit,
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
        SettingsDestination(ShellSection.Import, R.string.shell_import, ShellTags.OpenImport, requesters, onFocused, onOpenImport)
        SettingsDestination(ShellSection.Sync, R.string.shell_sync, ShellTags.OpenSync, requesters, onFocused, onOpenSync)
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
private fun SettingsDestination(
    section: ShellSection,
    label: Int,
    tag: String,
    requesters: Map<String, FocusRequester>,
    onFocused: (String) -> Unit,
    onClick: () -> Unit,
) {
    FbngButton(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().libraryFocus(tag, requesters.getValue(tag), onFocused),
        kind = FbngButtonKind.Neutral,
    ) {
        ShellGlyph(section, MaterialTheme.colorScheme.onSurface, Modifier.size(FbngDimens.icon))
        Spacer(Modifier.size(FbngDimens.space8))
        Text(stringResource(label))
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
