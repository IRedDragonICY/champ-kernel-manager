package com.ireddragonicy.champkernelmanager.data

import android.content.Context
import android.content.SharedPreferences

class AppSettings(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences(
        "champ_kernel_prefs", Context.MODE_PRIVATE
    )
    
    var applyOnBoot: Boolean
        get() = prefs.getBoolean(KEY_APPLY_ON_BOOT, false)
        set(value) = prefs.edit().putBoolean(KEY_APPLY_ON_BOOT, value).apply()
    
    var useDynamicColors: Boolean
        get() = prefs.getBoolean(KEY_USE_DYNAMIC_COLORS, true)
        set(value) = prefs.edit().putBoolean(KEY_USE_DYNAMIC_COLORS, value).apply()
    
    var darkTheme: Boolean
        get() = prefs.getBoolean(KEY_DARK_THEME, false)
        set(value) = prefs.edit().putBoolean(KEY_DARK_THEME, value).apply()
    
    var customPrimaryColor: Int
        get() = prefs.getInt(KEY_CUSTOM_PRIMARY_COLOR, -1)
        set(value) = prefs.edit().putInt(KEY_CUSTOM_PRIMARY_COLOR, value).apply()
    
    var savedCpuGovernor: String
        get() = prefs.getString(KEY_CPU_GOVERNOR, "") ?: ""
        set(value) = prefs.edit().putString(KEY_CPU_GOVERNOR, value).apply()
    
    var savedGpuGovernor: String
        get() = prefs.getString(KEY_GPU_GOVERNOR, "") ?: ""
        set(value) = prefs.edit().putString(KEY_GPU_GOVERNOR, value).apply()
    
    var savedGpuMaxFreq: String
        get() = prefs.getString(KEY_GPU_MAX_FREQ, "") ?: ""
        set(value) = prefs.edit().putString(KEY_GPU_MAX_FREQ, value).apply()
    
    var savedGpuMinFreq: String
        get() = prefs.getString(KEY_GPU_MIN_FREQ, "") ?: ""
        set(value) = prefs.edit().putString(KEY_GPU_MIN_FREQ, value).apply()
    
    var savedThermalProfile: String
        get() = prefs.getString(KEY_THERMAL_PROFILE, "") ?: ""
        set(value) = prefs.edit().putString(KEY_THERMAL_PROFILE, value).apply()
    
    var savedIoScheduler: String
        get() = prefs.getString(KEY_IO_SCHEDULER, "") ?: ""
        set(value) = prefs.edit().putString(KEY_IO_SCHEDULER, value).apply()
    
    var savedTcpCongestion: String
        get() = prefs.getString(KEY_TCP_CONGESTION, "") ?: ""
        set(value) = prefs.edit().putString(KEY_TCP_CONGESTION, value).apply()
    
    companion object {
        private const val KEY_APPLY_ON_BOOT = "apply_on_boot"
        private const val KEY_USE_DYNAMIC_COLORS = "use_dynamic_colors"
        private const val KEY_DARK_THEME = "dark_theme"
        private const val KEY_CUSTOM_PRIMARY_COLOR = "custom_primary_color"
        private const val KEY_CPU_GOVERNOR = "cpu_governor"
        private const val KEY_GPU_GOVERNOR = "gpu_governor"
        private const val KEY_GPU_MAX_FREQ = "gpu_max_freq"
        private const val KEY_GPU_MIN_FREQ = "gpu_min_freq"
        private const val KEY_THERMAL_PROFILE = "thermal_profile"
        private const val KEY_IO_SCHEDULER = "io_scheduler"
        private const val KEY_TCP_CONGESTION = "tcp_congestion"
    }
}