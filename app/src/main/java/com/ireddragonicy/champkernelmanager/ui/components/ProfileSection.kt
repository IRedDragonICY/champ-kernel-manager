package com.ireddragonicy.champkernelmanager.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.ireddragonicy.champkernelmanager.App
import com.ireddragonicy.champkernelmanager.data.DataRepository
import kotlinx.coroutines.launch

@Composable
fun ProfileSection() {
    val context = LocalContext.current
    val appSettings = remember { (context.applicationContext as App).settings }
    val scope = rememberCoroutineScope()
    val dataRepository = DataRepository.getInstance()
    
    var showSaveDialog by remember { mutableStateOf(false) }
    
    Column(modifier = Modifier.padding(16.dp)) {
        Text(
            text = "Profiles",
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(bottom = 16.dp)
        )
        
        Row(modifier = Modifier.fillMaxWidth()) {
            Button(
                onClick = { showSaveDialog = true },
                modifier = Modifier.weight(1f)
            ) {
                Text("Save Current Settings")
            }
            
            Spacer(modifier = Modifier.width(8.dp))
            
            OutlinedButton(
                onClick = {
                    scope.launch {
                        // Load CPU governor
                        if (appSettings.savedCpuGovernor.isNotEmpty()) {
                            dataRepository.setAllCoresGovernor(appSettings.savedCpuGovernor)
                        }
                        
                        // Load GPU settings
                        if (appSettings.savedGpuGovernor.isNotEmpty()) {
                            dataRepository.setGpuGovernor(appSettings.savedGpuGovernor)
                        }
                        
                        if (appSettings.savedGpuMaxFreq.isNotEmpty()) {
                            dataRepository.setGpuMaxFreq(appSettings.savedGpuMaxFreq)
                        }
                        
                        if (appSettings.savedGpuMinFreq.isNotEmpty()) {
                            dataRepository.setGpuMinFreq(appSettings.savedGpuMinFreq)
                        }
                        
                        // Load thermal profile
                        if (appSettings.savedThermalProfile.isNotEmpty()) {
                            dataRepository.setThermalProfile(appSettings.savedThermalProfile)
                        }
                        
                        // Load IO scheduler
                        if (appSettings.savedIoScheduler.isNotEmpty()) {
                            dataRepository.setIoScheduler(appSettings.savedIoScheduler)
                        }
                        
                        // Load TCP congestion
                        if (appSettings.savedTcpCongestion.isNotEmpty()) {
                            dataRepository.setTcpCongestion(appSettings.savedTcpCongestion)
                        }
                    }
                },
                modifier = Modifier.weight(1f)
            ) {
                Text("Apply Saved Settings")
            }
        }
        
        if (showSaveDialog) {
            SaveProfileDialog(
                onSave = { profileName ->
                    scope.launch {
                        // Get current CPU governor
                        val cpuInfo = dataRepository.getCpuClusters()
                        val currentGovernor = cpuInfo.firstOrNull()?.cores?.firstOrNull()?.governor
                        if (currentGovernor != null && currentGovernor != "N/A") {
                            appSettings.savedCpuGovernor = currentGovernor
                        }
                        
                        // Get current GPU settings
                        val gpuInfo = dataRepository.getGpuInfo()
                        appSettings.savedGpuGovernor = gpuInfo.currentGovernor
                        appSettings.savedGpuMaxFreq = gpuInfo.maxFreqMHz
                        appSettings.savedGpuMinFreq = gpuInfo.minFreqMHz
                        
                        // Get current thermal profile
                        val thermalInfo = dataRepository.getThermalInfo()
                        appSettings.savedThermalProfile = thermalInfo.currentProfile
                        
                        // Get system settings
                        val systemInfo = dataRepository.getSystemInfo()
                        appSettings.savedIoScheduler = systemInfo.currentIoScheduler
                        appSettings.savedTcpCongestion = systemInfo.currentTcpCongestion
                    }
                    showSaveDialog = false
                },
                onDismiss = { showSaveDialog = false }
            )
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Display current saved settings
        if (appSettings.savedCpuGovernor.isNotEmpty()) {
            Text("Saved CPU Governor: ${appSettings.savedCpuGovernor}")
        }
        
        if (appSettings.savedGpuGovernor.isNotEmpty()) {
            Text("Saved GPU Governor: ${appSettings.savedGpuGovernor}")
        }
    }
}

@Composable
fun SaveProfileDialog(
    onSave: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var profileName by remember { mutableStateOf("Default") }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Save Settings Profile") },
        text = {
            Column {
                Text("Save current settings to apply on boot or use later.")
                Spacer(modifier = Modifier.height(8.dp))
                TextField(
                    value = profileName,
                    onValueChange = { profileName = it },
                    label = { Text("Profile Name") }
                )
            }
        },
        confirmButton = {
            Button(onClick = { onSave(profileName) }) {
                Text("Save")
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}