package com.coinsprout.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.coinsprout.app.ui.components.*
import com.coinsprout.app.ui.theme.*

private val lessons = listOf(
    "How round-ups work" to "Each time you pay with M-Pesa, we round the purchase up to your chosen step (say KES 10). The difference waits as a round-up. When enough has built up, you approve one M-Pesa prompt and it is invested. If you make about 3 purchases a day and round up KES 7 on average, that is roughly KES 7,600 a year before any returns.",
    "Money Market Funds" to "A pooled fund that lends short-term to banks and the government. Money is usually available within a few days. Yields change over time and are not guaranteed, but the value rarely swings much.",
    "Treasury Bills" to "Short-term loans to the government. The rate is fixed at the time you buy and paid when the bill matures, so your return is known if you hold to the end.",
    "Equity Unit Trusts" to "Your money is pooled to buy shares in companies. Over many years shares have tended to grow faster than savings accounts, but the value can fall sharply, even by a fifth or more in a bad year. Only use money you will not need soon.",
    "Why a cash buffer?" to "A small slice stays in cash so a surprise expense does not force you to sell investments at a bad moment.",
    "About projections" to "Any projection is a set of assumptions, not a promise. That is why CoinSprout shows a bad case, an expected case and a good case instead of one number."
)

@Composable
fun EducationScreen(navController: NavHostController) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        BackBar("Learn", onBack = { navController.popBackStack() })
        lessons.forEach { (title, body) ->
            Card(colors = CardDefaults.cardColors(containerColor = Paper), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text(title, style = MaterialTheme.typography.titleMedium, color = Forest)
                    Spacer(Modifier.height(6.dp))
                    Text(body, color = Ink)
                }
            }
        }
        Text("General education, not personal financial advice.", style = MaterialTheme.typography.bodySmall, color = InkSoft)
    }
}
