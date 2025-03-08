package com.ireddragonicy.champkernelmanager.ui.screens

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.ireddragonicy.champkernelmanager.navigation.NavigationBar
import com.ireddragonicy.champkernelmanager.navigation.NavigationItem

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen() {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    Scaffold(
        bottomBar = {
            NavigationBar(
                navController = navController,
                currentRoute = currentRoute
            )
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = NavigationItem.Home.route,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(NavigationItem.Home.route) {
                HomeScreen(onNavigateToCoreControl = {
                    navController.navigate("corecontrol")
                })
            }
            composable(NavigationItem.LiveMonitor.route) {
                LiveMonitorScreen()
            }
            composable(NavigationItem.Settings.route) {
                SettingsScreen()
            }
            composable("corecontrol") {
                CoreControlScreen(onBackPressed = { navController.popBackStack() })
            }
        }
    }
}