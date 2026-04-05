/*
 * VARYNX 2.0 — Proprietary License
 * Copyright (c) 2026 VARYNX
 * All rights reserved.
 */
package com.varynx.varynx20.ui.navigation

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.varynx.varynx20.ui.screens.*

object VarynxRoutes {
    const val DASHBOARD = "dashboard"
    const val MODULES = "modules"
    const val MODULE_DETAIL = "module/{moduleId}"
    const val THREAT_LOG = "threat_log"
    const val REFLEX_HISTORY = "reflex_history"
    const val ENGINE_DIAGNOSTICS = "engine_diagnostics"
    const val SETTINGS = "settings"
    const val MESH = "mesh"
    const val SECURITY_SCAN = "security_scan"
    const val SKIMMER_SCAN = "skimmer_scan"
    const val QR_SCAN = "qr_scan"

    fun moduleDetail(moduleId: String) = "module/$moduleId"
}

private val fadeIn = fadeIn(animationSpec = tween(200))
private val fadeOut = fadeOut(animationSpec = tween(200))
private val slideInRight = slideInHorizontally(initialOffsetX = { it / 4 }, animationSpec = tween(250)) + fadeIn
private val slideOutLeft = slideOutHorizontally(targetOffsetX = { -it / 4 }, animationSpec = tween(250)) + fadeOut
private val slideInLeft = slideInHorizontally(initialOffsetX = { -it / 4 }, animationSpec = tween(250)) + fadeIn
private val slideOutRight = slideOutHorizontally(targetOffsetX = { it / 4 }, animationSpec = tween(250)) + fadeOut

@Composable
fun VarynxNavGraph(
    navController: NavHostController,
    modifier: Modifier = Modifier
) {
    NavHost(
        navController = navController,
        startDestination = VarynxRoutes.DASHBOARD,
        modifier = modifier,
        enterTransition = { slideInRight },
        exitTransition = { slideOutLeft },
        popEnterTransition = { slideInLeft },
        popExitTransition = { slideOutRight }
    ) {
        composable(VarynxRoutes.DASHBOARD) {
            DashboardScreen(
                onNavigateToModules = { navController.navigate(VarynxRoutes.MODULES) },
                onNavigateToThreatLog = { navController.navigate(VarynxRoutes.THREAT_LOG) },
                onNavigateToReflexHistory = { navController.navigate(VarynxRoutes.REFLEX_HISTORY) },
                onNavigateToEngineDiagnostics = { navController.navigate(VarynxRoutes.ENGINE_DIAGNOSTICS) },
                onNavigateToSettings = { navController.navigate(VarynxRoutes.SETTINGS) },
                onNavigateToMesh = { navController.navigate(VarynxRoutes.MESH) },
                onNavigateToSecurityScan = { navController.navigate(VarynxRoutes.SECURITY_SCAN) },
                onNavigateToSkimmerScan = { navController.navigate(VarynxRoutes.SKIMMER_SCAN) },
                onNavigateToQrScan = { navController.navigate(VarynxRoutes.QR_SCAN) }
            )
        }

        composable(VarynxRoutes.MODULES) {
            ModuleListScreen(
                onNavigateBack = { navController.popBackStack() },
                onModuleClick = { moduleId ->
                    navController.navigate(VarynxRoutes.moduleDetail(moduleId))
                }
            )
        }

        composable(
            route = VarynxRoutes.MODULE_DETAIL,
            arguments = listOf(navArgument("moduleId") { type = NavType.StringType })
        ) { backStackEntry ->
            val moduleId = backStackEntry.arguments?.getString("moduleId") ?: ""
            ModuleDetailScreen(
                moduleId = moduleId,
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(VarynxRoutes.THREAT_LOG) {
            ThreatLogScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(VarynxRoutes.REFLEX_HISTORY) {
            ReflexHistoryScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(VarynxRoutes.ENGINE_DIAGNOSTICS) {
            EngineDiagnosticsScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(VarynxRoutes.SETTINGS) {
            SettingsScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(VarynxRoutes.MESH) {
            MeshScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(VarynxRoutes.SECURITY_SCAN) {
            SecurityScanScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(VarynxRoutes.SKIMMER_SCAN) {
            SkimmerScanScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(VarynxRoutes.QR_SCAN) {
            QrScanScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }
    }
}
