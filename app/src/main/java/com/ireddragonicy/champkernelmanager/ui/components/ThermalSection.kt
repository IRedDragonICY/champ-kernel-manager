package com.ireddragonicy.champkernelmanager.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ireddragonicy.champkernelmanager.data.DataRepository
import com.ireddragonicy.champkernelmanager.data.ThermalInfo
import kotlinx.coroutines.launch

@Composable
fun ThermalSection(refreshTrigger: Int) {
    var expanded by remember { mutableStateOf(false) }
    var thermalInfo by remember { mutableStateOf<ThermalInfo?>(null) }
    val dataRepository = DataRepository.getInstance()
    val coroutineScope = rememberCoroutineScope()
    
    LaunchedEffect(refreshTrigger) {
        thermalInfo = dataRepository.getThermalInfo()
    }
    
    SectionCard(
        title = "Thermal",
        expanded = expanded,
        onExpandToggle = { expanded = !expanded }
    ) {
        thermalInfo?.let { thermal ->
            Column {
                thermal.zones.forEach { zone ->
                    InfoRow(title = zone.name, value = "${zone.temp}°C")
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                var thermalControlEnabled by remember { mutableStateOf(thermal.thermalServicesEnabled) }
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Thermal Services:",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(0.5f)
                    )
                    SwitchWithLabel(
                        checked = thermalControlEnabled,
                        onCheckedChange = { 
                            coroutineScope.launch {
                                if (dataRepository.setThermalServices(it)) {
                                    thermalControlEnabled = it
                                }
                            }
                        },
                        label = if (thermalControlEnabled) "Enabled" else "Disabled"
                    )
                }
                
                if (!thermalControlEnabled) {
                    Text(
                        text = "Warning: Disabling thermal services may cause your device to overheat. Use with caution!",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }


                }
            }

    }
}