@file:OptIn(ExperimentalMaterial3Api::class)

package com.ireddragonicy.champkernelmanager

import android.content.Context
import android.os.Bundle
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
import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import java.io.File

// Initialize Shell on app startup
class App : android.app.Application() {
    companion object {
        init {
            // Set flags to configure the shell
            Shell.enableVerboseLogging = BuildConfig.DEBUG
            Shell.setDefaultBuilder(
                Shell.Builder.create()
                    .setFlags(0)
                    .setTimeout(10)
            )
        }
    }

    override fun onCreate() {
        super.onCreate()
        // Initialize the shell asynchronously
        Shell.getShell()
    }
}

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
    } catch (_: Exception) {
        null
    }

    fun readFileAsRoot(filePath: String): String? {
        val result = Shell.cmd("cat $filePath").exec()
        return if (result.isSuccess) result.out.joinToString("\n").trim().takeIf { it.isNotEmpty() } else null
    }

    fun writeFileAsRoot(filePath: String, value: String): Boolean {
        return Shell.cmd("echo '$value' > $filePath").exec().isSuccess
    }

    fun runCommandAsRoot(command: String): String? {
        val result = Shell.cmd(command).exec()
        return if (result.isSuccess) result.out.joinToString("\n").trim() else null
    }
}

// CPU utilities
object CpuUtils {
    fun getCoreInfo(core: Int): CpuCoreInfo {
        val basePath = "/sys/devices/system/cpu/cpu$core/cpufreq/"

        fun getFreqMHz(freqKHz: String?) =
            if (freqKHz?.toLongOrNull() != null)
                "${freqKHz.toLong() / 1000} MHz" else "N/A"

        val curFreq = getFreqMHz(FileUtils.readFile(basePath + "scaling_cur_freq")
                            ?: FileUtils.readFileAsRoot(basePath + "scaling_cur_freq"))
        val maxFreq = getFreqMHz(FileUtils.readFile(basePath + "cpuinfo_max_freq")
                            ?: FileUtils.readFileAsRoot(basePath + "cpuinfo_max_freq"))
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
                    val name = when (groups.size) {
                        2 -> if (index == 0) "Little" else "Big"
                        3 -> when (index) {
                            0 -> "Little"
                            1 -> "Big"
                            else -> "Prime"
                        }
                        else -> "Cluster ${index + 1}"
                    }
                    CpuClusterInfo(name, cores)
                }
        } else {
            listOf(CpuClusterInfo("Single Cluster", coreInfos))
        }
    }
}

// Devfreq utilities
object DevfreqUtils {
    const val GPU_PATH = "/sys/class/devfreq/13000000.mali/"
    const val MTK_DEVFREQ_PATH = "/sys/class/devfreq/mtk-dvfsrc-devfreq/"
    const val UFSHCI_DEVFREQ_PATH = "/sys/class/devfreq/112b0000.ufshci/"

