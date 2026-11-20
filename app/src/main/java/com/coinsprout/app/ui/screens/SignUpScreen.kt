package com.coinsprout.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.coinsprout.app.ui.components.*
import com.coinsprout.app.ui.theme.*
import com.coinsprout.app.viewmodel.AuthStep
import com.coinsprout.app.viewmodel.AuthViewModel

@Composable
fun SignUpScreen(navController: NavHostController, auth: AuthViewModel) {
    val ui by auth.ui.collectAsStateWithLifecycle()

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp)) {
        when (ui.step) {
            AuthStep.PHONE -> {
                ScreenTitle("STEP 1 OF 2", "What's your M-Pesa number?", "We'll text you a 6-digit code. No password to remember.")
                Spacer(Modifier.height(20.dp))
                OutlinedTextField(
                    value = ui.phone, onValueChange = auth::onPhoneChange, label = { Text("Phone number") }, singleLine = true,
                    isError = ui.error != null, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    placeholder = { Text("0712 345 678") }, modifier = Modifier.fillMaxWidth()
                )
                ui.error?.let { Spacer(Modifier.height(8.dp)); ErrorBanner(it) }
                Spacer(Modifier.height(16.dp))
                PrimaryButton("Send code", onClick = auth::requestOtp, enabled = ui.phone.isNotBlank(), loading = ui.loading)
                Spacer(Modifier.height(16.dp))
                Text(
                    "We store your phone number and your savings activity to run CoinSprout. You can export or delete your data in Settings.",
                    style = MaterialTheme.typography.bodySmall, color = InkSoft
                )
            }
            AuthStep.OTP -> {
                ScreenTitle("STEP 2 OF 2", "Enter the code", "Sent to ${ui.phone}.")
                Spacer(Modifier.height(20.dp))
                OutlinedTextField(
                    value = ui.otp, onValueChange = auth::onOtpChange, label = { Text("6-digit code") }, singleLine = true,
                    isError = ui.error != null, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    modifier = Modifier.fillMaxWidth()
                )
                ui.devOtpHint?.let {
                    TextButton(onClick = { auth.onOtpChange(it) }, modifier = Modifier.heightIn(min = 48.dp)) { Text("Dev server code: $it (tap to fill)", color = SproutDark) }
                }
                ui.error?.let { Spacer(Modifier.height(8.dp)); ErrorBanner(it) }
                Spacer(Modifier.height(16.dp))
                PrimaryButton("Verify", onClick = {
                    auth.verify { hasProfile ->
                        navController.navigate(if (hasProfile) "dashboard" else "quiz") { popUpTo("splash") { inclusive = true } }
                    }
                }, enabled = ui.otp.length == 6, loading = ui.loading)
                Row {
                    TextButton(onClick = auth::requestOtp, enabled = ui.resendInSeconds == 0 && !ui.loading, modifier = Modifier.heightIn(min = 48.dp)) {
                        Text(if (ui.resendInSeconds > 0) "Resend in ${ui.resendInSeconds}s" else "Resend code", color = if (ui.resendInSeconds > 0) InkSoft else SproutDark)
                    }
                    TextButton(onClick = auth::backToPhone, modifier = Modifier.heightIn(min = 48.dp)) { Text("Change number", color = SproutDark) }
                }
            }
        }
    }
}
