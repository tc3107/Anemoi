package com.example.anemoi.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
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
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.roundToInt

@Composable
fun DaylightGraph(
    daylightHours: Double,
    nowMinutes: Int,
    modifier: Modifier = Modifier,
    sunriseMinutes: Int? = null,
    sunsetMinutes: Int? = null
) {
    val density = LocalDensity.current
    val h = daylightHours.coerceIn(0.0, 24.0)

    fun roundToNearestFiveMinutes(minutes: Int): Int {
        val clamped = minutes.coerceIn(0, 1439)
        val rounded = ((clamped + 2) / 5) * 5
        return if (rounded >= 1440) 1435 else rounded
    }

    val isEphemerisAvailable = sunriseMinutes != null && sunsetMinutes != null

    val finalSunriseMinutes = roundToNearestFiveMinutes(
        sunriseMinutes ?: (720 - (h * 60) / 2).roundToInt()
    )
    val finalSunsetMinutes = roundToNearestFiveMinutes(
        sunsetMinutes ?: (720 + (h * 60) / 2).roundToInt()
    )
    val finalPeakMinutes = roundToNearestFiveMinutes(
        ((finalSunriseMinutes + finalSunsetMinutes) / 2f).roundToInt()
    )

    val amplitude = 0.125f
    val targetXRise = (finalSunriseMinutes / 1440f).coerceIn(0f, 1f)
    val targetXSet = (finalSunsetMinutes / 1440f).coerceIn(targetXRise, 1f)
    val threshold = -amplitude * cos(2 * PI.toFloat() * targetXRise)

    val targetSunX = remember(nowMinutes, finalSunriseMinutes, finalSunsetMinutes, targetXRise, targetXSet) {
        val now = nowMinutes.coerceIn(0, 1439).toFloat()
        val rise = finalSunriseMinutes.toFloat()
        val set = finalSunsetMinutes.toFloat()

        when {
            now < rise -> {
                val u = if (rise > 0f) now / rise else 0f
                u * targetXRise
            }
            now <= set -> {
                val u = if (set > rise) (now - rise) / (set - rise) else 0f
                targetXRise + u * (targetXSet - targetXRise)
            }
            else -> {
                val u = if (1440f > set) (now - set) / (1440f - set) else 0f
                targetXSet + u * (1f - targetXSet)
            }
        }
    }
    val sunY = -amplitude * cos(2 * PI.toFloat() * targetSunX)

    fun formatMinutes(minutes: Int): String {
        val hrs = (minutes / 60) % 24
        val mins = minutes % 60
        return String.format(Locale.getDefault(), "%02d:%02d", hrs, mins)
    }

    val riseLabel = remember(sunriseMinutes, finalSunriseMinutes) {
        if (sunriseMinutes != null) formatMinutes(finalSunriseMinutes) else "--:--"
    }
    val setLabel = remember(sunsetMinutes, finalSunsetMinutes) {
        if (sunsetMinutes != null) formatMinutes(finalSunsetMinutes) else "--:--"
    }
    val peakLabel = remember(sunriseMinutes, sunsetMinutes, finalPeakMinutes) {
        if (sunriseMinutes != null && sunsetMinutes != null) formatMinutes(finalPeakMinutes) else "--:--"
    }

    val labelPaint = remember(density) {
        android.graphics.Paint().apply {
            color = android.graphics.Color.WHITE
            alpha = (0.6f * 255).toInt()
            textSize = with(density) { 9.sp.toPx() }
            textAlign = android.graphics.Paint.Align.CENTER
            typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val width = size.width
            val height = size.height

            val topPadding = 12.dp.toPx()
            val bottomPadding = 12.dp.toPx()
            val availableHeight = height - topPadding - bottomPadding
            if (availableHeight <= 0f) return@Canvas

            fun mapX(x: Float): Float = x * width
            fun mapY(y: Float): Float {
                val normalizedY = (y - (-amplitude)) / (2 * amplitude)
                return topPadding + (1f - normalizedY) * availableHeight
            }

            val daylineY = mapY(threshold)
            val thresholdPos = ((daylineY - topPadding) / availableHeight).coerceIn(0f, 1f)

            drawLine(
                brush = Brush.horizontalGradient(
                    0.0f to Color.Transparent,
                    0.2f to Color.Gray.copy(alpha = 0.3f),
                    0.8f to Color.Gray.copy(alpha = 0.3f),
                    1.0f to Color.Transparent
                ),
                start = Offset(0f, daylineY),
                end = Offset(width, daylineY),
                strokeWidth = 2.dp.toPx()
            )

            val path = Path()
            val segmentsCount = 120
            for (i in 0..segmentsCount) {
                val x = i.toFloat() / segmentsCount
                val y = -amplitude * cos(2 * PI.toFloat() * x)
                val px = mapX(x)
                val py = mapY(y)
                if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
            }

            val curveBrush = Brush.verticalGradient(
                0.0f to Color.White.copy(alpha = 0.88f),
                thresholdPos to Color.White.copy(alpha = 0.68f),
                thresholdPos to Color.White.copy(alpha = 0.26f),
                1.0f to Color.White.copy(alpha = 0.08f),
                startY = topPadding,
                endY = topPadding + availableHeight
            )
            val fadeWidth = min(18.dp.toPx(), width * 0.20f)
            drawContext.canvas.saveLayer(
                Rect(0f, 0f, size.width, size.height),
                Paint()
            )
            drawPath(
                path = path,
                brush = curveBrush,
                style = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round)
            )
            if (fadeWidth > 0f) {
                val fadeFraction = (fadeWidth / width).coerceIn(0f, 0.5f)
                drawRect(
                    brush = Brush.horizontalGradient(
                        colorStops = arrayOf(
                            0f to Color.Transparent,
                            fadeFraction to Color.Black,
                            (1f - fadeFraction) to Color.Black,
                            1f to Color.Transparent
                        )
                    ),
                    topLeft = Offset(0f, topPadding),
                    size = Size(width, availableHeight),
                    blendMode = BlendMode.DstIn
                )
            }
            drawContext.canvas.restore()

            val riseX = mapX(targetXRise)
            val setX = mapX(targetXSet)
            val peakX = (riseX + setX) / 2f
            val peakY = mapY(amplitude)
            val crossingY = mapY(threshold)

            if (isEphemerisAvailable) {
                val guideColor = Color.Gray.copy(alpha = 0.35f)
                drawLine(
                    color = guideColor,
                    start = Offset(riseX, crossingY),
                    end = Offset(riseX, topPadding),
                    strokeWidth = 1.2.dp.toPx()
                )
                drawLine(
                    color = guideColor,
                    start = Offset(setX, crossingY),
                    end = Offset(setX, topPadding),
                    strokeWidth = 1.2.dp.toPx()
                )
                drawLine(
                    color = guideColor,
                    start = Offset(peakX, peakY),
                    end = Offset(peakX, height - bottomPadding),
                    strokeWidth = 1.2.dp.toPx()
                )

                drawCircle(color = Color.White.copy(alpha = 0.75f), radius = 3.dp.toPx(), center = Offset(riseX, crossingY))
                drawCircle(color = Color.White.copy(alpha = 0.75f), radius = 3.dp.toPx(), center = Offset(setX, crossingY))
                drawCircle(color = Color.White.copy(alpha = 0.75f), radius = 3.dp.toPx(), center = Offset(peakX, peakY))
            }

            drawContext.canvas.nativeCanvas.apply {
                drawText(riseLabel, riseX, topPadding - 4.dp.toPx(), labelPaint)
                drawText(setLabel, setX, topPadding - 4.dp.toPx(), labelPaint)
                if (isEphemerisAvailable) {
                    drawText(peakLabel, peakX, height - 4.dp.toPx(), labelPaint)
                }
            }

            if (isEphemerisAvailable) {
                val sunPx = mapX(targetSunX)
                val sunPy = mapY(sunY)
                val sunRadius = 6.dp.toPx()
                val isDaytime = sunPy < daylineY

                if (isDaytime) {
                    val glowRadius = 14.dp.toPx()
                    drawCircle(
                        brush = Brush.radialGradient(
                            0.0f to Color(0xFFFFE082).copy(alpha = 0.38f),
                            1.0f to Color.Transparent,
                            center = Offset(sunPx, sunPy),
                            radius = glowRadius
                        ),
                        radius = glowRadius,
                        center = Offset(sunPx, sunPy)
                    )
                    drawCircle(
                        color = Color(0xFFFFE082),
                        radius = sunRadius,
                        center = Offset(sunPx, sunPy)
                    )
                    drawCircle(
                        color = Color.White.copy(alpha = 0.86f),
                        radius = sunRadius * 0.45f,
                        center = Offset(sunPx - sunRadius * 0.25f, sunPy - sunRadius * 0.25f)
                    )
                } else {
                    drawCircle(
                        color = Color.Black.copy(alpha = 0.6f),
                        radius = sunRadius * 0.82f,
                        center = Offset(sunPx, sunPy)
                    )
                    drawCircle(
                        color = Color.White.copy(alpha = 0.78f),
                        radius = sunRadius * 0.82f,
                        center = Offset(sunPx, sunPy),
                        style = Stroke(width = 1.4.dp.toPx())
                    )
                }
            }
        }
    }
}
