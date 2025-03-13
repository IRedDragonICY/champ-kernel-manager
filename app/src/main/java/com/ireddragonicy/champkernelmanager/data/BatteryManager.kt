package com.ireddragonicy.champkernelmanager.data

import com.ireddragonicy.champkernelmanager.data.models.BatteryInfo
import com.ireddragonicy.champkernelmanager.utils.FileUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class BatteryManager {
    companion object {
        private const val BATTERY_PATH = "/sys/class/power_supply/battery/"
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
}