package com.tudorc.anemoi.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale
import kotlin.math.*

@Composable
fun DaylightGraph(
    daylightHours: Double, // H: 0..24
    nowMinutes: Int, // 0..1439
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

    val amplitude = 0.125f // Amplitude
    
    val threshold = remember(h) {
        val target = (h / 24.0).toFloat()
        var lo = -amplitude
        var hi = amplitude
        val iterationsCount = 2000
        val dx = 1.0f / iterationsCount
        
        fun fractionAbove(t: Float): Float {
            var sumPlus = 0f
            var sumMinus = 0f
            for (i in 0 until iterationsCount) {
                val x = (i + 0.5f) * dx
                val y = -amplitude * cos(2 * PI.toFloat() * x)
                if (y > t) sumPlus += (y - t)
                else sumMinus += (t - y)
            }
            val aPlus = sumPlus * dx
            val aMinus = sumMinus * dx
            return if (aPlus + aMinus == 0f) 0.5f else aPlus / (aPlus + aMinus)
        }

        repeat(40) {
            val mid = (lo + hi) / 2
            if (fractionAbove(mid) > target) {
                lo = mid
            } else {
                hi = mid
            }
        }
        (lo + hi) / 2
    }

    val rValue = (-threshold / amplitude).coerceIn(-1f, 1f)
    val xRise = acos(rValue) / (2 * PI.toFloat())
    val xSet = 1f - xRise

    val sunX = remember(nowMinutes, finalSunriseMinutes, finalSunsetMinutes, xRise, xSet) {
        val now = nowMinutes.toFloat()
        val rise = finalSunriseMinutes.toFloat()
        val set = finalSunsetMinutes.toFloat()
        
        when {
            now < rise -> {
                val u = if (rise > 0) now / rise else 0f
                u * xRise
            }
            now <= set -> {
                val u = if (set > rise) (now - rise) / (set - rise) else 0f
                xRise + u * (xSet - xRise)
            }
            else -> {
                val u = if (1440f > set) (now - set) / (1440f - set) else 0f
                xSet + u * (1f - xSet)
            }
        }
    }
    val sunY = -amplitude * cos(2 * PI.toFloat() * sunX)

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

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val width = size.width
            val height = size.height
            
            // Fixed paddings to ensure content stays within the "safe area" of the container
            val topPadding = 12.dp.toPx() 
            val bottomPadding = 12.dp.toPx()
            val availableHeight = height - topPadding - bottomPadding
            
            if (availableHeight <= 0) return@Canvas

            fun mapX(x: Float) = x * width
            fun mapY(y: Float): Float {
                val normalizedY = (y - (-amplitude)) / (2 * amplitude)
                return topPadding + (1f - normalizedY) * availableHeight
            }

            val daylineY = mapY(threshold)
            val thresholdPos = (daylineY - topPadding) / availableHeight

            val curveBrush = Brush.verticalGradient(
                0.0f to Color.White.copy(alpha = 0.9f),
                thresholdPos to Color.White.copy(alpha = 0.7f),
                thresholdPos to Color.White.copy(alpha = 0.3f),
                1.0f to Color.White.copy(alpha = 0.1f),
                startY = topPadding,
                endY = topPadding + availableHeight
            )

            val horizontalFadeBrush = Brush.horizontalGradient(
                0.0f to Color.Transparent,
                0.1f to Color.White,
                0.9f to Color.White,
                1.0f to Color.Transparent
            )

            drawLine(
                brush = Brush.horizontalGradient(
                    0.0f to Color.Transparent,
                    0.2f to Color.White.copy(alpha = 0.26f),
                    0.8f to Color.White.copy(alpha = 0.26f),
                    1.0f to Color.Transparent
                ),
                start = Offset(0f, daylineY),
                end = Offset(width, daylineY),
                strokeWidth = 2.dp.toPx()
            )

            val path = Path()
            val segmentsCount = 200
            for (i in 0..segmentsCount) {
                val x = i.toFloat() / segmentsCount
                val y = -amplitude * cos(2 * PI.toFloat() * x)
                val px = mapX(x)
                val py = mapY(y)
                if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
            }

            drawContext.canvas.saveLayer(Rect(Offset.Zero, size), Paint())
            drawPath(
                path = path,
                brush = curveBrush,
                style = Stroke(width = 5.dp.toPx(), cap = StrokeCap.Round)
            )
            drawRect(
                brush = horizontalFadeBrush,
                blendMode = BlendMode.Modulate
            )
            drawContext.canvas.restore()

            val riseX = mapX(xRise)
            val setX = mapX(xSet)
            val peakX = (riseX + setX) / 2f
            val peakY = mapY(amplitude)
            val crossingY = mapY(threshold)
            
            if (isEphemerisAvailable) {
                val dottedEffect = PathEffect.dashPathEffect(floatArrayOf(5f, 5f), 0f)
                val dashedLineColor = Color.White.copy(alpha = 0.26f)
                
                drawLine(
                    color = dashedLineColor,
                    start = Offset(riseX, crossingY),
                    end = Offset(riseX, topPadding),
                    pathEffect = dottedEffect,
                    strokeWidth = 2.dp.toPx()
                )
                
                drawLine(
                    color = dashedLineColor,
                    start = Offset(setX, crossingY),
                    end = Offset(setX, topPadding),
                    pathEffect = dottedEffect,
                    strokeWidth = 2.dp.toPx()
                )

                drawLine(
                    color = dashedLineColor,
                    start = Offset(peakX, peakY),
                    end = Offset(peakX, height - bottomPadding),
                    pathEffect = dottedEffect,
                    strokeWidth = 2.dp.toPx()
                )

                drawCircle(color = Color.White.copy(alpha = 0.8f), radius = 4.dp.toPx(), center = Offset(riseX, crossingY))
                drawCircle(color = Color.White.copy(alpha = 0.8f), radius = 4.dp.toPx(), center = Offset(setX, crossingY))
                drawCircle(color = Color.White.copy(alpha = 0.8f), radius = 4.dp.toPx(), center = Offset(peakX, peakY))
            }

            drawContext.canvas.nativeCanvas.apply {
                val paint = android.graphics.Paint().apply {
                    color = android.graphics.Color.WHITE
                    alpha = (0.6f * 255).toInt()
                    textSize = with(density) { 9.sp.toPx() } 
                    textAlign = android.graphics.Paint.Align.CENTER
                    typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
                }
                drawText(riseLabel, riseX, topPadding - 4.dp.toPx(), paint)
                drawText(setLabel, setX, topPadding - 4.dp.toPx(), paint)
                if (isEphemerisAvailable) {
                    drawText(peakLabel, peakX, height - 4.dp.toPx(), paint)
                }
            }

            if (isEphemerisAvailable) {
                val sunPx = mapX(sunX)
                val sunPy = mapY(sunY)
                val sunRadius = 8.dp.toPx() 
                
                val isDaytime = sunPy < daylineY

                if (isDaytime) {
                    val glowRadius = 20.dp.toPx()

                    drawCircle(
                        brush = Brush.radialGradient(
                            0.0f to Color(0xFFFFE082).copy(alpha = 0.5f),
                            1.0f to Color.Transparent,
                            center = Offset(sunPx, sunPy),
                            radius = glowRadius
                        ),
                        radius = glowRadius,
                        center = Offset(sunPx, sunPy)
                    )

                    drawCircle(
                        brush = Brush.radialGradient(
                            0.0f to Color.White,
                            0.4f to Color(0xFFFFD54F),
                            1.0f to Color.Transparent,
                            center = Offset(sunPx, sunPy),
                            radius = sunRadius * 2.2f
                        ),
                        radius = sunRadius * 2.2f,
                        center = Offset(sunPx, sunPy)
                    )

                    drawCircle(
                        color = Color.White,
                        radius = sunRadius * 0.8f,
                        center = Offset(sunPx, sunPy)
                    )

                    drawCircle(
                        brush = Brush.radialGradient(
                            0.0f to Color.White.copy(alpha = 0.95f),
                            1.0f to Color.Transparent,
                            center = Offset(sunPx - sunRadius * 0.4f, sunPy - sunRadius * 0.4f),
                            radius = sunRadius * 0.7f
                        ),
                        radius = sunRadius * 0.7f,
                        center = Offset(sunPx - sunRadius * 0.4f, sunPy - sunRadius * 0.4f)
                    )
                } else {
                    drawCircle(
                        color = Color.Black.copy(alpha = 0.6f),
                        radius = sunRadius * 0.8f,
                        center = Offset(sunPx, sunPy)
                    )
                    drawCircle(
                        color = Color.White.copy(alpha = 0.8f),
                        radius = sunRadius * 0.8f,
                        center = Offset(sunPx, sunPy),
                        style = Stroke(width = 1.5.dp.toPx())
                    )
                }
            }
        }
    }
}
