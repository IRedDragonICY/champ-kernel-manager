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
        private const val THERMAL_BASE_PATH = "/sys/class/thermal/"
        private const val THERMAL_CACHE_VALID_MS = 2000

        // Pattern that matches: cpu-<type>-core<number>[-<suffix>]
        private val CPU_ZONE_PATTERN = Pattern.compile(
            "cpu-(little|medium|big|prime)-core(\\d+)(?:-(\\d+))?",
            Pattern.CASE_INSENSITIVE
        )

        // Helper functions to build thermal paths
        private fun getThermalZonePath(index: Int) = "${THERMAL_BASE_PATH}thermal_zone$index"
        private fun getThermalZoneTypePath(index: Int) = "${getThermalZonePath(index)}/type"
        private fun getThermalZoneTempPath(index: Int) = "${getThermalZonePath(index)}/temp"
        private fun getAllZonesTypePath() = "${THERMAL_BASE_PATH}thermal_zone*/type"
    }

    private var cpuThermalZonesCache: Map<Int, ThermalZoneInfo>? = null
    private var thermalZoneCacheTime: Long = 0
    private var thermalInfoCache: ThermalInfo? = null
    private var thermalInfoCacheTime: Long = 0

    suspend fun getThermalInfo(): ThermalInfo = withContext(Dispatchers.IO) {
        val currentTime = System.currentTimeMillis()
        thermalInfoCache?.let {
            if (currentTime - thermalInfoCacheTime < THERMAL_CACHE_VALID_MS) {
                return@withContext it
            }
        }

        val command = StringBuilder("su -c \"")
            .append("cat ${getAllZonesTypePath()} && echo '#TYPESEND#' && ")
            .append("getprop init.svc.thermald && ")
            .append("getprop init.svc.mi_thermald && ")
            .append("getprop init.svc.vendor.thermal-mediatek\"")

        val commandOutput = FileUtils.runCommandAsRoot(command.toString()) ?: ""
        val parts = commandOutput.split("#TYPESEND#")

        if (parts.size < 2) {
            return@withContext ThermalInfo(emptyList(), false)
        }

        val typesRaw = parts[0]
        val propResults = parts[1].trim().split('\n')

        val typeList = typesRaw.split('\n').filter { it.isNotBlank() }

        val tempCmd = StringBuilder("su -c \"cat ")
        typeList.indices.forEach { i ->
            tempCmd.append("${getThermalZoneTempPath(i)} ")
        }
        tempCmd.append("\"")

        val tempsRaw = FileUtils.runCommandAsRoot(tempCmd.toString()) ?: ""
        val tempsList = tempsRaw.split('\n').filter { it.isNotBlank() }

        val zoneData = ArrayList<ThermalZone>(typeList.size)
        for (i in typeList.indices) {
            val zoneType = typeList[i]
            tempsList.getOrNull(i)?.toFloatOrNull()?.let { rawVal ->
                zoneData.add(ThermalZone(zoneType, rawVal / 1000))
            }
        }

        val thermalEnabled = propResults.any { it.trim() == "running" }

        val thermalInfoResult = ThermalInfo(zones = zoneData, thermalServicesEnabled = thermalEnabled)
        thermalInfoCache = thermalInfoResult
        thermalInfoCacheTime = currentTime
        thermalInfoResult
    }

    suspend fun setThermalServices(enabled: Boolean): Boolean = withContext(Dispatchers.IO) {
        val commands = if (enabled) {
            "setprop persist.sys.turbosched.thermal_break.enable false && " +
            "start thermald && start mi_thermald && start vendor.thermal-mediatek"
        } else {
            "setprop persist.sys.turbosched.thermal_break.enable true && " +
            "stop thermald && stop mi_thermald && stop vendor.thermal-mediatek"
        }

        val cmdOutput = FileUtils.runCommandAsRoot("su -c \"$commands\"")
        thermalInfoCache = null
        cmdOutput != null
    }

    suspend fun getCpuThermalZones(): Map<Int, ThermalZoneInfo> = withContext(Dispatchers.IO) {
        val currentTime = System.currentTimeMillis()
        cpuThermalZonesCache?.let {
            if (currentTime - thermalZoneCacheTime < THERMAL_CACHE_VALID_MS) {
                return@withContext it
            }
        }

        val typesCmd = "su -c \"cat ${getAllZonesTypePath()}\""
        val typesRaw = FileUtils.runCommandAsRoot(typesCmd) ?: ""
        val types = typesRaw.split('\n').filter { it.isNotBlank() }

        val cpuZones = ArrayList<Pair<Int, String>>()
        for ((index, type) in types.withIndex()) {
            val matcher = CPU_ZONE_PATTERN.matcher(type)
            if (matcher.find()) {
                cpuZones.add(Pair(index, type))
            }
        }

        if (cpuZones.isEmpty()) {
            return@withContext emptyMap()
        }

        val tempCommand = StringBuilder("su -c \"cat ")
        cpuZones.forEach { (index, _) ->
            tempCommand.append("${getThermalZoneTempPath(index)} ")
        }
        tempCommand.append("\"")

        val tempRawItems = FileUtils.runCommandAsRoot(tempCommand.toString())
            ?.split('\n')?.filter { it.isNotBlank() }
            ?: return@withContext emptyMap()

        val tempRecords = ArrayList<TempRecord>(cpuZones.size)
        for (i in cpuZones.indices) {
            val (zoneId, zoneType) = cpuZones[i]
            val tempValRaw = tempRawItems.getOrNull(i)?.toFloatOrNull() ?: continue

            val matcher = CPU_ZONE_PATTERN.matcher(zoneType)
            if (matcher.find()) {
                val cpuType = matcher.group(1)?.lowercase()
                val coreNumber = matcher.group(2)?.toIntOrNull()

                val thermalZonePath = getThermalZonePath(zoneId).substringAfterLast('/')

                tempRecords.add(
                    TempRecord(
                        zoneId = zoneId,
                        zoneType = zoneType,
                        temperature = tempValRaw / 1000f,
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