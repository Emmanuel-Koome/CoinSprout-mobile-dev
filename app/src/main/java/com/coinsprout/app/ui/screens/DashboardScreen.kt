package com.coinsprout.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.coinsprout.app.data.RiskData
import com.coinsprout.app.state.AppState
import com.coinsprout.app.ui.components.AllocationDonut
import com.coinsprout.app.ui.theme.*
import com.coinsprout.app.viewmodel.CoinSproutViewModel
import kotlinx.coroutines.delay

@Composable
fun DashboardScreen(
    navController: NavHostController,
    appState: AppState,
    viewModel: CoinSproutViewModel = viewModel()
) {
    val riskCategory by viewModel.riskCategory.collectAsState()
    val portfolioBalance by viewModel.portfolioBalance.collectAsState()
    val pendingRoundUps by viewModel.pendingRoundUpBalance.collectAsState()
    val recentTransactions by viewModel.transactions.collectAsState()
    val allocation = RiskData.allocations.getValue(riskCategory)

    var counter by remember { mutableStateOf(0) }
    LaunchedEffect(portfolioBalance) {
        val target = portfolioBalance.toInt()
        val step = (target / 20).coerceAtLeast(10)
        while (counter < target) {
            counter = (counter + step).coerceAtMost(target)
            delay(16)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("CoinSprout 🌱", style = MaterialTheme.typography.headlineSmall, color = Forest)
                Text("Investor: $riskCategory Grower", style = MaterialTheme.typography.bodySmall, color = InkSoft)
            }
            IconButton(onClick = { navController.navigate("settings") }) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(Moss),
                    contentAlignment = Alignment.Center
                ) { Text("⚙️", style = MaterialTheme.typography.titleMedium) }
            }
        }
        Spacer(Modifier.height(16.dp))

        Card(colors = CardDefaults.cardColors(containerColor = Forest)) {
            Column(Modifier.padding(16.dp)) {
                Text("TOTAL PORTFOLIO VALUE", color = Moss, style = MaterialTheme.typography.bodySmall)
                Text(
                    "KES $counter",
                    style = MaterialTheme.typography.headlineMedium,
                    color = Paper
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Pending round-ups: KES ${pendingRoundUps.toInt()}", color = Moss, style = MaterialTheme.typography.bodySmall)
                    if (pendingRoundUps >= 100.0) {
                        TextButton(onClick = { viewModel.triggerStkPush() }) {
                            Text("STK Push ➔", color = Sprout, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(12.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(onClick = { navController.navigate("deposit") }, modifier = Modifier.weight(1f)) {
                Text("+ Top up")
            }
            OutlinedButton(onClick = { navController.navigate("goals") }, modifier = Modifier.weight(1f)) {
                Text("+ New goal")
            }
        }
        Spacer(Modifier.height(12.dp))

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { navController.navigate("portfolio") }
        ) {
            Row(
                Modifier.padding(16.dp).fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("ASSET ALLOCATION", color = InkSoft, style = MaterialTheme.typography.bodySmall)
                    Text("$riskCategory Split", style = MaterialTheme.typography.titleMedium, color = Forest)
                    Text("Tap to view growth simulator ➔", color = Sprout, style = MaterialTheme.typography.bodySmall)
                }
                AllocationDonut(allocation = allocation, diameter = 52.dp, strokeWidth = 10.dp)
            }
        }
        Spacer(Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Recent activity", style = MaterialTheme.typography.titleMedium, color = Forest)
            TextButton(onClick = { navController.navigate("transactions") }) {
                Text("View all", color = Sprout)
            }
        }
        Spacer(Modifier.height(4.dp))

        recentTransactions.take(4).forEach { tx ->
            Row(
                Modifier.fillMaxWidth().padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier.size(36.dp).background(Moss, RoundedCornerShape(10.dp)),
                        contentAlignment = Alignment.Center
                    ) { Text(tx.emoji) }
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text(tx.merchantName, style = MaterialTheme.typography.titleMedium)
                        Text(tx.timestamp, style = MaterialTheme.typography.bodySmall, color = InkSoft)
                    }
                }
                Text(
                    if (tx.roundUpAmount > 0) "+KES ${tx.roundUpAmount.toInt()}" else "KES 0",
                    color = ForestSoft,
                    style = MaterialTheme.typography.titleMedium
                )
            }
        }
    }
}
