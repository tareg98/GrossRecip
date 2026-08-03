package com.example.grossrecipes.ui.lists

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.zIndex
import kotlin.math.abs
import kotlin.math.roundToInt
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import com.example.grossrecipes.navigation.LocalBottomBarHeight
import com.example.grossrecipes.ui.theme.Accent
import com.example.grossrecipes.ui.theme.Accent2
import com.example.grossrecipes.ui.theme.Accent2Deep
import com.example.grossrecipes.ui.theme.Accent2Light
import com.example.grossrecipes.ui.theme.Background
import com.example.grossrecipes.ui.theme.CardShape
import com.example.grossrecipes.ui.theme.DisabledText
import com.example.grossrecipes.ui.theme.DividerLight
import com.example.grossrecipes.ui.theme.FaintText
import com.example.grossrecipes.ui.theme.MutedText
import com.example.grossrecipes.ui.theme.PillShape
import com.example.grossrecipes.ui.theme.PrimaryText
import com.example.grossrecipes.ui.theme.SyncOfflineBg
import com.example.grossrecipes.ui.theme.SyncOfflineText
import com.example.grossrecipes.ui.theme.Surface

@Composable
fun ListsScreen(viewModel: ListsViewModel = viewModel()) {
    val lists by viewModel.lists.collectAsState()
    val knownItemNames by viewModel.knownItemNames.collectAsState()
    val isOnline by viewModel.isOnline.collectAsState()
    val isSyncing by viewModel.isSyncing.collectAsState()
    val lastSyncError by viewModel.lastSyncError.collectAsState()
    val pendingChangeCount by viewModel.pendingChangeCount.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()
    var showNewListDialog by remember { mutableStateOf(false) }
    var shareDialogListId by remember { mutableStateOf<String?>(null) }

    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(errorMessage) {
        errorMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.errorShown()
        }
    }

    // Local drag order: while dragging we reorder this copy instantly for a
    // smooth visual, then push the final order up to the ViewModel/backend
    // when the finger lifts. It re-syncs with the real data whenever that
    // changes (e.g. after a pull from the server).
    var orderedLists by remember { mutableStateOf(lists) }
    LaunchedEffect(lists) { orderedLists = lists }

    val lazyListState = rememberLazyListState()
    val reorderableState = rememberReorderableLazyListState(lazyListState) { from, to ->
        // Index 0 in the LazyColumn is the "My Lists" header, so list cards
        // start at index 1 - shift by -1 to get the real position in orderedLists.
        val fromIndex = from.index - 1
        val toIndex = to.index - 1
        if (fromIndex in orderedLists.indices && toIndex in orderedLists.indices) {
            orderedLists = orderedLists.toMutableList().apply {
                add(toIndex, removeAt(fromIndex))
            }
        }
    }

    // No Scaffold here - Scaffold reserves its own top+bottom system bar
    // insets via innerPadding regardless of whether bottomBar/topBar slots
    // are used, and that was stacking on top of AppNavGraph's own insets one
    // level up. That double-counted bottom inset is what made the floating
    // nav bar look huge (extra blank block under it) and squeezed this
    // screen's own box short, which is what put the FAB in the middle of the
    // screen and clipped list items right at the boundary while typing.
    // A plain Box with the FAB/snackbar/scroll-end clearance pinned to the
    // bar's real measured height (LocalBottomBarHeight, set in AppNavGraph)
    // replaces all of that - a guessed 96dp fell short on this device
    // because the bar's actual height (its own content plus the gesture/nav
    // bar inset it pads itself by) came out taller than that guess, so the
    // bar covered the bottom of the FAB and ate into the list's last bit of
    // scrollable space.
    val bottomBarHeight = LocalBottomBarHeight.current
    val bottomClearance = bottomBarHeight + 16.dp

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            state = lazyListState,
            modifier = Modifier
                .fillMaxSize()
                .imePadding()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("My Lists", style = MaterialTheme.typography.titleLarge)
                    SyncPill(
                        isOnline = isOnline,
                        isSyncing = isSyncing,
                        lastSyncError = lastSyncError,
                        pendingChangeCount = pendingChangeCount
                    )
                }
            }

            itemsIndexed(orderedLists, key = { _, list -> list.id }) { _, list ->
                ReorderableItem(reorderableState, key = list.id) { _ ->
                    ListCard(
                        list = list,
                        knownItemNames = knownItemNames,
                        dragHandleModifier = Modifier.draggableHandle(
                            onDragStopped = { viewModel.reorderLists(orderedLists.map { it.id }) }
                        ),
                        onDelete = { viewModel.deleteList(list.id) },
                        onColorChange = { newColor -> viewModel.setColor(list.id, newColor) },
                        onAddItem = { itemName -> viewModel.addItem(list.id, itemName) },
                        onToggleChecked = { itemId, newChecked -> viewModel.toggleChecked(itemId, newChecked) },
                        onDeleteItem = { itemId -> viewModel.deleteItem(itemId) },
                        onToggleCheckedSectionExpanded = {
                            viewModel.toggleCheckedSectionExpanded(list.id, !list.checkedSectionExpanded)
                        },
                        onShareClick = { shareDialogListId = list.id },
                        onToggleDivider = { gapIndex -> viewModel.toggleDivider(list.id, gapIndex) },
                        onReorderItems = { orderedItemIds -> viewModel.reorderItems(orderedItemIds) }
                    )
                }
            }

            item { Spacer(Modifier.height(bottomClearance)) }
        }

        FloatingActionButton(
            onClick = { showNewListDialog = true },
            shape = CircleShape,
            containerColor = Accent,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 16.dp, bottom = bottomClearance)
                .size(56.dp)
        ) {
            Icon(Icons.Default.Add, contentDescription = "New list", tint = Background)
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = bottomClearance)
        ) { data ->
            Snackbar(snackbarData = data)
        }
    }

    if (showNewListDialog) {
        NewListDialog(
            onDismiss = { showNewListDialog = false },
            onLookupUsername = { typed -> viewModel.lookupUsername(typed) },
            onCreate = { name, color, sharedWithUserId ->
                viewModel.createList(name, color, sharedWithUserId)
                showNewListDialog = false
            }
        )
    }

    val shareDialogList = lists.find { it.id == shareDialogListId }
    if (shareDialogList != null) {
        ShareDialog(
            list = shareDialogList,
            onDismiss = { shareDialogListId = null },
            onLookupUsername = { typed -> viewModel.lookupUsername(typed) },
            onLookupUserId = { userId -> viewModel.lookupUserId(userId) },
            // ListShared now carries the recipient's userId, not their
            // username (see ListsRepository.shareList) - the username here
            // is only used to clear the text field / show messages, the
            // resolved userId is what actually goes to the share call.
            onShare = { _, userId -> viewModel.shareList(shareDialogList.id, userId) },
            onUnshare = { userId -> viewModel.unshareList(shareDialogList.id, userId) },
            onSharedExternally = { viewModel.markSharedExternally(shareDialogList.id) }
        )
    }
}

