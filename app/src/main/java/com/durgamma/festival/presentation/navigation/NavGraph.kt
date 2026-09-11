package com.durgamma.festival.presentation.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.durgamma.festival.presentation.dashboard.DashboardScreen
import com.durgamma.festival.presentation.splash.SplashScreen

object DurgammaRoutes {
    const val SPLASH = "splash"
    const val DASHBOARD = "dashboard"
}

@Composable
fun DurgammaNavGraph() {
    val navController = rememberNavController()
    NavHost(
        navController = navController,
        startDestination = DurgammaRoutes.SPLASH
    ) {
        composable(DurgammaRoutes.SPLASH) {
            SplashScreen(
                onTimeout = {
                    navController.navigate(DurgammaRoutes.DASHBOARD) {
                        popUpTo(DurgammaRoutes.SPLASH) { inclusive = true }
                    }
                }
            )
        }
        composable(DurgammaRoutes.DASHBOARD) {
            DashboardScreen()
        }
    }
}
