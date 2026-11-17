package com.xiaojiwei.fbng.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.hideFromAccessibility
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.window.layout.FoldingFeature
import androidx.window.layout.WindowInfoTracker
import androidx.window.layout.WindowLayoutInfo
import com.xiaojiwei.fbng.BuildConfig
import com.xiaojiwei.fbng.R
import com.xiaojiwei.fbng.library.FolderScan
import com.xiaojiwei.fbng.library.LibraryEntry
import com.xiaojiwei.fbng.library.LibraryResults
import com.xiaojiwei.fbng.library.LibrarySnapshot
import com.xiaojiwei.fbng.library.indexLibrary
import com.xiaojiwei.fbng.ui.theme.CornerRadiusOption
import com.xiaojiwei.fbng.ui.theme.FbngButton
import com.xiaojiwei.fbng.ui.theme.FbngButtonKind
import com.xiaojiwei.fbng.ui.theme.FbngDimens
import com.xiaojiwei.fbng.ui.theme.FbngPanel
import com.xiaojiwei.fbng.ui.theme.fbngFocusTarget
import com.xiaojiwei.fbng.ui.theme.fbngIsland
import com.xiaojiwei.fbng.update.UpdateState

private enum class LibraryPicker { Letters, Status }

@Composable
internal fun LibraryContent(
    load: CatalogLoad,
    snapshot: LibrarySnapshot?,
    busy: Boolean,
    readFold: @Composable () -> FoldSnapshot?,
    onPlay: (LibraryEntry, Int?) -> Unit,
    initialSection: ShellSection = ShellSection.Library,
    workMessage: Int = R.string.folder_scanning,
    folders: List<FolderScan> = emptyList(),
    folderError: Int? = null,
    launchError: String? = null,
    folderActions: FolderActions = FolderActions(),
    playBusy: Boolean = busy,
    favoriteNames: Set<String> = emptySet(),
    recentNames: List<String> = emptyList(),
    onToggleFavorite: (String) -> Unit = {},
    covers: CoverUiState? = null,
    appUpdate: UpdateUiState? = null,
    cornerRadius: CornerRadiusOption = CornerRadiusOption.Default,
    onCornerRadiusChange: (CornerRadiusOption) -> Unit = {},
    returnFromGame: String? = null,
    onReturnRestored: () -> Unit = {},
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
                busy = busy,
                playBusy = playBusy,
                readFold = readFold,
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
                appUpdate = appUpdate,
                cornerRadius = cornerRadius,
                onCornerRadiusChange = onCornerRadiusChange,
                returnFromGame = returnFromGame,
                onReturnRestored = onReturnRestored,
            )
        }
    }
}

