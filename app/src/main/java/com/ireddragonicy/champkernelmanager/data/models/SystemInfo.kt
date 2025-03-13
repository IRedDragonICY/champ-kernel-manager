package com.ireddragonicy.champkernelmanager.data.models

data class SystemInfo(
    val kernelVersion: String,
    val kernelBuild: String,
    val cpuArch: String,
    val deviceModel: String,
    val androidVersion: String,
    val uptime: String,
    val selinuxStatus: String,
    val availableIoSchedulers: List<String>,
    val currentIoScheduler: String,
    val availableTcpCongestion: List<String>,
    val currentTcpCongestion: String
)