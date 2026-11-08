package com.coinsprout.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.coinsprout.app.ui.theme.*
import com.coinsprout.app.viewmodel.CoinSproutViewModel

@Composable
fun GoalsScreen(
    navController: NavHostController,
    viewModel: CoinSproutViewModel = viewModel()
) {
    val goals by viewModel.goals.collectAsState()
    var showAddDialog by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize().padding(20.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("Your Goals", style = MaterialTheme.typography.headlineSmall, color = Forest)
            Button(
                onClick = { showAddDialog = true },
                colors = ButtonDefaults.buttonColors(containerColor = Forest)
            ) {
                Text("+ New Goal", color = Paper, style = MaterialTheme.typography.bodySmall)
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(
            "We auto-route your micro-roundups into designated goal buckets.",
            color = InkSoft,
            style = MaterialTheme.typography.bodyMedium
        )
        Spacer(Modifier.height(16.dp))

        LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items(goals, key = { it.id }) { goal ->
                val progress = (goal.savedAmount / goal.targetAmount).toFloat().coerceIn(0f, 1f)
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("${goal.emoji} ${goal.title}", style = MaterialTheme.typography.titleMedium, color = Forest, fontWeight = FontWeight.Bold)
                            Text(
                                "KES ${goal.savedAmount.toInt()} / ${goal.targetAmount.toInt()}",
                                color = InkSoft,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        LinearProgressIndicator(
                            progress = { progress },
                            modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(6.dp)),
                            color = Sprout,
                            trackColor = LineColor
                        )
                        Spacer(Modifier.height(6.dp))
                        Text("Deadline: ${goal.deadline} • ${(progress * 100).toInt()}% completed", color = InkSoft, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        var title by remember { mutableStateOf("") }
        var target by remember { mutableStateOf("50000") }
        var deadline by remember { mutableStateOf("Dec 2025") }

        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            title = { Text("Create New Goal") },
            text = {
                Column {
                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it },
                        label = { Text("Goal Title") },
                        placeholder = { Text("e.g., Land Deposit") },
                        singleLine = true
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = target,
                        onValueChange = { target = it },
                        label = { Text("Target Amount (KES)") },
                        singleLine = true
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = deadline,
                        onValueChange = { deadline = it },
                        label = { Text("Deadline") },
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val targetDouble = target.toDoubleOrNull() ?: 10000.0
                        if (title.isNotBlank()) {
                            viewModel.addGoal(title, targetDouble, "🎯", deadline)
                        }
                        showAddDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Forest)
                ) {
                    Text("Create Goal", color = Paper)
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddDialog = false }) { Text("Cancel") }
            }
        )
    }
}
