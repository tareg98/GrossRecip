package com.example.grossrecipes

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.example.grossrecipes.navigation.AppNavGraph
import com.example.grossrecipes.ui.theme.GrossRecipesTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            GrossRecipesTheme {
                AppNavGraph()
            }
        }
    }
}
