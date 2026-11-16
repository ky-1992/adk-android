package com.google.adk.demo.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.google.adk.demo.ui.chat.ChatScreen
import com.google.adk.demo.ui.sessions.SessionsScreen
import com.google.adk.demo.ui.settings.SettingsScreen
import com.google.adk.demo.ui.tools.ToolsScreen
import com.google.adk.demo.ui.theme.CarMinTouchTargetDp

sealed class Screen(val route: String, val label: String) {
    data object Chat : Screen("chat", "Chat")
    data object Sessions : Screen("sessions", "Sessions")
    data object Tools : Screen("tools", "Tools")
    data object Settings : Screen("settings", "Settings")
}

private fun Screen.icon(): ImageVector = when (this) {
    Screen.Chat -> Icons.AutoMirrored.Filled.Chat
    Screen.Sessions -> Icons.Default.History
    Screen.Tools -> Icons.Default.Build
    Screen.Settings -> Icons.Default.Settings
}

@Composable
fun AdkNavHost(
    windowWidthSizeClass: WindowWidthSizeClass = WindowWidthSizeClass.Compact,
) {
    val navController = rememberNavController()
    if (windowWidthSizeClass == WindowWidthSizeClass.Compact) {
        PhoneLayout(navController)
    } else {
        AutomotiveRailLayout(navController)
    }
}

/** Shared destinations, rendered inside whichever shell the window provides. */
@Composable
private fun DemoNavGraph(navController: NavHostController, modifier: Modifier = Modifier) {
    NavHost(
        navController = navController,
        startDestination = Screen.Chat.route,
        modifier = modifier,
    ) {
        composable(Screen.Chat.route) { ChatScreen() }
        composable(Screen.Sessions.route) {
            SessionsScreen(
                onSessionSelected = { sessionId ->
                    if (sessionId == "new") {
                        navController.navigateToTab(Screen.Chat)
                    } else {
                        // TODO: load specific session in ChatScreen
                        navController.navigateToTab(Screen.Chat)
                    }
                },
            )
        }
        composable(Screen.Tools.route) { ToolsScreen() }
        composable(Screen.Settings.route) { SettingsScreen() }
    }
}

private fun NavHostController.navigateToTab(screen: Screen) {
    navigate(screen.route) {
        popUpTo(graph.startDestinationId) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

/**
 * Phone layout (compact width, portrait) — unchanged from the original
 * bottom NavigationBar shell so portrait does not regress.
 */
@Composable
private fun PhoneLayout(navController: NavHostController) {
    val screens = listOf(Screen.Chat, Screen.Sessions, Screen.Tools, Screen.Settings)

    Scaffold(
        bottomBar = {
            NavigationBar {
                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val currentDestination = navBackStackEntry?.destination
                screens.forEach { screen ->
                    NavigationBarItem(
                        icon = { Icon(screen.icon(), contentDescription = screen.label) },
                        label = { Text(screen.label) },
                        selected = currentDestination?.hierarchy?.any { it.route == screen.route } == true,
                        onClick = { navController.navigateToTab(screen) },
                    )
                }
            }
        },
    ) { innerPadding ->
        DemoNavGraph(
            navController = navController,
            modifier = Modifier.padding(innerPadding),
        )
    }
}

/**
 * Automotive layout (medium / expanded width, car head unit landscape) —
 * left navigation rail + main content area, with automotive-safe touch
 * targets (>= 56dp) on every rail item.
 */
@Composable
private fun AutomotiveRailLayout(navController: NavHostController) {
    val screens = listOf(Screen.Chat, Screen.Sessions, Screen.Tools, Screen.Settings)

    Row(modifier = Modifier.fillMaxSize()) {
        NavigationRail(modifier = Modifier.width(108.dp)) {
            val navBackStackEntry by navController.currentBackStackEntryAsState()
            val currentDestination = navBackStackEntry?.destination
            screens.forEach { screen ->
                val selected = currentDestination?.hierarchy?.any { it.route == screen.route } == true
                CarRailItem(
                    selected = selected,
                    label = screen.label,
                    iconVector = screen.icon(),
                    onClick = { navController.navigateToTab(screen) },
                )
            }
        }
        DemoNavGraph(
            navController = navController,
            modifier = Modifier.fillMaxSize().weight(1f),
        )
    }
}

/**
 * Automotive navigation rail item with a guaranteed large touch target
 * (64 x 64dp, comfortably above the 56dp car-safety minimum) and a 28dp icon
 * for glanceability.
 */
@Composable
private fun CarRailItem(
    selected: Boolean,
    label: String,
    iconVector: ImageVector,
    onClick: () -> Unit,
) {
    val containerColor =
        if (selected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent
    val contentColor =
        if (selected) MaterialTheme.colorScheme.onSecondaryContainer
        else MaterialTheme.colorScheme.onSurfaceVariant

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height((CarMinTouchTargetDp + 8).dp)
            .padding(horizontal = 10.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(containerColor)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = iconVector,
                contentDescription = label,
                tint = contentColor,
                modifier = Modifier.size(28.dp),
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = contentColor,
            )
        }
    }
}
