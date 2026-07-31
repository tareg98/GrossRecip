package com.example.grossrecipes.ui.lists

import androidx.compose.ui.graphics.Color
import com.example.grossrecipes.ui.theme.Accent
import com.example.grossrecipes.ui.theme.Accent2
import com.example.grossrecipes.ui.theme.ListDustyBlue
import com.example.grossrecipes.ui.theme.ListOchre
import com.example.grossrecipes.ui.theme.ListPlum
import java.util.UUID

data class GroceryItem(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val checked: Boolean = false
)

data class GroceryList(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val color: Color? = null,
    val sharedWith: List<String> = emptyList(),
    val sharedExternally: Boolean = false,
    val items: List<GroceryItem> = emptyList(),
    val checkedSectionExpanded: Boolean = false,
    // Dividers are a purely local organizational aid - never synced, so each
    // device can lay a list out differently. topDivider is the one at the
    // very top (before the first item); everything else is anchored to the
    // stable id of the item right above it, not a raw index - an index would
    // silently point at the wrong gap the moment an item above it is added,
    // checked off, or removed.
    val topDivider: Boolean = false,
    val dividerAfterItemIds: Set<String> = emptySet()
)

val listColorPalette: List<Color> = listOf(Accent, Accent2, ListOchre, ListDustyBlue, ListPlum)