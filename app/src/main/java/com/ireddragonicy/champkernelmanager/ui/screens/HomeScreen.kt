package com.ireddragonicy.champkernelmanager.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import com.ireddragonicy.champkernelmanager.data.DataRepository
import com.ireddragonicy.champkernelmanager.ui.components.*
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(onNavigateToCoreControl: () -> Unit) {
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    var systemLoad by remember { mutableStateOf("") }
    var refreshTrigger by remember { mutableStateOf(0) }
    val dataRepository = DataRepository.getInstance()

    LaunchedEffect(Unit) {
        while (true) {
            systemLoad = dataRepository.getSystemLoad()
            refreshTrigger++
            delay(2000)
        }
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(
                title = { Text("Champ Kernel Manager") },
                scrollBehavior = scrollBehavior
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {

            CpuSection(
                refreshTrigger = refreshTrigger,
                onNavigateToCoreControl = onNavigateToCoreControl
            )
            GpuSection(refreshTrigger = refreshTrigger)
            BatterySection(refreshTrigger = refreshTrigger)
            ThermalSection(refreshTrigger = refreshTrigger)
            SystemSection(refreshTrigger = refreshTrigger)
        }
    }
}