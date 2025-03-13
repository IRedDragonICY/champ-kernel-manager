package com.ireddragonicy.champkernelmanager.data

import com.ireddragonicy.champkernelmanager.data.models.BatteryInfo
import com.ireddragonicy.champkernelmanager.data.models.CoreControlInfo
import com.ireddragonicy.champkernelmanager.data.models.CpuClusterInfo
import com.ireddragonicy.champkernelmanager.data.models.DevfreqInfo
import com.ireddragonicy.champkernelmanager.data.models.ThermalInfo
import com.ireddragonicy.champkernelmanager.data.models.SystemInfo


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
    }

    private val cpuManager by lazy { CpuManager() }
    private val gpuManager by lazy { GpuManager() }
    private val batteryManager by lazy { BatteryManager() }
    private val thermalManager by lazy { ThermalManager() }
    private val systemManager by lazy { SystemManager() }

    suspend fun getSystemLoad(): String = systemManager.getSystemLoad()
    suspend fun getCpuClusters(): List<CpuClusterInfo> = cpuManager.getCpuClusters()
    suspend fun getAvailableGovernors(): List<String> = cpuManager.getAvailableGovernors()
    suspend fun setAllCoresGovernor(governor: String): Boolean = cpuManager.setAllCoresGovernor(governor)
    suspend fun setScalingMaxFreq(core: Int, freq: String): Boolean = cpuManager.setScalingMaxFreq(core, freq)
    suspend fun setScalingMinFreq(core: Int, freq: String): Boolean = cpuManager.setScalingMinFreq(core, freq)
    suspend fun getCoreControlInfo(): CoreControlInfo = cpuManager.getCoreControlInfo()
    suspend fun setCoreState(core: Int, enabled: Boolean): Boolean = cpuManager.setCoreState(core, enabled)

    suspend fun getGpuInfo(): DevfreqInfo = gpuManager.getGpuInfo()
    suspend fun setGpuMaxFreq(freq: String): Boolean = gpuManager.setGpuMaxFreq(freq)
    suspend fun setGpuMinFreq(freq: String): Boolean = gpuManager.setGpuMinFreq(freq)
    suspend fun setGpuGovernor(governor: String): Boolean = gpuManager.setGpuGovernor(governor)

    suspend fun getBatteryInfo(): BatteryInfo = batteryManager.getBatteryInfo()
    suspend fun setFastCharge(enabled: Boolean): Boolean = batteryManager.setFastCharge(enabled)
    suspend fun setChargingLimit(limit: Int): Boolean = batteryManager.setChargingLimit(limit)

    suspend fun getThermalInfo(): ThermalInfo = thermalManager.getThermalInfo()
    suspend fun setThermalServices(enabled: Boolean): Boolean = thermalManager.setThermalServices(enabled)

    suspend fun getSystemInfo(): SystemInfo = systemManager.getSystemInfo()
    suspend fun setIoScheduler(scheduler: String): Boolean = systemManager.setIoScheduler(scheduler)
    suspend fun setTcpCongestion(algorithm: String): Boolean = systemManager.setTcpCongestion(algorithm)
}