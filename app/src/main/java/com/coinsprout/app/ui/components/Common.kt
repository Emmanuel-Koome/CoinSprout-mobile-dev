package com.coinsprout.app.ui.components

import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.unit.dp
import com.coinsprout.app.ui.theme.*

@Composable
fun ScreenTitle(eyebrow: String?, title: String, subtitle: String? = null) {
    Column {
        if (eyebrow != null) Text(eyebrow, style = MaterialTheme.typography.bodySmall, color = SproutDark, fontWeight = FontWeight.Bold)
        Text(title, style = MaterialTheme.typography.headlineSmall, color = Forest, modifier = Modifier.semantics { heading() })
        if (subtitle != null) {
            Spacer(Modifier.height(6.dp))
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = InkSoft)
        }
    }
}

@Composable
fun BackBar(title: String, onBack: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack) { Text("←", style = MaterialTheme.typography.headlineSmall, color = Forest, modifier = Modifier.semantics { contentDescription = "Back" }) }
        Text(title, style = MaterialTheme.typography.headlineSmall, color = Forest, modifier = Modifier.semantics { heading() })
    }
}

@Composable
fun ErrorBanner(message: String, modifier: Modifier = Modifier, onRetry: (() -> Unit)? = null) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(Color(0xFFFBE9E5), RoundedCornerShape(12.dp))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(message, color = Color(0xFF8A2A18), style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
        if (onRetry != null) TextButton(onClick = onRetry) { Text("Retry", color = Color(0xFF8A2A18), fontWeight = FontWeight.Bold) }
    }
}

@Composable
fun PrimaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, loading: Boolean = false) {
    Button(
        onClick = onClick,
        enabled = enabled && !loading,
        modifier = modifier.fillMaxWidth().heightIn(min = 48.dp),
        colors = ButtonDefaults.buttonColors(containerColor = Forest, contentColor = Paper)
    ) {
        if (loading) CircularProgressIndicator(Modifier.size(18.dp), color = Paper, strokeWidth = 2.dp)
        else Text(text, fontWeight = FontWeight.Bold)
    }
}

/** Radio-style choice: >= 48dp tall, announced to TalkBack as "selected / not selected, radio button". */
@Composable
fun SelectableChip(label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .heightIn(min = 48.dp)
            .border(1.5.dp, if (selected) Forest else LineColor, RoundedCornerShape(24.dp))
            .background(if (selected) Forest else Paper, RoundedCornerShape(24.dp))
            .selectable(selected = selected, onClick = onClick, role = Role.RadioButton)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(label, color = if (selected) Paper else Ink, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
    }
}

/** Emoji used as an icon: give it a spoken label, or hide it from TalkBack when it is purely decorative. */
@Composable
fun EmojiIcon(symbol: String, description: String? = null, modifier: Modifier = Modifier) {
    val m = if (description != null) modifier.semantics { contentDescription = description } else modifier.clearAndSetSemantics { }
    Text(symbol, modifier = m)
}

@Composable
fun DisclaimerText(modifier: Modifier = Modifier) {
    Text(
        "Investments can lose value and returns are not guaranteed. Figures are illustrations, not advice.",
        style = MaterialTheme.typography.bodySmall, color = InkSoft, modifier = modifier
    )
}
