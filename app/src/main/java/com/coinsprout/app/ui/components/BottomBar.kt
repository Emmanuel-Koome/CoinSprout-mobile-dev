package com.coinsprout.app.ui.components

import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController

data class BottomTab(val route: String, val label: String, val emoji: String)

val bottomTabs = listOf(
    BottomTab("dashboard", "Home", "🏠"),
    BottomTab("portfolio", "Portfolio", "📊"),
    BottomTab("transactions", "Activity", "📝"),
    BottomTab("goals", "Goals", "🎯"),
    BottomTab("settings", "Settings", "⚙️")
)

@Composable
fun CoinSproutBottomBar(navController: NavHostController, currentRoute: String?) {
    NavigationBar {
        bottomTabs.forEach { tab ->
            NavigationBarItem(
                selected = currentRoute == tab.route,
                onClick = {
                    if (currentRoute != tab.route) {
                        navController.navigate(tab.route) {
                            popUpTo("dashboard") { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                },
                icon = { Text(tab.emoji) },
                label = { Text(tab.label) }
            )
        }
    }
}
