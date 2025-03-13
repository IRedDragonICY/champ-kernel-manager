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
            val settings = AppSettings.getInstance(context)
            if (settings.applyOnBoot) {
                BootService.applySettings(context)
            }
        }
    }
}

object BootService {
    fun applySettings(context: Context) {
        val settings = AppSettings.getInstance(context)
        val repo = DataRepository.getInstance()

        CoroutineScope(Dispatchers.IO).launch {
            with(settings) {
                if (savedCpuGovernor.isNotEmpty()) {
                    repo.setAllCoresGovernor(savedCpuGovernor)
                }

                if (savedGpuGovernor.isNotEmpty()) {
                    repo.setGpuGovernor(savedGpuGovernor)
                }

                if (savedGpuMaxFreq.isNotEmpty()) {
                    repo.setGpuMaxFreq(savedGpuMaxFreq)
                }

                if (savedGpuMinFreq.isNotEmpty()) {
                    repo.setGpuMinFreq(savedGpuMinFreq)
                }

                if (savedIoScheduler.isNotEmpty()) {
                    repo.setIoScheduler(savedIoScheduler)
                }

                if (savedTcpCongestion.isNotEmpty()) {
                    repo.setTcpCongestion(savedTcpCongestion)
                }
            }
        }
    }
}