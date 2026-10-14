package com.anujstha.lifeassistant.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.anujstha.lifeassistant.domain.usecase.ReportMath

/** Distinct, theme-friendly slice colors; cycles if there are more slices. */
@Composable
fun chartPalette(): List<Color> {
    val c = MaterialTheme.colorScheme
    return listOf(c.primary, c.tertiary, c.secondary, Color(0xFF7E57C2), Color(0xFFD81B60), Color(0xFF43A047), Color(0xFF6D4C41), c.outline)
}

/**
 * Donut chart. Decorative for screen readers except for [description]; the same numbers are
 * always shown in a list next to it.
 */
@Composable
fun DonutChart(values: List<Long>, colors: List<Color>, description: String, modifier: Modifier = Modifier, size: Dp = 160.dp) {
    val angles = ReportMath.sweepAngles(values)
    val track = MaterialTheme.colorScheme.surfaceVariant
    Canvas(modifier.size(size).semantics { contentDescription = description }) {
        val stroke = this.size.minDimension * 0.18f
        val inset = stroke / 2
        val arcSize = Size(this.size.width - stroke, this.size.height - stroke)
        if (angles.all { it == 0f }) {
            drawArc(track, 0f, 360f, false, Offset(inset, inset), arcSize, style = Stroke(stroke))
            return@Canvas
        }
        var start = -90f
        angles.forEachIndexed { i, sweep ->
            if (sweep > 0f) {
                drawArc(colors[i % colors.size], start, sweep, false, Offset(inset, inset), arcSize, style = Stroke(stroke))
            }
            start += sweep
        }
    }
}

/** One group per label with up to two bars (e.g. income vs expenses), scaled to the largest value. */
@Composable
fun GroupedBarChart(
    labels: List<String>,
    series: List<List<Long>>,
    colors: List<Color>,
    description: String,
    modifier: Modifier = Modifier,
    height: Dp = 160.dp,
) {
    val max = series.flatten().maxOrNull()?.takeIf { it > 0 } ?: 1L
    val baseline = MaterialTheme.colorScheme.outlineVariant
    Column(modifier.semantics { contentDescription = description }) {
        Canvas(Modifier.fillMaxWidth().height(height)) {
            val groupWidth = size.width / labels.size
            val barWidth = groupWidth * 0.7f / series.size
            labels.indices.forEach { g ->
                series.forEachIndexed { s, values ->
                    val h = size.height * (values[g].toFloat() / max)
                    val x = g * groupWidth + groupWidth * 0.15f + s * barWidth
                    drawRoundRect(
                        colors[s % colors.size],
                        topLeft = Offset(x, size.height - h),
                        size = Size(barWidth * 0.9f, h),
                        cornerRadius = CornerRadius(4f, 4f),
                    )
                }
            }
            drawLine(baseline, Offset(0f, size.height), Offset(size.width, size.height), strokeWidth = 2f)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            labels.forEach {
                Text(it, style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
            }
        }
    }
}

/** Small colored square + label, for chart legends. */
@Composable
fun LegendDot(color: Color, label: String, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Canvas(Modifier.size(10.dp)) { drawCircle(color) }
        Text(label, style = MaterialTheme.typography.labelMedium)
    }
}
