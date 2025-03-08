package com.ireddragonicy.champkernelmanager

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.ireddragonicy.champkernelmanager.data.AppSettings
import com.ireddragonicy.champkernelmanager.data.DataRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            val settings = AppSettings(context)
            if (settings.applyOnBoot) {
                BootService.applySettings(context)
            }
        }
    }
}

object BootService {
    fun applySettings(context: Context) {
        val settings = AppSettings(context)
        val dataRepository = DataRepository.getInstance()
        
        CoroutineScope(Dispatchers.IO).launch {
            // Apply CPU governor
            if (settings.savedCpuGovernor.isNotEmpty()) {
                dataRepository.setAllCoresGovernor(settings.savedCpuGovernor)
            }
            
            // Apply GPU settings
            if (settings.savedGpuGovernor.isNotEmpty()) {
                dataRepository.setGpuGovernor(settings.savedGpuGovernor)
            }
            
            if (settings.savedGpuMaxFreq.isNotEmpty()) {
                dataRepository.setGpuMaxFreq(settings.savedGpuMaxFreq)
            }
            
            if (settings.savedGpuMinFreq.isNotEmpty()) {
                dataRepository.setGpuMinFreq(settings.savedGpuMinFreq)
            }
            
            // Apply thermal profile
            if (settings.savedThermalProfile.isNotEmpty()) {
                dataRepository.setThermalProfile(settings.savedThermalProfile)
            }
            
            // Apply IO scheduler
            if (settings.savedIoScheduler.isNotEmpty()) {
                dataRepository.setIoScheduler(settings.savedIoScheduler)
            }
            
            // Apply TCP congestion algorithm
            if (settings.savedTcpCongestion.isNotEmpty()) {
                dataRepository.setTcpCongestion(settings.savedTcpCongestion)
            }
        }
    }
}