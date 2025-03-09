package com.ireddragonicy.champkernelmanager.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ireddragonicy.champkernelmanager.data.DataRepository
import com.ireddragonicy.champkernelmanager.data.DevfreqInfo
import kotlinx.coroutines.launch

@Composable
fun GpuSection(refreshTrigger: Int) {
    var expanded by remember { mutableStateOf(false) }
    var gpuInfo by remember { mutableStateOf<DevfreqInfo?>(null) }
    val dataRepository = DataRepository.getInstance()
    val coroutineScope = rememberCoroutineScope()
    
    LaunchedEffect(refreshTrigger) {
        gpuInfo = dataRepository.getGpuInfo()
    }

    SectionCard(
        title = "GPU",
        expanded = expanded,
        onExpandToggle = { expanded = !expanded }
    ) {
        Column {
            gpuInfo?.let { gpu ->
                DevfreqInfoDisplay(devfreqInfo = gpu)
                
                Spacer(modifier = Modifier.height(16.dp))
                
                var expandedFreqMax by remember { mutableStateOf(false) }
                var expandedFreqMin by remember { mutableStateOf(false) }
                var expandedGovernor by remember { mutableStateOf(false) }
                
                FrequencySelector(
                    title = "Max Frequency",
                    current = gpu.maxFreqMHz,
                    options = gpu.availableFrequenciesMHz,
                    expanded = expandedFreqMax,
                    onExpandChange = { expandedFreqMax = it },
                    onOptionSelected = { 
                        coroutineScope.launch {
                            dataRepository.setGpuMaxFreq(it)
                            expandedFreqMax = false
                        }
                    }
                )
                
                FrequencySelector(
                    title = "Min Frequency",
                    current = gpu.minFreqMHz,
                    options = gpu.availableFrequenciesMHz,
                    expanded = expandedFreqMin,
                    onExpandChange = { expandedFreqMin = it },
                    onOptionSelected = { 
                        coroutineScope.launch {
                            dataRepository.setGpuMinFreq(it)
                            expandedFreqMin = false
                        }
                    }
                )
                
                if (gpu.availableGovernors.isNotEmpty()) {
                    GovernorSelector(
                        title = "GPU Governor",
                        current = gpu.currentGovernor,
                        options = gpu.availableGovernors,
                        expanded = expandedGovernor,
                        onExpandChange = { expandedGovernor = it },
                        onOptionSelected = { 
                            coroutineScope.launch {
                                dataRepository.setGpuGovernor(it)
                                expandedGovernor = false
                            }
                        }
                    )
                }
            } ?: Text("GPU information not available")
        }
    }
}

@Composable
fun DevfreqInfoDisplay(devfreqInfo: DevfreqInfo) {
    Column(
        modifier = Modifier.fillMaxWidth()
    ) {
        InfoRow(title = "Current", value = devfreqInfo.curFreqMHz)
        InfoRow(title = "Target", value = devfreqInfo.targetFreqMHz)
        InfoRow(title = "Maximum", value = devfreqInfo.maxFreqMHz)
        InfoRow(title = "Minimum", value = devfreqInfo.minFreqMHz)
        InfoRow(title = "Governor", value = devfreqInfo.currentGovernor)
    }
}

@Composable
fun FrequencySelector(
    title: String,
    current: String,
    options: List<String>,
    expanded: Boolean,
    onExpandChange: (Boolean) -> Unit,
    onOptionSelected: (String) -> Unit
) {
    SettingsDropdown(
        title = title,
        current = current,
        options = options,
        expanded = expanded,
        onExpandChange = onExpandChange,
        onOptionSelected = onOptionSelected
    )
}

@Composable
fun GovernorSelector(
    title: String,
    current: String,
    options: List<String>,
    expanded: Boolean,
    onExpandChange: (Boolean) -> Unit,
    onOptionSelected: (String) -> Unit
) {
    SettingsDropdown(
        title = title,
        current = current,
        options = options,
        expanded = expanded,
        onExpandChange = onExpandChange,
        onOptionSelected = onOptionSelected
    )
}