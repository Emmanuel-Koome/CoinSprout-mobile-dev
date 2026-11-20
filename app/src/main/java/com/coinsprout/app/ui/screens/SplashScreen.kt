package com.coinsprout.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.coinsprout.app.ui.components.EmojiIcon
import com.coinsprout.app.ui.components.PrimaryButton
import com.coinsprout.app.ui.theme.*
import com.coinsprout.app.viewmodel.CoinSproutViewModel

@Composable
fun SplashScreen(navController: NavHostController, vm: CoinSproutViewModel) {
    val loggedIn by vm.loggedIn.collectAsStateWithLifecycle()
    val portfolio by vm.portfolio.collectAsStateWithLifecycle()

    // A returning, signed-in user goes straight in (no more "I already have an account" skipping authentication).
    LaunchedEffect(loggedIn) {
        if (loggedIn) {
            val next = if (portfolio.recommendation != null) "dashboard" else "quiz"
            navController.navigate(next) { popUpTo("splash") { inclusive = true } }
        }
    }

    Column(
        Modifier.fillMaxSize().background(Forest).padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center
    ) {
        EmojiIcon("🌱", modifier = Modifier.padding(bottom = 8.dp))
        Text("CoinSprout", style = MaterialTheme.typography.displaySmall, color = Paper, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text("Turn spare change into steady savings.", color = Moss, textAlign = TextAlign.Center)
        Spacer(Modifier.height(40.dp))
        if (!loggedIn) {
            PrimaryButton("Get started", onClick = { navController.navigate("signup") })
            Spacer(Modifier.height(8.dp))
            TextButton(onClick = { navController.navigate("signup") }, modifier = Modifier.heightIn(min = 48.dp)) {
                Text("I already have an account", color = Sprout)   // Sprout on Forest = 5.3:1, passes AA
            }
        }
    }
}
