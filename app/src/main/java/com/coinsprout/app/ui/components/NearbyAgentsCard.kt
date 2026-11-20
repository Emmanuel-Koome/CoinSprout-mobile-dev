package com.coinsprout.app.ui.components

import android.Manifest
import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.coinsprout.app.data.remote.AgentDto
import com.coinsprout.app.ui.theme.*
import com.coinsprout.app.viewmodel.Load
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority

/**
 * Real location, minimal footprint: COARSE permission only, requested only when the user taps the button,
 * coordinates sent for this one request and never stored by the server.
 */
@Composable
fun NearbyAgentsCard(agents: Load<List<AgentDto>>, onLocation: (Double, Double) -> Unit, onUnavailable: (String) -> Unit) {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) fetchLocation(context, onLocation, onUnavailable) else onUnavailable("Location permission was not granted.")
    }
    Card(colors = CardDefaults.cardColors(containerColor = Moss), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text("M-Pesa agents near you", style = MaterialTheme.typography.titleMedium, color = Forest)
            Text("Uses your approximate location once, only when you ask.", style = MaterialTheme.typography.bodySmall, color = Forest)
            Spacer(Modifier.height(8.dp))
            when (agents) {
                Load.Idle -> {}
                Load.Loading -> CircularProgressIndicator(color = Forest, modifier = Modifier.size(24.dp))
                is Load.Failed -> Text(agents.message, color = Forest, style = MaterialTheme.typography.bodySmall)
                is Load.Ready -> {
                    if (agents.value.isEmpty()) Text("No agents found within 10 km.", color = Forest)
                    agents.value.forEach { a ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                            Column(Modifier.weight(1f)) {
                                Text(a.name, color = Forest, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                                Text("Agent ${a.agentNumber} · ${a.distanceKm} km" + if (a.illustrative) " · demo data" else "", color = Forest, style = MaterialTheme.typography.bodySmall)
                            }
                            TextButton(onClick = { openInMaps(context, a) }) { Text("Map", color = Forest, fontWeight = FontWeight.Bold) }
                        }
                    }
                }
            }
            Spacer(Modifier.height(4.dp))
            OutlinedButton(onClick = {
                if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) fetchLocation(context, onLocation, onUnavailable)
                else launcher.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
            }) { Text("Find agents near me", color = Forest) }
        }
    }
}

@SuppressLint("MissingPermission") // only called after the permission check / grant above
private fun fetchLocation(context: Context, onLocation: (Double, Double) -> Unit, onUnavailable: (String) -> Unit) {
    LocationServices.getFusedLocationProviderClient(context)
        .getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, null)
        .addOnSuccessListener { loc ->
            if (loc != null) onLocation(loc.latitude, loc.longitude) else onUnavailable("Couldn't get your location. Turn on location services and try again.")
        }
        .addOnFailureListener { onUnavailable("Couldn't get your location right now.") }
}

private fun openInMaps(context: Context, a: AgentDto) {
    val uri = Uri.parse("geo:${a.latitude},${a.longitude}?q=${a.latitude},${a.longitude}(${Uri.encode(a.name)})")
    try { context.startActivity(Intent(Intent.ACTION_VIEW, uri)) } catch (_: ActivityNotFoundException) { }
}
