package com.ireddragonicy.champkernelmanager.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ireddragonicy.champkernelmanager.data.BatteryInfo
import com.ireddragonicy.champkernelmanager.data.DataRepository
import kotlinx.coroutines.launch

@Composable
fun BatterySection(refreshTrigger: Int) {
    var expanded by remember { mutableStateOf(false) }
    var batteryInfo by remember { mutableStateOf<BatteryInfo?>(null) }
    val dataRepository = DataRepository.getInstance()
    val coroutineScope = rememberCoroutineScope()
    
    LaunchedEffect(refreshTrigger) {
        batteryInfo = dataRepository.getBatteryInfo()
    }
    
    SectionCard(
        title = "Battery",
        expanded = expanded,
        onExpandToggle = { expanded = !expanded }
    ) {
        batteryInfo?.let { battery ->
            Column {
                InfoRow(title = "Status", value = battery.status)
                InfoRow(title = "Level", value = "${battery.level}%")
                InfoRow(title = "Temperature", value = "${battery.temperature}°C")
                InfoRow(title = "Current", value = "${battery.currentNow}mA")
                InfoRow(title = "Voltage", value = "${battery.voltage}mV")
                InfoRow(title = "Health", value = battery.health)
                InfoRow(title = "Technology", value = battery.technology)
                
                Spacer(modifier = Modifier.height(16.dp))
                
                if (battery.fastChargeSupported) {
                    var fastChargeEnabled by remember { mutableStateOf(battery.fastChargeEnabled) }
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Fast Charging:",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(0.5f)
                        )
                        SwitchWithLabel(
                            checked = fastChargeEnabled,
                            onCheckedChange = { 
                                coroutineScope.launch {
                                    if (dataRepository.setFastCharge(it)) {
                                        fastChargeEnabled = it
                                    }
                                }
                            },
                            label = if (fastChargeEnabled) "Enabled" else "Disabled"
                        )
                    }
                }
                
                if (battery.chargingLimitSupported) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Charging Limit: ${battery.chargingLimit}%",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    var sliderValue by remember { mutableFloatStateOf(battery.chargingLimit.toFloat()) }
                    Slider(
                        value = sliderValue,
                        onValueChange = { sliderValue = it },
                        onValueChangeFinished = {
                            coroutineScope.launch {
                                dataRepository.setChargingLimit(sliderValue.toInt())
                            }
                        },
                        valueRange = 60f..100f,
                        steps = 8
                    )
                }
            }
        } ?: Text("Battery information not available")
    }
}