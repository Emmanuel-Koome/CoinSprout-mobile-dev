package com.coinsprout.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.coinsprout.app.data.RiskData
import com.coinsprout.app.state.AppState
import com.coinsprout.app.ui.theme.*
import kotlin.math.roundToInt

@Composable
fun CompareModesScreen(navController: NavHostController, appState: AppState) {
    val growth = RiskData.growth.getValue(appState.riskCategory)
    val aiPct = ((growth[1] - 1.0) * 100).roundToInt()
    val fixedPct = 4

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Text("RESEARCH FEATURE", style = MaterialTheme.typography.bodySmall, color = Sprout)
        Spacer(Modifier.height(4.dp))
        Text("AI vs. fixed allocation", style = MaterialTheme.typography.headlineSmall, color = Forest)
        Spacer(Modifier.height(6.dp))
        Text(
            "See how a personalised, AI-recommended split compares to the same fixed split every user gets.",
            color = InkSoft,
            style = MaterialTheme.typography.bodyMedium
        )
        Spacer(Modifier.height(16.dp))

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Row(
                    Modifier.fillMaxWidth().height(100.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.Bottom
                ) {
                    BarColumn(label = "AI mode", pct = aiPct, heightFraction = 0.7f, color = Forest)
                    BarColumn(label = "Fixed mode", pct = fixedPct, heightFraction = 0.4f, color = LineColor)
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    "hypothetical 1-year return, based on historical fund data",
                    color = InkSoft,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
        Spacer(Modifier.height(14.dp))

        Row(
            Modifier.fillMaxWidth().padding(vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text("Use AI-recommended mode", style = MaterialTheme.typography.titleMedium)
                Text("Switch to compare live in your portfolio", style = MaterialTheme.typography.bodySmall, color = InkSoft)
            }
            Switch(
                checked = appState.useAiMode,
                onCheckedChange = { appState.useAiMode = it },
                colors = SwitchDefaults.colors(checkedTrackColor = Sprout)
            )
        }

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Text("How much do you trust this recommendation?", style = MaterialTheme.typography.titleMedium)
                Row(Modifier.padding(top = 8.dp)) {
                    (1..5).forEach { i ->
                        Text(
                            "\u2605",
                            color = if (i <= appState.trustRating) Mustard else LineColor,
                            style = androidx.compose.ui.text.TextStyle(fontSize = 22.sp),
                            modifier = Modifier
                                .padding(end = 6.dp)
                                .then(Modifier)
                                .clickable { appState.trustRating = i }
                        )
                    }
                }
                if (appState.trustRating > 0) {
                    Text(
                        "Thanks \u2014 your response feeds directly into the study data.",
                        color = Sprout,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
    }
}

@Composable
private fun BarColumn(label: String, pct: Int, heightFraction: Float, color: androidx.compose.ui.graphics.Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text("+$pct%", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(4.dp))
        Box(
            Modifier
                .width(38.dp)
                .fillMaxHeight(heightFraction)
                .background(color, RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp))
        )
        Spacer(Modifier.height(6.dp))
        Text(label, color = InkSoft, style = MaterialTheme.typography.bodySmall)
    }
}
