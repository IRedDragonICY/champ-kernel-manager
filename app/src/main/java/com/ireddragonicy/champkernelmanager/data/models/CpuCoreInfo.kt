package com.ireddragonicy.champkernelmanager.data.models

data class CpuCoreInfo(
    val core: Int,
    val curFreqMHz: String,
    val hwMaxFreqMHz: String,
    val scalingMaxFreqMHz: String,
    val minFreqMHz: String,
    val governor: String,
    val online: Boolean
)