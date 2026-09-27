package com.coinsprout.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.coinsprout.app.ui.theme.*

@Composable
fun SignUpScreen(navController: NavHostController) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
    ) {
        Text("STEP 1 OF 2", style = MaterialTheme.typography.bodySmall, color = Sprout)
        Spacer(Modifier.height(4.dp))
        Text("Let's get you set up", style = MaterialTheme.typography.headlineSmall, color = Forest)
        Spacer(Modifier.height(6.dp))
        Text(
            "We sign you in the way you already sign in to mobile money \u2014 phone number first.",
            color = InkSoft,
            style = MaterialTheme.typography.bodyMedium
        )
        Spacer(Modifier.height(20.dp))

        Text("Phone number", style = MaterialTheme.typography.bodySmall, color = InkSoft)
        OutlinedTextField(
            value = "+254 7\u2022\u2022 \u2022\u2022\u2022 214",
            onValueChange = {},
            readOnly = true,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(14.dp))

        Text("Enter the OTP sent to you", style = MaterialTheme.typography.bodySmall, color = InkSoft)
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("7", "2", "4", "1").forEach { digit ->
                OutlinedTextField(
                    value = digit,
                    onValueChange = {},
                    readOnly = true,
                    modifier = Modifier.weight(1f)
                )
            }
        }
        Spacer(Modifier.height(16.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(Moss, RoundedCornerShape(16.dp))
                .padding(14.dp)
        ) {
            Text(
                "This pilot uses a simulated funding source \u2014 no real M-Pesa or bank credentials are stored.",
                color = Forest,
                style = MaterialTheme.typography.bodySmall
            )
        }
        Spacer(Modifier.height(20.dp))

        Button(
            onClick = { navController.navigate("quiz") },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = Forest, contentColor = Paper)
        ) {
            Text("Verify & continue", fontWeight = FontWeight.Bold)
        }
    }
}
