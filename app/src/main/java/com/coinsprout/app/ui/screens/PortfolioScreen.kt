package com.coinsprout.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.coinsprout.app.data.RiskData
import com.coinsprout.app.state.AppState
import com.coinsprout.app.ui.components.AllocationDonut
import com.coinsprout.app.ui.theme.*
import kotlin.math.roundToInt

private data class Legend(val label: String, val value: Int, val color: Color)

@Composable
fun PortfolioScreen(navController: NavHostController, appState: AppState) {
    val category = appState.riskCategory
    val allocation = RiskData.allocations.getValue(category)
    val growth = RiskData.growth.getValue(category) // [6mo, 1yr, 5yr]

    var periodIndex by remember { mutableStateOf(1) }
    val periodLabels = listOf("6 months", "1 year", "5 years")
    val projected = (appState.principal * growth[periodIndex]).roundToInt()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Text("Your portfolio", style = MaterialTheme.typography.headlineSmall, color = Forest)
        Spacer(Modifier.height(12.dp))

        Card(Modifier.fillMaxWidth()) {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                AllocationDonut(allocation = allocation)
                Spacer(Modifier.width(16.dp))
                Column {
                    val legends = listOf(
                        Legend("Money Market", allocation.mmf, Forest),
                        Legend("T-Bills", allocation.tbill, Sprout),
                        Legend("Unit Trust", allocation.unit, Mustard),
                        Legend("Cash buffer", allocation.cash, Moss)
                    )
                    legends.forEach { l ->
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 3.dp)) {
                            Box(Modifier.size(9.dp).background(l.color, RoundedCornerShape(3.dp)))
                            Spacer(Modifier.width(7.dp))
                            Text(l.label, color = InkSoft, style = MaterialTheme.typography.bodySmall, modifier = Modifier.width(90.dp))
                            Text("${l.value}%", style = MaterialTheme.typography.titleMedium)
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(12.dp))

        Box(
            Modifier
                .fillMaxWidth()
                .background(Moss, RoundedCornerShape(16.dp))
                .padding(14.dp)
        ) {
            Column {
                Text("WHY THIS SPLIT?", color = Forest, style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(2.dp))
                Text(
                    "Because you're a $category Grower, we balance safety with steady-growth funds using real NSE & CBK return data.",
                    color = Forest,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
        Spacer(Modifier.height(12.dp))

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Text("GROWTH SIMULATOR", color = InkSoft, style = MaterialTheme.typography.bodySmall)
                Text(
                    "KES $projected",
                    style = MaterialTheme.typography.headlineSmall,
                    color = Forest
                )
                Text("projected value", color = InkSoft, style = MaterialTheme.typography.bodySmall)
                Slider(
                    value = periodIndex.toFloat(),
                    onValueChange = { periodIndex = it.roundToInt() },
                    valueRange = 0f..2f,
                    steps = 1,
                    colors = SliderDefaults.colors(thumbColor = Forest, activeTrackColor = Forest)
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    periodLabels.forEach { Text(it, color = InkSoft, style = MaterialTheme.typography.bodySmall) }
                }
            }
        }
        Spacer(Modifier.height(12.dp))

        OutlinedButton(onClick = { navController.navigate("compare") }, modifier = Modifier.fillMaxWidth()) {
            Text("Compare AI vs. fixed allocation")
        }
        Spacer(Modifier.height(8.dp))
        Button(
            onClick = { navController.navigate("deposit") },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = Forest)
        ) {
            Text("Direct Deposit", color = Paper)
        }
        Spacer(Modifier.height(16.dp))
    }
}
