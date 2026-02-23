package com.tudorc.anemoi.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.max
import kotlin.math.roundToInt

data class ParticulateMetricBar(
    val label: String,
    val currentValue: Double?,
    val todayMax: Double?,
    val rangeMax: Double?
)

@OptIn(ExperimentalTextApi::class)
@Composable
fun ParticulatesWidget(
    pollutionMetrics: List<ParticulateMetricBar>,
    pollenMetrics: List<ParticulateMetricBar>,
    modifier: Modifier = Modifier
) {
    val leftRows = listOf("DUST", "PM10", "PM2.5").mapIndexed { index, fallbackLabel ->
        pollutionMetrics.getOrNull(index) ?: ParticulateMetricBar(
            label = fallbackLabel,
            currentValue = null,
            todayMax = null,
            rangeMax = null
        )
    }
    val rightRows = listOf("TREES", "GRASS", "WEEDS").mapIndexed { index, fallbackLabel ->
        pollenMetrics.getOrNull(index) ?: ParticulateMetricBar(
            label = fallbackLabel,
            currentValue = null,
            todayMax = null,
            rangeMax = null
        )
    }
    val textMeasurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val rowLabelStyle = remember {
        TextStyle(
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium
        )
    }
    val sharedLabelWidth = remember(leftRows, rightRows, textMeasurer, density, rowLabelStyle) {
        val maxLabelWidthPx = (leftRows + rightRows)
            .maxOfOrNull { row ->
                textMeasurer.measure(
                    text = row.label,
                    style = rowLabelStyle
                ).size.width
            }
            ?: 0
        with(density) { maxLabelWidthPx.toDp() + 2.dp }
    }
    val textToBarGap = 10.dp

    Row(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 12.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ParticulatesHalf(
            title = "Pollution",
            rows = leftRows,
            leftSide = true,
            labelWidth = sharedLabelWidth,
            textToBarGap = textToBarGap,
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
        )

        Box(
            modifier = Modifier
                .fillMaxHeight()
                .width(1.dp)
                .background(Color.White.copy(alpha = 0.22f))
        )

        ParticulatesHalf(
            title = "Pollen",
            rows = rightRows,
            leftSide = false,
            labelWidth = sharedLabelWidth,
            textToBarGap = textToBarGap,
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
        )
    }
}

@Composable
private fun ParticulatesHalf(
    title: String,
    rows: List<ParticulateMetricBar>,
    leftSide: Boolean,
    labelWidth: Dp,
    textToBarGap: Dp,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.padding(horizontal = 8.dp),
        verticalArrangement = Arrangement.SpaceEvenly
    ) {
        Text(
            text = title,
            color = Color.White.copy(alpha = 0.72f),
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )

        rows.take(3).forEach { row ->
            if (leftSide) {
                LeftMetricRow(
                    metric = row,
                    labelWidth = labelWidth,
                    textToBarGap = textToBarGap
                )
            } else {
                RightMetricRow(
                    metric = row,
                    labelWidth = labelWidth,
                    textToBarGap = textToBarGap
                )
            }
        }
    }
}

@Composable
private fun LeftMetricRow(
    metric: ParticulateMetricBar,
    labelWidth: Dp,
    textToBarGap: Dp
) {
    val currentValueText = formatCurrentValue(metric.currentValue)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(24.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = currentValueText,
            color = Color.White.copy(alpha = 0.42f),
            fontSize = 9.sp,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Start,
            modifier = Modifier.width(26.dp)
        )
        Spacer(modifier = Modifier.width(6.dp))
        OutwardGradientTrack(
            progress = normalizedProgress(
                todayMax = metric.todayMax,
                rangeMax = metric.rangeMax
            ),
            outwardToStart = true,
            modifier = Modifier
                .weight(1f)
                .height(10.dp)
        )
        Spacer(modifier = Modifier.width(textToBarGap))
        Text(
            text = metric.label,
            color = Color.White.copy(alpha = 0.84f),
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Start,
            modifier = Modifier.width(labelWidth)
        )
    }
}

@Composable
private fun RightMetricRow(
    metric: ParticulateMetricBar,
    labelWidth: Dp,
    textToBarGap: Dp
) {
    val currentValueText = formatCurrentValue(metric.currentValue)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(24.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = metric.label,
            color = Color.White.copy(alpha = 0.84f),
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.End,
            modifier = Modifier.width(labelWidth)
        )
        Spacer(modifier = Modifier.width(textToBarGap))
        OutwardGradientTrack(
            progress = normalizedProgress(
                todayMax = metric.todayMax,
                rangeMax = metric.rangeMax
            ),
            outwardToStart = false,
            modifier = Modifier
                .weight(1f)
                .height(10.dp)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = currentValueText,
            color = Color.White.copy(alpha = 0.42f),
            fontSize = 9.sp,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.End,
            modifier = Modifier.width(26.dp)
        )
    }
}

@Composable
private fun OutwardGradientTrack(
    progress: Float,
    outwardToStart: Boolean,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier.fillMaxWidth()) {
        val centerY = size.height / 2f
        val strokeWidth = max(2f, size.height * 0.65f)
        val trackGradientStartX = if (outwardToStart) size.width else 0f
        val trackGradientEndX = if (outwardToStart) 0f else size.width
        val baseGradient = Brush.horizontalGradient(
            colors = listOf(
                Color(0xFF435A69).copy(alpha = 0.82f),
                Color(0xFF4A6272).copy(alpha = 0.78f)
            ),
            startX = trackGradientStartX,
            endX = trackGradientEndX
        )

        drawLine(
            brush = baseGradient,
            start = Offset(0f, centerY),
            end = Offset(size.width, centerY),
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round
        )

        val clampedProgress = progress.coerceIn(0f, 1f)
        if (clampedProgress <= 0f) return@Canvas

        val rawSegmentWidth = size.width * clampedProgress
        val segmentWidth = max(rawSegmentWidth, strokeWidth.coerceAtMost(size.width))
        val startX = if (outwardToStart) size.width - segmentWidth else 0f
        val endX = if (outwardToStart) size.width else segmentWidth
        val uvGradient = Brush.horizontalGradient(
            colors = listOf(
                Color(0xFF4CAF50),
                Color(0xFFFBC02D),
                Color(0xFFF57C00),
                Color(0xFFD32F2F),
                Color(0xFF7B1FA2)
            ),
            startX = if (outwardToStart) size.width else 0f,
            endX = if (outwardToStart) 0f else size.width
        )

        drawLine(
            brush = uvGradient,
            start = Offset(startX.coerceAtLeast(0f), centerY),
            end = Offset(endX.coerceAtMost(size.width), centerY),
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round,
            alpha = 0.9f
        )
    }
}

private fun normalizedProgress(todayMax: Double?, rangeMax: Double?): Float {
    if (todayMax == null || rangeMax == null) return 0f
    if (!todayMax.isFinite() || !rangeMax.isFinite()) return 0f
    if (todayMax <= 0.0 || rangeMax <= 0.0) return 0f
    return (todayMax / rangeMax).coerceIn(0.0, 1.0).toFloat()
}

private fun formatCurrentValue(value: Double?): String {
    if (value == null || !value.isFinite()) return "--"
    val abs = kotlin.math.abs(value)
    return when {
        abs >= 100 -> value.roundToInt().toString()
        abs >= 10 -> String.format(java.util.Locale.US, "%.1f", value)
        else -> String.format(java.util.Locale.US, "%.2f", value)
    }
}
