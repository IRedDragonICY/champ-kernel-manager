package com.ireddragonicy.champkernelmanager.utils

import com.topjohnwu.superuser.Shell
import java.io.File

object FileUtils {
    fun readFile(filePath: String): String? = try {
        File(filePath).readText().trim().takeIf { it.isNotEmpty() }
    } catch (_: Exception) {
        null
    }

    fun readFileAsRoot(filePath: String): String? {
        val result = Shell.cmd("cat $filePath").exec()
        return if (result.isSuccess) result.out.joinToString("\n").trim().takeIf { it.isNotEmpty() } else null
    }

    fun writeFileAsRoot(filePath: String, value: String): Boolean {
        return Shell.cmd("echo '$value' > $filePath").exec().isSuccess
    }

    fun runCommandAsRoot(command: String): String? {
        val result = Shell.cmd(command).exec()
        return if (result.isSuccess) result.out.joinToString("\n").trim() else null
    }
    
    fun getPermissiblePaths(): List<String> {
        val paths = mutableListOf<String>()
        val possiblePaths = listOf(
            "/sys/devices/system/cpu/",
            "/sys/class/devfreq/",
            "/sys/class/power_supply/",
            "/sys/class/thermal/",
            "/proc/sys/net/ipv4/",
            "/sys/block/mmcblk0/queue/"
        )
        
        for (path in possiblePaths) {
            if (File(path).exists() || File(path).canRead()) {
                paths.add(path)
            }
        }
        
        return paths
    }
}