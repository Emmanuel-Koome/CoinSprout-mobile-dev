package com.coinsprout.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.coinsprout.app.data.RiskData
import com.coinsprout.app.state.AppState
import com.coinsprout.app.ui.theme.*

private data class QuizOption(val label: String, val value: Int)

@Composable
private fun QuizQuestion(
    question: String,
    options: List<QuizOption>,
    selected: Int,
    onSelect: (Int) -> Unit
) {
    Text(question, style = MaterialTheme.typography.titleMedium, color = Ink)
    Spacer(Modifier.height(8.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { option ->
            val isActive = option.value == selected
            Box(
                modifier = Modifier
                    .border(
                        width = 1.5.dp,
                        color = if (isActive) Forest else LineColor,
                        shape = RoundedCornerShape(20.dp)
                    )
                    .background(
                        if (isActive) Forest else Paper,
                        RoundedCornerShape(20.dp)
                    )
                    .clickable { onSelect(option.value) }
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Text(
                    option.label,
                    color = if (isActive) Paper else InkSoft,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
    Spacer(Modifier.height(18.dp))
}

@Composable
fun RiskQuizScreen(navController: NavHostController, appState: AppState) {
    var horizon by remember { mutableStateOf(3) }
    var drop by remember { mutableStateOf(2) }
    var income by remember { mutableStateOf(2) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Text("STEP 2 OF 2", style = MaterialTheme.typography.bodySmall, color = Sprout)
        Spacer(Modifier.height(4.dp))
        Text("A few quick questions", style = MaterialTheme.typography.headlineSmall, color = Forest)
        Spacer(Modifier.height(6.dp))
        Text(
            "This helps us recommend the right mix of investments for you.",
            color = InkSoft,
            style = MaterialTheme.typography.bodyMedium
        )
        Spacer(Modifier.height(18.dp))

        QuizQuestion(
            "When might you need this money?",
            listOf(QuizOption("Within a year", 1), QuizOption("1\u20133 years", 2), QuizOption("3+ years", 3)),
            horizon
        ) { horizon = it }

        QuizQuestion(
            "If your savings dropped 5% in a month, you'd\u2026",
            listOf(QuizOption("Withdraw everything", 1), QuizOption("Feel nervous, but hold", 2), QuizOption("Add more money", 3)),
            drop
        ) { drop = it }

        QuizQuestion(
            "How regular is your income?",
            listOf(QuizOption("Irregular", 1), QuizOption("Somewhat regular", 2), QuizOption("Very stable", 3)),
            income
        ) { income = it }

        Button(
            onClick = {
                val score = horizon + drop + income
                appState.riskCategory = RiskData.scoreToCategory(score)
                navController.navigate("result")
            },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = Forest, contentColor = Paper)
        ) {
            Text("See my results", fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(24.dp))
    }
}
