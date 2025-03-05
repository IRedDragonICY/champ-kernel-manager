package com.ireddragonicy.champkernelmanager

import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.widget.Toast

class GovernorTileService : TileService() {
    companion object {
        private const val CPU_GOVERNOR_PATH = "/sys/devices/system/cpu/cpu%d/cpufreq/scaling_governor"
        private const val CPU_AVAILABLE_GOVERNORS_PATH = "/sys/devices/system/cpu/cpu0/cpufreq/scaling_available_governors"
    }

    override fun onStartListening() {
        updateTile()
    }

    override fun onClick() {
        val newGovernor = toggleGovernor()
        updateTile()
        Toast.makeText(
            this,
            "Governor switched to: ${newGovernor ?: "failed"}",
            Toast.LENGTH_SHORT
        ).show()
    }

    private fun toggleGovernor(): String? {
        // Get available governors
        val availableGovs = FileUtils.readFile(CPU_AVAILABLE_GOVERNORS_PATH)
            ?.split(" ")
            ?.filter { it.isNotBlank() }
            ?.takeIf { it.isNotEmpty() }
            ?: return null

        // Get current governor
        val currentGov = FileUtils.readFileAsRoot(CPU_GOVERNOR_PATH.format(0))
            ?: return null

        // Calculate next governor
        val currentIndex = availableGovs.indexOf(currentGov).takeIf { it >= 0 } ?: 0
        val newGovernor = availableGovs[(currentIndex + 1) % availableGovs.size]

        // Apply new governor to all CPU cores
        val success = repeat(Runtime.getRuntime().availableProcessors()) { core ->
            FileUtils.writeFileAsRoot(CPU_GOVERNOR_PATH.format(core), newGovernor)
        }

        return if (success) newGovernor else null
    }

    private fun updateTile() {
        val tile = qsTile ?: return
        val currentGov = FileUtils.readFileAsRoot(CPU_GOVERNOR_PATH.format(0)) ?: "N/A"

        with(tile) {
            label = currentGov
            state = if (currentGov != "N/A") Tile.STATE_ACTIVE else Tile.STATE_UNAVAILABLE
            updateTile()
        }
    }

    private fun repeat(times: Int, action: (Int) -> Boolean): Boolean {
        for (i in 0 until times) {
            if (!action(i)) return false
        }
        return true
    }
}