package com.xiaojiwei.fbng.ui

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.xiaojiwei.fbng.R
import com.xiaojiwei.fbng.game.SaveSlotInfo
import com.xiaojiwei.fbng.game.SaveStateStore
import com.xiaojiwei.fbng.ui.theme.FbngButton
import com.xiaojiwei.fbng.ui.theme.FbngButtonKind
import com.xiaojiwei.fbng.ui.theme.FbngDimens
import com.xiaojiwei.fbng.ui.theme.fbngFocusTarget
import java.text.DateFormat
import java.util.Date
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal enum class SaveSlotMode { Save, Load }

@Composable
internal fun SaveSlotsDialog(
    store: SaveStateStore,
    mode: SaveSlotMode,
    busy: Boolean,
    message: String,
    revision: Int,
    onChoose: (Int) -> Unit,
    onCreate: () -> Unit,
    onDismiss: () -> Unit,
) {
    var slots by remember(store) { mutableStateOf<List<SaveSlotInfo>?>(null) }
    var listedRevision by remember(store) { mutableStateOf<Int?>(null) }
    var listingError by remember(store) { mutableStateOf(false) }
    var pending by remember { mutableStateOf<SaveSlotInfo?>(null) }
    val current = slots
    val manualSlots = current?.drop(1).orEmpty()
    val slotsFull = manualSlots.size >= SaveStateStore.MAX_MANUAL_SLOTS
    val slotControlsEnabled = !busy && !listingError && listedRevision == revision
    val canCreate = mode == SaveSlotMode.Save && slotControlsEnabled && !slotsFull
    val manualListState = rememberLazyListState()
    val requesters = remember { mutableMapOf<String, FocusRequester>() }
    fun requester(key: String) = requesters.getOrPut(key) { FocusRequester() }
    var focusedKey by remember { mutableStateOf<String?>(null) }
    fun target(key: String) = Modifier.focusRequester(requester(key))
        .onFocusChanged { if (it.isFocused) focusedKey = key }

    LaunchedEffect(store, revision) {
        listingError = false
        try {
            slots = withContext(Dispatchers.IO) { store.listSlots() }
            listedRevision = revision
        } catch (_: java.io.IOException) {
            listingError = true
        }
    }
    LaunchedEffect(current, pending, busy, listingError, slotControlsEnabled) {
        if (pending != null || busy || (!slotControlsEnabled && !listingError)) return@LaunchedEffect
        val defaultTarget = when {
            listingError -> "close"
            canCreate -> "new"
            mode == SaveSlotMode.Load && current?.first()?.available == true -> "slot0"
            manualSlots.isNotEmpty() -> "slot${manualSlots[manualListState.firstVisibleItemIndex.coerceAtMost(manualSlots.lastIndex)].id}"
            else -> "close"
        }
        val initial = focusedKey?.takeUnless { listingError || (it == "new" && !canCreate) } ?: defaultTarget
        // LazyRow may need a frame to attach the requested card.
        repeat(4) {
            withFrameNanos { }
            val focused = runCatching { requester(initial).requestFocus() }.getOrDefault(false)
            if (focused) return@LaunchedEffect
        }
        requester("close").requestFocus()
    }

    Dialog(
        onDismissRequest = { if (!busy) onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnBackPress = !busy,
            dismissOnClickOutside = !busy),
    ) {
        Surface(
            modifier = Modifier.widthIn(max = FbngDimens.saveDialogWidth).fillMaxWidth(.94f).fillMaxHeight(.94f)
                .onKeyEvent { event ->
                    if (event.key == Key.ButtonB && event.type == KeyEventType.KeyUp) {
                        if (!busy) onDismiss()
                        true
                    } else false
                },
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surface,
        ) {
            Column(Modifier.padding(FbngDimens.space16), verticalArrangement = Arrangement.spacedBy(FbngDimens.space8)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(FbngDimens.space16)) {
                    Text(stringResource(if (mode == SaveSlotMode.Save) R.string.save_slots_title else R.string.load_slots_title),
                        modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
                    FbngButton(onClick = onDismiss, enabled = !busy, kind = FbngButtonKind.Neutral,
                        modifier = target("close")) { Text(stringResource(R.string.close)) }
                }
                if (mode == SaveSlotMode.Save) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(FbngDimens.space8),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            if (slotsFull) stringResource(R.string.manual_slots_full, SaveStateStore.MAX_MANUAL_SLOTS)
                            else stringResource(R.string.save_slots_hint),
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        FbngButton(onClick = onCreate, enabled = canCreate, modifier = target("new")) {
                            Text(stringResource(R.string.slot_new), style = MaterialTheme.typography.labelLarge)
                        }
                    }
                } else {
                    Text(stringResource(R.string.load_slots_hint), style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (current == null || listingError) {
                    Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Text(stringResource(if (listingError) R.string.slots_list_failed else R.string.slots_loading))
                    }
                } else {
                    Row(Modifier.weight(1f).fillMaxWidth().padding(FbngDimens.space4),
                        horizontalArrangement = Arrangement.spacedBy(FbngDimens.space16)) {
                        val automatic = current.first()
                        SlotCard(store, automatic, enabled = slotControlsEnabled && mode == SaveSlotMode.Load && automatic.available,
                            modifier = target("slot0"), onClick = { pending = automatic })
                        LazyRow(Modifier.weight(1f).fillMaxHeight(),
                            state = manualListState,
                            horizontalArrangement = Arrangement.spacedBy(FbngDimens.space12)) {
                            items(manualSlots, key = { it.id }) { slot ->
                                SlotCard(store, slot, enabled = slotControlsEnabled, modifier = target("slot${slot.id}"),
                                    onClick = { pending = slot })
                            }
                            if (mode == SaveSlotMode.Load && manualSlots.isEmpty()) item(key = "empty") {
                                Box(Modifier.width(FbngDimens.saveCardWidth).fillMaxHeight(), contentAlignment = Alignment.Center) {
                                    Text(stringResource(R.string.manual_slots_empty), style = MaterialTheme.typography.bodyMedium)
                                }
                            }
                        }
                    }
                }
                if (message.isNotEmpty()) Text(message, style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
    }

    pending?.let { slot ->
        val cancel = remember { FocusRequester() }
        LaunchedEffect(slot.id) { cancel.requestFocus() }
        AlertDialog(
            modifier = Modifier.onKeyEvent { event ->
                if (event.key == Key.ButtonB && event.type == KeyEventType.KeyUp) {
                    pending = null
                    true
                } else false
            },
            onDismissRequest = { pending = null },
            title = { Text(stringResource(if (mode == SaveSlotMode.Save) R.string.overwrite_title else R.string.load_confirm_title)) },
            text = { Text(stringResource(if (mode == SaveSlotMode.Save) R.string.overwrite_body else R.string.load_confirm_body,
                slotName(slot))) },
            confirmButton = {
                FbngButton(onClick = {
                    pending = null
                    onChoose(slot.id)
                }) { Text(stringResource(if (mode == SaveSlotMode.Save) R.string.overwrite else R.string.load)) }
            },
            dismissButton = {
                FbngButton(onClick = { pending = null }, kind = FbngButtonKind.Neutral,
                    modifier = Modifier.focusRequester(cancel)) { Text(stringResource(R.string.cancel)) }
            },
        )
    }
}

@Composable
private fun slotName(slot: SaveSlotInfo): String = stringResource(
    if (slot.automatic) R.string.slot_automatic else R.string.slot_number, slot.id)

@Composable
private fun SlotCard(
    store: SaveStateStore,
    slot: SaveSlotInfo,
    enabled: Boolean,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    val cover by produceState<ImageBitmap?>(null, store, slot.id, slot.savedAtEpochMs) {
        value = withContext(Dispatchers.IO) {
            runCatching { store.readCover(slot.id)?.let { BitmapFactory.decodeByteArray(it, 0, it.size)?.asImageBitmap() } }
                .getOrNull()
        }
    }
    val time = remember(slot.savedAtEpochMs) {
        if (slot.savedAtEpochMs > 0) DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
            .format(Date(slot.savedAtEpochMs)) else ""
    }
    Column(
        modifier.width(FbngDimens.saveCardWidth).fillMaxHeight()
            .fbngFocusTarget(onClick = onClick, enabled = enabled,
                container = MaterialTheme.colorScheme.surfaceContainerHigh,
                restingStroke = if (slot.automatic) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.outlineVariant)
            .padding(FbngDimens.space8),
        verticalArrangement = Arrangement.spacedBy(FbngDimens.space4),
    ) {
        Box(Modifier.weight(1f).fillMaxWidth().heightIn(min = FbngDimens.buttonHeight)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest), contentAlignment = Alignment.Center) {
            val bitmap = cover
            if (bitmap != null) Image(bitmap, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
            else Text(stringResource(if (slot.available) R.string.slot_no_cover else R.string.slot_no_save),
                style = MaterialTheme.typography.bodyMedium)
        }
        Text(slotName(slot), style = MaterialTheme.typography.titleMedium, maxLines = 1)
        if (slot.automatic) Text(stringResource(R.string.slot_read_only), color = MaterialTheme.colorScheme.secondary,
            style = MaterialTheme.typography.bodyMedium)
        if (time.isNotEmpty() && slot.available) Text(time, style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
    }
}
