package com.ireddragonicy.champkernelmanager

import android.app.Application
import androidx.compose.runtime.mutableStateOf
import com.ireddragonicy.champkernelmanager.data.AppSettings
import com.topjohnwu.superuser.Shell

class App : Application() {
    lateinit var settings: AppSettings
        private set
        
    val isDarkTheme = mutableStateOf(false)
    val useDynamicColors = mutableStateOf(true)

    override fun onCreate() {
        super.onCreate()

        Shell.enableVerboseLogging = BuildConfig.DEBUG
        Shell.setDefaultBuilder(Shell.Builder.create()
            .setTimeout(10)
        )

        settings = AppSettings.getInstance(this)

        isDarkTheme.value = settings.darkTheme
        useDynamicColors.value = settings.useDynamicColors
    }
    
    fun updateTheme(darkTheme: Boolean) {
        settings.darkTheme = darkTheme
        isDarkTheme.value = darkTheme
    }
    
    fun updateDynamicColors(useDynamic: Boolean) {
        settings.useDynamicColors = useDynamic
        useDynamicColors.value = useDynamic
    }
}