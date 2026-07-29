package com.example.grossrecipes.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
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
import com.example.grossrecipes.ui.theme.Background
import com.example.grossrecipes.ui.theme.MutedText
import com.example.grossrecipes.ui.theme.Surface

object Routes {
    const val LOGIN = "login"
    const val SIGNUP = "signup"
    const val LISTS = "lists"
    const val SETTINGS = "settings"
}

// The floating bottom nav bar's real rendered height - screens read this to
// know exactly how much clearance to leave above it for their own FAB,
// snackbar, or scroll-end spacing, instead of guessing a fixed dp value that
// doesn't account for the device's actual gesture/nav bar inset (which is
// what made a hardcoded 96dp clearance fall short on some devices - the bar
// ends up taller than that guess, so it covers the bottom of the FAB and
// swallows the last bit of scrollable space).
val LocalBottomBarHeight = compositionLocalOf { 0.dp }

@OptIn(ExperimentalLayoutApi::class)
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
    // Hidden while the keyboard's up, not just resized around it - it isn't
    // needed while typing, and resizing it around the keyboard is what
    // squeezed it into a floating strip sitting right above the keyboard,
    // between it and the list.
    val imeVisible = WindowInsets.isImeVisible
    val showBottomBar = (currentRoute == Routes.LISTS || currentRoute == Routes.SETTINGS) && !imeVisible

    // Measured once the bar actually lays out, in real pixels converted to
    // dp - the true source of truth for its height on this device, rather
    // than a guessed constant.
    val density = LocalDensity.current
    var bottomBarHeight by remember { mutableStateOf(0.dp) }

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

    // No Scaffold here at all anymore - Scaffold always reserves its own
    // top+bottom system bar insets via innerPadding even with no bottomBar
    // registered, and ListsScreen has its own Scaffold doing the exact same
    // thing underneath it. Nesting the two double-counted the bottom inset:
    // once here, once again inside ListsScreen, which is what made the
    // floating nav bar render with a big extra blank block under it. Only
    // the top status bar inset is handled here (statusBarsPadding); the
    // bottom is left alone entirely so NavHost content reaches the true
    // screen edge and the floating bar (which pads itself away from the
    // real gesture/nav bar via its own default insets) is the only thing
    // consuming that space, exactly once.
    Box(modifier = Modifier.fillMaxSize().background(Background).statusBarsPadding()) {
        CompositionLocalProvider(LocalBottomBarHeight provides bottomBarHeight) {
            NavHost(
                navController = navController,
                startDestination = startDestination,
                modifier = Modifier.fillMaxSize()
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

        if (showBottomBar) {
            AppBottomNav(
                navController = navController,
                currentRoute = currentRoute,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .onGloballyPositioned { coordinates ->
                        bottomBarHeight = with(density) { coordinates.size.height.toDp() }
                    }
            )
        }
    }
}

@Composable
private fun AppBottomNav(navController: NavHostController, currentRoute: String?, modifier: Modifier = Modifier) {
    NavigationBar(containerColor = Surface, modifier = modifier) {
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
