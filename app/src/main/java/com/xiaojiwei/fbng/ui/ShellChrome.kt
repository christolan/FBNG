package com.xiaojiwei.fbng.ui

import com.xiaojiwei.fbng.ui.theme.FbngBackdrop
import androidx.compose.ui.unit.dp
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Badge
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.constrainHeight
import com.xiaojiwei.fbng.R
import com.xiaojiwei.fbng.ui.theme.CornerRadiusOption
import com.xiaojiwei.fbng.ui.theme.FbngButton
import com.xiaojiwei.fbng.ui.theme.FbngButtonKind
import com.xiaojiwei.fbng.ui.theme.FbngDimens
import com.xiaojiwei.fbng.ui.theme.fbngFocusTarget
import com.xiaojiwei.fbng.ui.theme.fbngIsland

internal enum class ShellSection {
    Favorites,
    Recent,
    Library,
    Import,
    Sync,
    Settings,
}

internal val ShellSection.topLevelTab: ShellSection
    get() = when (this) {
        ShellSection.Import, ShellSection.Sync -> ShellSection.Settings
        else -> this
    }

internal object ShellTags {
    const val Header = "shell-header"
    const val HeaderTitle = "shell-header-title"
    private const val GamePrefix = "shell-game-"
    private const val FavoritePrefix = "shell-favorite-game-"
    const val Play = "shell-play"
    const val OpenLibrary = "shell-open-library"
    const val ToggleFavorite = "shell-toggle-favorite"
    const val EmptyImport = "shell-library-import"
    const val DetailBack = "shell-detail-back"
    fun game(name: String) = "$GamePrefix$name"
    fun gameName(tag: String) = tag.takeIf { it.startsWith(GamePrefix) }?.removePrefix(GamePrefix)
    fun favorite(name: String) = "$FavoritePrefix$name"
    fun recent(name: String) = "shell-recent-game-$name"
    const val JumpToLetter = "shell-jump-to-letter"
    const val EditSearch = "shell-edit-search"
    const val ClearFilters = "shell-clear-filters"
    const val LibraryFilter = "shell-library-filter"
    const val PreviousPage = "shell-library-previous-page"
    const val NextPage = "shell-library-next-page"
    fun letter(value: Char) = "shell-letter-$value"
    const val AddFolder = "shell-add-folder"
    const val ScanFolders = "shell-scan-folders"
    fun removeFolder(uri: Uri) = "shell-remove-folder-$uri"
    const val Sync = "shell-sync"
    const val OpenImport = "shell-open-import"
    const val OpenSync = "shell-open-sync"
    const val SettingsInfo = "shell-settings-info"
    const val SettingsCore = "shell-settings-core"
    fun cornerRadius(option: CornerRadiusOption) = "shell-corner-radius-${option.name}"
    const val AppUpdate = "shell-app-update"
    const val FetchCovers = "shell-fetch-covers"
    const val RefreshCovers = "shell-refresh-covers"
    const val NavFavorites = "shell-nav-favorites"
    const val NavRecent = "shell-nav-recent"
    const val NavLibrary = "shell-nav-library"
    const val NavSettings = "shell-nav-settings"
    const val Save = "shell-save"
    const val Load = "shell-load"
    const val Quit = "shell-quit"
    const val Pause = "shell-pause"
    const val Reset = "shell-reset"
    const val DatError = "shell-dat-error"
    const val CoreMissing = "shell-core-missing"
}

private data class ShellDestination(
    val section: ShellSection,
    val label: Int,
    val tag: String,
)

private val Destinations = listOf(
    ShellDestination(ShellSection.Favorites, R.string.favorites_title, ShellTags.NavFavorites),
    ShellDestination(ShellSection.Recent, R.string.recent_title, ShellTags.NavRecent),
    ShellDestination(ShellSection.Library, R.string.shell_library, ShellTags.NavLibrary),
    ShellDestination(ShellSection.Settings, R.string.shell_settings, ShellTags.NavSettings),
)

