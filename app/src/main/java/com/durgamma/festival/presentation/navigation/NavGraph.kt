package com.durgamma.festival.presentation.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.durgamma.festival.presentation.announcement.AnnouncementQueueScreen
import com.durgamma.festival.presentation.dashboard.DashboardScreen
import com.durgamma.festival.presentation.donation.DonationEntryScreen
import com.durgamma.festival.presentation.donation.DonationListScreen
import com.durgamma.festival.presentation.expense.ExpenseEntryScreen
import com.durgamma.festival.presentation.expense.ExpenseListScreen
import com.durgamma.festival.presentation.history.ActivityFeedScreen
import com.durgamma.festival.presentation.reports.ExportScreen
import com.durgamma.festival.presentation.settings.AdminSettingsScreen
import com.durgamma.festival.presentation.settings.CloudSyncScreen
import com.durgamma.festival.presentation.splash.SplashScreen

object DurgammaRoutes {
    const val SPLASH = "splash"
    const val DASHBOARD = "dashboard"
    const val DONATION_ENTRY = "donation_entry"
    const val DONATIONS = "donations"
    const val ANNOUNCEMENTS = "announcements"
    const val EXPENSES = "expenses"
    const val EXPENSE_ENTRY = "expense_entry"
    const val HISTORY = "history"
    const val REPORTS = "reports"
    const val CLOUD_SYNC = "cloud_sync"
    const val ADMIN = "admin"
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
                onViewDonations = { navController.navigate(DurgammaRoutes.DONATIONS) },
                onAnnounce = { navController.navigate(DurgammaRoutes.ANNOUNCEMENTS) },
                onExpenses = { navController.navigate(DurgammaRoutes.EXPENSES) },
                onHistory = { navController.navigate(DurgammaRoutes.HISTORY) },
                onReports = { navController.navigate(DurgammaRoutes.REPORTS) },
                onSettings = { navController.navigate(DurgammaRoutes.CLOUD_SYNC) }
            )
        }
        composable(DurgammaRoutes.DONATION_ENTRY) {
            DonationEntryScreen(onDone = { navController.popBackStack() })
        }
        composable(DurgammaRoutes.DONATIONS) {
            DonationListScreen(onBack = { navController.popBackStack() })
        }
        composable(DurgammaRoutes.ANNOUNCEMENTS) {
            AnnouncementQueueScreen(onBack = { navController.popBackStack() })
        }
        composable(DurgammaRoutes.EXPENSES) {
            ExpenseListScreen(
                onBack = { navController.popBackStack() },
                onAddExpense = { navController.navigate(DurgammaRoutes.EXPENSE_ENTRY) }
            )
        }
        composable(DurgammaRoutes.EXPENSE_ENTRY) {
            ExpenseEntryScreen(onDone = { navController.popBackStack() })
        }
        composable(DurgammaRoutes.HISTORY) {
            ActivityFeedScreen(onBack = { navController.popBackStack() })
        }
        composable(DurgammaRoutes.REPORTS) {
            ExportScreen(onBack = { navController.popBackStack() })
        }
        composable(DurgammaRoutes.CLOUD_SYNC) {
            CloudSyncScreen(
                onBack = { navController.popBackStack() },
                onOpenAdmin = { navController.navigate(DurgammaRoutes.ADMIN) }
            )
        }
        composable(DurgammaRoutes.ADMIN) {
            AdminSettingsScreen(onBack = { navController.popBackStack() })
        }
    }
}
