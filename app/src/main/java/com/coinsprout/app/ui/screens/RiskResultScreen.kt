package com.coinsprout.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.coinsprout.app.data.RiskData
import com.coinsprout.app.state.AppState
import com.coinsprout.app.ui.components.AllocationDonut
import com.coinsprout.app.ui.theme.*

@Composable
fun RiskResultScreen(navController: NavHostController, appState: AppState) {
    val category = appState.riskCategory
    val allocation = RiskData.allocations.getValue(category)
    val description = RiskData.descriptions.getValue(category)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        AssistChip(onClick = {}, label = { Text("YOUR PROFILE") })
        Spacer(Modifier.height(10.dp))
        Text(
            "You're a $category Grower \uD83C\uDF31",
            style = MaterialTheme.typography.headlineSmall,
            color = Forest,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(8.dp))
        Text(
            description,
            color = InkSoft,
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodyMedium
        )
        Spacer(Modifier.height(18.dp))
        AllocationDonut(allocation = allocation)
        Spacer(Modifier.height(24.dp))
        Button(
            onClick = {
                navController.navigate("dashboard") {
                    popUpTo("splash") { inclusive = true }
                }
            },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = Forest, contentColor = Paper)
        ) {
            Text("Set up my portfolio", fontWeight = FontWeight.Bold)
        }
    }
}
