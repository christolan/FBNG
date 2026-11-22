package com.xiaojiwei.fbng.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.xiaojiwei.fbng.R
import com.xiaojiwei.fbng.game.GameProgressStore
import com.xiaojiwei.fbng.library.AppStorage
import com.xiaojiwei.fbng.library.LibraryEntry

@Composable
internal fun GameLaunchPage(
    entry: LibraryEntry,
    preview: GamePreviewArtwork,
    fold: FoldSnapshot?,
    favorite: Boolean,
    busy: Boolean,
    launchError: Boolean,
    onPlay: (Int?) -> Unit,
    onToggleFavorite: () -> Unit,
    onConfigureFolders: () -> Unit,
    onBack: () -> Unit,
    initialSelectedSlot: Int? = null,
) {
    val context = LocalContext.current
    val store = remember(context, entry.game.name) {
        GameProgressStore(AppStorage(context.filesDir).stateDir(entry.game.name))
    }
    GameProgressPage(
        entry = entry, preview = preview, fold = fold, favorite = favorite, store = store,
        busy = busy, ready = true,
        message = when {
            launchError -> stringResource(R.string.preparing_game_failed)
            busy -> stringResource(R.string.preparing_game)
            else -> ""
        },
        messageIsError = launchError,
        onPrimary = onPlay, onConfigureFolders = onConfigureFolders,
        initialSelectedSlot = initialSelectedSlot,
        onToggleFavorite = onToggleFavorite, onBack = onBack,
    )
}
