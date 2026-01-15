package com.voiceink.android.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.voiceink.android.ui.screens.history.HistoryScreen
import com.voiceink.android.ui.screens.home.HomeScreen
import com.voiceink.android.ui.screens.settings.SettingsScreen

sealed class Screen(val route: String) {
    data object Home : Screen("home")
    data object Settings : Screen("settings")
    data object History : Screen("history")
}

@Composable
fun VoiceInkNavHost() {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = Screen.Home.route
    ) {
        composable(Screen.Home.route) {
            HomeScreen(
                onNavigateToSettings = {
                    navController.navigate(Screen.Settings.route)
                },
                onNavigateToHistory = {
                    navController.navigate(Screen.History.route)
                }
            )
        }
        composable(Screen.Settings.route) {
            SettingsScreen(
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }
        composable(Screen.History.route) {
            HistoryScreen(
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }
    }
}
