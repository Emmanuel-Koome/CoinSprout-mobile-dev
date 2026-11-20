package com.coinsprout.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.coinsprout.app.domain.IsoTime
import com.coinsprout.app.ui.components.BackBar
import com.coinsprout.app.ui.theme.*
import com.coinsprout.app.viewmodel.CoinSproutViewModel

@Composable
fun NotificationsScreen(navController: NavHostController, vm: CoinSproutViewModel) {
    val items by vm.notifications.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { vm.refresh() }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { BackBar("Alerts", onBack = { vm.markNotificationsRead(); navController.popBackStack() }) }
        if (items.isEmpty()) item { Text("Nothing yet.", color = InkSoft) }
        items(items, key = { it.id }) { n ->
            Card(colors = CardDefaults.cardColors(containerColor = Paper), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp)) {
                    Text(n.title, color = Forest, fontWeight = if (n.isRead) FontWeight.Normal else FontWeight.Bold)
                    Text(n.message, color = Ink, style = MaterialTheme.typography.bodyMedium)
                    Text(IsoTime.display(n.createdAtMillis), color = InkSoft, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}
