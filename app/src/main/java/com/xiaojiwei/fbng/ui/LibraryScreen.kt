package com.xiaojiwei.fbng.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.stringResource
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
import com.xiaojiwei.fbng.game.GameProgressStore
import com.xiaojiwei.fbng.library.AppStorage
import com.xiaojiwei.fbng.library.FolderScan
import com.xiaojiwei.fbng.library.LibraryEntry
import com.xiaojiwei.fbng.library.LibraryResults
import com.xiaojiwei.fbng.library.LibrarySnapshot
import com.xiaojiwei.fbng.library.indexLibrary
import com.xiaojiwei.fbng.ui.theme.FbngDimens
import com.xiaojiwei.fbng.ui.theme.FbngShapes
import com.xiaojiwei.fbng.ui.theme.fbngFocusTarget
import com.xiaojiwei.fbng.ui.theme.fbngIsland
import com.xiaojiwei.fbng.update.UpdateState
import java.text.DateFormat
import java.util.Date
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private enum class LibraryPicker { Letters, Status }

private enum class SettingsCategory(val label: Int, val tag: String) {
    Import(R.string.shell_import, ShellTags.OpenImport),
    Covers(R.string.covers_title, "settings-covers"),
    Sync(R.string.shell_sync, ShellTags.OpenSync),
    About(R.string.settings_about, "settings-about"),
}


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
    recentTimes: Map<String, Long> = emptyMap(),
    onToggleFavorite: (String) -> Unit = {},
    covers: CoverUiState? = null,
    appUpdate: UpdateUiState? = null,
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
                recentTimes = recentTimes,
                onToggleFavorite = onToggleFavorite,
                covers = covers,
                appUpdate = appUpdate,
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
    recentTimes: Map<String, Long>,
    onToggleFavorite: (String) -> Unit,
    covers: CoverUiState?,
    appUpdate: UpdateUiState?,
    returnFromGame: String?,
    onReturnRestored: () -> Unit,
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val windowFocused = LocalWindowInfo.current.isWindowFocused
    val gamepad = LocalGamepadInput.current
    var sectionName by rememberSaveable { mutableStateOf(initialSection.name) }
    var settingsOrigin by rememberSaveable { mutableStateOf(ShellSection.Favorites) }
    var settingsCategory by rememberSaveable { mutableStateOf(SettingsCategory.Import) }
    var savedFocus by rememberSaveable { mutableStateOf("") }
    var libraryFocus by rememberSaveable { mutableStateOf("") }
    var favoritesFocus by rememberSaveable { mutableStateOf("") }
    var recentFocus by rememberSaveable { mutableStateOf("") }
    var favoritesIndex by rememberSaveable { mutableStateOf(0) }
    var recentIndex by rememberSaveable { mutableStateOf(0) }
    var pickerReturnFocus by rememberSaveable { mutableStateOf("") }
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
            ShellTags.ShowAllStatuses,
            ShellTags.LibraryFilter,
            ShellTags.PreviousPage,
            ShellTags.NextPage,
            ShellTags.AddFolder,
            ShellTags.ScanFolders,
            ShellTags.OpenImport,
            ShellTags.OpenSync,
            ShellTags.SettingsInfo,
            ShellTags.SettingsCore,
            ShellTags.AppUpdate,
            ShellTags.FetchCovers,
            ShellTags.RefreshCovers,
            ShellTags.ClearCovers,
            ShellTags.NavFavorites,
            ShellTags.NavRecent,
            ShellTags.NavLibrary,
            ShellTags.NavSettings,
        ).plus(SettingsCategory.entries.map { it.tag })
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
        val shelfPageSize = if (recent) RecentPageSize else FavoritesPageSize
        val shelfPage = shelfIndex.coerceAtLeast(0) / shelfPageSize
        val shelfPages = (shelfCards.size + shelfPageSize - 1) / shelfPageSize
        val needle = searchQuery.trim()
        val matchingEntries = remember(entries, needle) {
            if (needle.isEmpty()) entries else entries.filter { it.matches(needle) }
        }
        val results = remember(matchingEntries, playableOnly) {
            LibraryResults(if (playableOnly) matchingEntries.filter { it.game.canLaunch } else matchingEntries)
        }
        val noPlayableResults = playableOnly && matchingEntries.isNotEmpty() && results.entries.isEmpty()
        val emptySearchAction = if (noPlayableResults) ShellTags.ShowAllStatuses else ShellTags.EditSearch
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
            val lastIndex = minOf(libraryPageSize, results.entries.size - nextPageStart) - 1
            val row = minOf(selectedIndex.coerceAtLeast(0) / LibraryColumns, lastIndex / LibraryColumns)
            val column = if (next > currentPage) 0 else LibraryColumns - 1
            val targetIndex = minOf(row * LibraryColumns + column, lastIndex)
            val name = results.entries[nextPageStart + targetIndex].game.name
            libraryPage = next
            selectedName = name
            libraryFocus = ShellTags.game(name)
            restoreFocus(libraryFocus)
        }

        fun changeShelfPage(page: Int) {
            if (page !in 0 until shelfPages || page == shelfPage) return
            val first = page * shelfPageSize
            val offset = when {
                recent -> shelfIndex % shelfPageSize
                page < shelfPage -> shelfPageSize - 1
                else -> 0
            }
            val target = shelfCards[minOf(first + offset, shelfCards.lastIndex)]
            focusSaved(target)
            restoreFocus(target)
        }

        fun closePicker(showResults: Boolean = false) {
            libraryFocus = if (showResults) "" else pickerReturnFocus
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
            pickerReturnFocus = selectedName?.let(ShellTags::game)
                ?: libraryFocus.ifEmpty { if (target == LibraryPicker.Letters) ShellTags.JumpToLetter else ShellTags.LibraryFilter }
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
            if (destination == ShellSection.Settings && (showShelf || section == ShellSection.Library)) settingsOrigin = section
            navFocus = null
            savedFocus = when {
                destination == ShellSection.Settings && section == ShellSection.Import -> ShellTags.OpenImport
                destination == ShellSection.Settings && section == ShellSection.Sync -> ShellTags.OpenSync
                else -> ""
            }
            sectionName = destination.name
            val browseTarget = when (destination) {
                ShellSection.Favorites -> {
                    favoritesFocus.takeIf { it in favoriteCards }
                        ?: favoriteCards.getOrNull(favoritesIndex.coerceAtMost(favoriteCards.lastIndex))
                        ?: ShellTags.OpenLibrary
                }
                ShellSection.Recent -> {
                    recentFocus.takeIf { it in recentCards }
                        ?: recentCards.getOrNull(recentIndex.coerceAtMost(recentCards.lastIndex))
                        ?: ShellTags.OpenLibrary
                }
                ShellSection.Library -> {
                    libraryFocus.takeIf { savedGameIndex >= 0 }
                        ?: pageEntries.firstOrNull()?.let { ShellTags.game(it.game.name) }
                        ?: if (entries.isEmpty()) ShellTags.EmptyImport else emptySearchAction
                }
                else -> null
            }
            if (browseTarget != null) restoreFocus(browseTarget)
        }

        fun back() {
            when {
                pickerOpen -> closePicker()
                section == ShellSection.Import && navFocus == null && savedFocus.isNotEmpty() && savedFocus != SettingsCategory.Import.tag -> {
                    savedFocus = SettingsCategory.Import.tag
                    restoreFocus(SettingsCategory.Import.tag)
                }
                section == ShellSection.Import && importOrigin != null -> {
                    val origin = requireNotNull(importOrigin)
                    importOrigin = null
                    openSection(origin)
                    if (detailName == null) restoreFocus(ShellTags.EmptyImport)
                }
                section == ShellSection.Settings && navFocus == null && savedFocus.isNotEmpty() && savedFocus != settingsCategory.tag -> {
                    savedFocus = settingsCategory.tag
                    restoreFocus(settingsCategory.tag)
                }
                section == ShellSection.Settings -> openSection(settingsOrigin)
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
        val tabTag = when (section) {
            ShellSection.Favorites -> ShellTags.NavFavorites
            ShellSection.Recent -> ShellTags.NavRecent
            ShellSection.Library -> ShellTags.NavLibrary
            ShellSection.Settings, ShellSection.Import, ShellSection.Sync -> ShellTags.NavSettings
        }
        val libraryFocusBusy = section == ShellSection.Library && playBusy
        val importBusy = busy && (section == ShellSection.Import ||
            (section == ShellSection.Settings && settingsCategory == SettingsCategory.Import))
        val savedRowExists = pageEntries.any { it.game.name == focusedGameName }
        LaunchedEffect(
            section, emptySearchAction, results.entries.isNotEmpty(), savedRowExists, libraryFocusBusy, importBusy, folderTags, rail, fold,
            widthClass, pickerOpen, focusRequest, currentPage, libraryPageSize, returnFromGame,
            windowFocused,
            pageEntries.map { it.game.name }.takeIf { section == ShellSection.Library },
            shelfCards.takeIf { showShelf }, shelfPage.takeIf { showShelf },
            maxWidth.takeIf { showShelf }, maxHeight.takeIf { showShelf },
        ) {
            if (libraryFocusBusy || (importBusy && restoreTarget != null) || pickerOpen ||
                returnFromGame != null || !windowFocused
            ) return@LaunchedEffect
            val target = restoreTarget
            if (target != null) {
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
                        emptySearchAction,
                        ShellTags.JumpToLetter,
                        ShellTags.LibraryFilter,
                    )
                }
                ShellSection.Import -> listOf(
                    savedFocus,
                    folderTags.firstOrNull().orEmpty(),
                    ShellTags.AddFolder,
                )
                ShellSection.Sync -> listOf(savedFocus, SettingsCategory.Sync.tag)
                ShellSection.Settings -> listOf(savedFocus, settingsCategory.tag)
            }
            val targets = preferred.distinct()
            repeat(4) {
                if (targets.any(::request)) return@LaunchedEffect
                withFrameNanos { }
            }
            request(tabTag)
        }
        Box(Modifier.fillMaxSize().onPreviewKeyEvent { event ->
            if (pickerOpen || navFocus != null) return@onPreviewKeyEvent false
            val down = event.type == KeyEventType.KeyDown
            val index = if (section == ShellSection.Library && focusedGameName != null) {
                pageEntries.indexOfFirst { it.game.name == focusedGameName }
            } else -1
            if (index >= 0 && (event.key == Key.DirectionUp || event.key == Key.DirectionDown)) {
                val row = index / LibraryColumns + if (event.key == Key.DirectionUp) -1 else 1
                val first = row * LibraryColumns
                if (first !in pageEntries.indices) return@onPreviewKeyEvent false
                val neighbor = minOf(first + index % LibraryColumns, pageEntries.lastIndex)
                if (down) request(ShellTags.game(pageEntries[neighbor].game.name))
                return@onPreviewKeyEvent true
            }
            val step = when (event.key) {
                Key.DirectionLeft -> -1
                Key.DirectionRight -> 1
                else -> return@onPreviewKeyEvent false
            }
            val shelfCardFocused = showShelf && savedShelfFocus in shelfCards
            if (index < 0 && !shelfCardFocused) return@onPreviewKeyEvent false
            if (down) {
                if (index >= 0) {
                    val neighbor = index + step
                    if (neighbor in pageEntries.indices && neighbor / LibraryColumns == index / LibraryColumns) {
                        request(ShellTags.game(pageEntries[neighbor].game.name))
                    } else {
                        changePage(currentPage + step)
                    }
                } else {
                    val neighbor = shelfIndex + step
                    if (!recent && neighbor in shelfCards.indices && neighbor / shelfPageSize == shelfPage) {
                        request(shelfCards[neighbor])
                    } else {
                        changeShelfPage(shelfPage + step)
                    }
                }
            }
            true
        }.onKeyEvent { event ->
            if (event.type == KeyEventType.KeyUp) {
                val onLibraryList = section == ShellSection.Library && !pickerOpen
                val pageStep = when (event.key) {
                    Key.ButtonL1 -> -1
                    Key.ButtonR1 -> 1
                    else -> 0
                }
                if (pageStep != 0 && !pickerOpen) {
                    val index = BrowseSections.indexOf(section.topLevelTab)
                    BrowseSections.getOrNull(index + pageStep)?.let(::openSection)
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
            val shelfPageEntries = remember(shelfEntries, shelfPage) { shelfEntries.drop(shelfPage * shelfPageSize).take(shelfPageSize) }
            val shelfMetadata by produceState(emptyMap<String, String>(), section, shelfPageEntries, recentTimes, showDetails) {
                value = if (!showShelf) emptyMap() else withContext(Dispatchers.IO) {
                    val format = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
                    val storage = AppStorage(context.filesDir)
                    shelfPageEntries.mapNotNull { entry ->
                        val name = entry.game.name
                        val caption = if (recent) {
                            recentTimes[name]?.takeIf { it > 0 }?.let { format.format(Date(it)) }
                        } else {
                            val directory = storage.stateDir(name)
                            if (!directory.isDirectory) null else runCatching {
                                GameProgressStore(directory).slotWithProgress()
                                    ?.let { context.getString(R.string.shelf_has_progress, it) }
                            }.getOrNull()
                        }
                        caption?.let { name to it }
                    }.toMap()
                }
            }
            val navigation: @Composable () -> Unit = {
                ShellTabs(section, requesters, { if (it != section.topLevelTab) openSection(it) }, { navFocus = it },
                    appUpdate?.state is UpdateState.Available)
            }
            Box(if (pickerOpen) Modifier.semantics { hideFromAccessibility() } else Modifier) {
                when (section) {
                    ShellSection.Favorites, ShellSection.Recent -> GameShelfPage(
                        entries = shelfPageEntries,
                        hasSavedGames = if (recent) recentNames.isNotEmpty() else favoriteNames.isNotEmpty(),
                        recent = recent,
                        covers = covers,
                        scanning = workMessage == R.string.folder_scanning && busy,
                        margin = margin,
                        pageIndex = shelfPage,
                        selectedTag = shelfCards.getOrNull(shelfIndex),
                        pages = shelfPages,
                        onPage = ::changeShelfPage,
                        cardRequesters = cardRequesters,
                        requesters = requesters,
                        navigation = navigation,
                        metadata = shelfMetadata,
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
                        noPlayableResults = noPlayableResults,
                        modalOpen = picker == LibraryPicker.Status,
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
                        navigation = navigation,
                        scanning = workMessage == R.string.folder_scanning && busy,
                        margin = margin,
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
                        onShowAllStatuses = {
                            val firstMatch = ShellTags.game(matchingEntries.first().game.name)
                            playableOnly = false
                            libraryPage = 0
                            selectedName = null
                            focusSaved(firstMatch)
                            restoreFocus(firstMatch)
                        },
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
                    ShellSection.Import, ShellSection.Sync, ShellSection.Settings -> {
                        val directImport = section == ShellSection.Import
                        SettingsPage(
                            category = when (section) {
                                ShellSection.Import -> SettingsCategory.Import
                                ShellSection.Sync -> SettingsCategory.Sync
                                else -> settingsCategory
                            },
                            onCategory = { next ->
                                if (directImport && next != SettingsCategory.Import) openSection(ShellSection.Settings)
                                settingsCategory = next
                            },
                            busy = busy, folders = folders, folderError = folderError, workMessage = workMessage,
                            folderActions = folderActions, covers = covers, appUpdate = appUpdate,
                            margin = margin, requesters = requesters,
                            navigation = navigation, onFocused = ::focusSaved,
                            request = { restoreFocus(it) },
                        )
                    }
                }
            }
            if (pickerOpen) {
                if (picker == LibraryPicker.Status) {
                    LibraryStatusPicker(playableOnly, { closePicker() }) {
                        playableOnly = it
                        libraryPage = 0
                        selectedName = null
                        closePicker(showResults = true)
                    }
                } else {
                    letterPicker?.invoke()
                }
            }
        }
    }
}

@Composable
private fun SettingsPage(
    category: SettingsCategory,
    onCategory: (SettingsCategory) -> Unit,
    busy: Boolean,
    folders: List<FolderScan>,
    folderError: Int?,
    workMessage: Int,
    folderActions: FolderActions,
    covers: CoverUiState?,
    appUpdate: UpdateUiState?,
    margin: Dp,
    requesters: Map<String, FocusRequester>,
    navigation: @Composable () -> Unit,
    onFocused: (String) -> Unit,
    request: (String) -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    var clearCovers by remember { mutableStateOf(false) }
    var pendingRemoval by remember { mutableStateOf<FolderScan?>(null) }
    fun contentEntry(item: SettingsCategory): String? = when (item) {
        SettingsCategory.Import -> folders.firstOrNull()?.let { ShellTags.removeFolder(it.folder.uri) } ?: ShellTags.AddFolder
        SettingsCategory.Covers -> if (covers == null) null else ShellTags.FetchCovers
        SettingsCategory.Sync -> null
        SettingsCategory.About -> if (appUpdate == null) ShellTags.SettingsInfo else ShellTags.AppUpdate
    }
    if (clearCovers && covers != null) {
        fun dismiss() {
            clearCovers = false
            request(ShellTags.ClearCovers)
        }
        GameConfirmationDialog(
            title = stringResource(R.string.covers_clear_title),
            body = stringResource(R.string.covers_clear_body),
            actionLabel = stringResource(R.string.covers_clear),
            onConfirm = {
                clearCovers = false
                request(ShellTags.FetchCovers)
                covers.clear()
            },
            onDismiss = ::dismiss,
        )
    }
    ShellPage(
        title = "",
        modalOpen = pendingRemoval != null || clearCovers,
        margin = margin,
        leadingAction = navigation,
        footer = {
            ShellFooter(if (category == SettingsCategory.Sync) listOf("B" to R.string.back) else listOf(
                "A" to R.string.shell_hint_select,
                "B" to R.string.shell_hint_return_category,
            ))
        },
    ) {
        Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(FbngDimens.space12)) {
            Column(
                Modifier.weight(2f).fillMaxHeight().fbngIsland().padding(FbngDimens.panelInset),
                verticalArrangement = Arrangement.spacedBy(FbngDimens.space8),
            ) {
                for (item in SettingsCategory.entries) {
                    val enter: () -> Unit = {
                        onCategory(item)
                        contentEntry(item)?.let(request)
                    }
                    Row(
                        Modifier.weight(1f).fillMaxWidth()
                            .onKeyEvent {
                                if (it.key != Key.DirectionRight) false else {
                                    if (it.type == KeyEventType.KeyDown) enter()
                                    true
                                }
                            }
                            .focusProperties {
                                if (item == SettingsCategory.Import) up = requesters.getValue(ShellTags.NavSettings)
                            }
                            .libraryFocus(item.tag, requesters.getValue(item.tag)) {
                                onCategory(item)
                                onFocused(it)
                            }
                            .semantics { selected = item == category }
                            .fbngFocusTarget(
                                enter,
                                if (item == category) scheme.primary.copy(alpha = .22f) else scheme.surface,
                                restingStroke = if (item == category) scheme.primary.copy(alpha = .65f) else Color.Transparent,
                            )
                            .padding(horizontal = FbngDimens.space12),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(FbngDimens.space4),
                    ) {
                        Text(stringResource(item.label), Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, maxLines = 1)
                        if (contentEntry(item) != null) Text("›", color = scheme.secondary)
                    }
                }
            }
            val contentInset = if (category == SettingsCategory.About) {
                Modifier.clip(FbngShapes.Island).padding(horizontal = FbngDimens.panelInset)
            } else {
                Modifier.padding(FbngDimens.panelInset)
            }
            Column(
                Modifier.weight(5f).fillMaxHeight().fbngIsland().then(contentInset),
                verticalArrangement = Arrangement.spacedBy(FbngDimens.space8),
            ) {
                when (category) {
                    SettingsCategory.Import -> FolderManagement(
                        folders, busy, workMessage, folderError, requesters, onFocused, folderActions,
                        onRemove = { pendingRemoval = it },
                    )
                    SettingsCategory.Covers -> covers?.let { CoverSettings(it, requesters, onFocused) { clearCovers = true } }
                    SettingsCategory.Sync -> Text(
                        stringResource(R.string.sync_body), Modifier.padding(FbngDimens.space16),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    SettingsCategory.About -> AboutSettings(appUpdate, requesters, onFocused)
                }
            }
        }
    }
    pendingRemoval?.let { scan ->
        GameConfirmationDialog(
            title = stringResource(R.string.folder_remove_label, scan.folder.name),
            body = stringResource(R.string.folder_remove_confirmation),
            actionLabel = stringResource(R.string.folder_remove),
            onConfirm = {
                pendingRemoval = null
                val index = folders.indexOfFirst { it.folder.uri == scan.folder.uri }
                val neighbor = if (index >= 0) folders.getOrNull(index + 1) ?: folders.getOrNull(index - 1) else null
                folderActions.remove(scan.folder.uri)
                request(neighbor?.let { ShellTags.removeFolder(it.folder.uri) } ?: ShellTags.AddFolder)
            },
            onDismiss = {
                pendingRemoval = null
                request(ShellTags.removeFolder(scan.folder.uri))
            },
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AboutSettings(
    appUpdate: UpdateUiState?,
    requesters: Map<String, FocusRequester>,
    onFocused: (String) -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val inset = with(LocalDensity.current) { FbngDimens.panelInset.roundToPx().toFloat() }
    val scrollSpec = remember(inset) {
        object : BringIntoViewSpec {
            override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float): Float =
                super.calculateScrollDistance(offset - inset, size + 2 * inset, containerSize)
        }
    }
    CompositionLocalProvider(LocalBringIntoViewSpec provides scrollSpec) {
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(vertical = FbngDimens.panelInset),
            verticalArrangement = Arrangement.spacedBy(FbngDimens.space8),
        ) {
            if (appUpdate != null) UpdateSettings(appUpdate, requesters.getValue(ShellTags.AppUpdate), onFocused)
            FocusableMessage(ShellTags.SettingsInfo, requesters.getValue(ShellTags.SettingsInfo), onFocused, scheme.surface) {
                Text(stringResource(R.string.legal_notice), style = MaterialTheme.typography.bodyLarge)
            }
            FocusableMessage(ShellTags.SettingsCore, requesters.getValue(ShellTags.SettingsCore), onFocused, scheme.surface) {
                Text(stringResource(R.string.footer_core, BuildConfig.FBNEO_COMMIT_SHORT,
                    BuildConfig.FBNEO_BUILD_DATE, BuildConfig.LIBRETRODROID_VERSION), style = MaterialTheme.typography.bodyLarge)
            }
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
