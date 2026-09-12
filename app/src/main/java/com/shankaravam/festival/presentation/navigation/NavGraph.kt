package com.shankaravam.festival.presentation.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.shankaravam.festival.presentation.announcement.AnnouncementQueueScreen
import com.shankaravam.festival.presentation.dashboard.DashboardScreen
import com.shankaravam.festival.presentation.donation.DonationEntryScreen
import com.shankaravam.festival.presentation.donation.DonationListScreen
import com.shankaravam.festival.presentation.expense.ExpenseEntryScreen
import com.shankaravam.festival.presentation.expense.ExpenseListScreen
import com.shankaravam.festival.presentation.history.ActivityFeedScreen
import com.shankaravam.festival.presentation.reports.ExportScreen
import com.shankaravam.festival.presentation.settings.AdminSettingsScreen
import com.shankaravam.festival.presentation.settings.CloudSyncScreen
import com.shankaravam.festival.presentation.splash.SplashScreen

object ShankaRavamRoutes {
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
fun ShankaRavamNavGraph() {
    val navController = rememberNavController()
    NavHost(
        navController = navController,
        startDestination = ShankaRavamRoutes.SPLASH
    ) {
        composable(ShankaRavamRoutes.SPLASH) {
            SplashScreen(
                onTimeout = {
                    navController.navigate(ShankaRavamRoutes.DASHBOARD) {
                        popUpTo(ShankaRavamRoutes.SPLASH) { inclusive = true }
                    }
                }
            )
        }
        composable(ShankaRavamRoutes.DASHBOARD) {
            DashboardScreen(
                onAddDonation = { navController.navigate(ShankaRavamRoutes.DONATION_ENTRY) },
                onViewDonations = { navController.navigate(ShankaRavamRoutes.DONATIONS) },
                onAnnounce = { navController.navigate(ShankaRavamRoutes.ANNOUNCEMENTS) },
                onAddExpense = { navController.navigate(ShankaRavamRoutes.EXPENSE_ENTRY) },
                onViewExpenses = { navController.navigate(ShankaRavamRoutes.EXPENSES) },
                onHistory = { navController.navigate(ShankaRavamRoutes.HISTORY) },
                onReports = { navController.navigate(ShankaRavamRoutes.REPORTS) },
                onSettings = { navController.navigate(ShankaRavamRoutes.ADMIN) },
                onSync = { navController.navigate(ShankaRavamRoutes.CLOUD_SYNC) }
            )
        }
        composable(ShankaRavamRoutes.DONATION_ENTRY) {
            DonationEntryScreen(onDone = { navController.popBackStack() })
        }
        composable(ShankaRavamRoutes.DONATIONS) {
            DonationListScreen(onBack = { navController.popBackStack() })
        }
        composable(ShankaRavamRoutes.ANNOUNCEMENTS) {
            AnnouncementQueueScreen(onBack = { navController.popBackStack() })
        }
        composable(ShankaRavamRoutes.EXPENSES) {
            ExpenseListScreen(
                onBack = { navController.popBackStack() },
                onAddExpense = { navController.navigate(ShankaRavamRoutes.EXPENSE_ENTRY) }
            )
        }
        composable(ShankaRavamRoutes.EXPENSE_ENTRY) {
            ExpenseEntryScreen(onDone = { navController.popBackStack() })
        }
        composable(ShankaRavamRoutes.HISTORY) {
            ActivityFeedScreen(onBack = { navController.popBackStack() })
        }
        composable(ShankaRavamRoutes.REPORTS) {
            ExportScreen(onBack = { navController.popBackStack() })
        }
        composable(ShankaRavamRoutes.CLOUD_SYNC) {
            CloudSyncScreen(
                onBack = { navController.popBackStack() },
                onOpenAdmin = { navController.navigate(ShankaRavamRoutes.ADMIN) }
            )
        }
        composable(ShankaRavamRoutes.ADMIN) {
            AdminSettingsScreen(onBack = { navController.popBackStack() })
        }
    }
}
