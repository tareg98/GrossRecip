package com.example.grossrecipes.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.grossrecipes.data.SessionManager
import com.example.grossrecipes.ui.lists.ListsScreen
import com.example.grossrecipes.ui.login.LoginScreen
import com.example.grossrecipes.ui.login.SignUpScreen
import com.example.grossrecipes.ui.settings.SettingsScreen
import com.example.grossrecipes.ui.theme.AccentDeep
import com.example.grossrecipes.ui.theme.MutedText
import com.example.grossrecipes.ui.theme.Surface

object Routes {
    const val LOGIN = "login"
    const val SIGNUP = "signup"
    const val LISTS = "lists"
    const val SETTINGS = "settings"
}

@Composable
fun AppNavGraph() {
    val context = LocalContext.current
    val sessionManager = remember { SessionManager(context) }

    // Collected continuously, not just read once at launch - a background
    // sync can log us out on its own (ListsApi's Authenticator does this when
    // a stale access token can't be silently refreshed), and this is what
    // lets the app notice that and react instead of leaving the user
    // stranded on a dead screen.
    val session by sessionManager.sessionFlow.collectAsState(initial = null)

    // Still reading the saved session from disk - show nothing for a frame
    // rather than guessing whether the user is logged in.
    val currentSession = session ?: return

    val navController = rememberNavController()
    val startDestination = if (currentSession.isLoggedIn) Routes.LISTS else Routes.LOGIN

    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val showBottomBar = currentRoute == Routes.LISTS || currentRoute == Routes.SETTINGS

    // Only reacts to actually BECOMING logged out while already past the
    // login flow - LoginScreen/SignUpScreen already navigate forward
    // themselves on success, so this only needs to handle the one direction
    // they don't: a session dying out from under a screen that assumed it
    // was still valid.
    LaunchedEffect(currentSession.isLoggedIn) {
        if (!currentSession.isLoggedIn && currentRoute != null &&
            currentRoute != Routes.LOGIN && currentRoute != Routes.SIGNUP
        ) {
            navController.navigate(Routes.LOGIN) {
                popUpTo(0) { inclusive = true }
            }
        }
    }

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                AppBottomNav(navController = navController, currentRoute = currentRoute)
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = startDestination,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(Routes.LOGIN) {
                LoginScreen(
                    onLoginSuccess = {
                        navController.navigate(Routes.LISTS) {
                            popUpTo(Routes.LOGIN) { inclusive = true }
                        }
                    },
                    onNavigateToSignUp = {
                        navController.navigate(Routes.SIGNUP)
                    }
                )
            }
            composable(Routes.SIGNUP) {
                SignUpScreen(
                    onSignUpSuccess = {
                        navController.navigate(Routes.LISTS) {
                            popUpTo(Routes.LOGIN) { inclusive = true }
                        }
                    },
                    onNavigateToLogin = {
                        navController.popBackStack()
                    }
                )
            }
            composable(Routes.LISTS) {
                ListsScreen()
            }
            composable(Routes.SETTINGS) {
                SettingsScreen(
                    onLoggedOut = {
                        navController.navigate(Routes.LOGIN) {
                            popUpTo(0) { inclusive = true }
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun AppBottomNav(navController: NavHostController, currentRoute: String?) {
    NavigationBar(containerColor = Surface) {
        NavigationBarItem(
            selected = currentRoute == Routes.LISTS,
            onClick = {
                navController.navigate(Routes.LISTS) {
                    popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                    launchSingleTop = true
                    restoreState = true
                }
            },
            icon = { Icon(Icons.Default.Checklist, contentDescription = "Lists") },
            label = { Text("Lists") },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = AccentDeep,
                selectedTextColor = AccentDeep,
                unselectedIconColor = MutedText,
                unselectedTextColor = MutedText,
                indicatorColor = Surface
            )
        )
        NavigationBarItem(
            selected = currentRoute == Routes.SETTINGS,
            onClick = {
                navController.navigate(Routes.SETTINGS) {
                    popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                    launchSingleTop = true
                    restoreState = true
                }
            },
            icon = { Icon(Icons.Default.Settings, contentDescription = "Settings") },
            label = { Text("Settings") },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = AccentDeep,
                selectedTextColor = AccentDeep,
                unselectedIconColor = MutedText,
                unselectedTextColor = MutedText,
                indicatorColor = Surface
            )
        )
    }
}
