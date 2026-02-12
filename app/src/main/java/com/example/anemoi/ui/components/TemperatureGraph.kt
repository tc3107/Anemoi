package com.example.anemoi.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.anemoi.data.TempUnit
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

@OptIn(ExperimentalTextApi::class)
@Composable
fun TemperatureGraph(
    times: List<String>,
    temperatures: List<Double>,
    currentTemp: Double?,
    currentTimeIso: String?,
    tempUnit: TempUnit,
    widgetTopToGraphTopInset: Dp = 24.dp,
    modifier: Modifier = Modifier
) {
    val textMeasurer = rememberTextMeasurer()

    val dayTemps = remember(temperatures) {
        if (temperatures.size >= 25) temperatures.take(25)
        else if (temperatures.isNotEmpty()) temperatures
        else emptyList()
    }

    if (dayTemps.isEmpty()) {
        Box(modifier = modifier, contentAlignment = androidx.compose.ui.Alignment.Center) {
            androidx.compose.material3.Text("No data", color = Color.White.copy(alpha = 0.3f))
        }
        return
    }

    var dragX by remember { mutableStateOf<Float?>(null) }
    var isDragging by remember { mutableStateOf(false) }

    // Padding to keep graph content within intended area
    val leftPaddingDp = 64.dp 
    val rightPaddingDp = 16.dp
    val topPaddingDp = 12.dp 
    val bottomPaddingDp = 24.dp

    // Shared Interpolation Function for extreme smoothness
    fun getInterpolatedTemp(fraction: Float): Double {
        val totalPoints = dayTemps.size - 1
        if (totalPoints <= 0) return dayTemps.firstOrNull() ?: 0.0
        val f = fraction.coerceIn(0f, 1f) * totalPoints
        val i = f.toInt().coerceIn(0, totalPoints - 1)
        val dt = f - i

        val p0 = dayTemps[(i - 1).coerceIn(0, totalPoints)]
        val p1 = dayTemps[i]
        val p2 = dayTemps[i + 1]
        val p3 = dayTemps[(i + 2).coerceIn(0, totalPoints)]

        val tension = 0.5f
        val m1 = (p2 - p0) * tension
        val m2 = (p3 - p1) * tension
        val t2 = dt * dt
        val t3 = t2 * dt
        
        return (2 * t3 - 3 * t2 + 1) * p1 + 
               (t3 - 2 * t2 + dt) * m1 + 
               (-2 * t3 + 3 * t2) * p2 + 
               (t3 - t2) * m2
    }

    fun toDisplayTemp(celsius: Double): Double {
        return when (tempUnit) {
            TempUnit.CELSIUS -> celsius
            TempUnit.FAHRENHEIT -> celsius * 9 / 5 + 32
            TempUnit.KELVIN -> celsius + 273.15
        }
    }

    fun formatTempWithUnit(celsius: Double): String {
        val displayValue = toDisplayTemp(celsius).toInt()
        return when (tempUnit) {
            TempUnit.CELSIUS -> "${displayValue}°C"
            TempUnit.FAHRENHEIT -> "${displayValue}°F"
            TempUnit.KELVIN -> "${displayValue}K"
        }
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
                                    // If moving vertically, stop dragging and let parent handle it
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

                    // 1 & 2. Draw main content with fading effect using an internal layer.
                    drawContext.canvas.saveLayer(Rect(0f, 0f, w, h), Paint())
                    
                    drawContent()

                    // Apply Fade to graph area tips
                    val fadeWidth = 24.dp.toPx()
                    if (w > 0f) {
                        val leftFadeStart = (l / w).coerceIn(0f, 1f)
                        val leftFadeEnd = ((l + fadeWidth) / w).coerceIn(0f, 1f)
                        val rightFadeStart = ((w - r - fadeWidth) / w).coerceIn(0f, 1f)
                        val rightFadeEnd = ((w - r) / w).coerceIn(0f, 1f)
                        drawRect(
                            brush = Brush.horizontalGradient(
                                colorStops = arrayOf(
                                    0.0f to Color.Transparent,
                                    leftFadeStart to Color.Transparent,
                                    leftFadeEnd to Color.Black,
                                    rightFadeStart to Color.Black,
                                    rightFadeEnd to Color.Transparent,
                                    1.0f to Color.Transparent
                                )
                            ),
                            blendMode = BlendMode.DstIn
                        )
                    }
                    
                    drawContext.canvas.restore()

                    // 3. HUD Layer
                    if (drawW > 0 && drawH > 0) {
                        val minVal = dayTemps.minOrNull() ?: 0.0
                        val maxVal = dayTemps.maxOrNull() ?: 0.0
                        val dataRange = (maxVal - minVal).coerceAtLeast(1.0)
                        val yMin = minVal - (dataRange * 0.35)
                        val yMax = maxVal + (dataRange * 0.20)
                        val yRange = yMax - yMin

                        fun getYHUD(temp: Double): Float = t + (drawH - ((temp - yMin) / yRange * drawH)).toFloat()

                        // Right-aligned Y-axis labels
                        val labelStyle = TextStyle(color = Color.White.copy(alpha = 0.3f), fontSize = 9.sp, fontWeight = FontWeight.Medium)
                        val labels = (0 until 6).map { i ->
                            val tempValue = yMin + (i / 5.0f * yRange)
                            textMeasurer.measure(formatTempWithUnit(tempValue), labelStyle)
                        }
                        val alignRightX = l - 8.dp.toPx()

                        for (i in 0 until 6) {
                            val yPos = getYHUD(yMin + (i / 5.0f * yRange))
                            val textLayout = labels[i]
                            drawText(textLayout, topLeft = Offset(alignRightX - textLayout.size.width, yPos - textLayout.size.height / 2))
                        }

                        // Interaction Selected Value
                        val fraction = if (isDragging && dragX != null) {
                            (dragX!!.coerceIn(l, l + drawW) - l) / drawW
                        } else {
                            curF
                        }
                        val displayedTemp = if (isDragging && dragX != null) {
                            getInterpolatedTemp(fraction)
                        } else {
                            currentTemp ?: getInterpolatedTemp(fraction)
                        }

                        val readingLabel = formatTempWithUnit(displayedTemp)
                        val hr = (fraction * 24).toInt() % 24
                        val min = ((fraction * 24 - hr) * 60).toInt()
                        val clockLabel = String.format("%02d:%02d", hr, min)
                        val hudRightX = w - rightPaddingDp.toPx()
                        val widgetTopY = -widgetTopToGraphTopInset.toPx()
                        val topGridLineY = t
                        val availableHudHeight = (topGridLineY - widgetTopY).coerceAtLeast(1f)
                        val availableHudWidth = (hudRightX - l).coerceAtLeast(1f)
                        val baseGap = 6.dp.toPx()
                        val baseReadingTextSizeSp = 14f
                        val baseClockTextSizeSp = 12f

                        val baseReadingStyle = TextStyle(
                            color = Color.White,
                            fontSize = baseReadingTextSizeSp.sp,
                            fontWeight = FontWeight.Bold
                        )
                        val baseClockStyle = TextStyle(
                            color = Color.White.copy(alpha = 0.7f),
                            fontSize = baseClockTextSizeSp.sp,
                            fontWeight = FontWeight.Medium
                        )

                        var readingLayout = textMeasurer.measure(readingLabel, baseReadingStyle)
                        var timeLayout = textMeasurer.measure(clockLabel, baseClockStyle)
                        var readingClockGap = baseGap
                        var combinedWidth = readingLayout.size.width + readingClockGap + timeLayout.size.width
                        var maxTextHeight = max(readingLayout.size.height, timeLayout.size.height).toFloat()

                        if (maxTextHeight > availableHudHeight || combinedWidth > availableHudWidth) {
                            val scaleForHeight = availableHudHeight / maxTextHeight
                            val scaleForWidth = availableHudWidth / combinedWidth
                            val scale = min(scaleForHeight, scaleForWidth).coerceIn(0f, 1f)
                            val scaledReadingStyle = baseReadingStyle.copy(fontSize = (baseReadingTextSizeSp * scale).sp)
                            val scaledClockStyle = baseClockStyle.copy(fontSize = (baseClockTextSizeSp * scale).sp)
                            readingLayout = textMeasurer.measure(readingLabel, scaledReadingStyle)
                            timeLayout = textMeasurer.measure(clockLabel, scaledClockStyle)
                            readingClockGap = baseGap * scale
                            combinedWidth = readingLayout.size.width + readingClockGap + timeLayout.size.width
                            maxTextHeight = max(readingLayout.size.height, timeLayout.size.height).toFloat()
                        }

                        val hudCenterY = (widgetTopY + topGridLineY) * 0.5f
                        val rowStartX = hudRightX - combinedWidth
                        val readingTop = hudCenterY - (readingLayout.size.height * 0.5f)
                        val clockTop = hudCenterY - (timeLayout.size.height * 0.5f)

                        drawText(
                            readingLayout,
                            topLeft = Offset(
                                rowStartX,
                                readingTop
                            )
                        )
                        drawText(
                            timeLayout,
                            topLeft = Offset(
                                rowStartX + readingLayout.size.width + readingClockGap,
                                clockTop
                            )
                        )
                    }
                }
        ) {
            val l = leftPaddingDp.toPx()
            val r = rightPaddingDp.toPx()
            val t = topPaddingDp.toPx()
            val b = bottomPaddingDp.toPx()
            val drawW = size.width - l - r
            val drawH = size.height - t - b
            
            if (drawW <= 0f || drawH <= 0f) return@Canvas

            val minVal = dayTemps.minOrNull() ?: 0.0
            val maxVal = dayTemps.maxOrNull() ?: 0.0
            val dataRange = (maxVal - minVal).coerceAtLeast(1.0)
            val yMin = minVal - (dataRange * 0.35)
            val yMax = maxVal + (dataRange * 0.20)
            val yRange = yMax - yMin

            fun getX(fraction: Float): Float = l + (fraction * drawW)
            fun getY(temp: Double): Float = t + (drawH - ((temp - yMin) / yRange * drawH)).toFloat()

            // 1. Grid Lines
            val gridColor = Color.White.copy(alpha = 0.05f)
            for (i in 0 until 6) {
                val yPos = getY(yMin + (i / 5.0f * yRange))
                drawLine(gridColor, Offset(l, yPos), Offset(l + drawW, yPos), 1.dp.toPx())
            }

            // 1.5 Vertical Grid
            listOf(6, 12, 18).forEach { hour ->
                val x = getX(hour / 24f)
                drawLine(gridColor, Offset(x, t), Offset(x, t + drawH), 1.dp.toPx())
                val textLayout = textMeasurer.measure(String.format("%02d", hour), TextStyle(color = Color.White.copy(alpha = 0.3f), fontSize = 10.sp))
                drawText(textLayout, topLeft = Offset(x - textLayout.size.width / 2, t + drawH + 6.dp.toPx()))
            }

            // 2. Paths
            fun createSmoothPath(startFraction: Float, endFraction: Float): Path {
                val path = Path()
                path.moveTo(getX(startFraction), getY(getInterpolatedTemp(startFraction)))
                val totalPoints = dayTemps.size - 1
                val steps = ((endFraction - startFraction) * totalPoints * 10).toInt().coerceAtLeast(1)
                for (step in 1..steps) {
                    val f = startFraction + (step.toFloat() / steps) * (endFraction - startFraction)
                    path.lineTo(getX(f), getY(getInterpolatedTemp(f)))
                }
                return path
            }

            // 4. Shading
            val fullPath = createSmoothPath(0f, 1f)
            val fillPath = Path().apply { addPath(fullPath); lineTo(l + drawW, t + drawH); lineTo(l, t + drawH); close() }
            drawPath(fillPath, brush = Brush.verticalGradient(colors = listOf(Color(0xFF6EC9F7).copy(alpha = 0.45f), Color(0xFF01579B).copy(alpha = 0.15f)), startY = getY(maxVal), endY = t + drawH))

            // 5. Lines
            val lineStrokeWidth = 5.dp.toPx()
            val lineBrush = Brush.linearGradient(colors = listOf(Color(0xFF6EC9F7), Color(0xFF0288D1)), start = Offset(l, t + drawH / 2), end = Offset(l + drawW, t + drawH / 2))
            drawPath(createSmoothPath(0f, curF), brush = lineBrush, style = Stroke(width = lineStrokeWidth, cap = StrokeCap.Round, pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 28f), 0f)))
            drawPath(createSmoothPath(curF, 1f), brush = lineBrush, style = Stroke(width = lineStrokeWidth, cap = StrokeCap.Round, join = StrokeJoin.Round))

            // 6. Interaction Elements
            if (isDragging && dragX != null) {
                val interactX = dragX!!.coerceIn(l, l + drawW)
                val pointY = getY(getInterpolatedTemp((interactX - l) / drawW))
                drawLine(Color.White.copy(alpha = 0.9f), Offset(interactX, t), Offset(interactX, t + drawH), 1.5.dp.toPx())
                drawCircle(Color(0xFF263238), radius = 8.dp.toPx(), center = Offset(interactX, pointY))
                drawCircle(Color.White, radius = 6.dp.toPx(), center = Offset(interactX, pointY))
            }

            // 7. Fixed Points
            val totalPts = (dayTemps.size - 1).coerceAtLeast(1)
            val indicatorOutlineColor = Color(0xFF263238)

            fun drawPointHUD(center: Offset, fillColor: Color, label: String?) {
                drawCircle(indicatorOutlineColor, radius = 6.dp.toPx(), center = center)
                drawCircle(fillColor, radius = 4.dp.toPx(), center = center)
                if (label != null) {
                    val labelLayout = textMeasurer.measure(label, TextStyle(color = Color(0xFFAAAAAA), fontSize = 11.sp, fontWeight = FontWeight.Bold))
                    val isUpperHalf = center.y < (t + drawH / 2)
                    val labelY = if (isUpperHalf) center.y + 8.dp.toPx() else center.y - labelLayout.size.height - 8.dp.toPx()
                    drawText(labelLayout, topLeft = Offset(center.x - labelLayout.size.width / 2, labelY))
                }
            }

            fun pointOnCurve(fraction: Float): Offset {
                val clamped = fraction.coerceIn(0f, 1f)
                return Offset(getX(clamped), getY(getInterpolatedTemp(clamped)))
            }

            val highFraction = dayTemps.lastIndexOf(maxVal).toFloat() / totalPts
            val lowFraction = dayTemps.lastIndexOf(minVal).toFloat() / totalPts

            drawPointHUD(pointOnCurve(highFraction), Color(0xFF0288D1), "H")
            drawPointHUD(pointOnCurve(lowFraction), Color(0xFF0288D1), "L")
            drawPointHUD(pointOnCurve(curF), Color.White, null)
        }
    }
}
