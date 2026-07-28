package com.example.grossrecipes.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.example.grossrecipes.R

val CaprasimoFamily = FontFamily(
    Font(R.font.caprasimo_regular, FontWeight.Normal)
)

val FigtreeFamily = FontFamily(
    Font(R.font.figtree_regular, FontWeight.Normal),
    Font(R.font.figtree_medium, FontWeight.Medium),
    Font(R.font.figtree_semibold, FontWeight.SemiBold),
    Font(R.font.figtree_bold, FontWeight.Bold)
)

val Typography = Typography(
    titleLarge = TextStyle(
        fontFamily = CaprasimoFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 24.sp
    ),
    titleMedium = TextStyle(
        fontFamily = CaprasimoFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 19.sp
    ),
    titleSmall = TextStyle(
        fontFamily = CaprasimoFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp
    ),
    // These three (bodyLarge especially, since it's what text fields use for
    // typed text) previously had no explicit lineHeight - Compose then falls
    // back to the custom font's own reported line metrics, which for Figtree
    // were too tight and clipped the bottom of descenders (g/y/p/j) in every
    // OutlinedTextField in the app. Giving each an explicit lineHeight fixes it.
    bodyLarge = TextStyle(
        fontFamily = FigtreeFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 15.5.sp,
        lineHeight = 22.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = FigtreeFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp
    ),
    bodySmall = TextStyle(
        fontFamily = FigtreeFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 17.sp
    ),
    labelLarge = TextStyle(
        fontFamily = FigtreeFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp
    ),
    labelMedium = TextStyle(
        fontFamily = FigtreeFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 12.sp
    ),
    labelSmall = TextStyle(
        fontFamily = FigtreeFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 11.sp
    )
)