@file:OptIn(ExperimentalMaterial3Api::class)

package com.ireddragonicy.champkernelmanager

import android.content.Context
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.ireddragonicy.champkernelmanager.ui.theme.ChampKernelManagerTheme
import kotlinx.coroutines.delay
import java.io.File

// Data classes
data class CpuCoreInfo(
    val coreNumber: Int,
    val curFreqMHz: String,
    val maxFreqMHz: String,
    val governor: String
)

data class CpuClusterInfo(
    val clusterName: String,
    val cores: List<CpuCoreInfo>
)

data class DevfreqInfo(
    val name: String,
    val path: String,
    val maxFreqMHz: String,
    val minFreqMHz: String,
    val curFreqMHz: String,
    val targetFreqMHz: String,
    val availableFrequenciesMHz: List<String> = emptyList(),
    val availableGovernors: List<String> = emptyList(),
    val currentGovernor: String = "N/A"
)

// File operations utilities
object FileUtils {
    fun readFile(filePath: String): String? = try {
        File(filePath).readText().trim().takeIf { it.isNotEmpty() }
    } catch (e: Exception) {
        Log.e("FileUtils", "Error reading file: $filePath", e)
        null
    }

    fun readFileAsRoot(filePath: String): String? = RootShell.runCommand("cat $filePath")

    fun writeFileAsRoot(filePath: String, value: String): Boolean =
        RootShell.runCommand("echo '$value' > $filePath")?.trim()?.isEmpty() == true
}

// CPU utilities
object CpuUtils {
    fun getCoreInfo(core: Int): CpuCoreInfo {
        val basePath = "/sys/devices/system/cpu/cpu$core/cpufreq/"

        fun getFreqMHz(freqKHz: String?) = if (freqKHz?.toLongOrNull() != null)
            "${freqKHz.toLong() / 1000} MHz" else "N/A"

        val curFreq = getFreqMHz(FileUtils.readFile(basePath + "scaling_cur_freq"))
        val maxFreq = getFreqMHz(FileUtils.readFile(basePath + "cpuinfo_max_freq"))
        val governor = FileUtils.readFileAsRoot(basePath + "scaling_governor") ?: "N/A"

        return CpuCoreInfo(core, curFreq, maxFreq, governor)
    }

    fun getSystemLoad(): String =
        FileUtils.readFileAsRoot("/proc/loadavg")?.split(" ")?.take(3)?.joinToString(" ") ?: "N/A"

    fun groupClusters(coreInfos: List<CpuCoreInfo>): List<CpuClusterInfo> {
        val groups = coreInfos.groupBy { it.maxFreqMHz }
        return if (groups.size > 1) {
            groups.toList()
                .sortedBy { it.first.split(" ").firstOrNull()?.toLongOrNull() ?: Long.MAX_VALUE }
                .mapIndexed { index, (_, cores) ->
                    val name = when {
                        groups.size == 2 -> if (index == 0) "Little" else "Big"
                        else -> "Cluster ${index + 1}"
                    }
                    CpuClusterInfo(name, cores)
                }
        } else {
            listOf(CpuClusterInfo("Prime", coreInfos))
        }
    }
}

// Devfreq utilities
object DevfreqUtils {
    val GPU_PATH = "/sys/class/devfreq/13000000.mali/"
    val MTK_DEVFREQ_PATH = "/sys/class/devfreq/mtk-dvfsrc-devfreq/"
    val UFSHCI_DEVFREQ_PATH = "/sys/class/devfreq/112b0000.ufshci/"

    fun getDevfreqInfo(devfreqPath: String, devfreqName: String): DevfreqInfo {
        fun getFreqMHz(freqKHz: String?) = if (freqKHz?.toLongOrNull() != null)
            "${freqKHz.toLong() / 1000} MHz" else "N/A"

        val maxFreq = getFreqMHz(FileUtils.readFileAsRoot("${devfreqPath}max_freq"))
        val minFreq = getFreqMHz(FileUtils.readFileAsRoot("${devfreqPath}min_freq"))
        val curFreq = getFreqMHz(FileUtils.readFileAsRoot("${devfreqPath}cur_freq"))
        val targetFreq = getFreqMHz(FileUtils.readFileAsRoot("${devfreqPath}target_freq"))

        val availableFreqs = FileUtils.readFileAsRoot("${devfreqPath}available_frequencies")
            ?.split(" ")
            ?.mapNotNull { it.toLongOrNull()?.let { freq -> "${freq / 1000} MHz" } }
            ?.filter { it != "0 MHz" }
            ?: emptyList()

        val availableGovernors = FileUtils.readFileAsRoot("${devfreqPath}available_governors")
            ?.split(" ")
            ?.map { it.trim() }
            ?.filter { it.isNotEmpty() && !it.startsWith("apu") }
            ?: emptyList()

        val currentGovernor = FileUtils.readFileAsRoot("${devfreqPath}governor")?.trim() ?: "N/A"

        return DevfreqInfo(
            devfreqName,
            devfreqPath,
            maxFreq,
            minFreq,
            curFreq,
            targetFreq,
            availableFreqs,
            availableGovernors,
            currentGovernor
        )
    }

