package com.ireddragonicy.champkernelmanager.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.ireddragonicy.champkernelmanager.data.CpuClusterInfo
import com.ireddragonicy.champkernelmanager.data.DataRepository

@Composable
fun CoreControlGrid(
    coreControl: DataRepository.CoreControlInfo,
    cpuClusters: List<CpuClusterInfo>,
    onCoreToggle: (Int, Boolean) -> Unit
) {
    // Create a map of core numbers to their cluster names
    val coreToClusterMap = cpuClusters.flatMap { cluster ->
        cluster.cores.map { core -> core.core to cluster.name }
    }.toMap()

    // Create a map of core numbers to their current frequency and scaling max freq
    val coreInfoMap = cpuClusters.flatMap { cluster ->
        cluster.cores.map { core ->
            core.core to Pair(core.curFreqMHz, core.scalingMaxFreqMHz)
        }
    }.toMap()

    // Group cores by pairs to create rows with two cores each
    val coreRows = coreControl.cores.entries.chunked(2)

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        coreRows.forEach { rowCores ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                rowCores.forEach { (core, online) ->
                    val clusterName = coreToClusterMap[core] ?: "Unknown"
                    val (currentFreq, maxFreq) = coreInfoMap[core] ?: Pair("N/A", "N/A")

                    CoreCard(
                        core = core,
                        online = online,
                        clusterType = clusterName,
                        currentFreq = currentFreq,
                        maxFreq = maxFreq,
                        modifier = Modifier.weight(1f),
                        onToggle = { onCoreToggle(core, !online) }
                    )
                }

                // If we have an odd number of cores, add a spacer for the last row
                if (rowCores.size == 1) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CoreCard(
    core: Int,
    online: Boolean,
    clusterType: String,
    currentFreq: String,
    maxFreq: String,
    modifier: Modifier = Modifier,
    onToggle: () -> Unit
) {
    val alpha by animateFloatAsState(targetValue = if (online) 1f else 0.6f, label = "Alpha")
    val statusColor = if (online)
        MaterialTheme.colorScheme.primary
    else
        MaterialTheme.colorScheme.error

    ElevatedCard(
        onClick = { if (core != 0) onToggle() },
        enabled = core != 0,
        modifier = modifier,
        elevation = CardDefaults.elevatedCardElevation(
            defaultElevation = if (online) 4.dp else 1.dp
        ),
        colors = CardDefaults.elevatedCardColors(
            containerColor = if (online)
                MaterialTheme.colorScheme.surfaceVariant
            else
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
        )
    ) {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth()
                .alpha(alpha),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "CPU",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )

            Text(
                text = "$core",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = if (online) "ONLINE" else "OFFLINE",
                style = MaterialTheme.typography.labelMedium,
                color = statusColor,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = clusterType.uppercase(),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(4.dp))

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = currentFreq,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )

                Text(
                    text = "Max: $maxFreq",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}