@Composable
private fun SyncPill(isOnline: Boolean, isSyncing: Boolean, lastSyncError: String?, pendingChangeCount: Int) {
    // Same logic as SettingsScreen's status text - connectivity alone isn't
    // "synced" (the server can reject requests, e.g. an expired token), and
    // neither is an empty outbox right after login or after a failed pull
    // with nothing local to push (lastSyncError catches that one). Full
    // error detail lives in Settings - this pill just needs to say "wrong."
    val label = when {
        !isOnline -> "Offline"
        isSyncing -> "Syncing…"
        lastSyncError != null -> "Sync error"
        pendingChangeCount > 0 -> "Pending"
        else -> "Synced"
    }
    val fullySynced = isOnline && !isSyncing && lastSyncError == null && pendingChangeCount == 0
    val bg = if (fullySynced) Accent2Light else SyncOfflineBg
    val fg = if (fullySynced) Accent2Deep else SyncOfflineText
    val dotColor = if (fullySynced) Accent2 else Accent

    Row(
        modifier = Modifier
            .clip(PillShape)
            .background(bg)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(dotColor)
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = fg
        )
    }
}

@Composable
private fun ListCard(
    list: GroceryList,
    knownItemNames: List<String>,
    dragHandleModifier: Modifier,
    onDelete: () -> Unit,
    onColorChange: (Color?) -> Unit,
    onAddItem: (String) -> Unit,
    onToggleChecked: (String, Boolean) -> Unit,
    onDeleteItem: (String) -> Unit,
    onToggleCheckedSectionExpanded: () -> Unit,
    onShareClick: () -> Unit,
    onToggleDivider: (gapIndex: Int) -> Unit,
    onReorderItems: (orderedItemIds: List<String>) -> Unit
) {
    val cardBg = list.color?.let { lerp(Surface, it, 0.22f) } ?: Surface
    val fieldBg = list.color?.let { lerp(Background, it, 0.10f) } ?: Background

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(CardShape)
            .background(cardBg)
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ListAvatar(list = list, onColorChange = onColorChange)
            Spacer(Modifier.width(10.dp))
            // Title gets its own column with the full weight to itself - the
            // "Shared" tag used to sit in this same row competing for space,
            // which squeezed the title down to just a few letters before
            // ellipsis on anything but a very short name. It now sits on its
            // own line below the title instead, never touching its width.
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = list.name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (list.sharedWith.isNotEmpty() || list.sharedExternally) {
                    Spacer(Modifier.height(4.dp))
                    SharedTag()
                }
            }
            IconButton(onClick = onShareClick) {
                Icon(Icons.Default.Share, contentDescription = "Share list")
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = "Delete list")
            }
            Icon(
                imageVector = Icons.Default.DragHandle,
                contentDescription = "Drag to reorder",
                tint = MutedText,
                modifier = dragHandleModifier.padding(start = 4.dp)
            )
        }

        Spacer(Modifier.height(12.dp))

        AddItemField(fieldBg = fieldBg, knownItemNames = knownItemNames, onAddItem = onAddItem)

        val uncheckedItems = list.items.filter { !it.checked }
        val checkedItems = list.items.filter { it.checked }

        if (uncheckedItems.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            UncheckedItemsSection(
                items = uncheckedItems,
                dividerAtGapIndices = list.dividerAtGapIndices,
                onToggleChecked = onToggleChecked,
                onDeleteItem = onDeleteItem,
                onToggleDivider = onToggleDivider,
                onReorderItems = onReorderItems
            )
        }

        if (checkedItems.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            CheckedSection(
                items = checkedItems,
                expanded = list.checkedSectionExpanded,
                onToggleExpanded = onToggleCheckedSectionExpanded,
                onToggleChecked = onToggleChecked,
                onDeleteItem = onDeleteItem
            )
        }
    }
}

