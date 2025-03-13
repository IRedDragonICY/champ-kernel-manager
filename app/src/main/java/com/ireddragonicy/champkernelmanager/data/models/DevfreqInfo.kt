package com.ireddragonicy.champkernelmanager.data.models

data class DevfreqInfo(
    val name: String,
    val path: String,
    val maxFreqMHz: String,
    val minFreqMHz: String,
    val curFreqMHz: String,
    val targetFreqMHz: String,
    val availableFrequenciesMHz: List<String> = emptyList(),
    val availableGovernors: List<String> = emptyList(),
    val currentGovernor: String = "N/A"
)