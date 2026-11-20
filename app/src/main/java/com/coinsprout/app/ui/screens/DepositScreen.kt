package com.coinsprout.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.coinsprout.app.data.models.PaymentState
import com.coinsprout.app.domain.Money
import com.coinsprout.app.ui.components.*
import com.coinsprout.app.ui.theme.*
import com.coinsprout.app.viewmodel.CoinSproutViewModel

@Composable
fun DepositScreen(navController: NavHostController, vm: CoinSproutViewModel) {
    var text by rememberSaveable { mutableStateOf("") }
    val payment by vm.payment.collectAsStateWithLifecycle()
    val cents = Money.parseKesToCents(text)
    val error = when {
        text.isBlank() -> null
        cents == null -> "Enter an amount like 500"
        cents % 100L != 0L -> "M-Pesa accepts whole shillings only"
        cents > 15_000_000L -> "The M-Pesa limit is KES 150,000 per transaction"
        else -> null
    }

    PaymentDialog(payment, onClose = {
        val ok = payment is PaymentState.Success
        vm.resetPayment()
        if (ok) navController.popBackStack()
    }, onSimulate = vm::devSimulate)

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp)) {
        BackBar("Top up", onBack = { navController.popBackStack() })
        Spacer(Modifier.height(12.dp))
        Text("We'll send an M-Pesa prompt to your phone. You approve it with your PIN, and your balance updates only after M-Pesa confirms the payment.", color = InkSoft)
        Spacer(Modifier.height(16.dp))
        OutlinedTextField(
            value = text, onValueChange = { text = it.filter { c -> c.isDigit() || c == ',' } }, label = { Text("Amount (KES)") }, singleLine = true,
            isError = error != null, supportingText = { error?.let { Text(it) } },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(12.dp))
        PrimaryButton("Send M-Pesa prompt", onClick = { cents?.let(vm::startDeposit) }, enabled = cents != null && error == null && payment is PaymentState.Idle)
        Spacer(Modifier.height(12.dp))
        DisclaimerText()
    }
}