@Composable
private fun ListAvatar(list: GroceryList, onColorChange: (Color?) -> Unit) {
    var showPicker by remember { mutableStateOf(false) }
    val avatarColor = list.color ?: Accent2

    Box {
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(CircleShape)
                .background(avatarColor)
                .clickable { showPicker = true },
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = list.name.take(1).uppercase(),
                color = Background,
                style = MaterialTheme.typography.labelLarge
            )
        }

        if (showPicker) {
            Popup(
                alignment = Alignment.TopStart,
                offset = IntOffset(0, 110),
                onDismissRequest = { showPicker = false }
            ) {
                Row(
                    modifier = Modifier
                        .clip(CardShape)
                        .background(Surface)
                        .padding(10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listColorPalette.forEach { color ->
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(color)
                                .clickable {
                                    onColorChange(color)
                                    showPicker = false
                                }
                        )
                    }
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .drawBehind {
                                drawCircle(
                                    color = MutedText,
                                    style = Stroke(
                                        width = 2.dp.toPx(),
                                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 4f))
                                    )
                                )
                            }
                            .clickable {
                                onColorChange(null)
                                showPicker = false
                            }
                    )
                }
            }
        }
    }
}

@Composable
private fun SharedTag() {
    Row(
        modifier = Modifier
            .clip(PillShape)
            .background(Accent2Light)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Default.Share,
            contentDescription = null,
            tint = Accent2Deep,
            modifier = Modifier.size(12.dp)
        )
        Spacer(Modifier.width(4.dp))
        Text("Shared", style = MaterialTheme.typography.labelSmall, color = Accent2Deep)
    }
}

