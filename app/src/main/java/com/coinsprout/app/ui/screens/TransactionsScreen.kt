package com.coinsprout.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.coinsprout.app.BuildConfig
import com.coinsprout.app.data.models.PaymentState
import com.coinsprout.app.data.models.TransactionModel
import com.coinsprout.app.data.models.TxStatus
import com.coinsprout.app.domain.IsoTime
import com.coinsprout.app.domain.Money
import com.coinsprout.app.ui.components.*
import com.coinsprout.app.ui.theme.*
import com.coinsprout.app.viewmodel.CoinSproutViewModel

@Composable
fun TransactionRow(t: TransactionModel) {
    val status = when (t.status) {
        TxStatus.PENDING_ROUNDUP -> "Waiting to invest"
        TxStatus.STK_REQUESTED -> "Awaiting your M-Pesa approval"
        TxStatus.ROUTED_TO_PORTFOLIO -> "Invested"
        TxStatus.SKIPPED -> if (t.kind == "PAID" || t.kind == "AIRTIME") "No round-up" else "Not a purchase"
    }
    Row(Modifier.fillMaxWidth().background(Paper, RoundedCornerShape(16.dp)).padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(t.merchantName, color = Ink, fontWeight = FontWeight.Bold)
            Text("${IsoTime.display(t.occurredAtMillis)} · $status" + if (!t.synced) " · not synced yet" else "", style = MaterialTheme.typography.bodySmall, color = InkSoft)
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(Money.kes(t.amountCents), color = Ink)
            if (t.roundUpCents > 0) Text("+${Money.kes(t.roundUpCents)}", color = SproutDark, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
fun TransactionsScreen(navController: NavHostController, vm: CoinSproutViewModel) {
    val txs by vm.transactions.collectAsStateWithLifecycle()
    val pending by vm.pendingCents.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val payment by vm.payment.collectAsStateWithLifecycle()
    var query by rememberSaveable { mutableStateOf("") }
    var adding by rememberSaveable { mutableStateOf(false) }
    val shown = txs.filter { it.merchantName.contains(query, ignoreCase = true) }

    PaymentDialog(payment, onClose = vm::resetPayment, onSimulate = vm::devSimulate)
    if (adding) AddPurchaseDialog(onDismiss = { adding = false }, onSave = { m, c -> vm.addManualPurchase(m, c); adding = false })

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { ScreenTitle("ACTIVITY", "Purchases & round-ups", if (BuildConfig.SMS_AUTO_DETECT) "Captured from your M-Pesa messages, or added by hand." else "Add your purchases by hand. Automatic SMS capture isn't part of this version.") }
        item {
            Column(Modifier.fillMaxWidth().background(Moss, RoundedCornerShape(16.dp)).padding(16.dp)) {
                Text("Round-ups waiting: ${Money.kes(pending)}", color = Forest, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = vm::startSweep, enabled = pending >= settings.stkThresholdCents && payment is PaymentState.Idle, modifier = Modifier.heightIn(min = 48.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Forest, contentColor = Paper)) { Text("Invest now") }
                    OutlinedButton(onClick = { adding = true }, modifier = Modifier.heightIn(min = 48.dp)) { Text("+ Add purchase", color = Forest) }
                }
            }
        }
        item { OutlinedTextField(query, { query = it }, label = { Text("Search merchants") }, singleLine = true, modifier = Modifier.fillMaxWidth()) }
        if (shown.isEmpty()) item { Text(if (txs.isEmpty()) "Nothing here yet. Add your first purchase." else "No matches.", color = InkSoft) }
        items(shown, key = { it.mpesaCode }) { TransactionRow(it) }
    }
}

@Composable
private fun AddPurchaseDialog(onDismiss: () -> Unit, onSave: (String, Long) -> Unit) {
    var merchant by rememberSaveable { mutableStateOf("") }
    var amount by rememberSaveable { mutableStateOf("") }
    val cents = Money.parseKesToCents(amount)
    val amountError = if (amount.isNotBlank() && cents == null) "Enter an amount like 85 or 85.50" else null
    AlertDialog(
        onDismissRequest = onDismiss, containerColor = Paper,
        title = { Text("Add a purchase", color = Forest) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(merchant, { merchant = it.take(60) }, label = { Text("Where did you pay?") }, singleLine = true)
                OutlinedTextField(amount, { amount = it }, label = { Text("Amount (KES)") }, singleLine = true, isError = amountError != null,
                    supportingText = { amountError?.let { Text(it) } }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
            }
        },
        confirmButton = { TextButton(onClick = { cents?.let { onSave(merchant, it) } }, enabled = merchant.isNotBlank() && cents != null) { Text("Save", color = Forest) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = InkSoft) } }
    )
}
