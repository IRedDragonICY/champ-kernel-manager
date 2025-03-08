package com.ireddragonicy.champkernelmanager

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.ExperimentalMaterial3Api
import com.ireddragonicy.champkernelmanager.ui.screens.MainScreen
import com.ireddragonicy.champkernelmanager.ui.screens.RootCheckScreen
import com.ireddragonicy.champkernelmanager.ui.theme.ChampKernelManagerTheme
import com.topjohnwu.superuser.Shell
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen

class MainActivity : ComponentActivity() {
    @OptIn(ExperimentalMaterial3Api::class)
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
                    MainScreen()
                }
            }
        }
    }
}