package com.ireddragonicy.champkernelmanager.data

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit

class AppSettings internal constructor(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences(
        PREFS_NAME, Context.MODE_PRIVATE
    )

    // System settings
    var applyOnBoot: Boolean
        get() = prefs.getBoolean(KEY_APPLY_ON_BOOT, false)
        set(value) = prefs.edit { putBoolean(KEY_APPLY_ON_BOOT, value) }

    // UI settings
    var useDynamicColors: Boolean
        get() = prefs.getBoolean(KEY_USE_DYNAMIC_COLORS, true)
        set(value) = prefs.edit { putBoolean(KEY_USE_DYNAMIC_COLORS, value) }

    var darkTheme: Boolean
        get() = prefs.getBoolean(KEY_DARK_THEME, false)
        set(value) = prefs.edit { putBoolean(KEY_DARK_THEME, value) }

    var customPrimaryColor: Int
        get() = prefs.getInt(KEY_CUSTOM_PRIMARY_COLOR, -1)
        set(value) = prefs.edit { putInt(KEY_CUSTOM_PRIMARY_COLOR, value) }

    // Performance settings
    var savedCpuGovernor: String
        get() = prefs.getString(KEY_CPU_GOVERNOR, "") ?: ""
        set(value) = prefs.edit { putString(KEY_CPU_GOVERNOR, value) }

    var savedGpuGovernor: String
        get() = prefs.getString(KEY_GPU_GOVERNOR, "") ?: ""
        set(value) = prefs.edit { putString(KEY_GPU_GOVERNOR, value) }

    var savedGpuMaxFreq: String
        get() = prefs.getString(KEY_GPU_MAX_FREQ, "") ?: ""
        set(value) = prefs.edit { putString(KEY_GPU_MAX_FREQ, value) }

    var savedGpuMinFreq: String
        get() = prefs.getString(KEY_GPU_MIN_FREQ, "") ?: ""
        set(value) = prefs.edit { putString(KEY_GPU_MIN_FREQ, value) }

    var savedThermalProfile: String
        get() = prefs.getString(KEY_THERMAL_PROFILE, "") ?: ""
        set(value) = prefs.edit { putString(KEY_THERMAL_PROFILE, value) }

    var savedIoScheduler: String
        get() = prefs.getString(KEY_IO_SCHEDULER, "") ?: ""
        set(value) = prefs.edit { putString(KEY_IO_SCHEDULER, value) }

    var savedTcpCongestion: String
        get() = prefs.getString(KEY_TCP_CONGESTION, "") ?: ""
        set(value) = prefs.edit { putString(KEY_TCP_CONGESTION, value) }

    fun saveCpuSettings(governor: String) {
        prefs.edit {
            putString(KEY_CPU_GOVERNOR, governor)
        }
    }

    fun saveGpuSettings(governor: String, minFreq: String, maxFreq: String) {
        prefs.edit {
            putString(KEY_GPU_GOVERNOR, governor)
            putString(KEY_GPU_MIN_FREQ, minFreq)
            putString(KEY_GPU_MAX_FREQ, maxFreq)
        }
    }

    companion object {
        private const val PREFS_NAME = "champ_kernel_prefs"

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

        @Volatile
        private var instance: AppSettings? = null

        fun getInstance(context: Context): AppSettings {
            return instance ?: synchronized(this) {
                instance ?: AppSettings(context.applicationContext).also { instance = it }
            }
        }
    }
}