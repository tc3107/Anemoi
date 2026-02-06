package com.example.anemoi.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs
import kotlin.math.roundToInt

@OptIn(ExperimentalTextApi::class)
@Composable
fun PrecipitationGraph(
    times: List<String>,
    probabilities: List<Int>,
    precipitations: List<Double>,
    currentTimeIso: String?,
    modifier: Modifier = Modifier
) {
    val textMeasurer = rememberTextMeasurer()

    val dayProbs = remember(probabilities) {
        if (probabilities.size >= 25) probabilities.take(25).map { it.toDouble() }
        else if (probabilities.isNotEmpty()) probabilities.map { it.toDouble() }
        else emptyList()
    }
    
    val dayAmounts = remember(precipitations) {
        if (precipitations.size >= 25) precipitations.take(25)
        else if (precipitations.isNotEmpty()) precipitations
        else emptyList()
    }

    if (dayProbs.isEmpty()) {
        Box(modifier = modifier, contentAlignment = androidx.compose.ui.Alignment.Center) {
            androidx.compose.material3.Text("No data", color = Color.White.copy(alpha = 0.3f))
        }
        return
    }

    var dragX by remember { mutableStateOf<Float?>(null) }
    var isDragging by remember { mutableStateOf(false) }

    val leftPaddingDp = 64.dp 
    val rightPaddingDp = 16.dp
    val topPaddingDp = 12.dp 
    val bottomPaddingDp = 24.dp

    // Shared Interpolation Function for the Probability line
    fun getInterpolatedProb(fraction: Float): Double {
        val totalPoints = dayProbs.size - 1
        if (totalPoints <= 0) return dayProbs.firstOrNull() ?: 0.0
        val f = fraction.coerceIn(0f, 1f) * totalPoints
        val i = f.toInt().coerceIn(0, totalPoints - 1)
        val dt = f - i
        val p0 = dayProbs[(i - 1).coerceIn(0, totalPoints)]
        val p1 = dayProbs[i]
        val p2 = dayProbs[i + 1]
        val p3 = dayProbs[(i + 2).coerceIn(0, totalPoints)]
        val tension = 0.5f
        val m1 = (p2 - p0) * tension
        val m2 = (p3 - p1) * tension
        val t2 = dt * dt
        val t3 = t2 * dt
        return ((2 * t3 - 3 * t2 + 1) * p1 + (t3 - 2 * t2 + dt) * m1 + (-2 * t3 + 3 * t2) * p2 + (t3 - t2) * m2).coerceIn(0.0, 100.0)
    }

    // Snapping logic for Precipitation Amount Y-axis
    val maxActualPrecip = dayAmounts.maxOrNull() ?: 0.0
    val snappedMaxPrecip = when {
        maxActualPrecip <= 1.0 -> 1.0
        maxActualPrecip <= 2.0 -> 2.0
        maxActualPrecip <= 5.0 -> 5.0
        maxActualPrecip <= 10.0 -> 10.0
        maxActualPrecip <= 20.0 -> 20.0
        maxActualPrecip <= 50.0 -> 50.0
        else -> ((maxActualPrecip / 10).toInt() + 1) * 10.0
    }

    val curF = remember(currentTimeIso) {
        currentTimeIso?.let {
            try {
                val timePart = it.split("T").getOrNull(1) ?: ""
                val hr = timePart.split(":").getOrNull(0)?.toIntOrNull() ?: 0
                val min = timePart.split(":").getOrNull(1)?.toIntOrNull() ?: 0
                ((hr + (min / 60f)) / 24f).coerceIn(0f, 1f)
            } catch (e: Exception) { 0f }
        } ?: 0f
    }

    Box(modifier = modifier) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) {
                            val down = awaitFirstDown()
                            isDragging = true
                            dragX = down.position.x
                            
                            val pointerId = down.id
                            while (true) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull { it.id == pointerId }
                                if (change == null || !change.pressed || change.isConsumed) {
                                    isDragging = false
                                    dragX = null
                                    break
                                }
                                
                                val posChange = change.position - change.previousPosition
                                if (abs(posChange.x) > abs(posChange.y)) {
                                    dragX = change.position.x
                                    change.consume()
                                } else if (abs(posChange.y) > 2.dp.toPx()) {
                                    isDragging = false
                                    dragX = null
                                    break
                                }
                            }
                        }
                    }
                }
                .graphicsLayer(clip = false)
                .drawWithContent {
                    val l = leftPaddingDp.toPx()
                    val r = rightPaddingDp.toPx()
                    val t = topPaddingDp.toPx()
                    val b = bottomPaddingDp.toPx()
                    val w = size.width
                    val h = size.height
                    val drawW = w - l - r
                    val drawH = h - t - b

                    drawContext.canvas.saveLayer(Rect(0f, 0f, w, h), Paint())
                    
                    drawContent()

                    // Apply Fade
                    val fadeWidth = 24.dp.toPx()
                    if (w > 0f) {
                        drawRect(
                            brush = Brush.horizontalGradient(
                                colorStops = arrayOf(
                                    0.0f to Color.Black,
                                    ((l - 4.dp.toPx()) / w).coerceIn(0f, 1f) to Color.Black,
                                    (l / w).coerceIn(0f, 1f) to Color.Transparent,
                                    ((l + fadeWidth) / w).coerceIn(0f, 1f) to Color.Black,
                                    ((w - r - fadeWidth) / w).coerceIn(0f, 1f) to Color.Black,
                                    ((w - r) / w).coerceIn(0f, 1f) to Color.Transparent,
                                    1.0f to Color.Transparent
                                )
                            ),
                            blendMode = BlendMode.DstIn
                        )
                    }
                    
                    drawContext.canvas.restore()

                    // HUD Layer
                    if (drawW > 0 && drawH > 0) {
                        val labelStyle = TextStyle(color = Color.White.copy(alpha = 0.3f), fontSize = 9.sp, fontWeight = FontWeight.Medium)
                        val labels = (0..4).map { i ->
                            val mmValue = i * (snappedMaxPrecip / 4.0)
                            val labelText = if (snappedMaxPrecip <= 2.0) String.format("%.1f", mmValue) else mmValue.roundToInt().toString()
                            textMeasurer.measure("$labelText mm", labelStyle)
                        }
                        val maxLabelW = labels.maxOf { it.size.width }.toFloat()
                        val alignRightX = 8.dp.toPx() + maxLabelW

                        for (i in 0..4) {
                            val yPos = t + drawH - (i / 4.0f * drawH)
                            val textLayout = labels[i]
                            drawText(textLayout, topLeft = Offset(alignRightX - textLayout.size.width, yPos - textLayout.size.height / 2))
                        }

                        val fraction = if (isDragging && dragX != null) {
                            (dragX!!.coerceIn(l, l + drawW) - l) / drawW
                        } else {
                            curF
                        }
                        val interpolatedProb = getInterpolatedProb(fraction)
                        val hourIdx = (fraction * 24).toInt().coerceIn(0, dayAmounts.size - 1)
                        val amount = if (dayAmounts.isNotEmpty()) dayAmounts[hourIdx] else 0.0

                        // Measurements matching TemperatureGraph styling
                        val probLayout = textMeasurer.measure(
                            "${interpolatedProb.roundToInt()}%", 
                            TextStyle(color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                        )
                        val amountLayout = textMeasurer.measure(
                            String.format("%.1f mm", amount), 
                            TextStyle(color = Color.White.copy(alpha = 0.8f), fontSize = 11.sp, fontWeight = FontWeight.Medium)
                        )
                        val timeLayout = textMeasurer.measure(
                            String.format("%02d:%02d", (fraction * 24).toInt() % 24, ((fraction * 24 - (fraction * 24).toInt()) * 60).toInt()), 
                            TextStyle(color = Color.White.copy(alpha = 0.7f), fontSize = 11.sp)
                        )

                        val xOffset = w - 16.dp.toPx()
                        
                        // Vertical positions
                        val probY = -32.dp.toPx()
                        val clockY = -4.dp.toPx()
                        
                        // mm value center-aligned exactly in the gap between % bottom and clock top
                        val probBottom = probY + probLayout.size.height
                        val clockTop = clockY
                        val gapCenter = (probBottom + clockTop) / 2f
                        val amountY = gapCenter - (amountLayout.size.height / 2f)

                        drawText(probLayout, topLeft = Offset(xOffset - probLayout.size.width, probY))
                        drawText(amountLayout, topLeft = Offset(xOffset - amountLayout.size.width, amountY))
                        drawText(timeLayout, topLeft = Offset(xOffset - timeLayout.size.width, clockY))
                    }
                }
        ) {
            val l = leftPaddingDp.toPx()
            val r = rightPaddingDp.toPx()
            val t = topPaddingDp.toPx()
            val b = bottomPaddingDp.toPx()
            val drawW = size.width - l - r
            val drawH = size.height - t - b
            
            if (drawW <= 0 || drawH <= 0) return@Canvas

            fun getX(fraction: Float): Float = l + (fraction * drawW)
            fun getYProb(prob: Double): Float = t + (drawH - ((prob / 100.0 * drawH))).toFloat()

            // 1. Grid Lines
            val gridColor = Color.White.copy(alpha = 0.05f)
            for (i in 0..4) {
                val y = t + drawH - (i / 4.0f * drawH)
                drawLine(gridColor, Offset(l, y), Offset(l + drawW, y), 1.dp.toPx())
            }

            // 2. Vertical Grid
            listOf(6, 12, 18).forEach { hour ->
                val x = getX(hour / 24f)
                drawLine(gridColor, Offset(x, t), Offset(x, t + drawH), 1.dp.toPx())
                val textLayout = textMeasurer.measure(String.format("%02d", hour), TextStyle(color = Color.White.copy(alpha = 0.3f), fontSize = 10.sp))
                drawText(textLayout, topLeft = Offset(x - textLayout.size.width / 2, t + drawH + 6.dp.toPx()))
            }

            // 3. Probability Area Shading
            fun createSmoothPath(startFraction: Float, endFraction: Float): Path {
                val path = Path()
                val totalPoints = dayProbs.size - 1
                if (totalPoints > 0) {
                    path.moveTo(getX(startFraction), getYProb(getInterpolatedProb(startFraction)))
                    val steps = ((endFraction - startFraction) * totalPoints * 10).toInt().coerceAtLeast(1)
                    for (step in 1..steps) {
                        val f = startFraction + (step.toFloat() / steps) * (endFraction - startFraction)
                        path.lineTo(getX(f), getYProb(getInterpolatedProb(f)))
                    }
                }
                return path
            }

            if (dayProbs.isNotEmpty()) {
                val fullPath = createSmoothPath(0f, 1f)
                val fillPath = Path().apply { 
                    addPath(fullPath)
                    lineTo(l + drawW, t + drawH)
                    lineTo(l, t + drawH)
                    close() 
                }
                
                val maxProb = dayProbs.maxOrNull() ?: 0.0
                drawPath(
                    path = fillPath,
                    brush = Brush.verticalGradient(
                        colors = listOf(Color(0xFF5C6BC0).copy(alpha = 0.4f), Color(0xFF1A237E).copy(alpha = 0.05f)),
                        startY = getYProb(maxProb),
                        endY = t + drawH
                    )
                )
            }

            // 4. Precipitation Bars
            if (dayAmounts.isNotEmpty()) {
                val barGap = 2.dp.toPx()
                val barWidth = (drawW / 24f) - barGap
                dayAmounts.forEachIndexed { index, amount ->
                    if (amount > 0) {
                        val x = getX(index / 24f)
                        val barHeight = (amount / snappedMaxPrecip * drawH).toFloat().coerceAtLeast(1f)
                        val barTop = t + drawH - barHeight
                        val intensity = (amount / snappedMaxPrecip).coerceIn(0.0, 1.0).toFloat()
                        val barBrush = Brush.verticalGradient(
                            colors = listOf(
                                Color(0xFF5C6BC0).copy(alpha = 0.85f + intensity * 0.1f), 
                                Color(0xFF3949AB).copy(alpha = 0.75f + intensity * 0.15f)
                            ),
                            startY = barTop,
                            endY = t + drawH
                        )
                        drawRoundRect(
                            brush = barBrush,
                            topLeft = Offset(x - barWidth / 2, barTop),
                            size = Size(barWidth, barHeight),
                            cornerRadius = androidx.compose.ui.geometry.CornerRadius(2.dp.toPx(), 2.dp.toPx())
                        )
                        drawRoundRect(
                            color = Color(0xFF5C6BC0).copy(alpha = 0.4f + intensity * 0.4f),
                            topLeft = Offset(x - barWidth / 2, barTop),
                            size = Size(barWidth, barHeight),
                            cornerRadius = androidx.compose.ui.geometry.CornerRadius(2.dp.toPx(), 2.dp.toPx()),
                            style = Stroke(width = 0.5.dp.toPx())
                        )
                    }
                }
            }

            val lineStrokeWidth = 3.dp.toPx()
            val lineBrush = Brush.linearGradient(
                colors = listOf(Color(0xFF9FA8DA), Color(0xFF3949AB)), 
                start = Offset(l, t + drawH / 2), 
                end = Offset(l + drawW, t + drawH / 2)
            )
            
            val pastPath = createSmoothPath(0f, curF)
            drawPath(
                path = pastPath, 
                brush = lineBrush,
                style = Stroke(width = lineStrokeWidth, cap = StrokeCap.Round, pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 12f), 0f))
            )
            val futurePath = createSmoothPath(curF, 1f)
            drawPath(
                path = futurePath, 
                brush = lineBrush, 
                style = Stroke(width = lineStrokeWidth, cap = StrokeCap.Round, join = StrokeJoin.Round)
            )

            val indicatorOutlineColor = Color(0xFF263238)

            if (isDragging && dragX != null) {
                val interactX = dragX!!.coerceIn(l, l + drawW)
                val fraction = (interactX - l) / drawW
                val pointY = getYProb(getInterpolatedProb(fraction))
                drawLine(Color.White.copy(alpha = 0.9f), Offset(interactX, t), Offset(interactX, t + drawH), 1.5.dp.toPx())
                drawCircle(indicatorOutlineColor, radius = 8.dp.toPx(), center = Offset(interactX, pointY))
                drawCircle(Color.White, radius = 6.dp.toPx(), center = Offset(interactX, pointY))
            }
            
            val curPointY = getYProb(getInterpolatedProb(curF))
            drawCircle(indicatorOutlineColor, radius = 6.dp.toPx(), center = Offset(getX(curF), curPointY))
            drawCircle(Color.White, radius = 4.dp.toPx(), center = Offset(getX(curF), curPointY))
        }
    }
}
