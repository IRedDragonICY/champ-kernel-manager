package com.ireddragonicy.champkernelmanager.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavController

sealed class NavigationItem(val route: String, val icon: ImageVector, val title: String) {
    data object Home : NavigationItem(Screen.Home.route, Icons.Default.Home, "Home")
    data object LiveMonitor : NavigationItem(Screen.LiveMonitor.route, Icons.Default.Timeline, "Live Monitor")
    data object Settings : NavigationItem(Screen.Settings.route, Icons.Default.Settings, "Settings")

    companion object {
        val items = listOf(Home, LiveMonitor, Settings)
    }
}

@Composable
fun NavigationBar(navController: NavController, currentRoute: String?) {
    NavigationBar(
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface
    ) {
        NavigationItem.items.forEach { item ->
            NavigationBarItem(
                icon = { Icon(item.icon, contentDescription = null) },
                label = { Text(item.title) },
                selected = currentRoute == item.route,
                onClick = {
                    if (currentRoute != item.route) {
                        navController.navigateSingleTop(item.route)
                    }
                }
            )
        }
    }
}

fun NavController.navigateSingleTop(route: String) {
    navigate(route) {
        popUpTo(graph.startDestinationId) {
            saveState = true
        }
        launchSingleTop = true
        restoreState = true
    }
}