package com.example.anemoi.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
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

    val leftPaddingDp = 64.dp
    val rightPaddingDp = 16.dp
    val topPaddingDp = 12.dp
    val bottomPaddingDp = 24.dp

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
                val minute = timePart.split(":").getOrNull(1)?.toIntOrNull() ?: 0
                ((hr + (minute / 60f)) / 24f).coerceIn(0f, 1f)
            } catch (_: Exception) {
                0f
            }
        } ?: 0f
    }

    val minVal = remember(dayTemps) { dayTemps.minOrNull() ?: 0.0 }
    val maxVal = remember(dayTemps) { dayTemps.maxOrNull() ?: 0.0 }
    val dataRange = remember(minVal, maxVal) { (maxVal - minVal).coerceAtLeast(1.0) }
    val yMin = remember(minVal, dataRange) { minVal - (dataRange * 0.35) }
    val yMax = remember(maxVal, dataRange) { maxVal + (dataRange * 0.20) }
    val yRange = remember(yMin, yMax) { yMax - yMin }

    val totalPts = remember(dayTemps) { (dayTemps.size - 1).coerceAtLeast(1) }
    val highFraction = remember(dayTemps, maxVal, totalPts) {
        dayTemps.lastIndexOf(maxVal).toFloat() / totalPts
    }
    val lowFraction = remember(dayTemps, minVal, totalPts) {
        dayTemps.lastIndexOf(minVal).toFloat() / totalPts
    }

    val yAxisLabelStyle = remember {
        TextStyle(
            color = Color.White.copy(alpha = 0.3f),
            fontSize = 9.sp,
            fontWeight = FontWeight.Medium
        )
    }
    val yAxisLayouts = remember(textMeasurer, yMin, yRange, tempUnit) {
        (0 until 6).map { i ->
            val tempValue = yMin + (i / 5.0f * yRange)
            textMeasurer.measure(formatTempWithUnit(tempValue), yAxisLabelStyle)
        }
    }

    val hourLabelStyle = remember {
        TextStyle(color = Color.White.copy(alpha = 0.3f), fontSize = 10.sp)
    }
    val hourLayouts = remember(textMeasurer) {
        listOf(6, 12, 18).associateWith { hour ->
            textMeasurer.measure(String.format("%02d", hour), hourLabelStyle)
        }
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
        ) {
            val l = leftPaddingDp.toPx()
            val r = rightPaddingDp.toPx()
            val t = topPaddingDp.toPx()
            val b = bottomPaddingDp.toPx()
            val w = size.width
            val drawW = w - l - r
            val drawH = size.height - t - b

            if (drawW <= 0f || drawH <= 0f) return@Canvas

            fun getX(fraction: Float): Float = l + (fraction * drawW)
            fun getY(temp: Double): Float = t + (drawH - ((temp - yMin) / yRange * drawH)).toFloat()
            fun tempAt(fraction: Float): Double = sampleTemperatureSeriesAtFraction(dayTemps, fraction)

            drawContext.canvas.saveLayer(
                Rect(0f, 0f, size.width, size.height),
                Paint()
            )

            val gridColor = Color.White.copy(alpha = 0.05f)
            for (i in 0 until 6) {
                val yPos = getY(yMin + (i / 5.0f * yRange))
                drawLine(gridColor, Offset(l, yPos), Offset(l + drawW, yPos), 1.dp.toPx())
            }

            listOf(6, 12, 18).forEach { hour ->
                val x = getX(hour / 24f)
                drawLine(gridColor, Offset(x, t), Offset(x, t + drawH), 1.dp.toPx())
                val textLayout = hourLayouts.getValue(hour)
                drawText(textLayout, topLeft = Offset(x - textLayout.size.width / 2f, t + drawH + 6.dp.toPx()))
            }

            val fullPath = buildSampledPath(
                startFraction = 0f,
                endFraction = 1f,
                samples = 54,
                xForFraction = ::getX,
                yForFraction = { fraction -> getY(tempAt(fraction)) }
            )

            val fillPath = Path().apply {
                addPath(fullPath)
                lineTo(l + drawW, t + drawH)
                lineTo(l, t + drawH)
                close()
            }

            drawPath(
                path = fillPath,
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF6EC9F7).copy(alpha = 0.38f),
                        Color(0xFF01579B).copy(alpha = 0.10f)
                    ),
                    startY = getY(maxVal),
                    endY = t + drawH
                )
            )

            val lineStrokeWidth = 4.dp.toPx()
            val lineBrush = Brush.linearGradient(
                colors = listOf(Color(0xFF6EC9F7), Color(0xFF0288D1)),
                start = Offset(l, t + drawH / 2f),
                end = Offset(l + drawW, t + drawH / 2f)
            )
            val splitFraction = curF.coerceIn(0f, 1f)

            drawPath(
                path = buildSampledPath(
                    startFraction = 0f,
                    endFraction = splitFraction,
                    samples = 28,
                    xForFraction = ::getX,
                    yForFraction = { fraction -> getY(tempAt(fraction)) }
                ),
                brush = lineBrush,
                alpha = 0.58f,
                style = Stroke(width = lineStrokeWidth, cap = StrokeCap.Round, join = StrokeJoin.Round)
            )
            drawPath(
                path = buildSampledPath(
                    startFraction = splitFraction,
                    endFraction = 1f,
                    samples = 28,
                    xForFraction = ::getX,
                    yForFraction = { fraction -> getY(tempAt(fraction)) }
                ),
                brush = lineBrush,
                style = Stroke(width = lineStrokeWidth, cap = StrokeCap.Round, join = StrokeJoin.Round)
            )

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

            val indicatorOutlineColor = Color(0xFF263238)

            if (isDragging && dragX != null) {
                val interactX = dragX!!.coerceIn(l, l + drawW)
                val pointY = getY(tempAt((interactX - l) / drawW))
                drawLine(
                    Color.White.copy(alpha = 0.9f),
                    Offset(interactX, t),
                    Offset(interactX, t + drawH),
                    1.5.dp.toPx()
                )
                drawCircle(indicatorOutlineColor, radius = 8.dp.toPx(), center = Offset(interactX, pointY))
                drawCircle(Color.White, radius = 6.dp.toPx(), center = Offset(interactX, pointY))
            }

            fun pointOnCurve(fraction: Float): Offset {
                val clamped = fraction.coerceIn(0f, 1f)
                return Offset(getX(clamped), getY(tempAt(clamped)))
            }

            fun drawPointHUD(center: Offset, fillColor: Color) {
                drawCircle(indicatorOutlineColor, radius = 6.dp.toPx(), center = center)
                drawCircle(fillColor, radius = 4.dp.toPx(), center = center)
            }

            drawPointHUD(pointOnCurve(highFraction), Color(0xFF0288D1))
            drawPointHUD(pointOnCurve(lowFraction), Color(0xFF0288D1))
            drawPointHUD(pointOnCurve(splitFraction), Color.White)

            drawContext.canvas.restore()

            val alignRightX = l - 8.dp.toPx()
            for (i in 0 until 6) {
                val yPos = getY(yMin + (i / 5.0f * yRange))
                val layout = yAxisLayouts[i]
                drawText(
                    layout,
                    topLeft = Offset(alignRightX - layout.size.width, yPos - layout.size.height / 2f)
                )
            }

            val fraction = if (isDragging && dragX != null) {
                (dragX!!.coerceIn(l, l + drawW) - l) / drawW
            } else {
                splitFraction
            }
            val displayedTemp = if (isDragging && dragX != null) {
                tempAt(fraction)
            } else {
                currentTemp ?: tempAt(fraction)
            }

            val readingLabel = formatTempWithUnit(displayedTemp)
            val hour = (fraction * 24).toInt() % 24
            val minute = ((fraction * 24 - hour) * 60).toInt()
            val clockLabel = String.format("%02d:%02d", hour, minute)

            val hudRightX = w - rightPaddingDp.toPx()
            val widgetTopY = -widgetTopToGraphTopInset.toPx()
            val availableHudHeight = (t - widgetTopY).coerceAtLeast(1f)
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
            }

            val hudCenterY = (widgetTopY + t) * 0.5f
            val rowStartX = hudRightX - combinedWidth
            val readingTop = hudCenterY - (readingLayout.size.height * 0.5f)
            val clockTop = hudCenterY - (timeLayout.size.height * 0.5f)

            drawText(readingLayout, topLeft = Offset(rowStartX, readingTop))
            drawText(
                timeLayout,
                topLeft = Offset(rowStartX + readingLayout.size.width + readingClockGap, clockTop)
            )
        }
    }
}