@Composable
private fun AddItemField(fieldBg: Color, knownItemNames: List<String>, onAddItem: (String) -> Unit) {
    var text by remember { mutableStateOf("") }
    var focused by remember { mutableStateOf(false) }

    val suggestions = remember(text, knownItemNames) {
        if (text.isBlank()) emptyList()
        else knownItemNames.filter { it.contains(text, ignoreCase = true) }.take(5)
    }

    val submit = {
        if (text.isNotBlank()) {
            onAddItem(text.trim())
            text = ""
        }
    }

    Column {
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            placeholder = { Text("Add an item…") },
            singleLine = true,
            shape = PillShape,
            colors = OutlinedTextFieldDefaults.colors(
                unfocusedContainerColor = fieldBg,
                focusedContainerColor = fieldBg,
                unfocusedBorderColor = FaintText,
                focusedBorderColor = Accent
            ),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { submit() }),
            // A visible button, not just the keyboard's Done/Enter key - not
            // every keyboard makes that key obviously an "add" action.
            trailingIcon = {
                IconButton(onClick = { submit() }, enabled = text.isNotBlank()) {
                    Icon(Icons.Default.Add, contentDescription = "Add item")
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .onFocusChanged { focused = it.isFocused }
        )

        if (suggestions.isNotEmpty() && focused) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(CardShape)
                    .background(Surface)
            ) {
                suggestions.forEach { suggestion ->
                    Text(
                        text = suggestion,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onAddItem(suggestion)
                                text = ""
                            }
                            .padding(horizontal = 16.dp, vertical = 10.dp)
                    )
                }
            }
        }
    }
}

/**
 * Renders the unchecked items with any dividers interleaved between them,
 * and owns three interactions: a two-finger pinch spanning a gap between
 * adjacent items to toggle a divider there, a long-press on an item's name
 * for a divider menu (the discoverable fallback for the pinch), and a
 * dedicated drag handle to move an item to any position - hold and drag it
 * up or down past its neighbors.
 *
 * Gap `i` (0..items.lastIndex) is the space right above items[i] - gap 0 is
 * the very top of the list, gap i>0 is between items[i - 1] and items[i].
 * See [GroceryList.dividerAtGapIndices] for why this is a raw position
 * rather than something tied to a specific item.
 */
