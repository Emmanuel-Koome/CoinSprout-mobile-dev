package com.coinsprout.app.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import com.coinsprout.app.state.AppState
import com.coinsprout.app.ui.components.CoinSproutBottomBar
import com.coinsprout.app.ui.screens.*
import com.coinsprout.app.viewmodel.CoinSproutViewModel

private val TABBED_ROUTES = setOf("dashboard", "portfolio", "transactions", "goals", "settings")

@Composable
fun CoinSproutNavGraph(
    navController: NavHostController,
    appState: AppState,
    viewModel: CoinSproutViewModel = viewModel()
) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    Scaffold(
        bottomBar = {
            if (currentRoute in TABBED_ROUTES) {
                CoinSproutBottomBar(navController = navController, currentRoute = currentRoute)
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = "splash",
            modifier = Modifier.padding(innerPadding)
        ) {
            composable("splash") { SplashScreen(navController) }
            composable("signup") { SignUpScreen(navController) }
            composable("quiz") { RiskQuizScreen(navController, appState) }
            composable("result") { RiskResultScreen(navController, appState) }
            composable("dashboard") { DashboardScreen(navController, appState, viewModel) }
            composable("portfolio") { PortfolioScreen(navController, appState, viewModel) }
            composable("transactions") { TransactionsScreen(navController, viewModel) }
            composable("goals") { GoalsScreen(navController, viewModel) }
            composable("settings") { SettingsScreen(navController, viewModel) }
            composable("education") { EducationScreen(navController) }
            composable("compare") { CompareModesScreen(navController, appState) }
            composable("deposit") { DepositScreen(navController, appState, viewModel) }
        }
    }
}