    fun getGpuInfo(): DevfreqInfo = getDevfreqInfo(GPU_PATH, "GPU (Mali)")
    fun getMtkDevfreqInfo(): DevfreqInfo = getDevfreqInfo(MTK_DEVFREQ_PATH, "MTK Devfreq")
    fun getUfshciDevfreqInfo(): DevfreqInfo = getDevfreqInfo(UFSHCI_DEVFREQ_PATH, "UFSHCI Devfreq")
}

// UI Components
@Composable
fun FrequencyDropdown(
    label: String,
    value: String,
    expanded: Boolean,
    frequencies: List<String>,
    filePath: String,
    onExpandedChange: (Boolean) -> Unit,
    onValueChange: (String) -> Unit,
    context: Context // Pass context here for Toast
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(start = 16.dp, bottom = 2.dp)
    ) {
        Text(text = "$label: ")
        Spacer(modifier = Modifier.width(8.dp))

        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = onExpandedChange
        ) {
            TextField(
                readOnly = true,
                value = value,
                onValueChange = {},
                label = { Text(label) },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                colors = ExposedDropdownMenuDefaults.textFieldColors(),
                modifier = Modifier.menuAnchor()
            )

            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { onExpandedChange(false) }
            ) {
                frequencies.forEach { freq ->
                    DropdownMenuItem(
                        text = { Text(freq) },
                        onClick = {
                            onValueChange(freq)
                            onExpandedChange(false)
                            val freqKHz = freq.replace(" MHz", "").toLong() * 1000
                            if (FileUtils.writeFileAsRoot(filePath, freqKHz.toString())) {
                                Toast.makeText(context, "$label set to $freq", Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(context, "Failed to set $label", Toast.LENGTH_SHORT).show()
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun GovernorDropdown(
    label: String,
    currentGovernor: String,
    availableGovernors: List<String>,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    filePath: String,
    context: Context // Pass context here for Toast
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(start = 16.dp, bottom = 8.dp)
    ) {
        Text(text = "$label: ")
        Spacer(modifier = Modifier.width(8.dp))

        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = onExpandedChange
        ) {
            TextField(
                readOnly = true,
                value = currentGovernor,
                onValueChange = {},
                label = { Text(label) },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                colors = ExposedDropdownMenuDefaults.textFieldColors(),
                modifier = Modifier.menuAnchor()
            )

            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { onExpandedChange(false) }
            ) {
                availableGovernors.forEach { governor ->
                    DropdownMenuItem(
                        text = { Text(governor) },
                        onClick = {
                            onExpandedChange(false)
                            if (FileUtils.writeFileAsRoot(filePath, governor)) {
                                Toast.makeText(context, "$label set to $governor", Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(context, "Failed to set $label", Toast.LENGTH_SHORT).show()
                            }
                        }
                    )
                }
            }
        }
    }
}


// Main Activity and Screens
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ChampKernelManagerTheme {
                val isRooted = remember { mutableStateOf(isDeviceRooted()) }
                if (!isRooted.value) RootStatusScreen() else MainScreen()
            }
        }
    }

    private fun isDeviceRooted(): Boolean {
        val paths = arrayOf("/system/bin/su", "/system/xbin/su", "/sbin/su", "/vendor/bin/su", "/su/bin/su")
        return paths.any { File(it).exists() }
    }
}

@Composable
fun RootStatusScreen(modifier: Modifier = Modifier) {
    AlertDialog(
        onDismissRequest = { },
        title = { Text("Device Not Rooted") },
        text = { Text("This app requires root access to function.") },
        confirmButton = {
            Button(onClick = { }) {
                Text("OK")
            }
        },
        modifier = modifier
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen() {
    val coreCount = Runtime.getRuntime().availableProcessors()
    var cpuInfoState by remember { mutableStateOf<Pair<List<CpuClusterInfo>, String>?>(null) }
    var gpuInfoState by remember { mutableStateOf<DevfreqInfo?>(null) }
    var mtkDevfreqInfoState by remember { mutableStateOf<DevfreqInfo?>(null) }
    var ufshciDevfreqInfoState by remember { mutableStateOf<DevfreqInfo?>(null) }
    val context = LocalContext.current
    var selectedDevfreq by remember { mutableStateOf("GPU") } // "GPU", "MTK", "UFSHCI"

    LaunchedEffect(Unit) {
        while (true) {
            val cpuCoreInfos = (0 until coreCount).map { CpuUtils.getCoreInfo(it) }
            val clusters = CpuUtils.groupClusters(cpuCoreInfos)
            val systemLoad = CpuUtils.getSystemLoad()
            cpuInfoState = clusters to systemLoad
            gpuInfoState = DevfreqUtils.getGpuInfo()
            mtkDevfreqInfoState = DevfreqUtils.getMtkDevfreqInfo()
            ufshciDevfreqInfoState = DevfreqUtils.getUfshciDevfreqInfo()
            delay(1000L)
        }
    }

    Scaffold { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            // CPU Info Section
            cpuInfoState?.let { (clusters, systemLoad) ->
                Text("System Load: $systemLoad", modifier = Modifier.padding(bottom = 8.dp))
                clusters.forEach { cluster ->
                    CpuClusterSection(cluster)
                }
            } ?: Text("Loading CPU Info...")

            // Devfreq Selection
            DevfreqSelector(selectedDevfreq) { selectedDev ->
                selectedDevfreq = selectedDev
            }

            // Devfreq Info Section
            when (selectedDevfreq) {
                "GPU" -> gpuInfoState?.let { GpuSection(it, context) } ?: Text("Loading GPU Info...")
                "MTK" -> mtkDevfreqInfoState?.let { DevfreqSection(it, context) } ?: Text("Loading MTK Devfreq Info...")
                "UFSHCI" -> ufshciDevfreqInfoState?.let { DevfreqSection(it, context) } ?: Text("Loading UFSHCI Devfreq Info...")
            }
        }
    }
}

@Composable
fun DevfreqSelector(selectedDevfreq: String, onDevfreqChange: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val devfreqOptions = listOf("GPU", "MTK", "UFSHCI")

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(top = 16.dp, bottom = 8.dp, start = 16.dp)
    ) {
        Text(text = "Select Devfreq: ")
        Spacer(modifier = Modifier.width(8.dp))
        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = !expanded }
        ) {
            TextField(
                readOnly = true,
                value = selectedDevfreq,
                onValueChange = {},
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                modifier = Modifier.menuAnchor()
            )
            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                devfreqOptions.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(text = option) },
                        onClick = {
                            onDevfreqChange(option)
                            expanded = false
                        }
                    )
                }
            }
        }
    }
}


