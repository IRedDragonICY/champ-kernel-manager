package com.ireddragonicy.champkernelmanager.ui.screens

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.ireddragonicy.champkernelmanager.navigation.NavigationBar
import com.ireddragonicy.champkernelmanager.navigation.Screen

@Composable
fun MainScreen(hasRootAccess: Boolean = true) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route?.substringBefore("/")

    if (!hasRootAccess) {
        RootCheckScreen()
        return
    }

    Scaffold(
        bottomBar = {
            val showBottomNav = currentRoute in listOf(
                Screen.Home.route,
                Screen.LiveMonitor.route,
                Screen.Settings.route
            )

            if (showBottomNav) {
                NavigationBar(
                    navController = navController,
                    currentRoute = currentRoute
                )
            }
        }
    ) { innerPadding ->
        AppNavHost(
            navController = navController,
            modifier = Modifier.padding(innerPadding)
        )
    }
}

@Composable
private fun AppNavHost(
    navController: NavHostController,
    modifier: Modifier = Modifier
) {
    NavHost(
        navController = navController,
        startDestination = Screen.Home.route,
        modifier = modifier
    ) {
        composable(Screen.Home.route) {
            HomeScreen(
                onNavigateToCoreControl = {
                    navController.navigate(Screen.CoreControl.route)
                }
            )
        }

        composable(Screen.LiveMonitor.route) {
            LiveMonitorScreen()
        }

        composable(Screen.Settings.route) {
            SettingsScreen()
        }

        composable(Screen.CoreControl.route) {
            CoreControlScreen(
                onBackPressed = { navController.popBackStack() }
            )
        }

        composable(Screen.RootCheck.route) {
            RootCheckScreen()
        }

    }
}