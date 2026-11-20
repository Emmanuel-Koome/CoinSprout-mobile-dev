package com.coinsprout.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.coinsprout.app.data.models.Allocation
import com.coinsprout.app.ui.theme.*

private data class Slice(val label: String, val percent: Int, val color: Color)

private fun slices(a: Allocation) = listOf(
    Slice("Money Market", a.mmf, Forest), Slice("T-Bills", a.tbill, SproutDark),
    Slice("Unit Trust", a.unit, Mustard), Slice("Cash buffer", a.cash, Moss)
)

/** Colour is never the only carrier of meaning: [AllocationLegend] prints every percentage. */
@Composable
fun AllocationDonut(allocation: Allocation, modifier: Modifier = Modifier, diameter: Dp = 104.dp, strokeWidth: Dp = 20.dp) {
    val parts = slices(allocation)
    val description = "Portfolio split: " + parts.joinToString(", ") { "${it.label} ${it.percent} percent" }
    Canvas(modifier = modifier.size(diameter).semantics { contentDescription = description }) {
        var start = -90f
        val stroke = Stroke(width = strokeWidth.toPx())
        val arc = Size(size.width - stroke.width, size.height - stroke.width)
        parts.forEach { s ->
            val sweep = s.percent / 100f * 360f
            drawArc(s.color, start, sweep, false, Offset(stroke.width / 2, stroke.width / 2), arc, style = stroke)
            start += sweep
        }
    }
}

@Composable
fun AllocationLegend(allocation: Allocation) {
    Column {
        slices(allocation).forEach { s ->
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 3.dp)) {
                Box(Modifier.size(10.dp).background(s.color, RoundedCornerShape(3.dp)))
                Spacer(Modifier.width(8.dp))
                Text(s.label, color = InkSoft, style = MaterialTheme.typography.bodySmall, modifier = Modifier.width(92.dp))
                Text("${s.percent}%", style = MaterialTheme.typography.titleMedium, color = Ink)
            }
        }
    }
}