private fun buildSampledPath(
    startFraction: Float,
    endFraction: Float,
    samples: Int,
    xForFraction: (Float) -> Float,
    yForFraction: (Float) -> Float
): Path {
    val path = Path()
    val start = startFraction.coerceIn(0f, 1f)
    val end = endFraction.coerceIn(0f, 1f)

    if (end <= start) {
        val x = xForFraction(start)
        val y = yForFraction(start)
        path.moveTo(x, y)
        path.lineTo(x, y)
        return path
    }

    val steps = samples.coerceAtLeast(1)
    path.moveTo(xForFraction(start), yForFraction(start))
    for (i in 1..steps) {
        val t = i.toFloat() / steps
        val f = start + (end - start) * t
        path.lineTo(xForFraction(f), yForFraction(f))
    }
    return path
}

private fun sampleTemperatureSeriesAtFraction(
    values: List<Double>,
    fraction: Float
): Double {
    if (values.isEmpty()) return 0.0
    if (values.size == 1) return values[0]
    val clamped = fraction.coerceIn(0f, 1f)
    val scaled = clamped * (values.size - 1)
    val baseIndex = scaled.toInt().coerceIn(0, values.lastIndex - 1)
    val t = scaled - baseIndex
    val a = values[baseIndex]
    val b = values[baseIndex + 1]
    return a + (b - a) * t
}
