package com.ireddragonicy.champkernelmanager.data.models

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