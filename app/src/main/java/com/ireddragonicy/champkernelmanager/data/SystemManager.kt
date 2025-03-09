package com.ireddragonicy.champkernelmanager.data

import android.os.SystemClock
import com.ireddragonicy.champkernelmanager.utils.FileUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale

class SystemManager {
    companion object {
        private const val TCP_CONGESTION_PATH = "/proc/sys/net/ipv4/tcp_congestion_control"
        private const val AVAILABLE_TCP_CONGESTION_PATH = "/proc/sys/net/ipv4/tcp_available_congestion_control"
        private const val IO_SCHEDULER_PATH = "/sys/block/mmcblk0/queue/scheduler"
    }

    suspend fun getSystemLoad(): String = withContext(Dispatchers.IO) {
        FileUtils.readFileAsRoot("/proc/loadavg")
            ?.split(" ")
            ?.take(3)
            ?.joinToString(" ")
            ?: "N/A"
    }

    suspend fun getSystemInfo(): SystemInfo = withContext(Dispatchers.IO) {
        val kernelVersion = FileUtils.readFileAsRoot("uname -r") ?: "Unknown"
        val kernelBuild = FileUtils.readFileAsRoot("cat /proc/version") ?: "Unknown"
        val cpuArch = FileUtils.readFileAsRoot("uname -m") ?: "Unknown"
        val deviceModel = FileUtils.readFileAsRoot("getprop ro.product.model") ?: "Unknown"
        val androidVersion = FileUtils.readFileAsRoot("getprop ro.build.version.release") ?: "Unknown"

        val uptimeSeconds = SystemClock.elapsedRealtime() / 1000
        val hours = uptimeSeconds / 3600
        val minutes = (uptimeSeconds % 3600) / 60
        val seconds = uptimeSeconds % 60
        val uptime = String.format(Locale.US, "%02d:%02d:%02d", hours, minutes, seconds)

        val selinuxStatus = FileUtils.readFileAsRoot("getenforce") ?: "Unknown"

        val schedRaw = FileUtils.readFileAsRoot(IO_SCHEDULER_PATH) ?: ""
        val currentIoScheduler = schedRaw.substringAfter("[").substringBefore("]").takeIf { it.isNotBlank() } ?: "Unknown"
        val availableIoSchedulers = schedRaw.replace("[", "").replace("]", "").split(" ").filter { it.isNotBlank() }

        val currentTcpCongestion = FileUtils.readFileAsRoot(TCP_CONGESTION_PATH) ?: "Unknown"
        val availableTcpCongestion = FileUtils.readFileAsRoot(AVAILABLE_TCP_CONGESTION_PATH)
            ?.split(" ")
            ?.filter { it.isNotBlank() }
            ?: emptyList()

        SystemInfo(
            kernelVersion = kernelVersion,
            kernelBuild = kernelBuild,
            cpuArch = cpuArch,
            deviceModel = deviceModel,
            androidVersion = androidVersion,
            uptime = uptime,
            selinuxStatus = selinuxStatus,
            availableIoSchedulers = availableIoSchedulers,
            currentIoScheduler = currentIoScheduler,
            availableTcpCongestion = availableTcpCongestion,
            currentTcpCongestion = currentTcpCongestion
        )
    }

    suspend fun setIoScheduler(scheduler: String): Boolean = withContext(Dispatchers.IO) {
        FileUtils.writeFileAsRoot(IO_SCHEDULER_PATH, scheduler)
    }

    suspend fun setTcpCongestion(algorithm: String): Boolean = withContext(Dispatchers.IO) {
        FileUtils.writeFileAsRoot(TCP_CONGESTION_PATH, algorithm)
    }
}