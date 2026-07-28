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
    val checkedSectionExpanded: Boolean = false
)

val listColorPalette: List<Color> = listOf(Accent, Accent2, ListOchre, ListDustyBlue, ListPlum)