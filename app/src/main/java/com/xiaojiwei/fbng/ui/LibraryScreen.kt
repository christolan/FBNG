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
    recentNames: List<String> = emptyList(),
    onToggleFavorite: (String) -> Unit = {},
    covers: CoverUiState? = null,
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
                recentNames = recentNames,
                onToggleFavorite = onToggleFavorite,
                covers = covers,
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
    recentNames: List<String>,
    onToggleFavorite: (String) -> Unit,
    covers: CoverUiState?,
) {
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    var sectionName by rememberSaveable { mutableStateOf(initialSection.name) }
    var savedFocus by rememberSaveable { mutableStateOf("") }
    var libraryFocus by rememberSaveable { mutableStateOf("") }
    var favoritesFocus by rememberSaveable { mutableStateOf("") }
    var recentFocus by rememberSaveable { mutableStateOf("") }
    var navFocus by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedName by rememberSaveable { mutableStateOf<String?>(null) }
    var detailsOpen by rememberSaveable { mutableStateOf(false) }
    var playableOnly by rememberSaveable { mutableStateOf(false) }
    var picker by rememberSaveable { mutableStateOf<LibraryPicker?>(null) }
    var pickerReturnFocus by rememberSaveable { mutableStateOf<String?>(null) }
    var focusRequest by remember { mutableStateOf(0) }
    var libraryPage by rememberSaveable { mutableStateOf(0) }
    var libraryPageSize by rememberSaveable { mutableStateOf(1) }
    val favoritesGridState = rememberLazyGridState()
    val recentGridState = rememberLazyGridState()
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
            ShellTags.PreviousPage,
            ShellTags.NextPage,
            ShellTags.Import,
            ShellTags.AddFolder,
            ShellTags.ScanFolders,
            ShellTags.Sync,
            ShellTags.OpenImport,
            ShellTags.OpenSync,
            ShellTags.SettingsBack,
            ShellTags.SettingsInfo,
            ShellTags.SettingsCore,
            ShellTags.FetchCovers,
            ShellTags.RefreshCovers,
            ShellTags.NavFavorites,
            ShellTags.NavRecent,
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
            ShellSection.Recent -> recentFocus = id
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
        val margin = FbngDimens.compactMargin
        val kovshTitle = stringResource(R.string.game_title)
        val gameTitles = remember(localizedTitles, kovshTitle) { localizedTitles + ("kovsh" to kovshTitle) }
        val entries = remember(snapshot.games, gameTitles) { indexLibrary(snapshot.games, gameTitles) }
        val favoriteEntries = remember(entries, favoriteNames) { entries.filter { it.game.name in favoriteNames } }
        val favoriteCards = remember(favoriteEntries) { favoriteEntries.map { ShellTags.favorite(it.game.name) } }
        val recentEntries = remember(entries, recentNames) {
            val byName = entries.associateBy { it.game.name }
            recentNames.mapNotNull(byName::get)
        }
        val recentCards = remember(recentEntries) { recentEntries.map { ShellTags.recent(it.game.name) } }
        val needle = searchQuery.trim()
        val matchingEntries = remember(entries, needle) {
            if (needle.isEmpty()) entries else entries.filter { it.matches(needle) }
        }
        val results = remember(matchingEntries, playableOnly) {
            LibraryResults(if (playableOnly) matchingEntries.filter { it.game.canLaunch } else matchingEntries)
        }
        val selected = results.byName[selectedName] ?: results.entries.firstOrNull()
        val showDetails = detailsOpen && selected != null
        val currentPage = libraryPage.coerceIn(0, ((results.entries.size - 1) / libraryPageSize).coerceAtLeast(0))
        val pageEntries = results.entries.drop(currentPage * libraryPageSize).take(libraryPageSize)
        val pickerOpen = section == ShellSection.Library && picker != null && entries.isNotEmpty()

        fun returnToList(name: String? = selected?.game?.name) {
            navFocus = null
            val index = results.entries.indexOfFirst { it.game.name == name }
            if (index >= 0) libraryPage = index / libraryPageSize
            libraryFocus = name?.let(ShellTags::game)
                ?: if (searchQuery.isNotEmpty()) ShellTags.ClearSearch else ShellTags.LibraryFilter
            focusRequest++
        }

        fun changePage(page: Int) {
            val lastPage = ((results.entries.size - 1) / libraryPageSize).coerceAtLeast(0)
            val next = page.coerceIn(0, lastPage)
            if (next == currentPage) return
            libraryPage = next
            val first = results.entries.getOrNull(next * libraryPageSize) ?: return
            selectedName = first.game.name
            libraryFocus = ShellTags.game(first.game.name)
            navFocus = null
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
            focusManager.clearFocus(force = true)
            searchFocused = false
            pickerReturnFocus = when (target) {
                LibraryPicker.Letters -> selected?.game?.name?.let(ShellTags::game) ?: ShellTags.JumpToLetter
                LibraryPicker.Status -> ShellTags.LibraryFilter
            }
            picker = target
        }

        val letterPicker: (@Composable () -> Unit)? = if (pickerOpen && picker == LibraryPicker.Letters) {
            {
                LibraryQueryDialog(
                    query = searchQuery,
                    resultCount = results.entries.size,
                    onQuery = { searchQuery = it; libraryPage = 0; selectedName = null },
                    onDismiss = {
                        closePicker()
                        returnToList(results.entries.firstOrNull()?.game?.name)
                    },
                )
            }
        } else null

        LaunchedEffect(picker, entries.isEmpty()) {
            if (picker != null && entries.isEmpty()) {
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
                section == ShellSection.Library && showDetails -> {
                    detailsOpen = false
                    returnToList(selected.game.name)
                }
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
            ShellSection.Recent -> ShellTags.NavRecent
            ShellSection.Library -> ShellTags.NavLibrary
            ShellSection.Settings, ShellSection.Import, ShellSection.Sync -> ShellTags.NavSettings
        }
        val libraryFocusBusy = section == ShellSection.Library && playBusy
        val importBusy = section == ShellSection.Import && busy
        val focusedGameName = ShellTags.gameName(libraryFocus)
        val savedRowExists = pageEntries.any { it.game.name == focusedGameName }
        LaunchedEffect(
            section, results.entries.isNotEmpty(), savedRowExists, libraryFocusBusy, importBusy, folderTags, rail,
            widthClass, showDetails, pickerOpen, focusRequest, currentPage, libraryPageSize,
            favoriteCards.takeIf { section == ShellSection.Favorites },
            recentCards.takeIf { section == ShellSection.Recent },
        ) {
            if (libraryFocusBusy || pickerOpen || searchFocused) return@LaunchedEffect
            if (navFocus?.let(::request) == true) return@LaunchedEffect
            val preferred = when (section) {
                ShellSection.Favorites, ShellSection.Recent -> {
                    val cards = if (section == ShellSection.Recent) recentCards else favoriteCards
                    val saved = if (section == ShellSection.Recent) recentFocus else favoritesFocus
                    val grid = if (section == ShellSection.Recent) recentGridState else favoritesGridState
                    val card = saved.takeIf { it in cards } ?: cards.firstOrNull()
                    if (card != null &&
                        grid.layoutInfo.visibleItemsInfo.none { it.key == card }
                    ) {
                        grid.scrollToItem(cards.indexOf(card))
                    }
                    listOfNotNull(
                        card,
                        ShellTags.OpenLibrary.takeIf { cards.isEmpty() },
                    )
                }
                ShellSection.Library -> {
                    val name = focusedGameName.takeIf { savedRowExists } ?: pageEntries.firstOrNull()?.game?.name
                    val card = name?.let(ShellTags::game)
                    listOfNotNull(
                        libraryFocus,
                        ShellTags.Play.takeIf { showDetails },
                        card,
                        ShellTags.ClearSearch.takeIf { searchQuery.isNotEmpty() },
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
            if (event.type == KeyEventType.KeyUp && section == ShellSection.Library &&
                !showDetails && !pickerOpen && !searchFocused
            ) {
                when (event.key) {
                    Key.ButtonL1 -> { changePage(currentPage - 1); return@onKeyEvent true }
                    Key.ButtonR1 -> { changePage(currentPage + 1); return@onKeyEvent true }
                }
            }
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
                modifier = if (pickerOpen) {
                    Modifier.semantics { hideFromAccessibility() }
                } else Modifier,
            ) {
                when (section) {
                    ShellSection.Favorites, ShellSection.Recent -> GameShelfPage(
                        entries = if (section == ShellSection.Recent) recentEntries else favoriteEntries,
                        hasSavedGames = if (section == ShellSection.Recent) recentNames.isNotEmpty() else favoriteNames.isNotEmpty(),
                        recent = section == ShellSection.Recent,
                        covers = covers,
                        scanning = workMessage == R.string.folder_scanning && busy,
                        preparing = preparingGame,
                        launchError = launchError,
                        margin = margin,
                        gridState = if (section == ShellSection.Recent) recentGridState else favoritesGridState,
                        cardRequesters = cardRequesters,
                        libraryRequester = requesters.getValue(ShellTags.OpenLibrary),
                        onFocused = ::focusSaved,
                        onOpenLibrary = { openSection(ShellSection.Library) },
                        onChoose = { entry ->
                            val name = entry.game.name
                            if (section == ShellSection.Recent) recentFocus = ShellTags.recent(name)
                            else favoritesFocus = ShellTags.favorite(name)
                            if (entry.game.canLaunch) {
                                onPlay(name)
                            } else {
                                if (name !in results.byName) {
                                    searchQuery = ""
                                    playableOnly = false
                                }
                                selectedName = name
                                detailsOpen = true
                                libraryFocus = ShellTags.Play
                                openSection(ShellSection.Library)
                            }
                        },
                    )
                    ShellSection.Library -> GameLibraryPage(
                        covers = covers,
                        results = results,
                        selected = selected,
                        totalGames = entries.size,
                        playableOnly = playableOnly,
                        emptyMessage = if (playableOnly && matchingEntries.isNotEmpty()) {
                            R.string.library_no_playable
                        } else {
                            R.string.search_empty
                        },
                        pageIndex = currentPage,
                        pageSize = libraryPageSize,
                        onPage = ::changePage,
                        onPageSize = { size ->
                            if (size != libraryPageSize) {
                                val anchor = ShellTags.gameName(libraryFocus) ?: selectedName
                                val index = results.entries.indexOfFirst { it.game.name == anchor }
                                    .takeIf { it >= 0 } ?: currentPage * libraryPageSize
                                libraryPageSize = size
                                libraryPage = index / size
                            }
                            focusRequest++
                        },
                        cardRequesters = cardRequesters,
                        wide = rail,
                        showDetails = showDetails,
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
                            navFocus = null
                            selectedName = it
                            detailsOpen = true
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
                        search = {
                            LibrarySearch(
                                query = searchQuery,
                                onQuery = { searchQuery = it; libraryPage = 0; selectedName = null },
                                onSearchFocus = { searchFocused = it },
                                onExitSearch = ::back,
                                onClear = {
                                    focusManager.clearFocus(force = true)
                                    searchFocused = false
                                    navFocus = null
                                    searchQuery = ""
                                    libraryPage = 0
                                    selectedName = null
                                    libraryFocus = ""
                                    focusRequest++
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
                        covers = covers,
                        margin = margin,
                        requesters = requesters,
                        onFocused = ::focusSaved,
                        onOpenImport = { openSection(ShellSection.Import) },
                        onOpenSync = { openSection(ShellSection.Sync) },
                    )
                }
            }
            if (pickerOpen) {
                if (picker == LibraryPicker.Status) {
                    LibraryStatusPicker(playableOnly, ::closePicker) {
                        playableOnly = it
                        libraryPage = 0
                        selectedName = null
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
    covers: CoverUiState?,
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
        if (covers != null) CoverSettings(covers, requesters, onFocused)
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
        .collectAsStateWithLifecycle<WindowLayoutInfo?>(initialValue = null)
    val feature = layoutInfo?.displayFeatures?.filterIsInstance<FoldingFeature>()?.firstOrNull() ?: return null
    val vertical = feature.orientation == FoldingFeature.Orientation.VERTICAL
    return FoldSnapshot(
        orientationVertical = vertical,
        separating = feature.isSeparating,
        hingeStartPx = if (vertical) feature.bounds.left else feature.bounds.top,
        hingeEndPx = if (vertical) feature.bounds.right else feature.bounds.bottom,
    )
}
