package com.ireddragonicy.champkernelmanager.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ireddragonicy.champkernelmanager.data.models.CoreControlInfo
import com.ireddragonicy.champkernelmanager.data.models.CpuClusterInfo
import com.ireddragonicy.champkernelmanager.data.models.CpuCoreInfo
import com.ireddragonicy.champkernelmanager.data.DataRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun CpuSection(
    onNavigateToCoreControl: () -> Unit
) {
    var clusters by remember { mutableStateOf<List<CpuClusterInfo>>(emptyList()) }
    var availableGovernors by remember { mutableStateOf<List<String>>(emptyList()) }
    var coreControlInfo by remember { mutableStateOf<CoreControlInfo?>(null) }
    var systemLoads by remember { mutableStateOf<List<String>>(emptyList()) }
    var cores by remember { mutableStateOf<List<CpuCoreInfo>>(emptyList()) }

    val dataRepository = DataRepository.getInstance()
    val coroutineScope = rememberCoroutineScope()

    val clusterColors = mapOf(
        "Little" to MaterialTheme.colorScheme.primaryContainer,
        "Big" to MaterialTheme.colorScheme.secondaryContainer,
        "Prime" to MaterialTheme.colorScheme.tertiaryContainer
    )

    var coreToClusterMap by remember { mutableStateOf<Map<Int, String>>(emptyMap()) }

    // Initial data load
    LaunchedEffect(Unit) {
        // One-time data loads
        availableGovernors = dataRepository.getAvailableGovernors()
        coreControlInfo = dataRepository.getCoreControlInfo()

        // Start monitoring loop - updates every 500ms
        while (true) {
            // Update CPU clusters and core information
            clusters = dataRepository.getCpuClusters()
            cores = clusters.flatMap { it.cores }.sortedBy { it.core }

            // Update core-to-cluster mapping
            coreToClusterMap = clusters.flatMap { cluster ->
                cluster.cores.map { core -> core.core to cluster.name }
            }.toMap()

            // Update system load information
            val rawLoad = dataRepository.getSystemLoad()
            systemLoads = rawLoad.split(" ").filter { it.isNotBlank() }

            delay(500) // 500ms refresh rate
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        Text(
            text = "CPU",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 16.dp)
        )

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
                // Core Frequency Grid
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val rowCount = (cores.size + 3) / 4
                    for (rowIndex in 0 until rowCount) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            val startIdx = rowIndex * 4
                            val endIdx = minOf(startIdx + 4, cores.size)
                            for (i in startIdx until endIdx) {
                                val clusterName = coreToClusterMap[cores[i].core] ?: "Unknown"
                                val color = clusterColors[clusterName]
                                    ?: MaterialTheme.colorScheme.primaryContainer

                                CoreFrequencyItem(
                                    core = cores[i],
                                    backgroundColor = color,
                                    modifier = Modifier.weight(1f),
                                    onToggle = { coreNumber, newState ->
                                        coroutineScope.launch {
                                            if (dataRepository.setCoreState(coreNumber, newState)) {
                                                cores = cores.map {
                                                    if (it.core == coreNumber)
                                                        it.copy(online = newState)
                                                    else it
                                                }
                                            }
                                        }
                                    }
                                )
                            }
                            repeat(4 - (endIdx - startIdx)) {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // System Information
                Text(
                    text = "System Information",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )

                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 4.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f)
                )

                // Cluster Frequencies
                Text(
                    text = "Cluster Frequencies",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium
                )

                clusters.forEachIndexed { index, cluster ->
                    val clusterColor = clusterColors[cluster.name]
                        ?: MaterialTheme.colorScheme.primaryContainer

                    val hwMaxFreq = cluster.cores.firstOrNull()?.hwMaxFreqMHz ?: "N/A"
                    val scalingMaxFreq = cluster.cores.firstOrNull()?.scalingMaxFreqMHz ?: "N/A"

                    // Pair cluster with system load if available
                    val clusterLoad = systemLoads.getOrNull(index) ?: "N/A"

                    ClusterInfoCard(
                        clusterName = cluster.name,
                        hwMaxFreq = hwMaxFreq,
                        scalingMaxFreq = scalingMaxFreq,
                        color = clusterColor,
                        clusterLoad = clusterLoad
                    )
                }

                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 4.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f)
                )

                // CPU Governor
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

        // Core Control Section (shown if info.cores is not empty)
        coreControlInfo?.let { info ->
            if (info.cores.isNotEmpty()) {
                Spacer(modifier = Modifier.height(16.dp))
                CoreControlCard(onNavigateToCoreControl)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CoreFrequencyItem(
    core: CpuCoreInfo,
    backgroundColor: Color,
    modifier: Modifier = Modifier,
    onToggle: (Int, Boolean) -> Unit
) {
    val freqText = core.curFreqMHz
    val freqValueAndUnit = if (freqText.contains("MHz", ignoreCase = true)) {
        val parts = freqText.replace("MHz", "").trim() to "MHz"
        parts
    } else {
        freqText to ""
    }

    var isPressed by remember { mutableStateOf(false) }
    val isEnabled = core.core != 0

    val cardColor = when {
        isPressed -> backgroundColor.copy(alpha = 0.8f)
        core.online -> backgroundColor
        else -> MaterialTheme.colorScheme.errorContainer
    }

    ElevatedCard(
        onClick = {
            if (isEnabled) {
                isPressed = true
                onToggle(core.core, !core.online)
                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                    isPressed = false
                }, 200)
            }
        },
        enabled = isEnabled,
        modifier = modifier.aspectRatio(0.9f),
        colors = CardDefaults.elevatedCardColors(
            containerColor = cardColor,
            disabledContainerColor = if (core.online) backgroundColor else MaterialTheme.colorScheme.errorContainer
        ),
        elevation = CardDefaults.elevatedCardElevation(
            defaultElevation = if (core.online) 4.dp else 1.dp
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "CPU ${core.core}",
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Clip
            )

            if (core.online) {
                Text(
                    text = freqValueAndUnit.first,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Visible
                )

                Text(
                    text = freqValueAndUnit.second,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Normal,
                    textAlign = TextAlign.Center,
                    maxLines = 1
                )

                Spacer(modifier = Modifier.height(2.dp))
                HorizontalDivider(
                    modifier = Modifier.width(24.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
                    thickness = 1.dp
                )
                Spacer(modifier = Modifier.height(2.dp))

            } else {
                Text(
                    text = "Offline",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center
                )
                if (isEnabled) {
                    Text(
                        text = "Tap to enable",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        textAlign = TextAlign.Center,
                        maxLines = 1
                    )
                }
            }

            if (core.core == 0) {
                Text(
                    text = "(System Core)",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    textAlign = TextAlign.Center,
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
fun ClusterInfoCard(
    clusterName: String,
    hwMaxFreq: String,
    scalingMaxFreq: String,
    color: Color,
    clusterLoad: String
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
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = clusterName,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "${clusterLoad}%",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium
                )
            }

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