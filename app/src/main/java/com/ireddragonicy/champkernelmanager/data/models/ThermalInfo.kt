package com.ireddragonicy.champkernelmanager.data.models

data class ThermalInfo(
    val zones: List<ThermalZone>,
    val thermalServicesEnabled: Boolean
)