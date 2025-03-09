package com.ireddragonicy.champkernelmanager.data

import android.os.SystemClock
import android.util.Log
import com.ireddragonicy.champkernelmanager.utils.FileUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale
import java.util.regex.Pattern

class DataRepository private constructor() {

    companion object {
        private var INSTANCE: DataRepository? = null
        private const val TAG = "DataRepository"

        fun getInstance(): DataRepository {
            return INSTANCE ?: synchronized(this) {
                val instance = DataRepository()
                INSTANCE = instance
                instance
            }
        }

        private const val CPU_PATH = "/sys/devices/system/cpu/cpu"
        private const val CPU_FREQ_PATH = "/cpufreq/"
        private const val CPU_ONLINE_PATH = "/online"

        private const val GPU_PATH = "/sys/class/devfreq/13000000.mali/"

        private const val BATTERY_PATH = "/sys/class/power_supply/battery/"

        private const val THERMAL_BASE_PATH = "/sys/class/thermal/"

        private const val TCP_CONGESTION_PATH = "/proc/sys/net/ipv4/tcp_congestion_control"
        private const val AVAILABLE_TCP_CONGESTION_PATH = "/proc/sys/net/ipv4/tcp_available_congestion_control"
        private const val IO_SCHEDULER_PATH = "/sys/block/mmcblk0/queue/scheduler"
    }

    private var cpuThermalZonesCache: Map<Int, ThermalZoneInfo>? = null
    private var thermalZoneCacheTime: Long = 0
    private val THERMAL_CACHE_VALID_MS = 2000
    data class CoreControlInfo(
        val supported: Boolean,
        val cores: Map<Int, Boolean>
    )


    data class ThermalZoneInfo(
        val zoneId: Int,
        val type: String,
        val temp: Float,
        val cpuType: String? = null,
        val coreNumber: Int? = null
    )

    suspend fun getSystemLoad(): String = withContext(Dispatchers.IO) {
        FileUtils.readFileAsRoot("/proc/loadavg")
            ?.split(" ")
            ?.take(3)
            ?.joinToString(" ")
            ?: "N/A"
    }


    private suspend fun getCpuThermalZones(): Map<Int, ThermalZoneInfo> = withContext(Dispatchers.IO) {
        val currentTime = System.currentTimeMillis()
        cpuThermalZonesCache?.let {
            if (currentTime - thermalZoneCacheTime < THERMAL_CACHE_VALID_MS) {
                Log.d(TAG, "Using cached thermal zone data")
                return@withContext it
            }
        }

        val typesRaw = FileUtils.runCommandAsRoot("su -c \"cat ${THERMAL_BASE_PATH}thermal_zone*/type\"") ?: ""
        val types = typesRaw.split('\n').filter { it.isNotBlank() }

        Log.d(TAG, "Found ${types.size} total thermal zones by type listing")

        val cpuZoneIndices = mutableListOf<Int>()
        val cpuZoneTypes = mutableListOf<String>()
        for ((index, type) in types.withIndex()) {
            if ((type.contains("cpu", ignoreCase = true) || type.contains("core", ignoreCase = true))
                && !type.contains("dsu", ignoreCase = true)
            ) {
                cpuZoneIndices.add(index)
                cpuZoneTypes.add(type)
                Log.d(TAG, "Potential CPU zone: thermal_zone$index => $type")
            }
        }

        val tempCommand = "cat " + cpuZoneIndices.joinToString(" ") {
            "${THERMAL_BASE_PATH}thermal_zone$it/temp"
        }
        val tempRawItems = if (cpuZoneIndices.isNotEmpty()) {
            FileUtils.runCommandAsRoot("su -c \"$tempCommand\"")?.split('\n')?.filter { it.isNotBlank() }
        } else null

        data class TempRecord(
            val zoneId: Int,
            val zoneType: String,
            val temperature: Float,
            val cpuType: String?,
            val coreNumber: Int?
        )
        val tempRecords = mutableListOf<TempRecord>()

        for (i in cpuZoneIndices.indices) {
            val zoneId = cpuZoneIndices[i]
            val zoneType = cpuZoneTypes[i]
            val tempValRaw = tempRawItems?.getOrNull(i)?.toFloatOrNull()
                ?: FileUtils.readFileAsRoot("${THERMAL_BASE_PATH}thermal_zone$zoneId/temp")?.toFloatOrNull()
                ?: continue

            val tempCelsius = tempValRaw / 1000f
            var cpuType: String? = null
            var coreNumber: Int? = null

            when {
                zoneType.contains("little", ignoreCase = true) -> cpuType = "little"
                zoneType.contains("medium", ignoreCase = true) -> cpuType = "medium"
                zoneType.contains("big", ignoreCase = true) || zoneType.contains("prime", ignoreCase = true) -> cpuType = "big"
            }

            val pattern = Pattern.compile("core(\\d+)(?:-(\\d+))?", Pattern.CASE_INSENSITIVE)
            val matcher = pattern.matcher(zoneType)
            if (matcher.find()) {
                coreNumber = matcher.group(1)?.toIntOrNull()
            }

            tempRecords.add(
                TempRecord(
                    zoneId = zoneId,
                    zoneType = zoneType,
                    temperature = tempCelsius,
                    cpuType = cpuType,
                    coreNumber = coreNumber
                )
            )
        }

        val grouped = tempRecords.groupBy { Pair(it.cpuType, it.coreNumber) }
        val finalMap = mutableMapOf<Int, ThermalZoneInfo>()
        var assignedZoneId = 0

        grouped.forEach { (key, list) ->
            if (key.first != null && key.second != null && list.isNotEmpty()) {
                val avgTemp = list.map { it.temperature }.average().toFloat()
                finalMap[assignedZoneId] = ThermalZoneInfo(
                    zoneId = assignedZoneId,
                    type = "cpu-${key.first}-core${key.second}", // simplified name
                    temp = avgTemp,
                    cpuType = key.first,
                    coreNumber = key.second
                )
                assignedZoneId++
            } else {
                list.forEach { rec ->
                    finalMap[assignedZoneId] = ThermalZoneInfo(
                        zoneId = assignedZoneId,
                        type = rec.zoneType,
                        temp = rec.temperature,
                        cpuType = rec.cpuType,
                        coreNumber = rec.coreNumber
                    )
                    assignedZoneId++
                }
            }
        }

        cpuThermalZonesCache = finalMap
        thermalZoneCacheTime = currentTime
        finalMap
    }


