package com.ireddragonicy.champkernelmanager.utils

import com.topjohnwu.superuser.Shell

object FileUtils {

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

}