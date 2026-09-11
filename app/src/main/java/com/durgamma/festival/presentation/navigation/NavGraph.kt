package com.durgamma.festival.presentation.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.durgamma.festival.presentation.dashboard.DashboardScreen
import com.durgamma.festival.presentation.donation.DonationEntryScreen
import com.durgamma.festival.presentation.donation.DonationListScreen
import com.durgamma.festival.presentation.splash.SplashScreen

object DurgammaRoutes {
    const val SPLASH = "splash"
    const val DASHBOARD = "dashboard"
    const val DONATION_ENTRY = "donation_entry"
    const val DONATIONS = "donations"
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
            DashboardScreen(
                onAddDonation = { navController.navigate(DurgammaRoutes.DONATION_ENTRY) },
                onViewDonations = { navController.navigate(DurgammaRoutes.DONATIONS) }
            )
        }
        composable(DurgammaRoutes.DONATION_ENTRY) {
            DonationEntryScreen(onDone = { navController.popBackStack() })
        }
        composable(DurgammaRoutes.DONATIONS) {
            DonationListScreen(onBack = { navController.popBackStack() })
        }
    }
}
