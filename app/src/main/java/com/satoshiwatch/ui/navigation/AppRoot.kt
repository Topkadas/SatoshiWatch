package com.satoshiwatch.ui.navigation

import androidx.compose.runtime.Composable
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.satoshiwatch.ui.MainViewModel
import com.satoshiwatch.ui.screens.AddAddressScreen
import com.satoshiwatch.ui.screens.DashboardScreen
import com.satoshiwatch.ui.screens.SettingsScreen

private object Routes {
    const val DASHBOARD = "dashboard"
    const val ADD_ADDRESS = "add_address"
    const val SETTINGS = "settings"
}

/** Kořenová navigace aplikace – tři obrazovky, žádné argumenty v URL. */
@Composable
fun AppRoot() {
    val navController = rememberNavController()
    // Jedna sdílená instance (scope = aktivita): zprávy emitované při přidání
    // adresy tak dorazí do snackbaru na dashboardu i po popBackStack().
    val mainViewModel: MainViewModel = hiltViewModel()

    NavHost(navController = navController, startDestination = Routes.DASHBOARD) {
        composable(Routes.DASHBOARD) {
            DashboardScreen(
                viewModel = mainViewModel,
                onOpenSettings = { navController.navigate(Routes.SETTINGS) },
                onAddAddress = { navController.navigate(Routes.ADD_ADDRESS) }
            )
        }
        composable(Routes.ADD_ADDRESS) {
            AddAddressScreen(
                viewModel = mainViewModel,
                onDone = { navController.popBackStack() }
            )
        }
        composable(Routes.SETTINGS) {
            SettingsScreen(
                viewModel = hiltViewModel(),
                onBack = { navController.popBackStack() }
            )
        }
    }
}
