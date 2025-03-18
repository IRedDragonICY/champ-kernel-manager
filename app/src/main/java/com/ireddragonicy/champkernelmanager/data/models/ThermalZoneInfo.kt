package com.ireddragonicy.champkernelmanager.data.models

data class ThermalZoneInfo(
    val zoneId: Int,
    val type: String,
    val temp: Float,
    val cpuType: String? = null,
    val coreNumber: Int? = null,
    val sourceThermalZones: String
)