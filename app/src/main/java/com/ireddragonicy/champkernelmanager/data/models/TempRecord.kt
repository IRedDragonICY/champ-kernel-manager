package com.ireddragonicy.champkernelmanager.data.models

data class TempRecord(
    val zoneId: Int,
    val zoneType: String,
    val temperature: Float,
    val cpuType: String?,
    val coreNumber: Int?
)