    private suspend fun getCpuTemperatures(): Map<Int, String> = withContext(Dispatchers.IO) {
        val coreCount = Runtime.getRuntime().availableProcessors()
        val results = mutableMapOf<Int, String>()
        val allZones = getCpuThermalZones()

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

        // Group by hardware max freq to guess clusters
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

    suspend fun getThermalInfo(): ThermalInfo = withContext(Dispatchers.IO) {
        val typesRaw = FileUtils.runCommandAsRoot("su -c \"cat ${THERMAL_BASE_PATH}thermal_zone*/type\"") ?: ""
        val typeList = typesRaw.split('\n').filter { it.isNotBlank() }

        // single cat command for all 'temp' files
        val tempCmd = "cat " + typeList.indices.joinToString(" ") {
            "${THERMAL_BASE_PATH}thermal_zone$it/temp"
        }
        val tempsRaw = FileUtils.runCommandAsRoot("su -c \"$tempCmd\"") ?: ""
        val tempsList = tempsRaw.split('\n').filter { it.isNotBlank() }

        val zoneData = mutableListOf<ThermalZone>()
        for (i in typeList.indices) {
            val zoneType = typeList[i]
            val rawVal = tempsList.getOrNull(i)?.toFloatOrNull()
            if (rawVal != null) {
                val celsius = rawVal / 1000
                zoneData.add(ThermalZone(zoneType, celsius))
            }
        }

        val thermald = FileUtils.runCommandAsRoot("getprop init.svc.thermald") == "running"
        val miThermald = FileUtils.runCommandAsRoot("getprop init.svc.mi_thermald") == "running"
        val mediatekThermald = FileUtils.runCommandAsRoot("getprop init.svc.vendor.thermal-mediatek") == "running"
        val thermalEnabled = thermald || miThermald || mediatekThermald

        ThermalInfo(zones = zoneData, thermalServicesEnabled = thermalEnabled)
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
        // Usually core0 can't be offline
        if (core == 0) return@withContext false
        FileUtils.writeFileAsRoot("$CPU_PATH$core$CPU_ONLINE_PATH", if (enabled) "1" else "0")
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

    suspend fun getBatteryInfo(): BatteryInfo = withContext(Dispatchers.IO) {
        val status = FileUtils.readFileAsRoot("${BATTERY_PATH}status") ?: "Unknown"
        val level = FileUtils.readFileAsRoot("${BATTERY_PATH}capacity")?.toIntOrNull() ?: 0
        val tempRaw = FileUtils.readFileAsRoot("${BATTERY_PATH}temp")?.toFloatOrNull() ?: 0f
        val temperature = tempRaw / 10f
        val currentNow = FileUtils.readFileAsRoot("${BATTERY_PATH}current_now")?.toIntOrNull()?.div(1000) ?: 0
        val voltage = FileUtils.readFileAsRoot("${BATTERY_PATH}voltage_now")?.toIntOrNull()?.div(1000) ?: 0
        val health = FileUtils.readFileAsRoot("${BATTERY_PATH}health") ?: "Unknown"
        val technology = FileUtils.readFileAsRoot("${BATTERY_PATH}technology") ?: "Unknown"

        val fastChargeSupported = File("/sys/kernel/fast_charge/force_fast_charge").exists()
        val fastChargeEnabled = if (fastChargeSupported) {
            (FileUtils.readFileAsRoot("/sys/kernel/fast_charge/force_fast_charge") == "1")
        } else false

        val chargingLimitFile = File("/sys/class/power_supply/battery/charge_control_limit")
        val chargingLimitSupported = chargingLimitFile.exists()
        val chargingLimit = if (chargingLimitSupported) {
            FileUtils.readFileAsRoot(chargingLimitFile.absolutePath)?.toIntOrNull() ?: 100
        } else 100

        BatteryInfo(
            status = status,
            level = level,
            temperature = temperature,
            currentNow = currentNow,
            voltage = voltage,
            health = health,
            technology = technology,
            fastChargeSupported = fastChargeSupported,
            fastChargeEnabled = fastChargeEnabled,
            chargingLimitSupported = chargingLimitSupported,
            chargingLimit = chargingLimit
        )
    }

    suspend fun setFastCharge(enabled: Boolean): Boolean = withContext(Dispatchers.IO) {
        FileUtils.writeFileAsRoot("/sys/kernel/fast_charge/force_fast_charge", if (enabled) "1" else "0")
    }

    suspend fun setChargingLimit(limit: Int): Boolean = withContext(Dispatchers.IO) {
        FileUtils.writeFileAsRoot("/sys/class/power_supply/battery/charge_control_limit", limit.toString())
    }

    suspend fun setThermalServices(enabled: Boolean): Boolean = withContext(Dispatchers.IO) {
        if (enabled) {
            val cmds = listOf(
                "setprop persist.sys.turbosched.thermal_break.enable false",
                "start thermald",
                "start mi_thermald",
                "start vendor.thermal-mediatek"
            )
            cmds.all { FileUtils.runCommandAsRoot(it) != null }
        } else {
            val cmds = listOf(
                "setprop persist.sys.turbosched.thermal_break.enable true",
                "stop thermald",
                "stop mi_thermald",
                "stop vendor.thermal-mediatek"
            )
            cmds.all { FileUtils.runCommandAsRoot(it) != null }
        }
    }

    suspend fun getSystemInfo(): SystemInfo = withContext(Dispatchers.IO) {
        val kernelVersion = FileUtils.readFileAsRoot("uname -r") ?: "Unknown"
        val kernelBuild = FileUtils.readFileAsRoot("cat /proc/version") ?: "Unknown"
        val cpuArch = FileUtils.readFileAsRoot("uname -m") ?: "Unknown"
        val deviceModel = FileUtils.readFileAsRoot("getprop ro.product.model") ?: "Unknown"
        val androidVersion = FileUtils.readFileAsRoot("getprop ro.build.version.release") ?: "Unknown"

        val uptimeSeconds = SystemClock.elapsedRealtime() / 1000
        val hours = uptimeSeconds / 3600
        val minutes = (uptimeSeconds % 3600) / 60
        val seconds = uptimeSeconds % 60
        val uptime = String.format(Locale.US, "%02d:%02d:%02d", hours, minutes, seconds)

        val selinuxStatus = FileUtils.readFileAsRoot("getenforce") ?: "Unknown"

        val schedRaw = FileUtils.readFileAsRoot(IO_SCHEDULER_PATH) ?: ""
        val currentIoScheduler = schedRaw.substringAfter("[").substringBefore("]").takeIf { it.isNotBlank() } ?: "Unknown"
        val availableIoSchedulers = schedRaw.replace("[", "").replace("]", "").split(" ").filter { it.isNotBlank() }

        val currentTcpCongestion = FileUtils.readFileAsRoot(TCP_CONGESTION_PATH) ?: "Unknown"
        val availableTcpCongestion = FileUtils.readFileAsRoot(AVAILABLE_TCP_CONGESTION_PATH)
            ?.split(" ")
            ?.filter { it.isNotBlank() }
            ?: emptyList()

        SystemInfo(
            kernelVersion = kernelVersion,
            kernelBuild = kernelBuild,
            cpuArch = cpuArch,
            deviceModel = deviceModel,
            androidVersion = androidVersion,
            uptime = uptime,
            selinuxStatus = selinuxStatus,
            availableIoSchedulers = availableIoSchedulers,
            currentIoScheduler = currentIoScheduler,
            availableTcpCongestion = availableTcpCongestion,
            currentTcpCongestion = currentTcpCongestion
        )
    }

    suspend fun setIoScheduler(scheduler: String): Boolean = withContext(Dispatchers.IO) {
        FileUtils.writeFileAsRoot(IO_SCHEDULER_PATH, scheduler)
    }

    suspend fun setTcpCongestion(algorithm: String): Boolean = withContext(Dispatchers.IO) {
        FileUtils.writeFileAsRoot(TCP_CONGESTION_PATH, algorithm)
    }
}

