package com.coinsprout.app.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.coinsprout.app.BuildConfig
import com.coinsprout.app.data.models.PaymentState
import com.coinsprout.app.domain.Money
import com.coinsprout.app.ui.theme.*

/** The phone only SHOWS the M-Pesa prompt; the server initiates it and confirms it. Nothing is credited until confirmed. */
@Composable
fun PaymentDialog(state: PaymentState, onClose: () -> Unit, onSimulate: (String) -> Unit) {
    if (state is PaymentState.Idle) return
    AlertDialog(
        onDismissRequest = { if (state !is PaymentState.Waiting) onClose() },
        containerColor = Paper,
        title = {
            Text(
                when (state) {
                    is PaymentState.Waiting -> "Approve on your phone"
                    is PaymentState.Success -> "Payment confirmed"
                    is PaymentState.Failed -> "Not completed"
                    PaymentState.Idle -> ""
                },
                color = Forest
            )
        },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                when (state) {
                    is PaymentState.Waiting -> {
                        CircularProgressIndicator(color = Forest)
                        Spacer(Modifier.height(12.dp))
                        Text("Enter your M-Pesa PIN on the prompt to pay ${Money.kes(state.amountCents)}.", color = Ink)
                        if (BuildConfig.DEBUG) {
                            Spacer(Modifier.height(8.dp))
                            Text("Debug build + dev server only:", style = MaterialTheme.typography.bodySmall, color = InkSoft)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(onClick = { onSimulate("success") }) { Text("Approve") }
                                OutlinedButton(onClick = { onSimulate("cancel") }) { Text("Cancel") }
                            }
                        }
                    }
                    is PaymentState.Success -> Text("${Money.kes(state.amountCents)} has been added to your portfolio.", color = Ink)
                    is PaymentState.Failed -> Text(state.message, color = Ink)
                    PaymentState.Idle -> {}
                }
            }
        },
        confirmButton = { if (state !is PaymentState.Waiting) TextButton(onClick = onClose) { Text("Done", color = Forest) } }
    )
}