internal val BrowseSections = Destinations.map(ShellDestination::section)

@Composable
internal fun ShellPage(
    title: String,
    margin: Dp,
    headerAction: @Composable ((Dp) -> Unit)? = null,
    leadingAction: @Composable (() -> Unit)? = null,
    contentGap: Dp = FbngDimens.space12,
    verticalMargin: Dp = FbngDimens.space16,
    footer: (@Composable () -> Unit)? = null,
    modalOpen: Boolean = false,
    content: @Composable () -> Unit,
) {
    val backdrop = if (modalOpen) Modifier.blur(FbngDimens.glassBlur) else Modifier
    Box(Modifier.fillMaxSize().then(backdrop)) {
        FbngBackdrop()
        Column(
            Modifier.fillMaxSize().padding(horizontal = margin, vertical = verticalMargin),
            verticalArrangement = Arrangement.spacedBy(contentGap),
        ) {
            ShellHeader(title, leadingAction, headerAction)
            Box(Modifier.weight(1f).fillMaxWidth()) { content() }
            footer?.invoke()
        }
    }
}

@Composable
private fun ShellHeader(
    title: String,
    leadingAction: (@Composable () -> Unit)?,
    actions: (@Composable (Dp) -> Unit)?,
) {
    BoxWithConstraints(Modifier.fillMaxWidth().testTag(ShellTags.Header)) {
        val width = maxWidth
        Layout(
            contents = listOf(
                {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(FbngDimens.space8),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        leadingAction?.invoke()
                        if (title.isNotEmpty()) Box(
                            Modifier.weight(1f).widthIn(min = FbngDimens.shellHeaderTitleMinWidth)
                                .height(FbngDimens.shellHeaderHeight).testTag(ShellTags.HeaderTitle)
                                .padding(horizontal = FbngDimens.space4),
                            contentAlignment = Alignment.CenterStart,
                        ) {
                            Text(
                                title,
                                modifier = Modifier.semantics { heading() },
                                style = MaterialTheme.typography.headlineSmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                },
                { actions?.invoke(width) },
            ),
        ) { (heading, controls), constraints ->
            val rowWidth = constraints.maxWidth
            val rowHeight = FbngDimens.shellHeaderHeight.roundToPx()
            val gap = FbngDimens.space8.roundToPx()
            val titleRow = heading.single()
            val controlWidths = controls.map { it.maxIntrinsicWidth(rowHeight).coerceAtMost(rowWidth) }
            val controlsWidth = controlWidths.sum() + gap * (controls.size - 1).coerceAtLeast(0)
            val wrap = controls.isNotEmpty() && titleRow.maxIntrinsicWidth(rowHeight) + gap + controlsWidth > rowWidth
            val titleWidth = if (wrap || controls.isEmpty()) rowWidth else rowWidth - controlsWidth - gap
            val placedTitle = titleRow.measure(Constraints.fixed(titleWidth, rowHeight))

            // Reposition the existing controls on resize so their focus targets stay attached.
            var extraWidth = if (wrap) (rowWidth - controlsWidth).coerceAtLeast(0) else 0
            var x = if (wrap) 0 else titleWidth + gap
            var y = if (wrap) rowHeight + gap else 0
            val placedControls = controls.mapIndexed { index, control ->
                val extra = extraWidth / (controls.size - index)
                extraWidth -= extra
                val controlWidth = controlWidths[index] + extra
                if (wrap && x > 0 && x + controlWidth > rowWidth) {
                    x = 0
                    y += rowHeight + gap
                }
                val position = IntOffset(x, y)
                x += controlWidth + gap
                control.measure(Constraints.fixed(controlWidth, rowHeight)) to position
            }
            layout(rowWidth, constraints.constrainHeight(y + rowHeight)) {
                placedTitle.placeRelative(0, 0)
                placedControls.forEach { (control, position) -> control.placeRelative(position.x, position.y) }
            }
        }
    }
}

@Composable
internal fun ShellHeaderButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    selected: Boolean = false,
    content: @Composable RowScope.() -> Unit,
) {
    FbngButton(
        onClick = onClick,
        modifier = modifier.height(FbngDimens.shellHeaderHeight),
        enabled = enabled,
        kind = FbngButtonKind.Toolbar,
        contentPadding = PaddingValues(horizontal = FbngDimens.space4),
        selected = selected,
        content = content,
    )
}

@Composable
internal fun ShellHeaderBack(label: String, onClick: () -> Unit, modifier: Modifier, enabled: Boolean = true) {
    ShellHeaderButton(
        onClick = onClick,
        modifier = modifier.width(FbngDimens.shellHeaderHeight).semantics { contentDescription = label },
        enabled = enabled,
    ) { Text("‹", style = MaterialTheme.typography.titleMedium) }
}

@Composable
internal fun ShellTabs(
    section: ShellSection,
    requesters: Map<String, FocusRequester>,
    onSection: (ShellSection) -> Unit,
    onFocused: (String) -> Unit,
    updateAvailable: Boolean,
) {
    val scheme = MaterialTheme.colorScheme
    val width = with(LocalDensity.current) { LocalWindowInfo.current.containerSize.width.toDp() }
    val showBrand = width >= 460.dp
    val tabPadding = if (width < 360.dp) FbngDimens.space8 else FbngDimens.space12
    val updateDescription = if (updateAvailable) stringResource(R.string.update_nav_available) else null
    Row(Modifier.testTag(ShellTags.HeaderTitle),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(FbngDimens.space12)) {
        if (showBrand) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(FbngDimens.space8)) {
            Text("F", color = scheme.primary, style = MaterialTheme.typography.headlineSmall)
            Text("FBNG", style = MaterialTheme.typography.titleLarge)
        }
        Row(
            Modifier.fbngIsland().padding(FbngDimens.panelInset),
            horizontalArrangement = Arrangement.spacedBy(FbngDimens.space4),
        ) {
            Destinations.forEach { destination ->
                val selected = destination.section == section.topLevelTab
                val hasUpdate = destination.section == ShellSection.Settings && updateAvailable
                Box(
                    Modifier.libraryFocus(destination.tag, requesters.getValue(destination.tag), onFocused)
                        .semantics {
                            this.selected = selected
                            if (hasUpdate && updateDescription != null) stateDescription = updateDescription
                        }
                        .fbngFocusTarget(
                            onClick = { onSection(destination.section) },
                            container = if (selected) scheme.primary.copy(alpha = .22f) else Color.Transparent,
                            restingStroke = if (selected) scheme.primary.copy(alpha = .65f) else Color.Transparent,
                            role = Role.Tab,
                        )
                        .height(FbngDimens.space24).padding(horizontal = tabPadding),
                    contentAlignment = Alignment.Center,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(FbngDimens.space4),
                    ) {
                        Text(stringResource(destination.label), style = MaterialTheme.typography.labelLarge, maxLines = 1)
                        if (hasUpdate) Badge(containerColor = scheme.primary)
                    }
                }
            }
        }
    }
}

