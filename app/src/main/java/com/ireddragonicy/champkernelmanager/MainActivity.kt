package com.ireddragonicy.champkernelmanager

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.ireddragonicy.champkernelmanager.navigation.NavigationBar
import com.ireddragonicy.champkernelmanager.navigation.NavigationItem
import com.ireddragonicy.champkernelmanager.ui.screens.HomeScreen
import com.ireddragonicy.champkernelmanager.ui.screens.LiveMonitorScreen
import com.ireddragonicy.champkernelmanager.ui.screens.RootCheckScreen
import com.ireddragonicy.champkernelmanager.ui.screens.SettingsScreen
import com.ireddragonicy.champkernelmanager.ui.theme.ChampKernelManagerTheme
import com.topjohnwu.superuser.Shell

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        installSplashScreen()
        enableEdgeToEdge()
        
        setContent {
            ChampKernelManagerTheme {
                val isRooted = Shell.getShell().isRoot
                if (!isRooted) {
                    RootCheckScreen()
                } else {
                    MainContent()
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainContent() {
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
                HomeScreen()
            }
            composable(NavigationItem.LiveMonitor.route) {
                LiveMonitorScreen()
            }
            composable(NavigationItem.Settings.route) {
                SettingsScreen()
            }
        }
    }
}