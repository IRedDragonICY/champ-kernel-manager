package com.ireddragonicy.champkernelmanager.data

import com.ireddragonicy.champkernelmanager.data.models.TempRecord
import com.ireddragonicy.champkernelmanager.data.models.ThermalInfo
import com.ireddragonicy.champkernelmanager.data.models.ThermalZone
import com.ireddragonicy.champkernelmanager.data.models.ThermalZoneInfo
import com.ireddragonicy.champkernelmanager.utils.FileUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.regex.Pattern

class ThermalManager {
    companion object {
        private const val THERMAL_BASE_PATH = "/sys/class/thermal/"
        private const val THERMAL_CACHE_VALID_MS = 2000
        private val CPU_CORE_PATTERN = Pattern.compile("core(\\d+)(?:-(\\d+))?", Pattern.CASE_INSENSITIVE)
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

        // Execute single root command for all operations
        val command = StringBuilder("su -c \"")
            .append("cat ${THERMAL_BASE_PATH}thermal_zone*/type && echo '#TYPESEND#' && ")
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
            tempCmd.append("${THERMAL_BASE_PATH}thermal_zone$i/temp ")
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

        val typesRaw = FileUtils.runCommandAsRoot("su -c \"cat ${THERMAL_BASE_PATH}thermal_zone*/type\"") ?: ""
        val types = typesRaw.split('\n').filter { it.isNotBlank() }

        val cpuZones = ArrayList<Pair<Int, String>>(types.size / 2)
        for ((index, type) in types.withIndex()) {
            if ((type.contains("cpu", true) || type.contains("core", true)) && !type.contains("dsu", true)) {
                cpuZones.add(Pair(index, type))
            }
        }

        if (cpuZones.isEmpty()) {
            return@withContext emptyMap()
        }

        val tempCommand = StringBuilder("su -c \"cat ")
        cpuZones.forEach { (index, _) ->
            tempCommand.append("${THERMAL_BASE_PATH}thermal_zone$index/temp ")
        }
        tempCommand.append("\"")

        val tempRawItems = FileUtils.runCommandAsRoot(tempCommand.toString())
            ?.split('\n')?.filter { it.isNotBlank() }
            ?: return@withContext emptyMap()

        val tempRecords = ArrayList<TempRecord>(cpuZones.size)

        for (i in cpuZones.indices) {
            val (zoneId, zoneType) = cpuZones[i]
            val tempValRaw = tempRawItems.getOrNull(i)?.toFloatOrNull() ?: continue

            val cpuType = when {
                zoneType.contains("little", true) -> "little"
                zoneType.contains("medium", true) -> "medium"
                zoneType.contains("big", true) || zoneType.contains("prime", true) -> "big"
                else -> null
            }

            var coreNumber: Int? = null
            val matcher = CPU_CORE_PATTERN.matcher(zoneType)
            if (matcher.find()) {
                coreNumber = matcher.group(1)?.toIntOrNull()
            }

            tempRecords.add(
                TempRecord(
                    zoneId = zoneId,
                    zoneType = zoneType,
                    temperature = tempValRaw / 1000f,
                    cpuType = cpuType,
                    coreNumber = coreNumber
                )
            )
        }

        val finalMap = HashMap<Int, ThermalZoneInfo>(tempRecords.size)
        var assignedZoneId = 0

        tempRecords.groupBy { Pair(it.cpuType, it.coreNumber) }
            .forEach { (key, list) ->
                val (cpuType, coreNumber) = key

                if (cpuType != null && coreNumber != null && list.isNotEmpty()) {
                    var sum = 0f
                    list.forEach { sum += it.temperature }

                    finalMap[assignedZoneId] = ThermalZoneInfo(
                        zoneId = assignedZoneId,
                        type = "cpu-$cpuType-core$coreNumber",
                        temp = sum / list.size,
                        cpuType = cpuType,
                        coreNumber = coreNumber
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