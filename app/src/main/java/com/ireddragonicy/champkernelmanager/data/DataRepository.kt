package com.ireddragonicy.champkernelmanager.data

import android.os.SystemClock
import com.ireddragonicy.champkernelmanager.utils.FileUtils
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale

class DataRepository private constructor() {
    companion object {
        private var INSTANCE: DataRepository? = null

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

        private const val TCP_CONGESTION_PATH = "/proc/sys/net/ipv4/tcp_congestion_control"
        private const val AVAILABLE_TCP_CONGESTION_PATH = "/proc/sys/net/ipv4/tcp_available_congestion_control"
        private const val IO_SCHEDULER_PATH = "/sys/block/mmcblk0/queue/scheduler"
    }

    data class CoreControlInfo(
        val supported: Boolean,
        val cores: Map<Int, Boolean>
    )

    suspend fun getSystemLoad(): String = withContext(Dispatchers.IO) {
        FileUtils.readFileAsRoot("/proc/loadavg")?.split(" ")?.take(3)?.joinToString(" ") ?: "N/A"
    }

    suspend fun getCpuClusters(): List<CpuClusterInfo> = withContext(Dispatchers.IO) {
        val coreCount = Runtime.getRuntime().availableProcessors()
        val cpuCoreInfos = (0 until coreCount).map { core ->
            val basePath = "$CPU_PATH$core$CPU_FREQ_PATH"
            val onlinePath = "$CPU_PATH$core$CPU_ONLINE_PATH"

            fun getFreqMHz(freqPath: String): String {
                val freqKHz = FileUtils.readFileAsRoot(freqPath)?.toLongOrNull() ?: 0
                return "${freqKHz / 1000} MHz"
            }

            val curFreq = getFreqMHz(basePath + "scaling_cur_freq")
            val maxFreq = getFreqMHz(basePath + "cpuinfo_max_freq")
            val minFreq = getFreqMHz(basePath + "scaling_min_freq")
            val governor = FileUtils.readFileAsRoot(basePath + "scaling_governor") ?: "N/A"
            val online = FileUtils.readFileAsRoot(onlinePath) == "1" || core == 0

            CpuCoreInfo(core, curFreq, maxFreq, minFreq, governor, online)
        }

        val groups = cpuCoreInfos.groupBy {
            it.maxFreqMHz.split(" ").firstOrNull()?.toLongOrNull() ?: 0L
        }

        val sortedGroups = groups.toList().sortedBy { it.first }

        val names = when (sortedGroups.size) {
            2 -> listOf("Little", "Big")
            3 -> listOf("Little", "Big", "Prime")
            else -> sortedGroups.mapIndexed { index, _ -> "Cluster ${index + 1}" }
        }

        sortedGroups.mapIndexed { index, pair ->
            CpuClusterInfo(names[index], pair.second)
        }
    }

    suspend fun getAvailableGovernors(): List<String> = withContext(Dispatchers.IO) {
        FileUtils.readFileAsRoot("${CPU_PATH}cpu0${CPU_FREQ_PATH}scaling_available_governors")
            ?.split(" ")
            ?.filter { it.isNotBlank() }
            ?: emptyList()
    }

    suspend fun setAllCoresGovernor(governor: String) = withContext(Dispatchers.IO) {
        val coreCount = Runtime.getRuntime().availableProcessors()
        (0 until coreCount).all { core ->
            FileUtils.writeFileAsRoot("${CPU_PATH}cpu$core${CPU_FREQ_PATH}scaling_governor", governor)
        }
    }

    suspend fun getCoreControlInfo(): CoreControlInfo = withContext(Dispatchers.IO) {
        val coreCount = Runtime.getRuntime().availableProcessors()
        val coreStatus = (0 until coreCount).associate { core ->
            val onlinePath = "$CPU_PATH$core$CPU_ONLINE_PATH"
            val isCore0 = core == 0
            val online = if (isCore0) true else FileUtils.readFileAsRoot(onlinePath) == "1"
            core to online
        }

        val supported = coreStatus.any { !it.value } || coreStatus.size > 1

        CoreControlInfo(supported, coreStatus)
    }

    suspend fun setCoreState(core: Int, enabled: Boolean): Boolean = withContext(Dispatchers.IO) {
        if (core == 0) return@withContext false
        FileUtils.writeFileAsRoot("$CPU_PATH$core$CPU_ONLINE_PATH", if (enabled) "1" else "0")
    }

