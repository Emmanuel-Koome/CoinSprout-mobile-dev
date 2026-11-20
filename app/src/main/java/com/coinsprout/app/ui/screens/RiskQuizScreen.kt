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

private val questions = listOf(
    Question("When might you need this money?", listOf("Within a year", "In 1 to 3 years", "In 3+ years")),
    Question("If your savings dropped 10% in a month, you would…", listOf("Withdraw everything", "Wait and watch", "Add more while it's cheap")),
    Question("How steady is your income?", listOf("Irregular", "Mostly steady", "Very steady"))
)

@Composable
fun RiskQuizScreen(navController: NavHostController, vm: CoinSproutViewModel) {
    // 0 = unanswered. Nothing is pre-selected, so the answers (and any research data) are not anchored on a default.
    var answers by rememberSaveable { mutableStateOf(listOf(0, 0, 0)) }
    val quiz by vm.quiz.collectAsStateWithLifecycle()

    LaunchedEffect(quiz) {
        if (quiz is Load.Ready) {
            vm.resetQuiz()
            navController.navigate("result") { popUpTo("quiz") { inclusive = true } }
        }
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp)) {
        ScreenTitle("QUICK QUIZ", "Let's find your pace", "Three questions. There are no wrong answers.")
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
        (quiz as? Load.Failed)?.let { ErrorBanner(it.message, onRetry = { vm.submitQuiz(answers[0], answers[1], answers[2]) }); Spacer(Modifier.height(12.dp)) }
        PrimaryButton("See my result", onClick = { vm.submitQuiz(answers[0], answers[1], answers[2]) },
            enabled = answers.all { it > 0 }, loading = quiz is Load.Loading)
    }
}
