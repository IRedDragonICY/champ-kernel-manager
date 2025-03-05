package com.ireddragonicy.champkernelmanager

import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.InputStreamReader
import java.io.OutputStreamWriter

object RootShell {
    private var process: Process? = null
    private var writer: BufferedWriter? = null
    private var reader: BufferedReader? = null

    init {
        try {
            process = Runtime.getRuntime().exec("su")
            writer = BufferedWriter(OutputStreamWriter(process?.outputStream))
            reader = BufferedReader(InputStreamReader(process?.inputStream))
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun runCommand(command: String): String? {
        return try {
            writer?.write("$command\n")
            writer?.flush()
            writer?.write("echo __end__\n")
            writer?.flush()

            val result = StringBuilder()
            var line: String?
            while (reader?.readLine().also { line = it } != null) {
                if (line == "__end__") break
                result.append(line)
            }
            result.toString().trim()
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}