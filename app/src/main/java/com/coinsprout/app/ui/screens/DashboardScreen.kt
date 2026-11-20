package com.coinsprout.app.ui.screens

import android.Manifest
import android.os.Build
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.coinsprout.app.data.models.PaymentState
import com.coinsprout.app.domain.IsoTime
import com.coinsprout.app.domain.Money
import com.coinsprout.app.ui.components.*
import com.coinsprout.app.ui.theme.*
import com.coinsprout.app.viewmodel.CoinSproutViewModel
import com.coinsprout.app.viewmodel.Load

@Composable
fun DashboardScreen(navController: NavHostController, vm: CoinSproutViewModel) {
    val portfolio by vm.portfolio.collectAsStateWithLifecycle()
    val pending by vm.pendingCents.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val txs by vm.transactions.collectAsStateWithLifecycle()
    val payment by vm.payment.collectAsStateWithLifecycle()
    val insights by vm.insights.collectAsStateWithLifecycle()
    val agents by vm.agents.collectAsStateWithLifecycle()
    val syncing by vm.syncing.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    LaunchedEffect(Unit) {
        vm.refresh(); vm.loadInsights()
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    val ready = pending >= settings.stkThresholdCents && payment is PaymentState.Idle
    PaymentDialog(payment, onClose = vm::resetPayment, onSimulate = vm::devSimulate)

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            ScreenTitle("YOUR GARDEN", "Hello 👋")
            Row {
                TextButton(onClick = { navController.navigate("notifications") }, modifier = Modifier.heightIn(min = 48.dp)) { Text("Alerts", color = SproutDark) }
                TextButton(onClick = vm::refresh, enabled = !syncing, modifier = Modifier.heightIn(min = 48.dp)) { Text(if (syncing) "Syncing…" else "Refresh", color = SproutDark) }
            }
        }

        // Balance card: confirmed money and unconfirmed round-ups are shown separately and honestly.
        Column(Modifier.fillMaxWidth().background(Forest, RoundedCornerShape(24.dp)).padding(20.dp)) {
            Text("Invested balance", color = Moss, style = MaterialTheme.typography.bodySmall)
            Text(Money.kes(portfolio.balanceCents), color = Paper, style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(12.dp))
            Text("Round-ups waiting: ${Money.kes(pending)}", color = Paper)
            if (portfolio.inFlightCents > 0) Text("Awaiting your M-Pesa approval: ${Money.kes(portfolio.inFlightCents)}", color = Mustard, style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(4.dp))
            Text(
                if (ready) "You've reached ${Money.kes(settings.stkThresholdCents)}. Ready to invest." else "Invest once round-ups reach ${Money.kes(settings.stkThresholdCents)}.",
                color = Moss, style = MaterialTheme.typography.bodySmall
            )
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = vm::startSweep, enabled = ready, modifier = Modifier.heightIn(min = 48.dp), colors = ButtonDefaults.buttonColors(containerColor = Sprout, contentColor = Forest)) { Text("Invest round-ups", fontWeight = FontWeight.Bold) }
                OutlinedButton(onClick = { navController.navigate("deposit") }, modifier = Modifier.heightIn(min = 48.dp)) { Text("Top up", color = Paper) }
            }
            if (portfolio.asOfMillis > 0) Text("Updated ${IsoTime.display(portfolio.asOfMillis)}", color = Moss, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp))
        }

        portfolio.recommendation?.let { rec ->
            Card(colors = CardDefaults.cardColors(containerColor = Paper), modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    AllocationDonut(rec.allocation, diameter = 84.dp, strokeWidth = 16.dp)
                    Spacer(Modifier.width(16.dp))
                    Column {
                        Text(if (rec.aiLabel) "AI-recommended · ${rec.category}" else rec.category, style = MaterialTheme.typography.titleMedium, color = Forest)
                        TextButton(onClick = { navController.navigate("portfolio") }, modifier = Modifier.heightIn(min = 48.dp)) { Text("See details", color = SproutDark) }
                    }
                }
            }
        }

        Card(colors = CardDefaults.cardColors(containerColor = Paper), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Text("Spending insights", style = MaterialTheme.typography.titleMedium, color = Forest)
                when (val i = insights) {
                    Load.Idle, Load.Loading -> LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 8.dp))
                    is Load.Failed -> ErrorBanner(i.message, Modifier.padding(top = 8.dp), onRetry = vm::loadInsights)
                    is Load.Ready -> i.value.forEach { Text("• ${it.message}", color = Ink, modifier = Modifier.padding(top = 6.dp)) }
                }
            }
        }

        NearbyAgentsCard(agents, onLocation = vm::loadAgents, onUnavailable = vm::agentsUnavailable)

        Text("Recent activity", style = MaterialTheme.typography.titleMedium, color = Forest)
        if (txs.isEmpty()) Text("No purchases yet. Add one from the Activity tab.", color = InkSoft)
        txs.take(3).forEach { TransactionRow(it) }
        TextButton(onClick = { navController.navigate("transactions") }, modifier = Modifier.heightIn(min = 48.dp)) { Text("View all activity", color = SproutDark) }
    }
}