    fun getDevfreqInfo(devfreqPath: String, devfreqName: String): DevfreqInfo {
        fun getFreqMHz(freqKHz: String?) =
            if (freqKHz?.toLongOrNull() != null)
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

// Thermal utilities
object ThermalUtils {
    fun disableThermalControl(): Boolean {
        val commands = listOf(
            "setprop persist.sys.turbosched.thermal_break.enable true",
            "stop thermald",
            "stop mi_thermald",
            "stop vendor.thermal-mediatek"
        )

        // Execute all commands as a batch
        val result = Shell.cmd(*commands.toTypedArray()).exec()

        // Check if any service status changed to verify the operation worked
        val thermalBreakDisabled = FileUtils.runCommandAsRoot("getprop persist.sys.turbosched.thermal_break.enable") == "true"
        val thermaldStopped = FileUtils.runCommandAsRoot("getprop init.svc.thermald") !in listOf("running", "restarting")
        val miThermaldStopped = FileUtils.runCommandAsRoot("getprop init.svc.mi_thermald") !in listOf("running", "restarting")
        val vendorThermaldStopped = FileUtils.runCommandAsRoot("getprop init.svc.vendor.thermal-mediatek") !in listOf("running", "restarting")

        return result.isSuccess && (thermalBreakDisabled || thermaldStopped || miThermaldStopped || vendorThermaldStopped)
    }

    fun enableThermalControl(): Boolean {
        val commands = listOf(
            "setprop persist.sys.turbosched.thermal_break.enable false",
            "start thermald",
            "start mi_thermald",
            "start vendor.thermal-mediatek"
        )

        // Execute all commands as a batch
        val result = Shell.cmd(*commands.toTypedArray()).exec()

        // Check if any service status changed to verify the operation worked
        val thermalBreakEnabled = FileUtils.runCommandAsRoot("getprop persist.sys.turbosched.thermal_break.enable") == "false"
        val thermaldRunning = FileUtils.runCommandAsRoot("getprop init.svc.thermald") == "running"
        val miThermaldRunning = FileUtils.runCommandAsRoot("getprop init.svc.mi_thermald") == "running"
        val vendorThermaldRunning = FileUtils.runCommandAsRoot("getprop init.svc.vendor.thermal-mediatek") == "running"

        return result.isSuccess && (thermalBreakEnabled || thermaldRunning || miThermaldRunning || vendorThermaldRunning)
    }
}

// UI Components
@Composable
fun frequencyDropdown(
    label: String,
    value: String,
    expanded: Boolean,
    frequencies: List<String>,
    filePath: String,
    onExpandedChange: (Boolean) -> Unit,
    onValueChange: (String) -> Unit,
    context: Context
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
                modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryEditable, enabled = true)
            )
            DropdownMenu(
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
fun governorDropdown(
    label: String,
    currentGovernor: String,
    availableGovernors: List<String>,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    filePath: String,
    context: Context
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
                modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryEditable, enabled = true)
            )
            DropdownMenu(
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

@Composable
fun thermalControlSection(context: Context) {
    var isThermalServicesDisabled by remember { mutableStateOf(false) }

    Column(modifier = Modifier.padding(start = 16.dp, bottom = 8.dp, top = 16.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = "Disable Thermal Services: ")
            Spacer(modifier = Modifier.width(8.dp))
            Switch(
                checked = isThermalServicesDisabled,
                onCheckedChange = {
                    if (it) {
                        if (ThermalUtils.disableThermalControl()) {
                            isThermalServicesDisabled = true
                            Toast.makeText(context, "Thermal Services Disabled", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(context, "Failed to Disable Thermal Services", Toast.LENGTH_SHORT).show()
                        }
                    } else {
                        if (ThermalUtils.enableThermalControl()) {
                            isThermalServicesDisabled = false
                            Toast.makeText(context, "Thermal Services Enabled", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(context, "Failed to Enable Thermal Services", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            )
        }
        if (isThermalServicesDisabled) {
            Text(
                text = "Warning: Thermal control is disabled. Device may overheat. Use with extreme caution!",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(start = 0.dp, top = 4.dp)
            )
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
                val isRooted = remember { mutableStateOf(false) }
                LaunchedEffect(Unit) {
                    isRooted.value = Shell.getShell().isRoot
                }

                if (!isRooted.value) {
                    rootStatusScreen()
                } else {
                    mainScreen()
                }
            }
        }
    }
}

@Composable
fun rootStatusScreen(modifier: Modifier = Modifier) {
    AlertDialog(
        onDismissRequest = { },
        title = { Text("Device Not Rooted") },
        text = { Text("This app requires root access to function. Please grant root access and restart the app.") },
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
fun mainScreen() {
    val coreCount = Runtime.getRuntime().availableProcessors()
    var cpuInfoState by remember { mutableStateOf<Pair<List<CpuClusterInfo>, String>?>(null) }
    var gpuInfoState by remember { mutableStateOf<DevfreqInfo?>(null) }
    var mtkDevfreqInfoState by remember { mutableStateOf<DevfreqInfo?>(null) }
    var ufshciDevfreqInfoState by remember { mutableStateOf<DevfreqInfo?>(null) }
    val context = LocalContext.current
    var selectedDevfreq by remember { mutableStateOf("GPU") }

    LaunchedEffect(Unit) {
        val updateIntervalMillis = 2000L
        while (isActive) {
            val cpuCoreInfos = (0 until coreCount).map { CpuUtils.getCoreInfo(it) }
            val clusters = CpuUtils.groupClusters(cpuCoreInfos)
            val systemLoad = CpuUtils.getSystemLoad()
            cpuInfoState = clusters to systemLoad

            gpuInfoState = DevfreqUtils.getGpuInfo()
            try { mtkDevfreqInfoState = DevfreqUtils.getMtkDevfreqInfo() } catch (_: Exception) {}
            try { ufshciDevfreqInfoState = DevfreqUtils.getUfshciDevfreqInfo() } catch (_: Exception) {}

            delay(updateIntervalMillis)
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
            Text(
                text = "Champ Kernel Manager",
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            cpuInfoState?.let { (clusters, systemLoad) ->
                Text("System Load: $systemLoad", modifier = Modifier.padding(bottom = 8.dp))
                clusters.forEach { cluster ->
                    cpuClusterSection(cluster)
                }
            } ?: Text("Loading CPU Info...")

            devfreqSelector(selectedDevfreq) { selectedDev ->
                selectedDevfreq = selectedDev
            }

            when (selectedDevfreq) {
                "GPU" -> gpuInfoState?.let { gpuSection(it, context) } ?: Text("Loading GPU Info...")
                "MTK" -> mtkDevfreqInfoState?.let { devfreqSection(it, context) }
                       ?: Text("MTK Devfreq not available on this device")
                "UFSHCI" -> ufshciDevfreqInfoState?.let { devfreqSection(it, context) }
                          ?: Text("UFSHCI Devfreq not available on this device")
            }

            thermalControlSection(context = context)
        }
    }
}

@Composable
fun devfreqSelector(selectedDevfreq: String, onDevfreqChange: (String) -> Unit) {
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
                modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryEditable, enabled = true)
            )
            DropdownMenu(
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
private fun cpuClusterSection(cluster: CpuClusterInfo) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            Text(
                text = "CPU Cluster: ${cluster.clusterName} (Governor: ${cluster.cores.firstOrNull()?.governor ?: "N/A"})",
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(modifier = Modifier.height(4.dp))
            cluster.cores.forEach { core ->
                Text(
                    text = "Core ${core.coreNumber}: Current: ${core.curFreqMHz}, Max: ${core.maxFreqMHz}",
                    modifier = Modifier.padding(start = 16.dp, bottom = 2.dp),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}

@Composable
private fun devfreqSection(devfreqInfo: DevfreqInfo, context: Context) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = devfreqInfo.name,
                style = MaterialTheme.typography.titleMedium
            )

            Text(
                text = "Current Frequency: ${devfreqInfo.curFreqMHz}",
                modifier = Modifier.padding(start = 16.dp, top = 8.dp, bottom = 2.dp)
            )

            var expandedTargetFreq by remember { mutableStateOf(false) }
            var expandedMaxFreq by remember { mutableStateOf(false) }
            var expandedMinFreq by remember { mutableStateOf(false) }
            var expandedGovernor by remember { mutableStateOf(false) }

            frequencyDropdown(
                label = "Target Frequency",
                value = devfreqInfo.targetFreqMHz,
                expanded = expandedTargetFreq,
                frequencies = devfreqInfo.availableFrequenciesMHz,
                filePath = "${devfreqInfo.path}target_freq",
                onExpandedChange = { expandedTargetFreq = it },
                onValueChange = { },
                context = context
            )

            frequencyDropdown(
                label = "Max Frequency",
                value = devfreqInfo.maxFreqMHz,
                expanded = expandedMaxFreq,
                frequencies = devfreqInfo.availableFrequenciesMHz,
                filePath = "${devfreqInfo.path}max_freq",
                onExpandedChange = { expandedMaxFreq = it },
                onValueChange = { },
                context = context
            )

            frequencyDropdown(
                label = "Min Frequency",
                value = devfreqInfo.minFreqMHz,
                expanded = expandedMinFreq,
                frequencies = devfreqInfo.availableFrequenciesMHz,
                filePath = "${devfreqInfo.path}min_freq",
                onExpandedChange = { expandedMinFreq = it },
                onValueChange = { },
                context = context
            )

            if (devfreqInfo.availableGovernors.isNotEmpty()) {
                governorDropdown(
                    label = "Governor",
                    currentGovernor = devfreqInfo.currentGovernor,
                    availableGovernors = devfreqInfo.availableGovernors,
                    expanded = expandedGovernor,
                    onExpandedChange = { expandedGovernor = it },
                    filePath = "${devfreqInfo.path}governor",
                    context = context
                )
            }
        }
    }
}

@Composable
private fun gpuSection(gpuInfo: DevfreqInfo, context: Context) {
    devfreqSection(devfreqInfo = gpuInfo, context = context)
}