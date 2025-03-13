package com.ireddragonicy.champkernelmanager.data

import android.util.Log
import com.ireddragonicy.champkernelmanager.utils.FileUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.regex.Pattern

class ThermalManager {
    companion object {
        private const val THERMAL_BASE_PATH = "/sys/class/thermal/"
        private const val THERMAL_CACHE_VALID_MS = 2000
    }

    private var cpuThermalZonesCache: Map<Int, ThermalZoneInfo>? = null
    private var thermalZoneCacheTime: Long = 0



    suspend fun getThermalInfo(): ThermalInfo = withContext(Dispatchers.IO) {
        val typesRaw = FileUtils.runCommandAsRoot("su -c \"cat ${THERMAL_BASE_PATH}thermal_zone*/type\"") ?: ""
        val typeList = typesRaw.split('\n').filter { it.isNotBlank() }

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

    suspend fun getCpuThermalZones(): Map<Int, ThermalZoneInfo> = withContext(Dispatchers.IO) {
        val currentTime = System.currentTimeMillis()
        cpuThermalZonesCache?.let {
            if (currentTime - thermalZoneCacheTime < THERMAL_CACHE_VALID_MS) {
                Log.d("ThermalManager", "Using cached thermal zone data")
                return@withContext it
            }
        }

        val typesRaw = FileUtils.runCommandAsRoot("su -c \"cat ${THERMAL_BASE_PATH}thermal_zone*/type\"") ?: ""
        val types = typesRaw.split('\n').filter { it.isNotBlank() }

        Log.d("ThermalManager", "Found ${types.size} total thermal zones by type listing")

        val cpuZoneIndices = mutableListOf<Int>()
        val cpuZoneTypes = mutableListOf<String>()
        for ((index, type) in types.withIndex()) {
            if ((type.contains("cpu", ignoreCase = true) || type.contains("core", ignoreCase = true))
                && !type.contains("dsu", ignoreCase = true)
            ) {
                cpuZoneIndices.add(index)
                cpuZoneTypes.add(type)
                Log.d("ThermalManager", "Potential CPU zone: thermal_zone$index => $type")
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
                zoneType.contains("big", ignoreCase = true) || zoneType.contains(
                    "prime",
                    ignoreCase = true
                ) -> cpuType = "big"
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
                    type = "cpu-${key.first}-core${key.second}",
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
}