package com.coinsprout.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.coinsprout.app.ui.components.*
import com.coinsprout.app.ui.theme.*
import com.coinsprout.app.viewmodel.CoinSproutViewModel

@Composable
fun RiskResultScreen(navController: NavHostController, vm: CoinSproutViewModel) {
    val rec = vm.portfolio.collectAsStateWithLifecycle().value.recommendation

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp)) {
        if (rec == null) {
            ErrorBanner("We couldn't load your result.", onRetry = { vm.refresh() })
            return@Column
        }
        ScreenTitle(null, "Your starting split")
        Spacer(Modifier.height(8.dp))
        // The AI label is shown only to participants randomised into the "label" arm (server-decided).
        Text(
            if (rec.aiLabel) "AI-recommended · ${rec.category}" else rec.category,
            modifier = Modifier.background(Moss, RoundedCornerShape(50)).padding(horizontal = 12.dp, vertical = 6.dp),
            color = Forest, style = MaterialTheme.typography.bodySmall
        )
        Spacer(Modifier.height(16.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            AllocationDonut(rec.allocation)
            Spacer(Modifier.width(20.dp))
            AllocationLegend(rec.allocation)
        }
        Spacer(Modifier.height(16.dp))
        Text(rec.reasoning, color = Ink)
        Spacer(Modifier.height(8.dp))
        Text("At today's published rates this mix has an indicative yield of about ${"%.1f".format(rec.expectedAnnualYieldPct)}% a year. It is not a forecast.", color = InkSoft, style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.height(8.dp))
        DisclaimerText()
        Spacer(Modifier.height(24.dp))
        PrimaryButton("Go to my dashboard", onClick = { navController.navigate("dashboard") { popUpTo("result") { inclusive = true } } })
    }
}