    suspend fun getGpuInfo(): DevfreqInfo = withContext(Dispatchers.IO) {
        fun getFreqMHz(freqPath: String): String {
            val freqKHz = FileUtils.readFileAsRoot(freqPath)?.toLongOrNull() ?: 0
            return "${freqKHz / 1000} MHz"
        }

        val maxFreq = getFreqMHz("${GPU_PATH}max_freq")
        val minFreq = getFreqMHz("${GPU_PATH}min_freq")
        val curFreq = getFreqMHz("${GPU_PATH}cur_freq")
        val targetFreq = getFreqMHz("${GPU_PATH}target_freq")

        val availableFreqs = FileUtils.readFileAsRoot("${GPU_PATH}available_frequencies")
            ?.split(" ")
            ?.mapNotNull { it.toLongOrNull()?.let { freq -> "${freq / 1000} MHz" } }
            ?: emptyList()

        val availableGovernors = FileUtils.readFileAsRoot("${GPU_PATH}available_governors")
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
            availableGovernors = availableGovernors,
            currentGovernor = currentGovernor
        )
    }

    suspend fun setGpuMaxFreq(freq: String): Boolean = withContext(Dispatchers.IO) {
        val freqKHz = freq.replace(" MHz", "").toLong() * 1000
        FileUtils.writeFileAsRoot("${GPU_PATH}max_freq", freqKHz.toString())
    }

    suspend fun setGpuMinFreq(freq: String): Boolean = withContext(Dispatchers.IO) {
        val freqKHz = freq.replace(" MHz", "").toLong() * 1000
        FileUtils.writeFileAsRoot("${GPU_PATH}min_freq", freqKHz.toString())
    }

    suspend fun setGpuGovernor(governor: String): Boolean = withContext(Dispatchers.IO) {
        FileUtils.writeFileAsRoot("${GPU_PATH}governor", governor)
    }

    suspend fun getBatteryInfo(): BatteryInfo = withContext(Dispatchers.IO) {
        val status = FileUtils.readFileAsRoot("${BATTERY_PATH}status") ?: "Unknown"
        val level = FileUtils.readFileAsRoot("${BATTERY_PATH}capacity")?.toIntOrNull() ?: 0
        val tempRaw = FileUtils.readFileAsRoot("${BATTERY_PATH}temp")?.toFloatOrNull() ?: 0f
        val temp = tempRaw / 10
        val currentNow = FileUtils.readFileAsRoot("${BATTERY_PATH}current_now")?.toIntOrNull()?.div(1000) ?: 0
        val voltage = FileUtils.readFileAsRoot("${BATTERY_PATH}voltage_now")?.toIntOrNull()?.div(1000) ?: 0
        val health = FileUtils.readFileAsRoot("${BATTERY_PATH}health") ?: "Unknown"
        val technology = FileUtils.readFileAsRoot("${BATTERY_PATH}technology") ?: "Unknown"

        val fastChargeSupported = File("/sys/kernel/fast_charge/force_fast_charge").exists()
        val fastChargeEnabled = if (fastChargeSupported) {
            FileUtils.readFileAsRoot("/sys/kernel/fast_charge/force_fast_charge") == "1"
        } else false

        val chargingLimitSupported = File("/sys/class/power_supply/battery/charge_control_limit").exists()
        val chargingLimit = if (chargingLimitSupported) {
            FileUtils.readFileAsRoot("/sys/class/power_supply/battery/charge_control_limit")?.toIntOrNull() ?: 100
        } else 100

        BatteryInfo(
            status = status,
            level = level,
            temperature = temp,
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

    suspend fun getThermalInfo(): ThermalInfo = withContext(Dispatchers.IO) {
        val thermalZones = mutableListOf<ThermalZone>()
        val thermalPath = "/sys/class/thermal/"

        val thermalDir = File(thermalPath)
        if (thermalDir.exists() && thermalDir.isDirectory) {
            thermalDir.list()?.filter { it.startsWith("thermal_zone") }?.forEach { zone ->
                val tempPath = "$thermalPath$zone/temp"
                val typePath = "$thermalPath$zone/type"

                val temp = FileUtils.readFileAsRoot(tempPath)?.toFloatOrNull()?.div(1000) ?: 0f
                val type = FileUtils.readFileAsRoot(typePath) ?: zone

                thermalZones.add(ThermalZone(type, temp))
            }
        }

        val thermaldRunning = FileUtils.runCommandAsRoot("getprop init.svc.thermald") == "running"
        val miThermaldRunning = FileUtils.runCommandAsRoot("getprop init.svc.mi_thermald") == "running"
        val vendorThermaldRunning = FileUtils.runCommandAsRoot("getprop init.svc.vendor.thermal-mediatek") == "running"
        val thermalServicesEnabled = thermaldRunning || miThermaldRunning || vendorThermaldRunning

        val thermalProfiles = listOf("Balanced", "Performance", "Battery", "Gaming")
        val currentProfile = FileUtils.readFileAsRoot("/sys/class/thermal/thermal_policy") ?: "Default"

        ThermalInfo(
            zones = thermalZones,
            thermalServicesEnabled = thermalServicesEnabled,
            thermalProfiles = thermalProfiles,
            currentProfile = currentProfile
        )
    }

    suspend fun setThermalServices(enabled: Boolean): Boolean = withContext(Dispatchers.IO) {
        if (enabled) {
            val commands = listOf(
                "setprop persist.sys.turbosched.thermal_break.enable false",
                "start thermald",
                "start mi_thermald",
                "start vendor.thermal-mediatek"
            )
            commands.all { FileUtils.runCommandAsRoot(it) != null }
        } else {
            val commands = listOf(
                "setprop persist.sys.turbosched.thermal_break.enable true",
                "stop thermald",
                "stop mi_thermald",
                "stop vendor.thermal-mediatek"
            )
            commands.all { FileUtils.runCommandAsRoot(it) != null }
        }
    }

    suspend fun setThermalProfile(profile: String): Boolean = withContext(Dispatchers.IO) {
        FileUtils.writeFileAsRoot("/sys/class/thermal/thermal_policy", profile)
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

        val ioSchedulerData = FileUtils.readFileAsRoot(IO_SCHEDULER_PATH) ?: ""
        val currentIoScheduler = ioSchedulerData.substringAfter("[").substringBefore("]").takeIf { it.isNotBlank() } ?: "Unknown"
        val availableIoSchedulers = ioSchedulerData.replace("[", "").replace("]", "").split(" ").filter { it.isNotBlank() }

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