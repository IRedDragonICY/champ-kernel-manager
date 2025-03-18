package com.ireddragonicy.champkernelmanager.data

import com.ireddragonicy.champkernelmanager.data.models.CoreControlInfo
import com.ireddragonicy.champkernelmanager.data.models.CpuClusterInfo
import com.ireddragonicy.champkernelmanager.data.models.CpuCoreInfo
import com.ireddragonicy.champkernelmanager.utils.FileUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class CpuManager {
    companion object {
        private const val CPU_PATH = "/sys/devices/system/cpu/cpu"
        private const val CPU_FREQ_PATH = "/cpufreq/"
        private const val CPU_ONLINE_PATH = "/online"
    }

    private val thermalManager = ThermalManager()

    suspend fun getCpuClusters(): List<CpuClusterInfo> = withContext(Dispatchers.IO) {
        val coreCount = Runtime.getRuntime().availableProcessors()
        val temperaturesMap = thermalManager.getCpuTemperatures()

        val cpuCoreInfos = (0 until coreCount).map { core ->
            val basePath = "$CPU_PATH$core$CPU_FREQ_PATH"
            val onlinePath = "$CPU_PATH$core$CPU_ONLINE_PATH"

            fun readFreqMHz(filePath: String): String {
                val freqKHz = FileUtils.readFileAsRoot(filePath)?.toLongOrNull() ?: 0
                return "${freqKHz / 1000} MHz"
            }

            val curFreq = readFreqMHz(basePath + "scaling_cur_freq")
            val hwMaxFreq = readFreqMHz(basePath + "cpuinfo_max_freq")
            val scalingMaxFreq = readFreqMHz(basePath + "scaling_max_freq")
            val minFreq = readFreqMHz(basePath + "scaling_min_freq")
            val governor = FileUtils.readFileAsRoot(basePath + "scaling_governor") ?: "N/A"
            val isOnline = FileUtils.readFileAsRoot(onlinePath) == "1" || core == 0
            val tempDisplay = temperaturesMap[core] ?: "N/A"

            CpuCoreInfo(
                core = core,
                curFreqMHz = curFreq,
                hwMaxFreqMHz = hwMaxFreq,
                scalingMaxFreqMHz = scalingMaxFreq,
                minFreqMHz = minFreq,
                governor = governor,
                online = isOnline,
                temperature = tempDisplay
            )
        }

        val grouped = cpuCoreInfos.groupBy {
            it.hwMaxFreqMHz.split(" ").firstOrNull()?.toLongOrNull() ?: 0L
        }.toList().sortedBy { it.first }

        val clusterNames = when (grouped.size) {
            2 -> listOf("Little", "Big")
            3 -> listOf("Little", "Medium", "Big")
            else -> grouped.mapIndexed { idx, _ -> "Cluster ${idx + 1}" }
        }

        grouped.mapIndexed { index, pair ->
            CpuClusterInfo(name = clusterNames.getOrNull(index) ?: "Cluster ${index + 1}", cores = pair.second)
        }
    }

    suspend fun getAvailableGovernors(): List<String> = withContext(Dispatchers.IO) {
        FileUtils.readFileAsRoot("${CPU_PATH}0${CPU_FREQ_PATH}scaling_available_governors")
            ?.split(" ")
            ?.filter { it.isNotBlank() }
            ?: emptyList()
    }

    suspend fun setAllCoresGovernor(governor: String): Boolean = withContext(Dispatchers.IO) {
        val coreCount = Runtime.getRuntime().availableProcessors()
        (0 until coreCount).all { core ->
            FileUtils.writeFileAsRoot("${CPU_PATH}$core${CPU_FREQ_PATH}scaling_governor", governor)
        }
    }

    suspend fun setScalingMaxFreq(core: Int, freq: String): Boolean = withContext(Dispatchers.IO) {
        val freqKHz = freq.replace(" MHz", "").toLongOrNull()?.times(1000) ?: return@withContext false
        FileUtils.writeFileAsRoot("${CPU_PATH}$core${CPU_FREQ_PATH}scaling_max_freq", freqKHz.toString())
    }

    suspend fun setScalingMinFreq(core: Int, freq: String): Boolean = withContext(Dispatchers.IO) {
        val freqKHz = freq.replace(" MHz", "").toLongOrNull()?.times(1000) ?: return@withContext false
        FileUtils.writeFileAsRoot("${CPU_PATH}$core${CPU_FREQ_PATH}scaling_min_freq", freqKHz.toString())
    }

    suspend fun getCoreControlInfo(): CoreControlInfo = withContext(Dispatchers.IO) {
        val coreCount = Runtime.getRuntime().availableProcessors()
        val coreStates = (0 until coreCount).associate { c ->
            val path = "$CPU_PATH$c$CPU_ONLINE_PATH"
            val online = if (c == 0) true else FileUtils.readFileAsRoot(path) == "1"
            c to online
        }
        val supported = coreStates.any { !it.value } || coreCount > 1
        CoreControlInfo(supported, coreStates)
    }

    suspend fun setCoreState(core: Int, enabled: Boolean): Boolean = withContext(Dispatchers.IO) {
        if (core == 0) return@withContext false
        FileUtils.writeFileAsRoot("$CPU_PATH$core$CPU_ONLINE_PATH", if (enabled) "1" else "0")
    }
}