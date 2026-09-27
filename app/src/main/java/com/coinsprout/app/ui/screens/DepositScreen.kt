package com.coinsprout.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.coinsprout.app.state.AppState
import com.coinsprout.app.ui.theme.Forest
import com.coinsprout.app.ui.theme.Moss
import com.coinsprout.app.ui.theme.Paper
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DepositScreen(navController: NavHostController, appState: AppState) {
    var amountText by remember { mutableStateOf("") }
    var isSuccess by remember { mutableStateOf(false) }
    val amount = amountText.toIntOrNull() ?: 0

    LaunchedEffect(isSuccess) {
        if (isSuccess) {
            delay(2000)
            navController.popBackStack()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Direct Deposit", color = Forest) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Text("←", style = MaterialTheme.typography.headlineSmall)
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (!isSuccess) {
                Text(
                    "Enter amount to deposit into your $${appState.riskCategory} portfolio",
                    style = MaterialTheme.typography.bodyLarge,
                    color = Forest
                )
                Spacer(Modifier.height(24.dp))

                OutlinedTextField(
                    value = amountText,
                    onValueChange = { if (it.all { char -> char.isDigit() }) amountText = it },
                    label = { Text("Amount (KES)") },
                    prefix = { Text("KES ") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Spacer(Modifier.height(32.dp))

                Button(
                    onClick = {
                        if (amount > 0) {
                            appState.deposit(amount)
                            isSuccess = true
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = amount > 0,
                    colors = ButtonDefaults.buttonColors(containerColor = Forest)
                ) {
                    Text("Deposit Now", color = Paper)
                }
            } else {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .size(80.dp)
                            .background(Moss, shape = androidx.compose.foundation.shape.CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("✓", style = MaterialTheme.typography.headlineLarge, color = Forest)
                    }
                    Spacer(Modifier.height(24.dp))
                    Text("Deposit Successful!", style = MaterialTheme.typography.headlineSmall, color = Forest)
                    Text("KES $amount has been added to your portfolio.", style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}
