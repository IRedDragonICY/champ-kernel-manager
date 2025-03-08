package com.ireddragonicy.champkernelmanager

import android.app.Application
import androidx.compose.runtime.mutableStateOf
import com.ireddragonicy.champkernelmanager.data.AppSettings
import com.topjohnwu.superuser.Shell

class App : Application() {
    lateinit var settings: AppSettings
        private set
        
    // Observable theme state
    val isDarkTheme = mutableStateOf(false)
    val useDynamicColors = mutableStateOf(true)
    
    override fun onCreate() {
        super.onCreate()
        
        // Initialize shell
        Shell.enableVerboseLogging = BuildConfig.DEBUG
        Shell.setDefaultBuilder(Shell.Builder.create()
            .setFlags(Shell.FLAG_REDIRECT_STDERR)
            .setTimeout(10)
        )
        
        // Initialize settings
        settings = AppSettings(this)
        
        // Initialize theme state from settings
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