@Composable
private fun UncheckedItemsSection(
    items: List<GroceryItem>,
    dividerAtGapIndices: Set<Int>,
    onToggleChecked: (String, Boolean) -> Unit,
    onDeleteItem: (String) -> Unit,
    onToggleDivider: (gapIndex: Int) -> Unit,
    onReorderItems: (orderedItemIds: List<String>) -> Unit
) {
    // Local drag order - same pattern as ListsScreen's whole-list reordering:
    // reorder this copy instantly for a smooth visual while dragging, push
    // the final order up once the finger lifts, and re-sync with the real
    // data whenever that changes underneath (an item added/checked off/
    // deleted, or a pull from the server).
    var orderedItems by remember { mutableStateOf(items) }
    var draggingIndex by remember { mutableStateOf<Int?>(null) }
    LaunchedEffect(items) {
        // Skip the re-sync while a drag is actively in progress - a sync
        // landing mid-gesture shouldn't yank the row out from under the
        // finger still dragging it.
        if (draggingIndex == null) orderedItems = items
    }

    // Each item row's current top-Y, in this Column's own coordinate space -
    // the same space the pinch gesture's pointer events arrive in - captured
    // live off actual layout so the gesture always lines up with what's on
    // screen, even as dividers add or remove rows above a given item.
    val gapPositions = remember { mutableStateMapOf<Int, Float>() }
    var menuGapIndex by remember { mutableStateOf<Int?>(null) }
    var dragOffsetPx by remember { mutableStateOf(0f) }

    // pointerInput below only restarts when the item ids themselves change
    // (added/removed) - but dividerAtGapIndices changes on every toggle,
    // without the id SET changing at all. Without rememberUpdatedState, the
    // pinch gesture's long-lived coroutine would keep reading whatever this
    // was the moment it last launched, so a divider toggled via the
    // long-press menu could look stale to the very next pinch gesture.
    val currentDividerAtGapIndices = rememberUpdatedState(dividerAtGapIndices)

    fun hasDividerAt(gapIndex: Int): Boolean = gapIndex in currentDividerAtGapIndices.value

    // A rough, fixed estimate of a row's height rather than something
    // measured live - good enough to decide "has this drag crossed into the
    // next row" and simpler than keeping per-row heights in sync mid-drag.
    val density = LocalDensity.current
    val estimatedRowHeightPx = remember(density) { with(density) { 48.dp.toPx() } }

    Column(
        modifier = Modifier.pointerInput(items.map { it.id }) {
            detectDividerPinch(
                gapPositions = { gapPositions },
                hasDividerAt = ::hasDividerAt,
                onToggle = { gapIndex -> onToggleDivider(gapIndex) }
            )
        }
    ) {
        orderedItems.forEachIndexed { index, item ->
            if (hasDividerAt(index)) {
                DividerRow()
            }
            // Keyed by the item's own stable id, not just its position in
            // this loop - without this, swapping two items during a drag
            // makes Compose treat "slot 2" as still being the same
            // composable it always was, just fed different data, instead of
            // recognizing that the item which WAS at slot 2 has moved to
            // slot 3. That mismatch changes this row's pointerInput key
            // mid-gesture (the item's id at this slot just changed), which
            // cancels the in-flight drag and fires onDragCancel - which is
            // exactly what "pulls, then instantly snaps back" was: the very
            // first swap cancelled the gesture and reset everything.
            key(item.id) {
                val isDragging = draggingIndex == index
                Box(
                    modifier = Modifier
                        .onGloballyPositioned { coordinates ->
                            gapPositions[index] = coordinates.positionInParent().y
                        }
                        .zIndex(if (isDragging) 1f else 0f)
                        .offset { IntOffset(0, if (isDragging) dragOffsetPx.roundToInt() else 0) }
                ) {
                    ItemRow(
                        item = item,
                        checkedStyle = false,
                        onToggleChecked = { onToggleChecked(item.id, !item.checked) },
                        onDelete = { onDeleteItem(item.id) },
                        onLongPress = { menuGapIndex = index },
                        dragHandleModifier = Modifier.pointerInput(item.id) {
                            // Plain detectDragGestures, not the
                            // "AfterLongPress" variant - dragging the handle
                            // now starts moving the item the instant you
                            // touch and move, no hold-and-wait first. The
                            // long-press-to-hold behavior stays only on the
                            // item's name text (see onLongPress below /
                            // ItemRow's combinedClickable), reserved purely
                            // for opening the divider menu.
                            detectDragGestures(
                                onDragStart = {
                                    draggingIndex = orderedItems.indexOfFirst { it.id == item.id }
                                    dragOffsetPx = 0f
                                },
                                onDrag = { change, dragAmount ->
                                    change.consume()
                                    dragOffsetPx += dragAmount.y

                                    // Move one slot at a time whenever the drag
                                    // crosses half a row past its current spot,
                                    // compensating the offset by exactly one row
                                    // each time so the item doesn't visually jump -
                                    // it just keeps tracking the finger smoothly
                                    // across however many rows it passes.
                                    while (dragOffsetPx > estimatedRowHeightPx / 2 &&
                                        (draggingIndex ?: 0) < orderedItems.lastIndex
                                    ) {
                                        val from = draggingIndex ?: return@detectDragGestures
                                        val to = from + 1
                                        orderedItems = orderedItems.toMutableList().apply {
                                            add(to, removeAt(from))
                                        }
                                        draggingIndex = to
                                        dragOffsetPx -= estimatedRowHeightPx
                                    }
                                    while (dragOffsetPx < -estimatedRowHeightPx / 2 &&
                                        (draggingIndex ?: 0) > 0
                                    ) {
                                        val from = draggingIndex ?: return@detectDragGestures
                                        val to = from - 1
                                        orderedItems = orderedItems.toMutableList().apply {
                                            add(to, removeAt(from))
                                        }
                                        draggingIndex = to
                                        dragOffsetPx += estimatedRowHeightPx
                                    }
                                },
                                onDragEnd = {
                                    onReorderItems(orderedItems.map { it.id })
                                    draggingIndex = null
                                    dragOffsetPx = 0f
                                },
                                onDragCancel = {
                                    // Discard the in-progress reorder - re-sync
                                    // back to the real order next recomposition
                                    // via the LaunchedEffect(items) above.
                                    orderedItems = items
                                    draggingIndex = null
                                    dragOffsetPx = 0f
                                }
                            )
                        }
                    )
                }
            }
            if (index != orderedItems.lastIndex) {
                HorizontalDivider(color = DividerLight, thickness = 1.dp)
            }
        }
    }

    val gapIndex = menuGapIndex
    if (gapIndex != null) {
        val exists = hasDividerAt(gapIndex)
        // Without an explicit alignment/offset, Popup anchors itself to the
        // top-left of the whole card instead of the gap you actually
        // long-pressed on - which is why it was showing up on top of the
        // card's header row instead of next to the item you were touching.
        // gapPositions already tracks each item's real on-screen Y (recorded
        // for the pinch gesture), so reuse it here to land the menu in the
        // right spot.
        Popup(
            alignment = Alignment.TopStart,
            offset = IntOffset(0, gapPositions[gapIndex]?.roundToInt() ?: 0),
            onDismissRequest = { menuGapIndex = null }
        ) {
            Box(
                modifier = Modifier
                    .clip(CardShape)
                    .background(Surface)
                    .border(1.dp, DividerLight, CardShape)
            ) {
                Text(
                    text = if (exists) "Remove divider above" else "Add divider above",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier
                        .clickable {
                            onToggleDivider(gapIndex)
                            menuGapIndex = null
                        }
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                )
            }
        }
    }
}

