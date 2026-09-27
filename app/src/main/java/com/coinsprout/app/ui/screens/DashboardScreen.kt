package com.coinsprout.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.coinsprout.app.data.RiskData
import com.coinsprout.app.state.AppState
import com.coinsprout.app.ui.components.AllocationDonut
import com.coinsprout.app.ui.theme.*
import kotlinx.coroutines.delay

private data class TxItem(val emoji: String, val name: String, val`when`: String, val amount: String)

@Composable
fun DashboardScreen(navController: NavHostController, appState: AppState) {
    val allocation = RiskData.allocations.getValue(appState.riskCategory)
    var counter by remember { mutableStateOf(0) }

    LaunchedEffect(Unit) {
        val target = 1240
        val step = 60
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
            Text("CoinSprout", style = MaterialTheme.typography.headlineSmall, color = Forest)
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(Moss),
                contentAlignment = Alignment.Center
            ) { Text("K", color = Forest, style = MaterialTheme.typography.titleMedium) }
        }
        Spacer(Modifier.height(16.dp))

        Card(colors = CardDefaults.cardColors(containerColor = Forest)) {
            Column(Modifier.padding(16.dp)) {
                Text("TOTAL ROUND-UPS THIS MONTH", color = Moss, style = MaterialTheme.typography.bodySmall)
                Text(
                    "KES $counter",
                    style = MaterialTheme.typography.headlineMedium,
                    color = Paper
                )
                Text("That's about 12 rounded-up purchases \uD83C\uDF31", color = Moss, style = MaterialTheme.typography.bodySmall)
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
                    Text("PORTFOLIO VALUE", color = InkSoft, style = MaterialTheme.typography.bodySmall)
                    Text("KES ${appState.principal}", style = MaterialTheme.typography.headlineSmall, color = Forest)
                }
                AllocationDonut(allocation = allocation, diameter = 52.dp, strokeWidth = 10.dp)
            }
        }
        Spacer(Modifier.height(8.dp))

        Text("Recent activity", style = MaterialTheme.typography.titleMedium, color = Forest)
        Spacer(Modifier.height(4.dp))

        val recent = listOf(
            TxItem("\u2615", "Java House", "Today", "+KES 10"),
            TxItem("\uD83D\uDE95", "Little Cab", "Yesterday", "+KES 30"),
            TxItem("\uD83D\uDED2", "Naivas Supermarket", "2 days ago", "+KES 6")
        )
        recent.forEach { tx ->
            Row(
                Modifier.fillMaxWidth().padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier.size(36.dp).background(Moss, androidx.compose.foundation.shape.RoundedCornerShape(10.dp)),
                        contentAlignment = Alignment.Center
                    ) { Text(tx.emoji) }
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text(tx.name, style = MaterialTheme.typography.titleMedium)
                        Text(tx.`when`, style = MaterialTheme.typography.bodySmall, color = InkSoft)
                    }
                }
                Text(tx.amount, color = ForestSoft, style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}
