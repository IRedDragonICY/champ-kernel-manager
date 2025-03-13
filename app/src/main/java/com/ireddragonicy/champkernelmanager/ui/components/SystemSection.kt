package com.ireddragonicy.champkernelmanager.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ireddragonicy.champkernelmanager.data.DataRepository
import com.ireddragonicy.champkernelmanager.data.models.SystemInfo
import kotlinx.coroutines.launch

@Composable
fun SystemSection(refreshTrigger: Int) {
    var expanded by remember { mutableStateOf(false) }
    var systemInfo by remember { mutableStateOf<SystemInfo?>(null) }
    val dataRepository = DataRepository.getInstance()
    val coroutineScope = rememberCoroutineScope()
    
    LaunchedEffect(refreshTrigger) {
        systemInfo = dataRepository.getSystemInfo()
    }
    
    SectionCard(
        title = "System",
        expanded = expanded,
        onExpandToggle = { expanded = !expanded }
    ) {
        systemInfo?.let { info ->
            Column {
                InfoRow(title = "Kernel Version", value = info.kernelVersion)
                InfoRow(title = "Kernel Build", value = info.kernelBuild)
                InfoRow(title = "CPU Architecture", value = info.cpuArch)
                InfoRow(title = "Device Model", value = info.deviceModel)
                InfoRow(title = "Android Version", value = info.androidVersion)
                InfoRow(title = "Uptime", value = info.uptime)
                InfoRow(title = "SELinux Status", value = info.selinuxStatus)
                
                Spacer(modifier = Modifier.height(16.dp))
                
                var expandedIOScheduler by remember { mutableStateOf(false) }
                
                SettingsDropdown(
                    title = "I/O Scheduler",
                    current = info.currentIoScheduler,
                    options = info.availableIoSchedulers,
                    expanded = expandedIOScheduler,
                    onExpandChange = { expandedIOScheduler = it },
                    onOptionSelected = { 
                        coroutineScope.launch {
                            dataRepository.setIoScheduler(it)
                            expandedIOScheduler = false
                        }
                    }
                )
                
                if (info.availableTcpCongestion.isNotEmpty()) {
                    var expandedTcp by remember { mutableStateOf(false) }
                    
                    SettingsDropdown(
                        title = "TCP Congestion",
                        current = info.currentTcpCongestion,
                        options = info.availableTcpCongestion,
                        expanded = expandedTcp,
                        onExpandChange = { expandedTcp = it },
                        onOptionSelected = { 
                            coroutineScope.launch {
                                dataRepository.setTcpCongestion(it)
                                expandedTcp = false
                            }
                        }
                    )
                }
            }
        } ?: InfoRow(title = "System Info", value = "Loading...")
    }
}