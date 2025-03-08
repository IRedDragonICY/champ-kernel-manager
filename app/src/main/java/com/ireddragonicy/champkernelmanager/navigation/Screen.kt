package com.ireddragonicy.champkernelmanager.navigation

import androidx.navigation.NamedNavArgument
import androidx.navigation.NavType
import androidx.navigation.navArgument

sealed class Screen(
    val route: String,
    val baseRoute: String = route,
    val arguments: List<NamedNavArgument> = emptyList()
) {
    object Home : Screen("home")
    object LiveMonitor : Screen("live_monitor")
    object Settings : Screen("settings")
    object CoreControl : Screen("core_control")
    object RootCheck : Screen("root_check")

    object CpuClusterDetail : Screen(
        route = "cpu_cluster_detail/{clusterId}",
        baseRoute = "cpu_cluster_detail",
        arguments = listOf(
            navArgument("clusterId") { type = NavType.IntType }
        )
    ) {
        fun createRoute(clusterId: Int) = "cpu_cluster_detail/$clusterId"
    }
}