@Composable
private fun CpuClusterSection(cluster: CpuClusterInfo) {
    Text(
        text = "CPU Cluster: ${cluster.clusterName} (Governor: ${cluster.cores.firstOrNull()?.governor ?: "N/A"})",
        modifier = Modifier.padding(vertical = 4.dp)
    )
    cluster.cores.forEach { core ->
        Text(
            text = "Core ${core.coreNumber}: Current: ${core.curFreqMHz}, Max: ${core.maxFreqMHz}",
            modifier = Modifier.padding(start = 16.dp, bottom = 2.dp)
        )
    }
    Spacer(modifier = Modifier.height(8.dp))
}

@Composable
private fun DevfreqSection(devfreqInfo: DevfreqInfo, context: Context) {
    Text(
        text = devfreqInfo.name,
        modifier = Modifier.padding(top = 16.dp, bottom = 8.dp)
    )

    Text(
        text = "Current Frequency: ${devfreqInfo.curFreqMHz}",
        modifier = Modifier.padding(start = 16.dp, bottom = 2.dp)
    )

    var expandedTargetFreq by remember { mutableStateOf(false) }
    var expandedMaxFreq by remember { mutableStateOf(false) }
    var expandedMinFreq by remember { mutableStateOf(false) }
    var expandedGovernor by remember { mutableStateOf(false) }

    FrequencyDropdown(
        label = "Target Frequency",
        value = devfreqInfo.targetFreqMHz,
        expanded = expandedTargetFreq,
        frequencies = devfreqInfo.availableFrequenciesMHz,
        filePath = "${devfreqInfo.path}target_freq",
        onExpandedChange = { expandedTargetFreq = it },
        onValueChange = { }, // Handled in FrequencyDropdown with Toast and File write
        context = context
    )

    FrequencyDropdown(
        label = "Max Frequency",
        value = devfreqInfo.maxFreqMHz,
        expanded = expandedMaxFreq,
        frequencies = devfreqInfo.availableFrequenciesMHz,
        filePath = "${devfreqInfo.path}max_freq",
        onExpandedChange = { expandedMaxFreq = it },
        onValueChange = { }, // Handled in FrequencyDropdown with Toast and File write
        context = context
    )

    FrequencyDropdown(
        label = "Min Frequency",
        value = devfreqInfo.minFreqMHz,
        expanded = expandedMinFreq,
        frequencies = devfreqInfo.availableFrequenciesMHz,
        filePath = "${devfreqInfo.path}min_freq",
        onExpandedChange = { expandedMinFreq = it },
        onValueChange = { }, // Handled in FrequencyDropdown with Toast and File write
        context = context
    )

    GovernorDropdown(
        label = "Governor",
        currentGovernor = devfreqInfo.currentGovernor,
        availableGovernors = devfreqInfo.availableGovernors,
        expanded = expandedGovernor,
        onExpandedChange = { expandedGovernor = it },
        filePath = "${devfreqInfo.path}governor",
        context = context
    )
}


@Composable
private fun GpuSection(gpuInfo: DevfreqInfo, context: Context) {
    DevfreqSection(devfreqInfo = gpuInfo, context = context)
}