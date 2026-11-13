package com.xiaojiwei.fbng.ui

import android.net.Uri
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import com.xiaojiwei.fbng.R
import com.xiaojiwei.fbng.ui.theme.FbngDimens
import com.xiaojiwei.fbng.ui.theme.fbngFocusTarget
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

internal enum class ShellSection {
    Favorites,
    Recent,
    Library,
    Import,
    Sync,
    Settings,
}

internal object ShellTags {
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
    const val Search = "shell-search"
    const val ClearSearch = "shell-search-clear"
    const val JumpToLetter = "shell-jump-to-letter"
    const val LibraryFilter = "shell-library-filter"
    const val PreviousPage = "shell-library-previous-page"
    const val NextPage = "shell-library-next-page"
    fun letter(value: Char) = "shell-letter-$value"
    const val Import = "shell-import"
    const val AddFolder = "shell-add-folder"
    const val ScanFolders = "shell-scan-folders"
    fun removeFolder(uri: Uri) = "shell-remove-folder-$uri"
    const val Sync = "shell-sync"
    const val OpenImport = "shell-open-import"
    const val OpenSync = "shell-open-sync"
    const val SettingsBack = "shell-settings-back"
    const val SettingsInfo = "shell-settings-info"
    const val SettingsCore = "shell-settings-core"
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

@Composable
internal fun ShellScaffold(
    section: ShellSection,
    rail: Boolean,
    expanded: Boolean,
    requesters: Map<String, FocusRequester>,
    onSection: (ShellSection) -> Unit,
    onNavFocused: (String) -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Column(modifier.fillMaxSize().background(scheme.background)) {
        if (rail) {
            Row(Modifier.weight(1f).fillMaxWidth()) {
                ShellRail(
                    section = section,
                    expanded = expanded,
                    requesters = requesters,
                    onSection = onSection,
                    onNavFocused = onNavFocused,
                )
                Box(Modifier.weight(1f).fillMaxHeight()) { content() }
            }
        } else {
            Box(Modifier.weight(1f).fillMaxWidth()) { content() }
        }
        if (section != ShellSection.Library) PadHint()
        if (!rail) {
            ShellBottomBar(
                section = section,
                requesters = requesters,
                onSection = onSection,
                onNavFocused = onNavFocused,
            )
        }
    }
}

@Composable
private fun ShellRail(
    section: ShellSection,
    expanded: Boolean,
    requesters: Map<String, FocusRequester>,
    onSection: (ShellSection) -> Unit,
    onNavFocused: (String) -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val width = if (expanded) FbngDimens.railWidthExpanded else FbngDimens.railWidthMedium
    Column(
        Modifier
            .width(width)
            .fillMaxHeight()
            .background(scheme.surfaceContainerLow)
            .verticalScroll(rememberScrollState())
            .padding(vertical = FbngDimens.space16, horizontal = FbngDimens.space8),
        verticalArrangement = Arrangement.spacedBy(FbngDimens.space8),
    ) {
        Wordmark(Modifier.padding(horizontal = FbngDimens.space8))
        Destinations.forEach { destination ->
            RailItem(
                destination = destination,
                selected = destination.section == section,
                requester = requesters.getValue(destination.tag),
                onClick = { onSection(destination.section) },
                onFocused = { onNavFocused(destination.tag) },
            )
        }
    }
}

@Composable
private fun Wordmark(modifier: Modifier = Modifier) {
    val name = stringResource(R.string.app_name)
    val head = name.take(2)
    val tail = name.drop(2)
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Text(head, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface)
        if (tail.isNotEmpty()) {
            Text(tail, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.secondary)
        }
    }
}

