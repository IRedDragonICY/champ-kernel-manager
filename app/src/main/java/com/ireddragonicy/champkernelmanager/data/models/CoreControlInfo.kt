package com.ireddragonicy.champkernelmanager.data.models

data class CoreControlInfo(
    val supported: Boolean,
    val cores: Map<Int, Boolean>
)