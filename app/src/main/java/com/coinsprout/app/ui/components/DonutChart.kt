package com.coinsprout.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.coinsprout.app.data.Allocation
import com.coinsprout.app.ui.theme.Forest
import com.coinsprout.app.ui.theme.Moss
import com.coinsprout.app.ui.theme.Mustard
import com.coinsprout.app.ui.theme.Sprout

/**
 * Ring chart showing the four-way fund split. Mirrors the conic-gradient
 * donut used in the HTML mockup, drawn as four stacked arcs.
 */
@Composable
fun AllocationDonut(
    allocation: Allocation,
    modifier: Modifier = Modifier,
    diameter: androidx.compose.ui.unit.Dp = 104.dp,
    strokeWidth: androidx.compose.ui.unit.Dp = 20.dp
) {
    val slices = listOf(
        allocation.mmf to Forest,
        allocation.tbill to Sprout,
        allocation.unit to Mustard,
        allocation.cash to Moss
    )

    Canvas(modifier = modifier.size(diameter)) {
        var startAngle = -90f
        val stroke = Stroke(width = strokeWidth.toPx())
        val arcSize = Size(size.width - stroke.width, size.height - stroke.width)
        val topLeft = androidx.compose.ui.geometry.Offset(stroke.width / 2, stroke.width / 2)

        slices.forEach { (value, color) ->
            val sweep = value / 100f * 360f
            drawArc(
                color = color,
                startAngle = startAngle,
                sweepAngle = sweep,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = stroke
            )
            startAngle += sweep
        }
    }
}
