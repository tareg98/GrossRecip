package com.example.grossrecipes.ui.lists

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
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
    var showNewListDialog by remember { mutableStateOf(false) }
    var shareDialogListId by remember { mutableStateOf<String?>(null) }

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

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showNewListDialog = true },
                shape = CircleShape,
                containerColor = Accent,
                modifier = Modifier.size(56.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = "New list", tint = Background)
            }
        }
    ) { innerPadding ->
        LazyColumn(
            state = lazyListState,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
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
                    SyncPill(isOnline = isOnline)
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
                        onShareClick = { shareDialogListId = list.id }
                    )
                }
            }

            item { Spacer(Modifier.height(80.dp)) }
        }
    }

    if (showNewListDialog) {
        NewListDialog(
            onDismiss = { showNewListDialog = false },
            onCreate = { name, color, sharedUsername ->
                viewModel.createList(name, color, sharedUsername)
                showNewListDialog = false
            }
        )
    }

    val shareDialogList = lists.find { it.id == shareDialogListId }
    if (shareDialogList != null) {
        ShareDialog(
            list = shareDialogList,
            onDismiss = { shareDialogListId = null },
            onShare = { username -> viewModel.shareList(shareDialogList.id, username) },
            onUnshare = { username -> viewModel.unshareList(shareDialogList.id, username) },
            onSharedExternally = { viewModel.markSharedExternally(shareDialogList.id) }
        )
    }
}

@Composable
private fun SyncPill(isOnline: Boolean) {
    val bg = if (isOnline) Accent2Light else SyncOfflineBg
    val fg = if (isOnline) Accent2Deep else SyncOfflineText
    val dotColor = if (isOnline) Accent2 else Accent

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
            text = if (isOnline) "Synced" else "Offline",
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
    onShareClick: () -> Unit
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
            Text(
                text = list.name,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false)
            )
            if (list.sharedWith.isNotEmpty() || list.sharedExternally) {
                Spacer(Modifier.width(8.dp))
                SharedTag()
            }
            Spacer(Modifier.weight(1f))
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
            Column {
                uncheckedItems.forEachIndexed { index, item ->
                    ItemRow(
                        item = item,
                        checkedStyle = false,
                        onToggleChecked = { onToggleChecked(item.id, !item.checked) },
                        onDelete = { onDeleteItem(item.id) }
                    )
                    if (index != uncheckedItems.lastIndex) {
                        HorizontalDivider(color = DividerLight, thickness = 1.dp)
                    }
                }
            }
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
            keyboardActions = KeyboardActions(onDone = {
                if (text.isNotBlank()) {
                    onAddItem(text.trim())
                    text = ""
                }
            }),
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

@Composable
private fun ItemRow(
    item: GroceryItem,
    checkedStyle: Boolean,
    onToggleChecked: () -> Unit,
    onDelete: () -> Unit
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
            modifier = Modifier.weight(1f)
        )
        if (!checkedStyle) {
            IconButton(onClick = onDelete, modifier = Modifier.size(28.dp)) {
                Icon(Icons.Default.Close, contentDescription = "Delete item", modifier = Modifier.size(16.dp))
            }
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