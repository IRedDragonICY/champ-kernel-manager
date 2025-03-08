package com.ireddragonicy.champkernelmanager.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.ireddragonicy.champkernelmanager.data.CpuClusterInfo
import com.ireddragonicy.champkernelmanager.data.CpuCoreInfo
import com.ireddragonicy.champkernelmanager.data.DataRepository
import kotlinx.coroutines.launch

@Composable
fun CpuSection(
    refreshTrigger: Int,
    onNavigateToCoreControl: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    var clusters by remember { mutableStateOf<List<CpuClusterInfo>>(emptyList()) }
    var availableGovernors by remember { mutableStateOf<List<String>>(emptyList()) }
    var coreControlInfo by remember { mutableStateOf<DataRepository.CoreControlInfo?>(null) }

    val context = LocalContext.current
    val dataRepository = DataRepository.getInstance()
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(refreshTrigger) {
        clusters = dataRepository.getCpuClusters()
        availableGovernors = dataRepository.getAvailableGovernors()
        coreControlInfo = dataRepository.getCoreControlInfo()
    }

    SectionCard(
        title = "CPU",
        expanded = expanded,
        onExpandToggle = { expanded = !expanded }
    ) {
        Column {
            clusters.forEach { cluster ->
                ClusterCard(cluster = cluster)
                Spacer(modifier = Modifier.height(8.dp))
            }

            if (clusters.isNotEmpty()) {
                if (availableGovernors.isNotEmpty()) {
                    Text(
                        text = "CPU Governor",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(top = 8.dp, bottom = 8.dp)
                    )

                    GovernorSelector(
                        currentGovernor = clusters.firstOrNull()?.cores?.firstOrNull()?.governor ?: "unknown",
                        availableGovernors = availableGovernors,
                        onGovernorSelected = { governor ->
                            coroutineScope.launch {
                                dataRepository.setAllCoresGovernor(governor)
                            }
                        }
                    )
                }

                coreControlInfo?.let { coreControl ->
                    if (coreControl.supported) {
                        Text(
                            text = "Core Control",
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(top = 16.dp, bottom = 8.dp)
                        )

                        // Card to navigate to Core Control screen
                        CoreControlNavigationCard(onNavigate = onNavigateToCoreControl)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CoreControlNavigationCard(onNavigate: () -> Unit) {
    ElevatedCard(
        onClick = onNavigate,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Manage CPU Cores",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Icon(
                imageVector = Icons.Default.KeyboardArrowRight,
                contentDescription = "Navigate to Core Control",
                tint = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
fun ClusterCard(cluster: CpuClusterInfo) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "Cluster: ${cluster.clusterName}",
                style = MaterialTheme.typography.titleSmall
            )
            Divider(modifier = Modifier.padding(vertical = 8.dp))

            cluster.cores.forEach { core ->
                CoreInfo(core = core)
            }
        }
    }
}

@Composable
fun CoreInfo(core: CpuCoreInfo) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = "Core ${core.coreNumber}",
            style = MaterialTheme.typography.bodyMedium
        )
        Text(
            text = core.curFreqMHz,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

@Composable
fun GovernorSelector(
    currentGovernor: String,
    availableGovernors: List<String>,
    onGovernorSelected: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    SettingsDropdown(
        title = "Governor",
        current = currentGovernor,
        options = availableGovernors,
        expanded = expanded,
        onExpandChange = { expanded = it },
        onOptionSelected = {
            onGovernorSelected(it)
            expanded = false
        }
    )
}