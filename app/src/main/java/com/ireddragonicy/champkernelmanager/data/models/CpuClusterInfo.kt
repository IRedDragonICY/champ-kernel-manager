package com.ireddragonicy.champkernelmanager.data.models

data class CpuClusterInfo(
    val name: String,
    val cores: List<CpuCoreInfo>
)