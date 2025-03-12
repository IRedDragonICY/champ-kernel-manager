package com.ireddragonicy.champkernelmanager.data

import com.ireddragonicy.champkernelmanager.utils.FileUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.*

class CpuManager {
    companion object {
        private const val CPU_PATH = "/sys/devices/system/cpu/cpu"
        private const val CPU_FREQ_PATH = "/cpufreq/"
        private const val CPU_ONLINE_PATH = "/online"
    }

    private val thermalManager = ThermalManager()

    suspend fun getCpuClusters(): List<CpuClusterInfo> = withContext(Dispatchers.IO) {
        val coreCount = Runtime.getRuntime().availableProcessors()
        val temperaturesMap = getCpuTemperatures()

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

    private suspend fun getCpuTemperatures(): Map<Int, String> = withContext(Dispatchers.IO) {
        val coreCount = Runtime.getRuntime().availableProcessors()
        val results = mutableMapOf<Int, String>()
        val allZones = thermalManager.getCpuThermalZones()

        for (coreIndex in 0 until coreCount) {
            val directZone = allZones.values.find { it.coreNumber == coreIndex && it.temp > 1f }
            if (directZone != null) {
                results[coreIndex] = String.format(Locale.US, "%.1f°C", directZone.temp)
            }
        }

        val littleZones = allZones.values.filter { it.cpuType == "little" && it.temp > 1f }
        val mediumZones = allZones.values.filter { it.cpuType == "medium" && it.temp > 1f }
        val bigZones = allZones.values.filter { it.cpuType == "big" && it.temp > 1f }

        if (coreCount == 8) {
            for (coreIndex in 0 until 8) {
                if (coreIndex in results) continue
                when {
                    coreIndex in 0..3 && littleZones.isNotEmpty() -> {
                        val avg = littleZones.map { it.temp }.average().toFloat()
                        results[coreIndex] = String.format(Locale.US, "%.1f°C", avg)
                    }
                    coreIndex in 4..6 && mediumZones.isNotEmpty() -> {
                        val avg = mediumZones.map { it.temp }.average().toFloat()
                        results[coreIndex] = String.format(Locale.US, "%.1f°C", avg)
                    }
                    coreIndex == 7 && bigZones.isNotEmpty() -> {
                        val avg = bigZones.map { it.temp }.average().toFloat()
                        results[coreIndex] = String.format(Locale.US, "%.1f°C", avg)
                    }
                }
            }
        } else {
            for (coreIndex in 0 until coreCount) {
                if (coreIndex in results) continue
                when {
                    coreIndex < (coreCount / 2) && littleZones.isNotEmpty() -> {
                        val avg = littleZones.map { it.temp }.average().toFloat()
                        results[coreIndex] = String.format(Locale.US, "%.1f°C", avg)
                    }
                    else -> {
                        val perfZones = if (bigZones.isNotEmpty()) bigZones else mediumZones
                        if (perfZones.isNotEmpty()) {
                            val avg = perfZones.map { it.temp }.average().toFloat()
                            results[coreIndex] = String.format(Locale.US, "%.1f°C", avg)
                        }
                    }
                }
            }
        }

        val genericCpuZones = allZones.values.filter {
            it.type.contains("cpu", ignoreCase = true) && it.temp > 1f && !it.type.contains("dsu", ignoreCase = true)
        }
        for (coreIndex in 0 until coreCount) {
            if (coreIndex !in results && genericCpuZones.isNotEmpty()) {
                val avg = genericCpuZones.map { it.temp }.average().toFloat()
                results[coreIndex] = String.format(Locale.US, "%.1f°C", avg)
            }
        }

        for (coreIndex in 0 until coreCount) {
            if (coreIndex !in results) {
                results[coreIndex] = "N/A"
            }
        }

        results
    }
}