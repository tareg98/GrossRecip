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
    // device can lay a list out differently. Anchored to a raw gap position
    // (0 = above the first item, 1 = between the 1st and 2nd, etc.), not to
    // an item's id - so a divider stays exactly where it was placed as items
    // get dragged around, added, checked off, or removed, instead of
    // following whichever item it was originally next to.
    val dividerAtGapIndices: Set<Int> = emptySet()
)

val listColorPalette: List<Color> = listOf(Accent, Accent2, ListOchre, ListDustyBlue, ListPlum)