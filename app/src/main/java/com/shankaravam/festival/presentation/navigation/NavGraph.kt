package com.shankaravam.festival.presentation.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
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
        startDestination = ShankaRavamRoutes.SPLASH,
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        enterTransition = {
            // Forward Push: Entering screen slides in from +28% right while gently fading in
            slideIntoContainer(
                towards = AnimatedContentTransitionScope.SlideDirection.Start,
                animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing),
                initialOffset = { fullWidth -> (fullWidth * 0.28f).toInt() }
            ) + fadeIn(
                animationSpec = tween(durationMillis = 240, easing = LinearOutSlowInEasing)
            )
        },
        exitTransition = {
            // Forward Push: Outgoing screen recedes slightly left (-20%) while softly fading out
            slideOutOfContainer(
                towards = AnimatedContentTransitionScope.SlideDirection.Start,
                animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing),
                targetOffset = { fullWidth -> -(fullWidth * 0.20f).toInt() }
            ) + fadeOut(
                animationSpec = tween(durationMillis = 220, easing = FastOutLinearInEasing)
            )
        },
        popEnterTransition = {
            // Backward Pop: Returning screen glides back from -20% left to center while fading in
            slideIntoContainer(
                towards = AnimatedContentTransitionScope.SlideDirection.End,
                animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing),
                initialOffset = { fullWidth -> -(fullWidth * 0.20f).toInt() }
            ) + fadeIn(
                animationSpec = tween(durationMillis = 240, easing = LinearOutSlowInEasing)
            )
        },
        popExitTransition = {
            // Backward Pop: Exiting screen slides gracefully off to +28% right while fading out
            slideOutOfContainer(
                towards = AnimatedContentTransitionScope.SlideDirection.End,
                animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing),
                targetOffset = { fullWidth -> (fullWidth * 0.28f).toInt() }
            ) + fadeOut(
                animationSpec = tween(durationMillis = 220, easing = FastOutLinearInEasing)
            )
        }
    ) {
        composable(
            route = ShankaRavamRoutes.SPLASH,
            enterTransition = { fadeIn(animationSpec = tween(300, easing = LinearOutSlowInEasing)) },
            exitTransition = { fadeOut(animationSpec = tween(300, easing = FastOutLinearInEasing)) }
        ) {
            SplashScreen(
                onTimeout = {
                    navController.navigate(ShankaRavamRoutes.DASHBOARD) {
                        popUpTo(ShankaRavamRoutes.SPLASH) { inclusive = true }
                    }
                }
            )
        }
        composable(
            route = ShankaRavamRoutes.DASHBOARD,
            enterTransition = {
                if (initialState.destination.route == ShankaRavamRoutes.SPLASH) {
                    fadeIn(animationSpec = tween(durationMillis = 300, easing = LinearOutSlowInEasing))
                } else {
                    slideIntoContainer(
                        towards = AnimatedContentTransitionScope.SlideDirection.End,
                        animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing),
                        initialOffset = { fullWidth -> -(fullWidth * 0.20f).toInt() }
                    ) + fadeIn(
                        animationSpec = tween(durationMillis = 240, easing = LinearOutSlowInEasing)
                    )
                }
            }
        ) {
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
            // F1: Cloud Sync now lives in the gear as the Team & Cloud Sync
            // accordion — the old route redirects there, expanded. No dead
            // links from the dashboard Sync tile.
            AdminSettingsScreen(
                onBack = { navController.popBackStack() },
                expandTeam = true
            )
        }
        composable(ShankaRavamRoutes.ADMIN) {
            AdminSettingsScreen(onBack = { navController.popBackStack() })
        }
    }
}
