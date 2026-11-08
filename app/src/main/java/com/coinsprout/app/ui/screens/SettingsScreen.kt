package com.coinsprout.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.coinsprout.app.ui.theme.*
import com.coinsprout.app.viewmodel.CoinSproutViewModel

@Composable
fun SettingsScreen(navController: NavHostController, viewModel: CoinSproutViewModel) {
    val settings by viewModel.settings.collectAsState()
    val riskCategory by viewModel.riskCategory.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Text("App Settings & Rules", style = MaterialTheme.typography.headlineSmall, color = Forest)
        Spacer(Modifier.height(16.dp))

        Text("SAVINGS RULES", style = MaterialTheme.typography.bodySmall, color = InkSoft, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))

        Card {
            Column(Modifier.padding(16.dp)) {
                Text("Round-Up Increment", style = MaterialTheme.typography.titleMedium, color = Forest)
                Text("Every purchase will be rounded up to the nearest milestone.", style = MaterialTheme.typography.bodySmall, color = InkSoft)
                Spacer(Modifier.height(8.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(10, 50, 100).forEach { inc ->
                        val isSelected = settings.roundUpIncrement == inc
                        FilterChip(
                            selected = isSelected,
                            onClick = { viewModel.updateSettings(settings.copy(roundUpIncrement = inc)) },
                            label = { Text("Nearest KES $inc") }
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(12.dp))

        Card {
            Column(Modifier.padding(16.dp)) {
                Text("STK Push Trigger Threshold", style = MaterialTheme.typography.titleMedium, color = Forest)
                Text("Send M-Pesa PIN prompt once pending round-ups reach this target.", style = MaterialTheme.typography.bodySmall, color = InkSoft)
                Spacer(Modifier.height(8.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(100.0, 200.0, 500.0).forEach { target ->
                        val isSelected = settings.stkThreshold == target
                        FilterChip(
                            selected = isSelected,
                            onClick = { viewModel.updateSettings(settings.copy(stkThreshold = target)) },
                            label = { Text("KES ${target.toInt()}") }
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(16.dp))

        Text("AUTOMATION & PERMISSIONS", style = MaterialTheme.typography.bodySmall, color = InkSoft, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))

        Card {
            Column(Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("SMS Auto-Detection", style = MaterialTheme.typography.titleMedium, color = Forest)
                        Text("Parse M-Pesa messages passively in the background.", style = MaterialTheme.typography.bodySmall, color = InkSoft)
                    }
                    Switch(
                        checked = settings.isSmsListenerEnabled,
                        onCheckedChange = { viewModel.updateSettings(settings.copy(isSmsListenerEnabled = it)) }
                    )
                }
                HorizontalDivider(Modifier.padding(vertical = 12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Automatic STK Push", style = MaterialTheme.typography.titleMedium, color = Forest)
                        Text("Prompt for M-Pesa PIN immediately when threshold is reached.", style = MaterialTheme.typography.bodySmall, color = InkSoft)
                    }
                    Switch(
                        checked = settings.isAutoStkEnabled,
                        onCheckedChange = { viewModel.updateSettings(settings.copy(isAutoStkEnabled = it)) }
                    )
                }
            }
        }
        Spacer(Modifier.height(16.dp))

        Text("INVESTMENT PROFILE", style = MaterialTheme.typography.bodySmall, color = InkSoft, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))

        Card {
            Column(Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Current Risk Category", style = MaterialTheme.typography.titleMedium, color = Forest)
                        Text("$riskCategory Grower", style = MaterialTheme.typography.headlineSmall, color = Sprout)
                    }
                    OutlinedButton(onClick = { navController.navigate("quiz") }) {
                        Text("Retake Quiz")
                    }
                }
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}