@Composable
internal fun ShellFooter(
    hints: List<Pair<String, Int>>,
    trailing: @Composable RowScope.() -> Unit = {},
) {
    val scheme = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth().height(FbngDimens.space24),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(FbngDimens.space12),
    ) {
        Row(
            Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(FbngDimens.space12),
        ) {
            hints.forEach { (key, label) ->
                Row(horizontalArrangement = Arrangement.spacedBy(FbngDimens.space4), verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.background(
                            if (key == "A") scheme.primary else scheme.surfaceContainerHighest,
                            MaterialTheme.shapes.extraSmall,
                        ).padding(horizontal = FbngDimens.space8, vertical = 2.dp),
                    ) {
                        Text(key, color = if (key == "A") scheme.onPrimary else scheme.onSurface, style = MaterialTheme.typography.labelSmall, maxLines = 1)
                    }
                    Text(stringResource(label), color = scheme.onSurfaceVariant, style = MaterialTheme.typography.labelMedium, maxLines = 1)
                }
            }
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(FbngDimens.space12),
            content = trailing,
        )
    }
}

@Composable
internal fun BrowseFooter(
    page: Int,
    pages: Int,
    tab: String,
    scanning: Boolean,
    requesters: Map<String, FocusRequester>,
    onFocused: (String) -> Unit,
    onPage: (Int) -> Unit,
) {
    val hints = buildList {
        add("A" to R.string.shell_hint_open)
        val direction = when (tab) {
            ShellTags.NavFavorites -> R.string.shell_hint_gallery
            ShellTags.NavLibrary -> R.string.shell_hint_grid
            else -> R.string.shell_hint_page
        }
        if (pages > 1 || (pages == 1 && tab != ShellTags.NavRecent)) add("← / →" to direction)
        if (tab == ShellTags.NavLibrary) add("Y" to R.string.library_jump_to_letter)
        add("L1 / R1" to R.string.shell_hint_switch_page)
    }
    ShellFooter(hints) {
        if (scanning) {
            val color = MaterialTheme.colorScheme.primary
            Row(
                horizontalArrangement = Arrangement.spacedBy(FbngDimens.space4),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(FbngDimens.space4).background(color, CircleShape))
                Text(stringResource(R.string.scanning_badge), color = color, style = MaterialTheme.typography.labelMedium)
            }
        }
        if (pages > 0) {
            val scheme = MaterialTheme.colorScheme
            for ((step, glyph, label) in listOf(Triple(-1, "‹", R.string.library_previous_page), Triple(0, "", 0), Triple(1, "›", R.string.library_next_page))) {
                if (step == 0) {
                    val description = stringResource(R.string.library_page_number, page + 1, pages)
                    Text(stringResource(R.string.library_page_short, page + 1, pages),
                        Modifier.clearAndSetSemantics { contentDescription = description },
                        style = MaterialTheme.typography.labelMedium, color = scheme.onSurfaceVariant)
                } else if (pages > 1) {
                    val description = stringResource(label)
                    val tag = if (step < 0) ShellTags.PreviousPage else ShellTags.NextPage
                    Box(
                        Modifier.size(FbngDimens.space24).libraryFocus(tag, requesters.getValue(tag), onFocused).semantics { contentDescription = description }
                            .fbngFocusTarget({ onPage(page + step) }, Color.Transparent, enabled = page + step in 0 until pages),
                        contentAlignment = Alignment.Center,
                    ) { Text(glyph, style = MaterialTheme.typography.labelLarge) }
                }
            }
        }
    }
}

@Composable
internal fun FocusableMessage(
    tag: String,
    requester: FocusRequester,
    onFocused: (String) -> Unit,
    container: Color,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Column(
        modifier
            .fillMaxWidth()
            .onFocusChanged { if (it.isFocused) onFocused(tag) }
            .testTag(tag)
            .focusRequester(requester)
            .fbngFocusTarget(
                onClick = {},
                container = container,
            )
            .padding(FbngDimens.space16),
        verticalArrangement = Arrangement.spacedBy(FbngDimens.space8),
        content = { content() },
    )
}

/** Keeps a non-click message in the focus order for the DAT error screen. */
@Composable
internal fun PlainFocusBlock(
    tag: String,
    requester: FocusRequester,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Column(
        modifier
            .fillMaxWidth()
            .testTag(tag)
            .focusRequester(requester)
            .focusable()
            .padding(FbngDimens.space4),
        verticalArrangement = Arrangement.spacedBy(FbngDimens.space12),
        content = { content() },
    )
}
