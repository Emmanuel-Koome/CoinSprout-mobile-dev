package com.coinsprout.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.coinsprout.app.data.remote.ForecastDto
import com.coinsprout.app.data.remote.RateDto
import com.coinsprout.app.domain.Money
import com.coinsprout.app.ui.components.*
import com.coinsprout.app.ui.theme.*
import com.coinsprout.app.viewmodel.CoinSproutViewModel
import com.coinsprout.app.viewmodel.Load

private val periods = listOf(6 to "6 months", 12 to "1 year", 60 to "5 years")

@Composable
fun PortfolioScreen(navController: NavHostController, vm: CoinSproutViewModel) {
    val portfolio by vm.portfolio.collectAsStateWithLifecycle()
    val pending by vm.pendingCents.collectAsStateWithLifecycle()
    val rates by vm.rates.collectAsStateWithLifecycle()
    val forecast by vm.forecast.collectAsStateWithLifecycle()
    var period by rememberSaveable { mutableIntStateOf(1) }
    val rec = portfolio.recommendation
    val base = portfolio.balanceCents + pending

    LaunchedEffect(Unit) { vm.loadRates() }
    LaunchedEffect(period, base) { vm.loadForecast(periods[period].first) }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val wide = maxWidth >= 720.dp
        val allocationCard: @Composable () -> Unit = {
            Card(colors = CardDefaults.cardColors(containerColor = Paper), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    if (rec == null) {
                        Text("Take the quiz to see your split.", color = InkSoft)
                        TextButton(onClick = { navController.navigate("quiz") }, modifier = Modifier.heightIn(min = 48.dp)) { Text("Start quiz", color = SproutDark) }
                    } else {
                        Text(if (rec.aiLabel) "AI-recommended · ${rec.category}" else rec.category, style = MaterialTheme.typography.titleMedium, color = Forest)
                        Spacer(Modifier.height(12.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            AllocationDonut(rec.allocation); Spacer(Modifier.width(20.dp)); AllocationLegend(rec.allocation)
                        }
                        Spacer(Modifier.height(12.dp))
                        Text("Why this split?", fontWeight = FontWeight.Bold, color = Forest)
                        Text(rec.reasoning, color = Ink, style = MaterialTheme.typography.bodyMedium)
                        Spacer(Modifier.height(4.dp))
                        Text("Indicative yield at today's published rates: ${"%.1f".format(rec.expectedAnnualYieldPct)}% a year. Not a forecast.", color = InkSoft, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
        val projectionCard: @Composable () -> Unit = {
            Card(colors = CardDefaults.cardColors(containerColor = Paper), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text("What could happen", style = MaterialTheme.typography.titleMedium, color = Forest)
                    Text(
                        if (base > 0) "Based on your current ${Money.kes(base)}, with no new deposits." else "Example only: if you invested ${Money.kes(CoinSproutViewModel.EXAMPLE_PRINCIPAL_CENTS)}.",
                        style = MaterialTheme.typography.bodySmall, color = InkSoft
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        periods.forEachIndexed { i, (_, label) -> SelectableChip(label, selected = i == period, onClick = { period = i }) }
                    }
                    Spacer(Modifier.height(12.dp))
                    when (val f = forecast) {
                        Load.Idle, Load.Loading -> LinearProgressIndicator(Modifier.fillMaxWidth())
                        is Load.Failed -> ErrorBanner(f.message, onRetry = { vm.loadForecast(periods[period].first) })
                        is Load.Ready -> ForecastBlock(f.value)
                    }
                }
            }
        }
        val ratesCard: @Composable () -> Unit = {
            Card(colors = CardDefaults.cardColors(containerColor = Paper), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text("Published rates", style = MaterialTheme.typography.titleMedium, color = Forest)
                    when (val r = rates) {
                        Load.Idle, Load.Loading -> LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 8.dp))
                        is Load.Failed -> ErrorBanner(r.message, Modifier.padding(top = 8.dp), onRetry = vm::loadRates)
                        is Load.Ready -> r.value.forEach { RateRow(it) }
                    }
                }
            }
        }
        if (wide) {
            Row(Modifier.fillMaxSize().padding(20.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) { PortfolioHeader(); allocationCard(); ratesCard() }
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) { projectionCard(); DisclaimerText() }
            }
        } else {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                PortfolioHeader(); allocationCard(); projectionCard(); ratesCard(); DisclaimerText()
            }
        }
    }
}

@Composable
private fun PortfolioHeader() = ScreenTitle("YOUR MIX", "Portfolio", "Where your invested savings go.")

@Composable
private fun ForecastBlock(f: ForecastDto) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        ForecastLine("If things go badly", f.pessimisticValueCents, f.pessimisticAnnualReturnPct)
        ForecastLine("Expected", f.projectedValueCents, f.expectedAnnualReturnPct, emphasise = true)
        ForecastLine("If things go well", f.optimisticValueCents, f.optimisticAnnualReturnPct)
        Text("Expected value in today's money (after ${"%.0f".format(f.assumedInflationPct)}% a year inflation): ${Money.kes(f.inflationAdjustedValueCents)}", style = MaterialTheme.typography.bodySmall, color = InkSoft)
        var open by rememberSaveable { mutableStateOf(false) }
        TextButton(onClick = { open = !open }, modifier = Modifier.heightIn(min = 48.dp)) { Text(if (open) "Hide assumptions" else "Show assumptions", color = SproutDark) }
        if (open) f.assumptions.forEach { Text("• $it", style = MaterialTheme.typography.bodySmall, color = InkSoft) }
        Text(f.disclaimer, style = MaterialTheme.typography.bodySmall, color = InkSoft)
    }
}

@Composable
private fun ForecastLine(label: String, cents: Long, annualPct: Double, emphasise: Boolean = false) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Column(Modifier.weight(1f)) {
            Text(label, color = Ink, fontWeight = if (emphasise) FontWeight.Bold else FontWeight.Normal)
            Text("${"%.1f".format(annualPct)}% a year", style = MaterialTheme.typography.bodySmall, color = InkSoft)
        }
        Text(Money.kes(cents), color = Forest, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun RateRow(r: RateDto) {
    Column(Modifier.padding(top = 10.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(r.assetClass, color = Ink, modifier = Modifier.weight(1f))
            Text("${"%.2f".format(r.annualYieldRate)}%", color = Forest, fontWeight = FontWeight.Bold)
        }
        Text(
            if (r.illustrative) "Illustrative demo value · not live market data" else "${r.providerName} · as of ${r.effectiveDate}",
            style = MaterialTheme.typography.bodySmall, color = InkSoft
        )
    }
}
