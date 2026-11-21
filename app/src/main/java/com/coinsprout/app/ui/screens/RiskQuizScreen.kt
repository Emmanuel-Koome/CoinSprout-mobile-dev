package com.coinsprout.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.coinsprout.app.ui.components.*
import com.coinsprout.app.ui.theme.*
import com.coinsprout.app.viewmodel.CoinSproutViewModel
import com.coinsprout.app.viewmodel.Load

private data class Question(val title: String, val options: List<String>)

// Order and 1..5 scale must match the backend (RiskProfileIn) and the AI engine's features (ml/train.py):
// time_horizon, risk_tolerance, income_stability, investment_experience. Option i is sent as i + 1.
private val questions = listOf(
    Question("When might you need this money?",
        listOf("Within a year", "In 1 to 2 years", "In 2 to 5 years", "In 5 to 10 years", "Not for 10+ years")),
    Question("If your investment dropped 20% in a month, you would…",
        listOf("Withdraw everything", "Withdraw some of it", "Wait and watch", "Stay calm and hold", "Add more while it's cheap")),
    Question("How steady is your income or allowance?",
        listOf("Very irregular", "Often irregular", "Somewhat steady", "Mostly steady", "Very steady")),
    Question("How much have you invested before?",
        listOf("Never", "Only savings or a SACCO", "A money market fund", "Unit trusts or bonds", "I have bought shares (NSE)"))
)

@Composable
fun RiskQuizScreen(navController: NavHostController, vm: CoinSproutViewModel) {
    // 0 = unanswered. Nothing is pre-selected, so the answers (and any research data) are not anchored on a default.
    var answers by rememberSaveable { mutableStateOf(List(questions.size) { 0 }) }
    val quiz by vm.quiz.collectAsStateWithLifecycle()

    LaunchedEffect(quiz) {
        if (quiz is Load.Ready) {
            vm.resetQuiz()
            navController.navigate("result") { popUpTo("quiz") { inclusive = true } }
        }
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp)) {
        ScreenTitle("QUICK QUIZ", "Let's find your pace", "Four questions. There are no wrong answers.")
        Spacer(Modifier.height(16.dp))
        questions.forEachIndexed { qi, q ->
            Text(q.title, style = MaterialTheme.typography.titleMedium, color = Ink)
            Spacer(Modifier.height(8.dp))
            Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                q.options.forEachIndexed { oi, label ->
                    SelectableChip(label, selected = answers[qi] == oi + 1, onClick = {
                        answers = answers.toMutableList().also { it[qi] = oi + 1 }
                    }, modifier = Modifier.fillMaxWidth())
                }
            }
            Spacer(Modifier.height(20.dp))
        }
        val submit = { vm.submitQuiz(answers[0], answers[1], answers[2], answers[3]) }
        (quiz as? Load.Failed)?.let { ErrorBanner(it.message, onRetry = submit); Spacer(Modifier.height(12.dp)) }
        PrimaryButton("See my result", onClick = submit,
            enabled = answers.all { it > 0 }, loading = quiz is Load.Loading)
    }
}
