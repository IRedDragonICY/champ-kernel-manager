package com.ireddragonicy.champkernelmanager.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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
    var expanded by remember { mutableStateOf(true) }
    var clusters by remember { mutableStateOf<List<CpuClusterInfo>>(emptyList()) }
    var availableGovernors by remember { mutableStateOf<List<String>>(emptyList()) }
    var coreControlInfo by remember { mutableStateOf<DataRepository.CoreControlInfo?>(null) }
    var systemLoad by remember { mutableStateOf("N/A") }
    var cores by remember { mutableStateOf<List<CpuCoreInfo>>(emptyList()) }

    val dataRepository = DataRepository.getInstance()
    val coroutineScope = rememberCoroutineScope()

    // Define cluster colors
    val clusterColors = mapOf(
        "Little" to MaterialTheme.colorScheme.primaryContainer,
        "Big" to MaterialTheme.colorScheme.secondaryContainer,
        "Prime" to MaterialTheme.colorScheme.tertiaryContainer
    )

    // Core to cluster map for coloring
    var coreToClusterMap by remember { mutableStateOf<Map<Int, String>>(emptyMap()) }

    LaunchedEffect(refreshTrigger) {
        clusters = dataRepository.getCpuClusters()
        availableGovernors = dataRepository.getAvailableGovernors()
        coreControlInfo = dataRepository.getCoreControlInfo()
        systemLoad = dataRepository.getSystemLoad()

        // Flatten cores from all clusters for grid display
        cores = clusters.flatMap { it.cores }.sortedBy { it.core }

        // Create mapping of core number to cluster name for coloring
        coreToClusterMap = clusters.flatMap { cluster ->
            cluster.cores.map { core -> core.core to cluster.name }
        }.toMap()
    }

    SectionCard(
        title = "CPU",
        expanded = expanded,
        onExpandToggle = { expanded = !expanded }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp)
        ) {
            // Core frequency grid with modern styling
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                ),
                shape = MaterialTheme.shapes.large
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = "Core Frequencies",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )

                    // Cluster legend
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        clusters.forEach { cluster ->
                            val color = clusterColors[cluster.name]
                                ?: MaterialTheme.colorScheme.primaryContainer
                            ClusterLegendItem(
                                clusterName = cluster.name,
                                color = color,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    // Use a non-lazy grid approach with rows of cores
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Calculate how many rows we need with 4 cores per row
                        val rowCount = (cores.size + 3) / 4

                        for (rowIndex in 0 until rowCount) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                // For each row, take up to 4 cores
                                val startIdx = rowIndex * 4
                                val endIdx = minOf(startIdx + 4, cores.size)

                                for (i in startIdx until endIdx) {
                                    val clusterName = coreToClusterMap[cores[i].core] ?: "Unknown"
                                    val color = clusterColors[clusterName]
                                        ?: MaterialTheme.colorScheme.primaryContainer

                                    CoreFrequencyItem(
                                        core = cores[i],
                                        backgroundColor = color,
                                        modifier = Modifier.weight(1f)
                                    )
                                }

                                // Add placeholders if the row isn't complete
                                repeat(4 - (endIdx - startIdx)) {
                                    Spacer(modifier = Modifier.weight(1f))
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // System info card with modern styling
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                ),
                shape = MaterialTheme.shapes.large
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "System Information",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )

                    CpuInfoRow(title = "System Load:", value = systemLoad)

                    // Divider for visual separation
                    Divider(
                        modifier = Modifier.padding(vertical = 4.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f)
                    )

                    Text(
                        text = "Cluster Frequencies",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Medium
                    )

                    // Cluster frequency info with color coding
                    clusters.forEachIndexed { index, cluster ->
                        val clusterColor = clusterColors[cluster.name]
                            ?: MaterialTheme.colorScheme.primaryContainer

                        val hwMaxFreq = cluster.cores.firstOrNull()?.hwMaxFreqMHz ?: "N/A"
                        val scalingMaxFreq = cluster.cores.firstOrNull()?.scalingMaxFreqMHz ?: "N/A"

                        ClusterInfoCard(
                            clusterName = cluster.name,
                            hwMaxFreq = hwMaxFreq,
                            scalingMaxFreq = scalingMaxFreq,
                            color = clusterColor
                        )
                    }

                    // Divider before governor section
                    Divider(
                        modifier = Modifier.padding(vertical = 4.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f)
                    )

                    // Governor selector
                    Text(
                        text = "CPU Governor",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Medium
                    )

                    val currentGovernor = cores.firstOrNull()?.governor ?: "unknown"
                    GovernorSelector(
                        currentGovernor = currentGovernor,
                        availableGovernors = availableGovernors,
                        onGovernorSelected = { governor ->
                            coroutineScope.launch {
                                dataRepository.setAllCoresGovernor(governor)
                            }
                        }
                    )
                }
            }

            // Add core control section if supported
            coreControlInfo?.let { info ->
                if (info.supported) {
                    Spacer(modifier = Modifier.height(16.dp))
                    CoreControlCard(onNavigate = onNavigateToCoreControl)
                }
            }
        }
    }
}

@Composable
fun ClusterLegendItem(
    clusterName: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        color = color,
        shape = MaterialTheme.shapes.small,
        shadowElevation = 0.dp
    ) {
        Text(
            text = clusterName,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp)
        )
    }
}

@Composable
fun ClusterInfoCard(
    clusterName: String,
    hwMaxFreq: String,
    scalingMaxFreq: String,
    color: Color
) {
    Surface(
        shape = MaterialTheme.shapes.small,
        color = color.copy(alpha = 0.7f),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Column(
            modifier = Modifier.padding(8.dp)
        ) {
            Text(
                text = clusterName,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Current Max Limit:",
                    style = MaterialTheme.typography.bodySmall
                )
                Text(
                    text = scalingMaxFreq,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

@Composable
fun CoreFrequencyItem(core: CpuCoreInfo, backgroundColor: Color, modifier: Modifier = Modifier) {
    ElevatedCard(
        modifier = modifier.aspectRatio(1f),
        colors = CardDefaults.elevatedCardColors(
            containerColor = if (core.online)
                backgroundColor
            else
                MaterialTheme.colorScheme.errorContainer
        ),
        elevation = CardDefaults.elevatedCardElevation(
            defaultElevation = if (core.online) 4.dp else 1.dp
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "CPU ${core.core}",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = core.curFreqMHz,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
fun CpuInfoRow(title: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyMedium
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CoreControlCard(onNavigate: () -> Unit) {
    ElevatedCard(
        onClick = onNavigate,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
            contentColor = MaterialTheme.colorScheme.onTertiaryContainer
        ),
        shape = MaterialTheme.shapes.large
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Core Control Settings",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium
            )

            FilledTonalButton(
                onClick = onNavigate,
                shape = MaterialTheme.shapes.medium
            ) {
                Text("Manage")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GovernorSelector(
    currentGovernor: String,
    availableGovernors: List<String>,
    onGovernorSelected: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = Modifier.fillMaxWidth()
    ) {
        OutlinedTextField(
            value = currentGovernor,
            onValueChange = {},
            readOnly = true,
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(MenuAnchorType.PrimaryEditable, enabled = true),
            colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
            shape = MaterialTheme.shapes.medium
        )

        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            availableGovernors.forEach { governor ->
                DropdownMenuItem(
                    text = { Text(governor) },
                    onClick = {
                        onGovernorSelected(governor)
                        expanded = false
                    }
                )
            }
        }
    }
}