package com.ireddragonicy.champkernelmanager.utils

import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
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

    // Run multiple commands in parallel and return their results
    suspend fun runCommandsParallel(commands: List<String>): List<String?> = withContext(Dispatchers.IO) {
        commands.map { command ->
            async { runCommandAsRoot(command) }
        }.awaitAll()
    }

    // Read multiple files in parallel
    suspend fun readFilesParallel(filePaths: List<String>): List<String?> = withContext(Dispatchers.IO) {
        filePaths.map { path ->
            async { readFileAsRoot(path) }
        }.awaitAll()
    }

    // Generic parallel execution for any shell operation
    suspend fun <T, R> executeParallel(items: List<T>, operation: suspend (T) -> R): List<R> = coroutineScope {
        items.map { item ->
            async(Dispatchers.IO) {
                operation(item)
            }
        }.awaitAll()
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