package com.ireddragonicy.champkernelmanager.data

import com.ireddragonicy.champkernelmanager.utils.FileUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class GpuManager {
    companion object {
        private const val GPU_PATH = "/sys/class/devfreq/13000000.mali/"
    }

    suspend fun getGpuInfo(): DevfreqInfo = withContext(Dispatchers.IO) {
        fun readFreqMHz(filePath: String): String {
            val hz = FileUtils.readFileAsRoot(filePath)?.toLongOrNull() ?: 0
            return "${hz / 1_000_000} MHz"
        }

        val maxFreq = readFreqMHz("${GPU_PATH}max_freq")
        val minFreq = readFreqMHz("${GPU_PATH}min_freq")
        val curFreq = readFreqMHz("${GPU_PATH}cur_freq")
        val targetFreq = readFreqMHz("${GPU_PATH}target_freq")

        val availableFreqs = FileUtils.readFileAsRoot("${GPU_PATH}available_frequencies")
            ?.split(" ")
            ?.mapNotNull { it.toLongOrNull() }
            ?.map { "${it / 1_000_000} MHz" }
            ?: emptyList()

        val availableGovs = FileUtils.readFileAsRoot("${GPU_PATH}available_governors")
            ?.split(" ")
            ?.filter { it.isNotBlank() }
            ?: emptyList()

        val currentGovernor = FileUtils.readFileAsRoot("${GPU_PATH}governor")?.trim() ?: "N/A"

        DevfreqInfo(
            name = "Mali GPU",
            path = GPU_PATH,
            maxFreqMHz = maxFreq,
            minFreqMHz = minFreq,
            curFreqMHz = curFreq,
            targetFreqMHz = targetFreq,
            availableFrequenciesMHz = availableFreqs,
            availableGovernors = availableGovs,
            currentGovernor = currentGovernor
        )
    }

    suspend fun setGpuMaxFreq(freq: String): Boolean = withContext(Dispatchers.IO) {
        val freqHz = freq.replace(" MHz", "").toLongOrNull()?.times(1_000_000) ?: return@withContext false
        FileUtils.writeFileAsRoot("${GPU_PATH}max_freq", freqHz.toString())
    }

    suspend fun setGpuMinFreq(freq: String): Boolean = withContext(Dispatchers.IO) {
        val freqHz = freq.replace(" MHz", "").toLongOrNull()?.times(1_000_000) ?: return@withContext false
        FileUtils.writeFileAsRoot("${GPU_PATH}min_freq", freqHz.toString())
    }

    suspend fun setGpuGovernor(governor: String): Boolean = withContext(Dispatchers.IO) {
        FileUtils.writeFileAsRoot("${GPU_PATH}governor", governor)
    }
}