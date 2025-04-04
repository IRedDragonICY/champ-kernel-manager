package com.ireddragonicy.champkernelmanager.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.ireddragonicy.champkernelmanager.data.models.CpuClusterInfo
import com.ireddragonicy.champkernelmanager.data.models.CoreControlInfo

@Composable
fun CoreControlGrid(
    coreControl: CoreControlInfo,
    cpuClusters: List<CpuClusterInfo>,
    onCoreToggle: (Int, Boolean) -> Unit
) {
    val coreToClusterMap = cpuClusters.flatMap { cluster ->
        cluster.cores.map { core -> core.core to cluster.name }
    }.toMap()

    val coreInfoMap = cpuClusters.flatMap { cluster ->
        cluster.cores.map { core ->
            core.core to Pair(core.curFreqMHz, core.scalingMaxFreqMHz)
        }
    }.toMap()

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
                rowCores.forEach { entry ->
                    val core = entry.key
                    val online = entry.value
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
    var isPressed by remember { mutableStateOf(false) }

    val alpha by animateFloatAsState(
        targetValue = if (online) 1f else 0.6f,
        label = "Alpha"
    )

    val backgroundColor by animateColorAsState(
        targetValue = when {
            isPressed -> MaterialTheme.colorScheme.primaryContainer
            online -> MaterialTheme.colorScheme.surfaceVariant
            else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
        },
        animationSpec = tween(durationMillis = 200),
        label = "BackgroundColor"
    )

    val statusColor = if (online)
        MaterialTheme.colorScheme.primary
    else
        MaterialTheme.colorScheme.error

    val isEnabled = core != 0

    ElevatedCard(
        onClick = {
            if (isEnabled) {
                isPressed = true
                onToggle()
                // Reset pressed state after a short delay
                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                    isPressed = false
                }, 200)
            }
        },
        enabled = isEnabled,
        modifier = modifier,
        elevation = CardDefaults.elevatedCardElevation(
            defaultElevation = if (online) 4.dp else 1.dp
        ),
        colors = CardDefaults.elevatedCardColors(
            containerColor = backgroundColor,
            disabledContainerColor = if (online)
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