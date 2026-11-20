package com.coinsprout.app.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.coinsprout.app.BuildConfig
import com.coinsprout.app.ui.components.*
import com.coinsprout.app.ui.theme.*
import com.coinsprout.app.viewmodel.CoinSproutViewModel

@Composable
fun SettingsScreen(navController: NavHostController, vm: CoinSproutViewModel) {
    val s by vm.settings.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var confirmDelete by remember { mutableStateOf(false) }
    val smsGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.RECEIVE_SMS) == PackageManager.PERMISSION_GRANTED
    val smsLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) vm.updateSettings(s.copy(smsListenerEnabled = true))
    }

    if (confirmDelete) AlertDialog(
        onDismissRequest = { confirmDelete = false }, containerColor = Paper,
        title = { Text("Delete your account?", color = Forest) },
        text = { Text("This permanently deletes your profile, purchases, goals and survey answers from our servers and from this phone. Money already invested stays with your fund manager and is not affected here.") },
        confirmButton = { TextButton(onClick = { confirmDelete = false; vm.deleteAccount { navController.navigate("splash") { popUpTo(0) } } }) { Text("Delete everything", color = Forest, fontWeight = FontWeight.Bold) } },
        dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Keep my account", color = InkSoft) } }
    )

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        ScreenTitle("SETTINGS", "Your rules")

        Section("Round up to the nearest", "Smaller steps feel lighter on a small budget. A purchase never rounds up by more than ${com.coinsprout.app.domain.Money.kes(s.maxRoundUpCents)}.") {
            Row(Modifier.selectableGroup(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(1000L to "KES 10", 2000L to "KES 20", 5000L to "KES 50", 10000L to "KES 100").forEach { (v, label) ->
                    SelectableChip(label, s.roundUpIncrementCents == v, { vm.updateSettings(s.copy(roundUpIncrementCents = v)) })
                }
            }
        }

        Section("Invest when round-ups reach", "M-Pesa charges can apply to each payment, so fewer, larger payments cost less.") {
            Row(Modifier.selectableGroup(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(10000L to "KES 100", 20000L to "KES 200", 50000L to "KES 500").forEach { (v, label) ->
                    SelectableChip(label, s.stkThresholdCents == v, { vm.updateSettings(s.copy(stkThresholdCents = v)) })
                }
            }
        }

        if (BuildConfig.SMS_AUTO_DETECT) {
            SwitchRow(
                "Read M-Pesa confirmation messages",
                "Only messages from \"MPESA\" are looked at; everything else is ignored. We keep the amount, merchant and receipt code, never your other messages or phone numbers.",
                checked = s.smsListenerEnabled && smsGranted,
                onChange = { on ->
                    if (!on) vm.updateSettings(s.copy(smsListenerEnabled = false))
                    else if (smsGranted) vm.updateSettings(s.copy(smsListenerEnabled = true))
                    else smsLauncher.launch(Manifest.permission.RECEIVE_SMS)
                }
            )
        }
        SwitchRow(
            "Offer to invest automatically",
            "When round-ups reach your limit we'll remind you to approve an M-Pesa prompt. You always approve with your PIN.",
            checked = s.autoStkEnabled, onChange = { vm.updateSettings(s.copy(autoStkEnabled = it)) }
        )

        Section("Your profile", null) {
            OutlinedButton(onClick = { navController.navigate("quiz") }, modifier = Modifier.heightIn(min = 48.dp)) { Text("Retake the risk quiz", color = Forest) }
            OutlinedButton(onClick = { navController.navigate("education") }, modifier = Modifier.heightIn(min = 48.dp)) { Text("Learn: how round-ups work", color = Forest) }
        }

        Section("Account & privacy", "You can leave at any time.") {
            OutlinedButton(onClick = { vm.logout(); navController.navigate("splash") { popUpTo(0) } }, modifier = Modifier.heightIn(min = 48.dp)) { Text("Sign out", color = Forest) }
            OutlinedButton(onClick = { confirmDelete = true }, modifier = Modifier.heightIn(min = 48.dp)) { Text("Delete my account and data", color = Forest) }
        }
        Text("CoinSprout ${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.bodySmall, color = InkSoft)
    }
}

@Composable
private fun Section(title: String, hint: String?, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium, color = Forest)
        if (hint != null) Text(hint, style = MaterialTheme.typography.bodySmall, color = InkSoft)
        content()
    }
}

@Composable
private fun SwitchRow(title: String, hint: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = Forest)
            Text(hint, style = MaterialTheme.typography.bodySmall, color = InkSoft)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
