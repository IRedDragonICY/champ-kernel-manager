package com.ireddragonicy.champkernelmanager.data


data class CpuCoreInfo(
    val core: Int,
    val curFreqMHz: String,
    val hwMaxFreqMHz: String,
    val scalingMaxFreqMHz: String,
    val minFreqMHz: String,
    val governor: String,
    val online: Boolean,
    val temperature: String
)

data class CoreControlInfo(
    val supported: Boolean,
    val cores: Map<Int, Boolean>
)

data class CpuClusterInfo(
    val name: String,
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

data class BatteryInfo(
    val status: String,
    val level: Int,
    val temperature: Float,
    val currentNow: Int,
    val voltage: Int,
    val health: String,
    val technology: String,
    val fastChargeSupported: Boolean = false,
    val fastChargeEnabled: Boolean = false,
    val chargingLimitSupported: Boolean = false,
    val chargingLimit: Int = 100
)

data class ThermalZone(
    val name: String,
    val temp: Float
)

data class ThermalInfo(
    val zones: List<ThermalZone>,
    val thermalServicesEnabled: Boolean
)

data class SystemInfo(
    val kernelVersion: String,
    val kernelBuild: String,
    val cpuArch: String,
    val deviceModel: String,
    val androidVersion: String,
    val uptime: String,
    val selinuxStatus: String,
    val availableIoSchedulers: List<String>,
    val currentIoScheduler: String,
    val availableTcpCongestion: List<String>,
    val currentTcpCongestion: String
)