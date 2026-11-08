package com.coinsprout.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.coinsprout.app.data.models.TransactionModel
import com.coinsprout.app.data.models.TransactionStatus
import com.coinsprout.app.ui.theme.*
import com.coinsprout.app.viewmodel.CoinSproutViewModel

@Composable
fun TransactionsScreen(navController: NavHostController, viewModel: CoinSproutViewModel) {
    val transactions by viewModel.transactions.collectAsState()
    val pendingBalance by viewModel.pendingRoundUpBalance.collectAsState()
    var searchQuery by remember { mutableStateOf("") }
    var showSimulateDialog by remember { mutableStateOf(false) }

    val filtered = remember(transactions, searchQuery) {
        if (searchQuery.isBlank()) transactions
        else transactions.filter { it.merchantName.contains(searchQuery, ignoreCase = true) }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Activity & Round-ups", style = MaterialTheme.typography.headlineSmall, color = Forest)
            Button(
                onClick = { showSimulateDialog = true },
                colors = ButtonDefaults.buttonColors(containerColor = Sprout)
            ) {
                Text("+ Test SMS", color = Forest, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
            }
        }
        Spacer(Modifier.height(12.dp))

        Card(colors = CardDefaults.cardColors(containerColor = Moss)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("PENDING ROUND-UPS", style = MaterialTheme.typography.bodySmall, color = Forest)
                    Text("KES ${pendingBalance.toInt()}", style = MaterialTheme.typography.headlineMedium, color = Forest)
                }
                if (pendingBalance >= 100.0) {
                    Button(
                        onClick = { viewModel.triggerStkPush() },
                        colors = ButtonDefaults.buttonColors(containerColor = Forest)
                    ) {
                        Text("Trigger STK", color = Paper, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
        Spacer(Modifier.height(16.dp))

        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text("Search transactions...") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
        Spacer(Modifier.height(12.dp))

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            items(filtered, key = { it.id }) { tx ->
                TransactionRow(tx)
            }
        }
    }

    if (showSimulateDialog) {
        SimulateSmsDialog(
            onDismiss = { showSimulateDialog = false },
            onSimulate = { merchant, amount ->
                viewModel.simulateSmsTransaction(merchant, amount)
                showSimulateDialog = false
            }
        )
    }
}

@Composable
private fun TransactionRow(tx: TransactionModel) {
    Card(colors = CardDefaults.cardColors(containerColor = Paper)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Moss),
                    contentAlignment = Alignment.Center
                ) { Text(tx.emoji) }
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(tx.merchantName, style = MaterialTheme.typography.titleMedium, color = Forest)
                    Text("${tx.timestamp} • Spent KES ${tx.originalAmount.toInt()}", style = MaterialTheme.typography.bodySmall, color = InkSoft)
                }
            }

            Column(horizontalAlignment = Alignment.End) {
                if (tx.roundUpAmount > 0) {
                    Text("+KES ${tx.roundUpAmount.toInt()}", style = MaterialTheme.typography.titleMedium, color = ForestSoft)
                } else {
                    Text("KES 0", style = MaterialTheme.typography.bodySmall, color = InkSoft)
                }

                val statusLabel = when (tx.status) {
                    TransactionStatus.PENDING_ROUNDUP -> "Pending STK"
                    TransactionStatus.DEDUCTED_STK -> "STK Paid"
                    TransactionStatus.ROUTED_TO_PORTFOLIO -> "Routed"
                }
                Text(statusLabel, style = MaterialTheme.typography.bodySmall, color = Sprout, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun SimulateSmsDialog(onDismiss: () -> Unit, onSimulate: (String, Double) -> Unit) {
    var merchant by remember { mutableStateOf("Java House") }
    var amount by remember { mutableStateOf("285") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Simulate M-Pesa SMS") },
        text = {
            Column {
                Text("Simulate an incoming M-Pesa confirmation SMS to test background parsing & round-up calculation.")
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = merchant,
                    onValueChange = { merchant = it },
                    label = { Text("Merchant / Till Name") },
                    singleLine = true
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = amount,
                    onValueChange = { amount = it },
                    label = { Text("Amount Spent (KES)") },
                    singleLine = true
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val amtDouble = amount.toDoubleOrNull() ?: 100.0
                    onSimulate(merchant, amtDouble)
                },
                colors = ButtonDefaults.buttonColors(containerColor = Forest)
            ) {
                Text("Receive SMS", color = Paper)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
