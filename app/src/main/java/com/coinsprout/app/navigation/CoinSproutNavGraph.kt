package com.coinsprout.app.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import com.coinsprout.app.ui.components.CoinSproutBottomBar
import com.coinsprout.app.ui.components.bottomTabs
import com.coinsprout.app.ui.screens.*
import com.coinsprout.app.ui.theme.Cream
import com.coinsprout.app.viewmodel.AuthViewModel
import com.coinsprout.app.viewmodel.CoinSproutViewModel

@Composable
fun CoinSproutNavGraph(
    navController: NavHostController,
    vm: CoinSproutViewModel = viewModel(),
    auth: AuthViewModel = viewModel()
) {
    val backStack by navController.currentBackStackEntryAsState()
    val route = backStack?.destination?.route
    val loggedIn by vm.loggedIn.collectAsStateWithLifecycle()
    val message by vm.message.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(message) { message?.let { snackbar.showSnackbar(it); vm.consumeMessage() } }

    // If the session ends while the user is inside the app (refresh token rejected, account deleted), go back to sign-in.
    LaunchedEffect(loggedIn, route) {
        if (!loggedIn && route != null && route !in setOf("splash", "signup")) navController.navigate("splash") { popUpTo(0) }
    }

    Scaffold(
        containerColor = Cream,
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = { if (route in bottomTabs.map { it.route }) CoinSproutBottomBar(navController, route) }
    ) { padding ->
        NavHost(navController, startDestination = "splash", modifier = Modifier.padding(padding)) {
            composable("splash") { SplashScreen(navController, vm) }
            composable("signup") { SignUpScreen(navController, auth) }
            composable("quiz") { RiskQuizScreen(navController, vm) }
            composable("result") { RiskResultScreen(navController, vm) }
            composable("dashboard") { DashboardScreen(navController, vm) }
            composable("portfolio") { PortfolioScreen(navController, vm) }
            composable("transactions") { TransactionsScreen(navController, vm) }
            composable("goals") { GoalsScreen(navController, vm) }
            composable("settings") { SettingsScreen(navController, vm) }
            composable("education") { EducationScreen(navController) }
            composable("deposit") { DepositScreen(navController, vm) }
            composable("notifications") { NotificationsScreen(navController, vm) }
        }
    }
}
