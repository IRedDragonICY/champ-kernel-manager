package com.ireddragonicy.champkernelmanager.data

import com.ireddragonicy.champkernelmanager.data.models.TempRecord
import com.ireddragonicy.champkernelmanager.data.models.ThermalInfo
import com.ireddragonicy.champkernelmanager.data.models.ThermalZone
import com.ireddragonicy.champkernelmanager.data.models.ThermalZoneInfo
import com.ireddragonicy.champkernelmanager.utils.FileUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale
import java.util.regex.Pattern

class ThermalManager {
    companion object {
        private const val THERMAL_CACHE_VALID_MS = 2000
        private val CPU_ZONE_PATTERN = Pattern.compile(
            "cpu-(little|medium|big|prime)-core(\\d+)(?:-(\\d+))?",
            Pattern.CASE_INSENSITIVE
        )
    }

    private var cpuThermalZonesCache: Map<Int, ThermalZoneInfo>? = null
    private var thermalZoneCacheTime: Long = 0
    private var thermalInfoCache: ThermalInfo? = null
    private var thermalInfoCacheTime: Long = 0
    private var thermalDataCache: List<Pair<String, Float>>? = null
    private var thermalDataCacheTime: Long = 0

    private suspend fun getRawThermalData(): List<Pair<String, Float>> = withContext(Dispatchers.IO) {
        val currentTime = System.currentTimeMillis()
        thermalDataCache?.let {
            if (currentTime - thermalDataCacheTime < THERMAL_CACHE_VALID_MS) {
                return@withContext it
            }
        }

        val command = "su -c 'for z in /sys/devices/virtual/thermal/thermal_zone*; do t=\$(cat \$z/temp 2>/dev/null); y=\$(cat \$z/type 2>/dev/null); echo \"\$y|\$t\"; done'"
        val output = FileUtils.runCommandAsRoot(command) ?: ""
        val result = output.split('\n')
            .filter { it.isNotEmpty() }
            .mapNotNull { line ->
                val parts = line.trim().split('|')
                if (parts.size == 2) {
                    val type = parts[0].trim()
                    parts[1].trim().toFloatOrNull()?.let { temp ->
                        Pair(type, temp / 1000f)
                    }
                } else null
            }
        thermalDataCache = result
        thermalDataCacheTime = currentTime
        result
    }

    suspend fun getThermalInfo(): ThermalInfo = withContext(Dispatchers.IO) {
        val currentTime = System.currentTimeMillis()
        thermalInfoCache?.let {
            if (currentTime - thermalInfoCacheTime < THERMAL_CACHE_VALID_MS) {
                return@withContext it
            }
        }

        val thermalData = getRawThermalData()
        val serviceStatusCmd = "su -c 'getprop init.svc.thermald && getprop init.svc.mi_thermald && getprop init.svc.vendor.thermal-mediatek'"
        val serviceOutput = FileUtils.runCommandAsRoot(serviceStatusCmd) ?: ""
        val thermalEnabled = serviceOutput.split('\n').any { it.trim() == "running" }

        val zones = thermalData.map { (type, temp) ->
            ThermalZone(type, temp)
        }

        val thermalInfoResult = ThermalInfo(zones = zones, thermalServicesEnabled = thermalEnabled)
        thermalInfoCache = thermalInfoResult
        thermalInfoCacheTime = currentTime
        thermalInfoResult
    }

    suspend fun setThermalServices(enabled: Boolean): Boolean = withContext(Dispatchers.IO) {
        val commands = if (enabled) {
            "setprop persist.sys.turbosched.thermal_break.enable false && start thermald && start mi_thermald && start vendor.thermal-mediatek"
        } else {
            "setprop persist.sys.turbosched.thermal_break.enable true && stop thermald && stop mi_thermald && stop vendor.thermal-mediatek"
        }

        val cmdOutput = FileUtils.runCommandAsRoot("su -c '$commands'")
        thermalInfoCache = null
        thermalDataCache = null
        cmdOutput != null
    }

    suspend fun getCpuThermalZones(): Map<Int, ThermalZoneInfo> = withContext(Dispatchers.IO) {
        val currentTime = System.currentTimeMillis()
        cpuThermalZonesCache?.let {
            if (currentTime - thermalZoneCacheTime < THERMAL_CACHE_VALID_MS) {
                return@withContext it
            }
        }

        val thermalData = getRawThermalData()
        val cpuZones = ArrayList<Triple<Int, String, Float>>()

        for ((index, pair) in thermalData.withIndex()) {
            val (type, temp) = pair
            val matcher = CPU_ZONE_PATTERN.matcher(type)
            if (matcher.find()) {
                cpuZones.add(Triple(index, type, temp))
            }
        }

        if (cpuZones.isEmpty()) {
            return@withContext emptyMap()
        }

        val tempRecords = ArrayList<TempRecord>(cpuZones.size)
        for ((zoneId, zoneType, tempVal) in cpuZones) {
            val matcher = CPU_ZONE_PATTERN.matcher(zoneType)
            if (matcher.find()) {
                val cpuType = matcher.group(1)?.lowercase()
                val coreNumber = matcher.group(2)?.toIntOrNull()
                val thermalZonePath = "thermal_zone$zoneId"
                tempRecords.add(
                    TempRecord(
                        zoneId = zoneId,
                        zoneType = zoneType,
                        temperature = tempVal,
                        cpuType = cpuType,
                        coreNumber = coreNumber,
                        thermalZonePath = thermalZonePath
                    )
                )
            }
        }

        val finalMap = HashMap<Int, ThermalZoneInfo>()
        var assignedZoneId = 0
        val coreGroups = tempRecords.groupBy { Pair(it.cpuType, it.coreNumber) }

        coreGroups.forEach { (key, records) ->
            val (cpuType, coreNumber) = key
            if (cpuType != null && coreNumber != null) {
                val avgTemp = records.sumOf { it.temperature.toDouble() } / records.size
                val sourceThermalZones = records.joinToString(", ") { it.thermalZonePath }
                finalMap[assignedZoneId] = ThermalZoneInfo(
                    zoneId = assignedZoneId,
                    type = "cpu-$cpuType-core$coreNumber",
                    temp = avgTemp.toFloat(),
                    cpuType = cpuType,
                    coreNumber = coreNumber,
                    sourceThermalZones = sourceThermalZones
                )
                assignedZoneId++
            }
        }

        cpuThermalZonesCache = finalMap
        thermalZoneCacheTime = currentTime
        finalMap
    }

    suspend fun getCpuTemperatures(): Map<Int, String> = withContext(Dispatchers.IO) {
        val coreCount = Runtime.getRuntime().availableProcessors()
        val results = mutableMapOf<Int, String>()
        val allZones = getCpuThermalZones()

        for (coreIndex in 0 until coreCount) {
            val directZone = allZones.values.find { it.coreNumber == coreIndex && it.temp > 1f }
            directZone?.let {
                results[coreIndex] = String.format(Locale.US, "%.1f°C", it.temp)
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