package com.ireddragonicy.champkernelmanager.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.ireddragonicy.champkernelmanager.App
import com.ireddragonicy.champkernelmanager.ui.components.InfoRow
import com.ireddragonicy.champkernelmanager.ui.components.SettingsCategory
import com.ireddragonicy.champkernelmanager.ui.components.SettingsSwitch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen() {
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    val context = LocalContext.current
    val app = remember { context.applicationContext as App }
    
    var applyOnBoot by remember { mutableStateOf(app.settings.applyOnBoot) }
    // Use the app's observable state which will already be in sync with settings
    val useDynamicColors by app.useDynamicColors
    val darkTheme by app.isDarkTheme
    
    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                scrollBehavior = scrollBehavior
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
        ) {
            SettingsCategory(title = "App Settings") {
                SettingsSwitch(
                    title = "Apply Settings On Boot",
                    checked = applyOnBoot,
                    onCheckedChange = {
                        applyOnBoot = it
                        app.settings.applyOnBoot = it
                    }
                )
                
                SettingsSwitch(
                    title = "Use Dynamic Colors",
                    checked = useDynamicColors,
                    onCheckedChange = {
                        // Use the new App method to update both settings and state
                        app.updateDynamicColors(it)
                    }
                )
                
                SettingsSwitch(
                    title = "Dark Theme",
                    checked = darkTheme,
                    onCheckedChange = {
                        // Use the new App method to update both settings and state
                        app.updateTheme(it)
                    }
                )
            }
            
            SettingsCategory(title = "About") {
                InfoRow(title = "Version", value = "1.0.0")
                InfoRow(title = "Developer", value = "IRedDragonICY")
            }
        }
    }
}