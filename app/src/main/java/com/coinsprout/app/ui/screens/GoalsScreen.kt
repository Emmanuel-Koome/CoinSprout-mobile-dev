package com.coinsprout.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.coinsprout.app.ui.theme.*

private data class Goal(val name: String, val saved: Int, val target: Int, val note: String, val color: androidx.compose.ui.graphics.Color)

@Composable
fun GoalsScreen(navController: NavHostController) {
    val goals = listOf(
        Goal("Emergency Fund", 6000, 20000, "Target: Dec 2026 \u00B7 on track at current pace", Sprout),
        Goal("Laptop Fund", 2450, 60000, "Try 3x round-ups to reach this 4 months sooner", Mustard)
    )

    Column(modifier = Modifier.fillMaxSize().padding(20.dp)) {
        Text("Your goals", style = MaterialTheme.typography.headlineSmall, color = Forest)
        Spacer(Modifier.height(4.dp))
        Text(
            "We'll suggest a round-up pace to help you hit each one on time.",
            color = InkSoft,
            style = MaterialTheme.typography.bodyMedium
        )
        Spacer(Modifier.height(16.dp))

        goals.forEach { goal ->
            Card(modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
                Column(Modifier.padding(16.dp)) {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(goal.name, style = MaterialTheme.typography.titleMedium)
                        Text(
                            "KES ${goal.saved} / ${goal.target}",
                            color = InkSoft,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    LinearProgressIndicator(
                        progress = { goal.saved.toFloat() / goal.target },
                        modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(6.dp)),
                        color = goal.color,
                        trackColor = LineColor
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(goal.note, color = InkSoft, style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        Button(
            onClick = { },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = Forest, contentColor = Paper)
        ) {
            Text("+ Create a new goal", fontWeight = FontWeight.Bold)
        }
    }
}