/**
 * Watches for exactly two fingers down over this Column and measures how the
 * vertical distance between them changes from where they started - spreading
 * them apart past [thresholdPx] inserts a divider at whichever gap between
 * items they're centered over; pinching them back together past that same
 * threshold removes one that's already there. Locks onto the gap it started
 * over for the rest of the gesture (so drifting fingers can't jump to a
 * different gap mid-pinch) and only fires once per gesture.
 */
private suspend fun PointerInputScope.detectDividerPinch(
    gapPositions: () -> Map<Int, Float>,
    hasDividerAt: (Int) -> Boolean,
    onToggle: (Int) -> Unit
) {
    val thresholdPx = 60.dp.toPx()
    awaitEachGesture {
        awaitFirstDown(requireUnconsumed = false)
        var lockedGap: Int? = null
        var initialDistance: Float? = null
        var handled = false

        while (true) {
            val event = awaitPointerEvent()
            val pressed = event.changes.filter { it.pressed }

            if (pressed.size < 2) {
                if (event.changes.all { !it.pressed }) break
                continue
            }

            val p1 = pressed[0].position
            val p2 = pressed[1].position
            val avgY = (p1.y + p2.y) / 2f
            val distance = abs(p1.y - p2.y)

            if (lockedGap == null) {
                lockedGap = gapPositions().minByOrNull { (_, y) -> abs(y - avgY) }?.key
                initialDistance = distance
            }

            val gap = lockedGap
            val startDistance = initialDistance
            if (gap != null && startDistance != null && !handled) {
                val delta = distance - startDistance
                val dividerExists = hasDividerAt(gap)
                if (!dividerExists && delta > thresholdPx) {
                    onToggle(gap)
                    handled = true
                } else if (dividerExists && delta < -thresholdPx) {
                    onToggle(gap)
                    handled = true
                }
            }

            event.changes.forEach { change ->
                if (change.positionChanged()) change.consume()
            }
        }
    }
}

