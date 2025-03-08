package com.ireddragonicy.champkernelmanager.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ireddragonicy.champkernelmanager.data.CpuClusterInfo
import com.ireddragonicy.champkernelmanager.data.DataRepository
import com.ireddragonicy.champkernelmanager.ui.components.CoreControlGrid
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CoreControlScreen(onBackPressed: () -> Unit) {
    var coreControlInfo by remember { mutableStateOf<DataRepository.CoreControlInfo?>(null) }
    var clusters by remember { mutableStateOf<List<CpuClusterInfo>>(emptyList()) }

    val dataRepository = DataRepository.getInstance()
    val coroutineScope = rememberCoroutineScope()
    val scrollState = rememberScrollState()

    LaunchedEffect(Unit) {
        coreControlInfo = dataRepository.getCoreControlInfo()
        clusters = dataRepository.getCpuClusters()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Core Control") },
                navigationIcon = {
                    IconButton(onClick = onBackPressed) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
                .verticalScroll(scrollState)
        ) {
            Text(
                text = "CPU Core Management",
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            Text(
                text = "Toggle individual CPU cores by tapping on them. Core 0 cannot be disabled.",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            coreControlInfo?.let { coreControl ->
                if (coreControl.supported) {
                    CoreControlGrid(
                        coreControl = coreControl,
                        cpuClusters = clusters,
                        onCoreToggle = { core, enabled ->
                            coroutineScope.launch {
                                if (dataRepository.setCoreState(core, enabled)) {
                                    // Update the local state to reflect the change
                                    coreControlInfo = dataRepository.getCoreControlInfo()
                                }
                            }
                        }
                    )
                } else {
                    Text(
                        text = "Core control is not supported on this device",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            } ?: Text(
                text = "Loading core information...",
                style = MaterialTheme.typography.bodyLarge
            )
        }
    }
}