@Composable
private fun RailItem(
    destination: ShellDestination,
    selected: Boolean,
    requester: FocusRequester,
    onClick: () -> Unit,
    onFocused: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val tint = if (selected) scheme.secondary else scheme.onSurfaceVariant
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = FbngDimens.railItemHeight)
            .onFocusChanged { if (it.isFocused) onFocused() }
            .testTag(destination.tag)
            .semantics { this.selected = selected }
            .focusRequester(requester)
            .fbngFocusTarget(
                onClick = onClick,
                container = if (selected) scheme.surface else scheme.surfaceContainerLow,
                shape = androidx.compose.foundation.shape.RoundedCornerShape(FbngDimens.controlRadius),
                role = Role.Tab,
            )
            .padding(horizontal = FbngDimens.space8),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(FbngDimens.space4, Alignment.CenterVertically),
    ) {
        Box(
            Modifier
                .width(FbngDimens.space24)
                .height(FbngDimens.space4)
                .background(if (selected) scheme.primary else Color.Transparent),
        )
        ShellGlyph(destination.section, tint, Modifier.size(FbngDimens.icon))
        Text(
            stringResource(destination.label),
            color = tint,
            style = MaterialTheme.typography.labelLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun ShellBottomBar(
    section: ShellSection,
    requesters: Map<String, FocusRequester>,
    onSection: (ShellSection) -> Unit,
    onNavFocused: (String) -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Row(
        Modifier
            .fillMaxWidth()
            .background(scheme.surfaceContainerLow)
            .padding(horizontal = FbngDimens.space8, vertical = FbngDimens.space8),
        horizontalArrangement = Arrangement.spacedBy(FbngDimens.space4),
    ) {
        Destinations.forEach { destination ->
            val selected = destination.section == section
            val tint = if (selected) scheme.secondary else scheme.onSurfaceVariant
            Column(
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = FbngDimens.buttonHeight)
                    .onFocusChanged { if (it.isFocused) onNavFocused(destination.tag) }
                    .testTag(destination.tag)
                    .semantics { this.selected = selected }
                    .focusRequester(requesters.getValue(destination.tag))
                    .fbngFocusTarget(
                        onClick = { onSection(destination.section) },
                        container = if (selected) scheme.surface else scheme.surfaceContainerLow,
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(FbngDimens.controlRadius),
                        role = Role.Tab,
                    )
                    .padding(vertical = FbngDimens.space4),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Box(
                    Modifier
                        .width(FbngDimens.space24)
                        .height(FbngDimens.space4)
                        .background(if (selected) scheme.primary else Color.Transparent),
                )
                Spacer(Modifier.height(FbngDimens.space4))
                ShellGlyph(destination.section, tint, Modifier.size(FbngDimens.icon))
                Text(
                    stringResource(destination.label),
                    color = tint,
                    style = MaterialTheme.typography.labelLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
internal fun PadHint(modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier
            .fillMaxWidth()
            .background(scheme.surfaceContainerLow)
            .padding(horizontal = FbngDimens.space16, vertical = FbngDimens.space8),
        horizontalArrangement = Arrangement.spacedBy(FbngDimens.space24),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PadKey("A", stringResource(R.string.pad_confirm))
        PadKey("B", stringResource(R.string.pad_back))
    }
}

@Composable
private fun PadKey(symbol: String, label: String) {
    val scheme = MaterialTheme.colorScheme
    Row(
        horizontalArrangement = Arrangement.spacedBy(FbngDimens.space8),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(FbngDimens.padKey)
                .border(FbngDimens.focusStroke, scheme.primary, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(symbol, color = scheme.onSurface, style = MaterialTheme.typography.labelLarge)
        }
        Text(label, color = scheme.onSurface, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
internal fun ShellGlyph(section: ShellSection, color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val stroke = Stroke(width = size.minDimension * 0.08f, cap = StrokeCap.Round, join = androidx.compose.ui.graphics.StrokeJoin.Round)
        when (section) {
            ShellSection.Favorites -> {
                val radius = size.minDimension / 2f - stroke.width
                val star = Path()
                for (vertex in 0 until 10) {
                    val angle = vertex * PI / 5 - PI / 2
                    val distance = if (vertex % 2 == 0) radius else radius * 0.45f
                    val x = center.x + cos(angle).toFloat() * distance
                    val y = center.y + sin(angle).toFloat() * distance
                    if (vertex == 0) star.moveTo(x, y) else star.lineTo(x, y)
                }
                star.close()
                drawPath(star, color, style = stroke)
            }
            ShellSection.Recent -> {
                drawCircle(color, radius = size.minDimension * 0.4f, style = stroke)
                drawLine(color, center, Offset(center.x, size.height * 0.25f), stroke.width, StrokeCap.Round)
                drawLine(color, center, Offset(size.width * 0.7f, center.y), stroke.width, StrokeCap.Round)
            }
            ShellSection.Library -> {
                val gap = size.minDimension * 0.16f
                val cell = (size.minDimension - gap) / 2f
                val radius = CornerRadius(cell * 0.22f, cell * 0.22f)
                for (row in 0..1) {
                    for (col in 0..1) {
                        drawRoundRect(
                            color = color,
                            topLeft = Offset(col * (cell + gap), row * (cell + gap)),
                            size = Size(cell, cell),
                            cornerRadius = radius,
                            style = stroke,
                        )
                    }
                }
            }
            ShellSection.Import -> {
                val mid = size.width / 2f
                drawLine(color, Offset(mid, size.height * 0.08f), Offset(mid, size.height * 0.55f), stroke.width, StrokeCap.Round)
                drawLine(color, Offset(mid, size.height * 0.55f), Offset(size.width * 0.28f, size.height * 0.34f), stroke.width, StrokeCap.Round)
                drawLine(color, Offset(mid, size.height * 0.55f), Offset(size.width * 0.72f, size.height * 0.34f), stroke.width, StrokeCap.Round)
                drawLine(color, Offset(size.width * 0.18f, size.height * 0.62f), Offset(size.width * 0.18f, size.height * 0.88f), stroke.width, StrokeCap.Round)
                drawLine(color, Offset(size.width * 0.82f, size.height * 0.62f), Offset(size.width * 0.82f, size.height * 0.88f), stroke.width, StrokeCap.Round)
                drawLine(color, Offset(size.width * 0.18f, size.height * 0.88f), Offset(size.width * 0.82f, size.height * 0.88f), stroke.width, StrokeCap.Round)
            }
            ShellSection.Sync -> {
                val inset = size.minDimension * 0.14f
                val arcSize = Size(size.width - inset * 2f, size.height - inset * 2f)
                val topLeft = Offset(inset, inset)
                val strokeWidth = size.minDimension * 0.1f
                drawArc(
                    color,
                    startAngle = -40f,
                    sweepAngle = 200f,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
                )
                drawArc(
                    color,
                    startAngle = 140f,
                    sweepAngle = 200f,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
                )
                drawLine(
                    color,
                    Offset(size.width * 0.72f, size.height * 0.18f),
                    Offset(size.width * 0.86f, size.height * 0.18f),
                    strokeWidth,
                    StrokeCap.Round,
                )
                drawLine(
                    color,
                    Offset(size.width * 0.86f, size.height * 0.18f),
                    Offset(size.width * 0.86f, size.height * 0.34f),
                    strokeWidth,
                    StrokeCap.Round,
                )
                drawLine(
                    color,
                    Offset(size.width * 0.14f, size.height * 0.66f),
                    Offset(size.width * 0.14f, size.height * 0.82f),
                    strokeWidth,
                    StrokeCap.Round,
                )
                drawLine(
                    color,
                    Offset(size.width * 0.14f, size.height * 0.82f),
                    Offset(size.width * 0.28f, size.height * 0.82f),
                    strokeWidth,
                    StrokeCap.Round,
                )
            }
            ShellSection.Settings -> {
                val knobs = floatArrayOf(0.68f, 0.36f, 0.56f)
                val ys = floatArrayOf(0.25f, 0.50f, 0.75f)
                for (index in ys.indices) {
                    val y = size.height * ys[index]
                    drawLine(
                        color,
                        Offset(size.width * 0.14f, y),
                        Offset(size.width * 0.86f, y),
                        stroke.width,
                        StrokeCap.Round,
                    )
                    drawCircle(color, radius = stroke.width * 1.5f, center = Offset(size.width * knobs[index], y))
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
                large = true,
                shape = androidx.compose.foundation.shape.RoundedCornerShape(FbngDimens.panelRadius),
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