/** Purely visual - a dashed rule marking a user-placed divider between items. */
@Composable
private fun DividerRow() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .height(1.dp)
            .drawBehind {
                drawLine(
                    color = MutedText,
                    start = Offset(0f, size.height / 2f),
                    end = Offset(size.width, size.height / 2f),
                    strokeWidth = 2.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 6f))
                )
            }
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ItemRow(
    item: GroceryItem,
    checkedStyle: Boolean,
    onToggleChecked: () -> Unit,
    onDelete: () -> Unit,
    onLongPress: (() -> Unit)? = null,
    dragHandleModifier: Modifier? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(24.dp)
                .clip(CircleShape)
                .then(
                    if (checkedStyle) Modifier.background(Accent2)
                    else Modifier.border(2.dp, Accent2, CircleShape)
                )
                .clickable { onToggleChecked() },
            contentAlignment = Alignment.Center
        ) {
            if (checkedStyle) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    tint = Background,
                    modifier = Modifier.size(14.dp)
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Text(
            text = item.name,
            style = MaterialTheme.typography.bodyLarge,
            color = if (checkedStyle) DisabledText else PrimaryText,
            textDecoration = if (checkedStyle) TextDecoration.LineThrough else null,
            modifier = Modifier
                .weight(1f)
                .then(
                    if (onLongPress != null) {
                        Modifier.combinedClickable(onClick = {}, onLongClick = onLongPress)
                    } else {
                        Modifier
                    }
                )
        )
        if (!checkedStyle) {
            IconButton(onClick = onDelete, modifier = Modifier.size(28.dp)) {
                Icon(Icons.Default.Close, contentDescription = "Delete item", modifier = Modifier.size(16.dp))
            }
        }
        if (dragHandleModifier != null) {
            Icon(
                imageVector = Icons.Default.DragHandle,
                contentDescription = "Drag to reorder",
                tint = MutedText,
                modifier = dragHandleModifier
                    .padding(start = 4.dp)
                    .size(20.dp)
            )
        }
    }
}

@Composable
private fun CheckedSection(
    items: List<GroceryItem>,
    expanded: Boolean,
    onToggleExpanded: () -> Unit,
    onToggleChecked: (String, Boolean) -> Unit,
    onDeleteItem: (String) -> Unit
) {
    val rotation by animateFloatAsState(if (expanded) 180f else 0f, label = "chevronRotation")

    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onToggleExpanded() }
                .padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "CHECKED (${items.size})",
                style = MaterialTheme.typography.labelMedium,
                color = MutedText
            )
            Icon(
                imageVector = Icons.Default.KeyboardArrowDown,
                contentDescription = if (expanded) "Collapse" else "Expand",
                modifier = Modifier.rotate(rotation)
            )
        }
        if (expanded) {
            items.forEach { item ->
                ItemRow(
                    item = item,
                    checkedStyle = true,
                    onToggleChecked = { onToggleChecked(item.id, !item.checked) },
                    onDelete = { onDeleteItem(item.id) }
                )
            }
        }
    }
}