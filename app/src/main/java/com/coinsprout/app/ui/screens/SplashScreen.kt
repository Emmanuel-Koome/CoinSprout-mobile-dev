package com.coinsprout.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.coinsprout.app.ui.theme.Forest
import com.coinsprout.app.ui.theme.InkSoft
import com.coinsprout.app.ui.theme.Paper

@Composable
fun SplashScreen(navController: NavHostController) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .background(Forest, RoundedCornerShape(20.dp)),
            contentAlignment = Alignment.Center
        ) {
            Text("\uD83C\uDF31", fontSize = 28.sp)
        }
        Spacer(Modifier.height(16.dp))
        Text(
            "CoinSprout",
            style = MaterialTheme.typography.headlineMedium,
            color = Forest
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "Round up what you spend. Let AI grow what you save.",
            color = InkSoft,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
        Spacer(Modifier.height(28.dp))
        Button(
            onClick = { navController.navigate("signup") },
            modifier = Modifier.fillMaxWidth(),
            colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = Forest, contentColor = Paper)
        ) {
            Text("Get started", fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(10.dp))
        OutlinedButton(
            onClick = { navController.navigate("dashboard") },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("I already have an account")
        }
    }
}
