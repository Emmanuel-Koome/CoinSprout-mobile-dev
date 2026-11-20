package com.coinsprout.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.coinsprout.app.data.models.GoalModel
import com.coinsprout.app.domain.IsoTime
import com.coinsprout.app.domain.Money
import com.coinsprout.app.ui.components.*
import com.coinsprout.app.ui.theme.*
import com.coinsprout.app.viewmodel.CoinSproutViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun GoalsScreen(navController: NavHostController, vm: CoinSproutViewModel) {
    val goals by vm.goals.collectAsStateWithLifecycle()
    val portfolio by vm.portfolio.collectAsStateWithLifecycle()
    var creating by rememberSaveable { mutableStateOf(false) }
    var contributing by remember { mutableStateOf<GoalModel?>(null) }
    var deleting by remember { mutableStateOf<GoalModel?>(null) }

    if (creating) NewGoalDialog(onDismiss = { creating = false }, onSave = { t, c, d -> vm.createGoal(t, c, d) { creating = false } })
    contributing?.let { g -> ContributeDialog(g, portfolio.unallocatedCents, onDismiss = { contributing = null }, onSave = { c -> vm.contribute(g.id, c) { contributing = null } }) }
    deleting?.let { g ->
        AlertDialog(onDismissRequest = { deleting = null }, containerColor = Paper, title = { Text("Delete \"${g.title}\"?", color = Forest) },
            text = { Text("Money earmarked for this goal goes back to your general savings. Nothing is withdrawn.") },
            confirmButton = { TextButton(onClick = { vm.deleteGoal(g.id); deleting = null }) { Text("Delete", color = Forest) } },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Keep", color = InkSoft) } })
    }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { ScreenTitle("GOALS", "What are you saving for?", "Available to assign: ${Money.kes(portfolio.unallocatedCents)}") }
        if (goals.isEmpty()) item { Text("No goals yet. Create one to give your savings a purpose.", color = InkSoft) }
        items(goals, key = { it.id }) { g ->
            val progress = if (g.targetCents > 0) (g.savedCents.toFloat() / g.targetCents).coerceIn(0f, 1f) else 0f
            Card(colors = CardDefaults.cardColors(containerColor = Paper), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text("${g.emoji} ${g.title}", style = MaterialTheme.typography.titleMedium, color = Forest)
                    Spacer(Modifier.height(8.dp))
                    LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth().semantics { contentDescription = "${(progress * 100).toInt()} percent saved" }, color = SproutDark, trackColor = Moss)
                    Text("${Money.kes(g.savedCents)} of ${Money.kes(g.targetCents)}" + (g.deadline?.let { " · by $it" } ?: ""), style = MaterialTheme.typography.bodySmall, color = InkSoft, modifier = Modifier.padding(top = 6.dp))
                    Row {
                        TextButton(onClick = { contributing = g }, modifier = Modifier.heightIn(min = 48.dp)) { Text("Add savings", color = SproutDark, fontWeight = FontWeight.Bold) }
                        TextButton(onClick = { deleting = g }, modifier = Modifier.heightIn(min = 48.dp)) { Text("Delete", color = InkSoft) }
                    }
                }
            }
        }
        item { PrimaryButton("+ New goal", onClick = { creating = true }) }
    }
}

@Composable
private fun NewGoalDialog(onDismiss: () -> Unit, onSave: (String, Long, String?) -> Unit) {
    var title by rememberSaveable { mutableStateOf("") }
    var target by rememberSaveable { mutableStateOf("") }
    var deadline by rememberSaveable { mutableStateOf("") }
    val cents = Money.parseKesToCents(target)
    val today = remember { SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date()) }
    val deadlineError = when {
        deadline.isBlank() -> null
        !IsoTime.isValidDate(deadline) -> "Use the format 2027-06-30"
        deadline < today -> "Pick a date in the future"      // ISO dates sort lexicographically
        else -> null
    }
    AlertDialog(
        onDismissRequest = onDismiss, containerColor = Paper, title = { Text("New goal", color = Forest) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(title, { title = it.take(60) }, label = { Text("Goal name") }, singleLine = true)
                OutlinedTextField(target, { target = it }, label = { Text("Target (KES)") }, singleLine = true, isError = target.isNotBlank() && cents == null,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                OutlinedTextField(deadline, { deadline = it.take(10) }, label = { Text("Deadline (optional)") }, placeholder = { Text("YYYY-MM-DD") }, singleLine = true,
                    isError = deadlineError != null, supportingText = { deadlineError?.let { Text(it) } })
            }
        },
        confirmButton = {
            TextButton(onClick = { cents?.let { onSave(title.trim(), it, deadline.ifBlank { null }) } },
                enabled = title.isNotBlank() && cents != null && cents >= 10_000 && deadlineError == null) { Text("Create", color = Forest) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = InkSoft) } }
    )
}

@Composable
private fun ContributeDialog(goal: GoalModel, available: Long, onDismiss: () -> Unit, onSave: (Long) -> Unit) {
    var text by rememberSaveable { mutableStateOf("") }
    val cents = Money.parseKesToCents(text)
    val error = when { text.isBlank() -> null; cents == null -> "Enter an amount like 500"; cents > available -> "You only have ${Money.kes(available)} available"; else -> null }
    AlertDialog(
        onDismissRequest = onDismiss, containerColor = Paper, title = { Text("Add to ${goal.title}", color = Forest) },
        text = {
            Column {
                Text("Available to assign: ${Money.kes(available)}", style = MaterialTheme.typography.bodySmall, color = InkSoft)
                OutlinedTextField(text, { text = it }, label = { Text("Amount (KES)") }, singleLine = true, isError = error != null, supportingText = { error?.let { Text(it) } },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
            }
        },
        confirmButton = { TextButton(onClick = { cents?.let(onSave) }, enabled = cents != null && error == null) { Text("Add", color = Forest) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = InkSoft) } }
    )
}