@Composable
private fun LibraryShell(
    snapshot: LibrarySnapshot,
    localizedTitles: Map<String, String>,
    busy: Boolean,
    playBusy: Boolean,
    readFold: @Composable () -> FoldSnapshot?,
    onPlay: (LibraryEntry, Int?) -> Unit,
    initialSection: ShellSection,
    workMessage: Int,
    folders: List<FolderScan>,
    folderError: Int?,
    launchError: String?,
    folderActions: FolderActions,
    favoriteNames: Set<String>,
    recentNames: List<String>,
    onToggleFavorite: (String) -> Unit,
    covers: CoverUiState?,
    appUpdate: UpdateUiState?,
    cornerRadius: CornerRadiusOption,
    onCornerRadiusChange: (CornerRadiusOption) -> Unit,
    returnFromGame: String?,
    onReturnRestored: () -> Unit,
) {
    val focusManager = LocalFocusManager.current
    val windowFocused = LocalWindowInfo.current.isWindowFocused
    val gamepad = LocalGamepadInput.current
    var sectionName by rememberSaveable { mutableStateOf(initialSection.name) }
    var savedFocus by rememberSaveable { mutableStateOf("") }
    var libraryFocus by rememberSaveable { mutableStateOf("") }
    var favoritesFocus by rememberSaveable { mutableStateOf("") }
    var recentFocus by rememberSaveable { mutableStateOf("") }
    var favoritesIndex by rememberSaveable { mutableStateOf(0) }
    var recentIndex by rememberSaveable { mutableStateOf(0) }
    var navFocus by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedName by rememberSaveable { mutableStateOf<String?>(null) }
    var detailName by rememberSaveable { mutableStateOf<String?>(null) }
    var importOrigin by rememberSaveable { mutableStateOf<ShellSection?>(null) }
    var playableOnly by rememberSaveable { mutableStateOf(false) }
    var picker by rememberSaveable { mutableStateOf<LibraryPicker?>(null) }
    var focusRequest by remember { mutableStateOf(0) }
    var restoreTarget by remember { mutableStateOf<String?>(null) }
    var libraryPage by rememberSaveable { mutableStateOf(0) }
    var libraryRows by rememberSaveable { mutableStateOf(MaxLibraryRows) }
    val libraryPageSize = LibraryColumns * libraryRows
    val cardRequesters = remember { mutableMapOf<String, FocusRequester>() }
    var searchQuery by rememberSaveable { mutableStateOf("") }
    val section = ShellSection.valueOf(sectionName)
    val shellRequesters = remember {
        listOf(
            ShellTags.OpenLibrary,
            ShellTags.EmptyImport,
            ShellTags.JumpToLetter,
            ShellTags.EditSearch,
            ShellTags.ClearFilters,
            ShellTags.LibraryFilter,
            ShellTags.PreviousPage,
            ShellTags.NextPage,
            ShellTags.AddFolder,
            ShellTags.ScanFolders,
            ShellTags.Sync,
            ShellTags.OpenImport,
            ShellTags.OpenSync,
            ShellTags.SettingsBack,
            ShellTags.SettingsInfo,
            ShellTags.SettingsCore,
            ShellTags.AppUpdate,
            ShellTags.FetchCovers,
            ShellTags.RefreshCovers,
            ShellTags.NavFavorites,
            ShellTags.NavRecent,
            ShellTags.NavLibrary,
            ShellTags.NavSettings,
        ).plus(CornerRadiusOption.entries.map(ShellTags::cornerRadius))
            .associateWith { FocusRequester() }
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

    fun restoreFocus(id: String) {
        restoreTarget = id
        navFocus = null
        focusRequest++
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val widthDp = maxWidth.value.toInt()
        val widthClass = shellWidth(widthDp)
        val fold = readFold()
        val rail = shellUsesNavigationRail(widthDp, fold)
        val compactBottomBar = !rail && maxHeight < FbngDimens.shortShellHeight
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
        val showShelf = section == ShellSection.Favorites || section == ShellSection.Recent
        val recent = section == ShellSection.Recent
        val shelfEntries = if (recent) recentEntries else favoriteEntries
        val shelfCards = if (recent) recentCards else favoriteCards
        val savedShelfFocus = if (recent) recentFocus else favoritesFocus
        val savedShelfIndex = if (recent) recentIndex else favoritesIndex
        val shelfIndex = shelfCards.indexOf(savedShelfFocus).takeIf { it >= 0 }
            ?: savedShelfIndex.coerceAtMost(shelfCards.lastIndex)
        val shelfPage = shelfIndex.coerceAtLeast(0) / ShelfPageSize
        val shelfPages = (shelfCards.size + ShelfPageSize - 1) / ShelfPageSize
        val needle = searchQuery.trim()
        val matchingEntries = remember(entries, needle) {
            if (needle.isEmpty()) entries else entries.filter { it.matches(needle) }
        }
        val results = remember(matchingEntries, playableOnly) {
            LibraryResults(if (playableOnly) matchingEntries.filter { it.game.canLaunch } else matchingEntries)
        }
        val byName = remember(entries) { entries.associateBy { it.game.name } }
        val detail = byName[detailName].takeIf { showShelf || section == ShellSection.Library }
        val showDetails = detail != null
        val pickerOpen = section == ShellSection.Library && picker != null
        val focusedGameName = ShellTags.gameName(libraryFocus)
        val savedGameIndex = remember(results, focusedGameName) {
            if (focusedGameName == null) -1 else results.entries.indexOfFirst { it.game.name == focusedGameName }
        }
        val selectedGameIndex = remember(results, selectedName) {
            if (selectedName == null) -1 else results.entries.indexOfFirst { it.game.name == selectedName }
        }
        val useGamePage = section == ShellSection.Library && !pickerOpen && !showDetails && savedGameIndex >= 0
        val pageCount = (results.entries.size + libraryPageSize - 1) / libraryPageSize
        val lastPage = (pageCount - 1).coerceAtLeast(0)
        val currentPage = if (useGamePage) savedGameIndex / libraryPageSize else libraryPage.coerceIn(0, lastPage)
        val pageEntries = results.entries.drop(currentPage * libraryPageSize).take(libraryPageSize)
        LaunchedEffect(useGamePage, currentPage) {
            if (useGamePage) libraryPage = currentPage
        }
        LaunchedEffect(gamepad, section, showDetails, pickerOpen) {
            gamepad?.shellSurfaceChanged()
        }

        fun returnToCards(name: String?) {
            detailName = null
            if (showShelf) {
                val target = shelfCards.getOrNull(shelfIndex) ?: ShellTags.OpenLibrary
                focusSaved(target)
                restoreFocus(target)
            } else {
                val index = results.entries.indexOfFirst { it.game.name == name }
                if (index >= 0) libraryPage = index / libraryPageSize
                libraryFocus = name?.let(ShellTags::game) ?: ShellTags.JumpToLetter
                restoreFocus(libraryFocus)
            }
        }

        LaunchedEffect(returnFromGame) {
            if (returnFromGame == null) return@LaunchedEffect
            returnToCards(returnFromGame)
            onReturnRestored()
        }

        LaunchedEffect(detailName, detail, section) {
            if (detailName != null && detail == null && (showShelf || section == ShellSection.Library)) {
                returnToCards(detailName)
            }
        }

        fun changePage(page: Int) {
            val next = page.coerceIn(0, lastPage)
            if (next == currentPage) return
            val selectedIndex = pageEntries.indexOfFirst { it.game.name == selectedName }
            val nextPageStart = next * libraryPageSize
            val targetIndex = selectedIndex.takeIf {
                it >= 0 && nextPageStart + it < results.entries.size
            } ?: 0
            val name = results.entries[nextPageStart + targetIndex].game.name
            libraryPage = next
            selectedName = name
            libraryFocus = ShellTags.game(name)
            restoreFocus(libraryFocus)
        }

        fun changeShelfPage(page: Int) {
            if (page !in 0 until shelfPages || page == shelfPage) return
            val first = page * ShelfPageSize
            val target = shelfCards.getOrNull(first + shelfIndex % ShelfPageSize) ?: shelfCards[first]
            focusSaved(target)
            restoreFocus(target)
        }

        fun closePicker(showResults: Boolean = false) {
            libraryFocus = when (picker) {
                LibraryPicker.Letters -> if (showResults) {
                    // Let the focus effect choose from the newly confirmed results.
                    ""
                } else ShellTags.JumpToLetter
                LibraryPicker.Status, null -> ShellTags.LibraryFilter
            }
            picker = null
            if (showResults) {
                restoreTarget = null
                navFocus = null
                focusRequest++
            } else restoreFocus(libraryFocus)
        }

        fun showPicker(target: LibraryPicker) {
            if (pickerOpen) {
                if (picker == target) closePicker()
                return
            }
            focusManager.clearFocus(force = true)
            picker = target
        }

        val letterPicker: (@Composable () -> Unit)? = if (pickerOpen && picker == LibraryPicker.Letters) {
            {
                LibraryQueryDialog(
                    query = searchQuery,
                    onConfirm = {
                        searchQuery = it
                        libraryPage = 0
                        selectedName = null
                        closePicker(showResults = true)
                    },
                    onDismiss = { closePicker() },
                )
            }
        } else null

        fun openSection(destination: ShellSection) {
            if (destination == section) return
            if (pickerOpen) closePicker()
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
                section == ShellSection.Import && importOrigin != null -> {
                    val origin = requireNotNull(importOrigin)
                    importOrigin = null
                    openSection(origin)
                }
                section == ShellSection.Import || section == ShellSection.Sync -> openSection(ShellSection.Settings)
                section != ShellSection.Favorites -> openSection(ShellSection.Favorites)
            }
        }

        if (detail != null) {
            key(detail.game.name) {
                GameLaunchPage(
                    entry = detail,
                    cover = covers?.images?.get(detail.game.name),
                    fold = fold,
                    favorite = detail.game.name in favoriteNames,
                    busy = playBusy || returnFromGame != null,
                    launchError = launchError == detail.game.name,
                    onPlay = { onPlay(detail, it) },
                    onToggleFavorite = { onToggleFavorite(detail.game.name) },
                    onConfigureFolders = {
                        importOrigin = section
                        openSection(ShellSection.Import)
                    },
                    onBack = { returnToCards(detail.game.name) },
                )
            }
            return@BoxWithConstraints
        }

        BackHandler(enabled = section != ShellSection.Favorites, onBack = ::back)
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
        val savedRowExists = pageEntries.any { it.game.name == focusedGameName }
        LaunchedEffect(
            section, results.entries.isNotEmpty(), savedRowExists, libraryFocusBusy, importBusy, folderTags, rail, fold,
            widthClass, pickerOpen, focusRequest, currentPage, libraryPageSize, returnFromGame,
            windowFocused,
            shelfCards.takeIf { showShelf }, shelfPage.takeIf { showShelf },
            maxWidth.takeIf { showShelf }, maxHeight.takeIf { showShelf },
        ) {
            if (libraryFocusBusy || pickerOpen || returnFromGame != null) return@LaunchedEffect
            val target = restoreTarget
            if (target != null) {
                if (!windowFocused) return@LaunchedEffect
                repeat(4) {
                    withFrameNanos { }
                    if (request(target)) {
                        restoreTarget = null
                        return@LaunchedEffect
                    }
                }
                restoreTarget = null
            } else if (navFocus?.let(::request) == true) return@LaunchedEffect
            val preferred = when (section) {
                ShellSection.Favorites, ShellSection.Recent -> listOf(
                    shelfCards.getOrNull(shelfIndex) ?: ShellTags.OpenLibrary,
                )
                ShellSection.Library -> {
                    val name = focusedGameName.takeIf { savedRowExists } ?: pageEntries.firstOrNull()?.game?.name
                    val card = name?.let(ShellTags::game)
                    listOfNotNull(
                        libraryFocus,
                        card,
                        ShellTags.EmptyImport,
                        ShellTags.EditSearch,
                        ShellTags.JumpToLetter,
                        ShellTags.LibraryFilter,
                    )
                }
                ShellSection.Import -> listOf(
                    savedFocus,
                    ShellTags.AddFolder,
                    ShellTags.SettingsBack,
                )
                ShellSection.Sync -> listOf(savedFocus, ShellTags.SettingsBack, ShellTags.Sync)
                ShellSection.Settings -> listOfNotNull(
                    savedFocus,
                    ShellTags.AppUpdate.takeIf {
                        appUpdate?.state is UpdateState.Available
                    },
                    ShellTags.OpenImport,
                    ShellTags.OpenSync,
                )
            }
            val targets = preferred.distinct()
            repeat(4) {
                if (targets.any(::request)) return@LaunchedEffect
                withFrameNanos { }
            }
            request(tabTag)
        }
        Box(Modifier.fillMaxSize().onKeyEvent { event ->
            if (event.type == KeyEventType.KeyUp) {
                val onLibraryList = section == ShellSection.Library && !pickerOpen
                val pageStep = when (event.key) {
                    Key.ButtonL1 -> -1
                    Key.ButtonR1 -> 1
                    else -> 0
                }
                if (pageStep != 0 && (showShelf || onLibraryList)) {
                    if (showShelf) changeShelfPage(shelfPage + pageStep)
                    else changePage(currentPage + pageStep)
                    return@onKeyEvent true
                }
                if (onLibraryList && event.key == Key.ButtonY) {
                    showPicker(LibraryPicker.Letters)
                    return@onKeyEvent true
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
                compactBottomBar = compactBottomBar,
                requesters = requesters,
                onSection = ::openSection,
                onNavFocused = { navFocus = it },
                updateAvailable = appUpdate?.state is UpdateState.Available,
                modifier = if (pickerOpen) {
                    Modifier.semantics { hideFromAccessibility() }
                } else Modifier,
            ) {
                when (section) {
                    ShellSection.Favorites, ShellSection.Recent -> GameShelfPage(
                        entries = shelfEntries.drop(shelfPage * ShelfPageSize).take(ShelfPageSize),
                        hasSavedGames = if (recent) recentNames.isNotEmpty() else favoriteNames.isNotEmpty(),
                        recent = recent,
                        covers = covers,
                        scanning = workMessage == R.string.folder_scanning && busy,
                        margin = margin,
                        pageIndex = shelfPage,
                        pages = shelfPages,
                        onPage = ::changeShelfPage,
                        cardRequesters = cardRequesters,
                        requesters = requesters,
                        onPageControlFocused = { navFocus = null },
                        onFocused = { id ->
                            focusSaved(id)
                            shelfCards.indexOf(id).takeIf { it >= 0 }?.let { index ->
                                if (recent) recentIndex = index else favoritesIndex = index
                            }
                        },
                        onOpenLibrary = { openSection(ShellSection.Library) },
                        onChoose = { entry ->
                            val name = entry.game.name
                            val tag = if (recent) ShellTags.recent(name) else ShellTags.favorite(name)
                            focusSaved(tag)
                            val index = shelfCards.indexOf(tag)
                            if (recent) recentIndex = index else favoritesIndex = index
                            detailName = name
                        },
                    )
                    ShellSection.Library -> GameLibraryPage(
                        games = pageEntries,
                        totalGames = entries.size,
                        playableOnly = playableOnly,
                        emptyMessage = if (playableOnly && matchingEntries.isNotEmpty()) {
                            R.string.library_no_playable
                        } else {
                            R.string.search_empty
                        },
                        pageIndex = currentPage,
                        pages = pageCount,
                        rows = libraryRows,
                        onRows = { rows ->
                            val anchor = selectedGameIndex.takeIf { it >= 0 } ?: currentPage * libraryPageSize
                            libraryPage = anchor / (LibraryColumns * rows)
                            libraryRows = rows
                        },
                        onPage = ::changePage,
                        cardRequesters = cardRequesters,
                        wide = rail,
                        compactBottomBar = compactBottomBar,
                        margin = margin,
                        busy = playBusy,
                        requesters = requesters,
                        onFocused = ::focusSaved,
                        onSelected = { selectedName = it },
                        onDetails = { name ->
                            if (pickerOpen) closePicker()
                            navFocus = null
                            selectedName = name
                            libraryFocus = ShellTags.game(name)
                            detailName = name
                        },
                        onOpenImport = {
                            importOrigin = section
                            openSection(ShellSection.Import)
                        },
                        onOpenLetters = { showPicker(LibraryPicker.Letters) },
                        onOpenFilter = { showPicker(LibraryPicker.Status) },
                        onClearFilters = {
                            searchQuery = ""
                            playableOnly = false
                            libraryPage = 0
                            selectedName = null
                            libraryFocus = ""
                            navFocus = null
                            focusRequest++
                        },
                        query = searchQuery,
                    )
                    ShellSection.Import -> ImportPage(
                        busy = busy,
                        margin = margin,
                        requesters = requesters,
                        onFocused = ::focusSaved,
                        onBack = ::back,
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
                        appUpdate = appUpdate,
                        cornerRadius = cornerRadius,
                        onCornerRadiusChange = onCornerRadiusChange,
                        margin = margin,
                        requesters = requesters,
                        onFocused = ::focusSaved,
                        onOpenImport = {
                            importOrigin = null
                            openSection(ShellSection.Import)
                        },
                        onOpenSync = { openSection(ShellSection.Sync) },
                    )
                }
            }
            if (pickerOpen) {
                if (picker == LibraryPicker.Status) {
                    LibraryStatusPicker(playableOnly, { closePicker() }) {
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
    busy: Boolean,
    margin: Dp,
    requesters: Map<String, FocusRequester>,
    onFocused: (String) -> Unit,
    onBack: () -> Unit,
    workMessage: Int,
    folders: List<FolderScan>,
    folderError: Int?,
    folderActions: FolderActions,
) {
    SettingsSubpage(R.string.shell_import, margin, requesters, onFocused, onBack) {
        FolderManagement(
            folders = folders,
            busy = busy,
            workMessage = workMessage,
            error = folderError,
            requesters = requesters,
            onFocused = onFocused,
            actions = folderActions,
        )
    }
}

@Composable
private fun SyncPage(
    margin: Dp,
    requesters: Map<String, FocusRequester>,
    onFocused: (String) -> Unit,
    onBack: () -> Unit,
) {
    SettingsSubpage(R.string.shell_sync, margin, requesters, onFocused, onBack) {
        FocusableMessage(
            tag = ShellTags.Sync,
            requester = requesters.getValue(ShellTags.Sync),
            onFocused = onFocused,
            container = MaterialTheme.colorScheme.surfaceContainerHigh,
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
private fun SettingsSubpage(
    title: Int,
    margin: Dp,
    requesters: Map<String, FocusRequester>,
    onFocused: (String) -> Unit,
    onBack: () -> Unit,
    content: @Composable () -> Unit,
) {
    ShellPage(
        title = stringResource(
            R.string.shell_page_path, stringResource(R.string.shell_settings), stringResource(title),
        ),
        margin = margin,
        leadingAction = {
            ShellHeaderBack(
                label = stringResource(R.string.settings_back),
                onClick = onBack,
                modifier = Modifier.libraryFocus(
                    ShellTags.SettingsBack, requesters.getValue(ShellTags.SettingsBack), onFocused,
                ),
            )
        },
    ) {
        Column(Modifier.verticalScroll(rememberScrollState()), content = { content() })
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
    appUpdate: UpdateUiState?,
    cornerRadius: CornerRadiusOption,
    onCornerRadiusChange: (CornerRadiusOption) -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    ShellPage(title = stringResource(R.string.shell_settings), margin = margin) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(FbngDimens.space16),
        ) {
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                if (maxWidth >= FbngDimens.libraryHeaderWide) {
                    Row(horizontalArrangement = Arrangement.spacedBy(FbngDimens.space12)) {
                        SettingsDestination(ShellSection.Import, R.string.shell_import, ShellTags.OpenImport,
                            requesters, onFocused, onOpenImport, Modifier.weight(1f))
                        SettingsDestination(ShellSection.Sync, R.string.shell_sync, ShellTags.OpenSync,
                            requesters, onFocused, onOpenSync, Modifier.weight(1f))
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(FbngDimens.space12)) {
                        SettingsDestination(ShellSection.Import, R.string.shell_import, ShellTags.OpenImport,
                            requesters, onFocused, onOpenImport)
                        SettingsDestination(ShellSection.Sync, R.string.shell_sync, ShellTags.OpenSync,
                            requesters, onFocused, onOpenSync)
                    }
                }
            }
            CornerRadiusButtonGroup(cornerRadius, requesters, onFocused, onCornerRadiusChange)
            if (appUpdate != null) UpdateSettings(appUpdate, requesters.getValue(ShellTags.AppUpdate), onFocused)
            if (covers != null) CoverSettings(covers, requesters, onFocused)
            FocusableMessage(
                tag = ShellTags.SettingsInfo,
                requester = requesters.getValue(ShellTags.SettingsInfo),
                onFocused = onFocused,
                container = scheme.surfaceContainerHigh,
            ) {
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
                container = scheme.surfaceContainerHigh,
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
}

@Composable
private fun CornerRadiusButtonGroup(
    selected: CornerRadiusOption,
    requesters: Map<String, FocusRequester>,
    onFocused: (String) -> Unit,
    onSelect: (CornerRadiusOption) -> Unit,
) {
    FbngPanel {
        Text(stringResource(R.string.corner_radius_title), style = MaterialTheme.typography.titleMedium)
        Row(
            modifier = Modifier.fillMaxWidth().selectableGroup(),
            horizontalArrangement = Arrangement.spacedBy(FbngDimens.space4),
        ) {
            CornerRadiusOption.entries.forEach { option ->
                val label = when (option) {
                    CornerRadiusOption.Smaller -> R.string.corner_radius_smaller
                    CornerRadiusOption.Default -> R.string.corner_radius_default
                    CornerRadiusOption.Larger -> R.string.corner_radius_larger
                }
                val tag = ShellTags.cornerRadius(option)
                val checked = option == selected
                FbngButton(
                    onClick = { if (!checked) onSelect(option) },
                    modifier = Modifier.weight(1f)
                        .libraryFocus(tag, requesters.getValue(tag), onFocused)
                        .semantics { this.selected = checked },
                    kind = if (checked) FbngButtonKind.Filled else FbngButtonKind.Neutral,
                    contentPadding = PaddingValues(FbngDimens.space8),
                    role = Role.RadioButton,
                ) {
                    Text(stringResource(label), style = MaterialTheme.typography.labelLarge)
                }
            }
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
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier.fillMaxWidth().libraryFocus(tag, requesters.getValue(tag), onFocused)
            .fbngFocusTarget(
                onClick = onClick,
                container = scheme.surfaceContainerHigh,
            ).padding(FbngDimens.space16),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(FbngDimens.space12),
    ) {
        ShellGlyph(section, scheme.onSurface, Modifier.size(FbngDimens.icon))
        Text(stringResource(label), style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.weight(1f))
        Text("›", style = MaterialTheme.typography.titleLarge, color = scheme.onSurfaceVariant)
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
            .focusable()
            .fbngIsland().padding(FbngDimens.space24),
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
            .padding(FbngDimens.space24)
            .fbngIsland().padding(FbngDimens.space24),
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
