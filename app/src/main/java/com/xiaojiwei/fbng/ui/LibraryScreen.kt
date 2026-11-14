package com.xiaojiwei.fbng.ui

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalContext
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
import com.xiaojiwei.fbng.library.FolderScan
import com.xiaojiwei.fbng.library.LibraryResults
import com.xiaojiwei.fbng.library.LibrarySnapshot
import com.xiaojiwei.fbng.library.indexLibrary
import com.xiaojiwei.fbng.ui.theme.FbngButton
import com.xiaojiwei.fbng.ui.theme.FbngButtonKind
import com.xiaojiwei.fbng.ui.theme.FbngDimens
import com.xiaojiwei.fbng.update.UpdateState

private enum class LibraryPicker { Letters, Status }

@Composable
internal fun LibraryContent(
    load: CatalogLoad,
    snapshot: LibrarySnapshot?,
    busy: Boolean,
    readFold: @Composable () -> FoldSnapshot?,
    onPlay: (String) -> Unit,
    initialSection: ShellSection = ShellSection.Library,
    workMessage: Int = R.string.folder_scanning,
    folders: List<FolderScan> = emptyList(),
    folderError: Int? = null,
    launchError: Boolean = false,
    folderActions: FolderActions = FolderActions(),
    playBusy: Boolean = busy,
    favoriteNames: Set<String> = emptySet(),
    recentNames: List<String> = emptyList(),
    onToggleFavorite: (String) -> Unit = {},
    covers: CoverUiState? = null,
    appUpdate: UpdateUiState? = null,
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
    appUpdate: UpdateUiState?,
) {
    val focusManager = LocalFocusManager.current
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
    var focusRequest by remember { mutableStateOf(0) }
    var libraryPage by rememberSaveable { mutableStateOf(0) }
    val libraryPageSize = LibraryPageSize
    val favoritesListState = rememberLazyListState()
    val recentListState = rememberLazyListState()
    val cardRequesters = remember { mutableMapOf<String, FocusRequester>() }
    var searchQuery by rememberSaveable { mutableStateOf("") }
    val section = ShellSection.valueOf(sectionName)
    val shellRequesters = remember {
        listOf(
            ShellTags.Play,
            ShellTags.ToggleFavorite,
            ShellTags.OpenLibrary,
            ShellTags.EmptyImport,
            ShellTags.DetailBack,
            ShellTags.JumpToLetter,
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
        val pickerOpen = section == ShellSection.Library && picker != null

        fun returnToList(name: String? = selected?.game?.name) {
            navFocus = null
            val index = results.entries.indexOfFirst { it.game.name == name }
            if (index >= 0) libraryPage = index / libraryPageSize
            libraryFocus = name?.let(ShellTags::game)
                ?: ShellTags.JumpToLetter
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
            libraryFocus = when (picker) {
                LibraryPicker.Letters -> ShellTags.JumpToLetter
                LibraryPicker.Status, null -> ShellTags.LibraryFilter
            }
            picker = null
            focusRequest++
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
                    resultCount = results.entries.size,
                    onQuery = { searchQuery = it; libraryPage = 0; selectedName = null },
                    onDismiss = ::closePicker,
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
            if (libraryFocusBusy || pickerOpen) return@LaunchedEffect
            if (navFocus?.let(::request) == true) return@LaunchedEffect
            val preferred = when (section) {
                ShellSection.Favorites, ShellSection.Recent -> {
                    val cards = if (section == ShellSection.Recent) recentCards else favoriteCards
                    val saved = if (section == ShellSection.Recent) recentFocus else favoritesFocus
                    val listState = if (section == ShellSection.Recent) recentListState else favoritesListState
                    val card = saved.takeIf { it in cards } ?: cards.firstOrNull()
                    if (card != null &&
                        listState.layoutInfo.visibleItemsInfo.none { it.key == card }
                    ) {
                        listState.scrollToItem(cards.indexOf(card))
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
                        ShellTags.EmptyImport,
                        ShellTags.JumpToLetter,
                        ShellTags.LibraryFilter,
                        ShellTags.DetailBack,
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
            if (event.type == KeyEventType.KeyUp && section == ShellSection.Library &&
                !showDetails && !pickerOpen
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
                updateAvailable = appUpdate?.state is UpdateState.Available,
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
                        launchError = launchError,
                        margin = margin,
                        listState = if (section == ShellSection.Recent) recentListState else favoritesListState,
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
                        onPage = ::changePage,
                        cardRequesters = cardRequesters,
                        wide = rail,
                        showDetails = showDetails,
                        margin = margin,
                        busy = playBusy,
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
        headerAction = {
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
) {
    val scheme = MaterialTheme.colorScheme
    ShellPage(title = stringResource(R.string.shell_settings), margin = margin) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(FbngDimens.space16),
        ) {
            SettingsDestination(ShellSection.Import, R.string.shell_import, ShellTags.OpenImport, requesters, onFocused, onOpenImport)
            SettingsDestination(ShellSection.Sync, R.string.shell_sync, ShellTags.OpenSync, requesters, onFocused, onOpenSync)
            if (appUpdate != null) UpdateSettings(appUpdate, requesters.getValue(ShellTags.AppUpdate), onFocused)
            if (covers != null) CoverSettings(covers, requesters, onFocused)
            FocusableMessage(
                tag = ShellTags.SettingsInfo,
                requester = requesters.getValue(ShellTags.SettingsInfo),
                onFocused = onFocused,
                container = scheme.surface,
            ) {
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
