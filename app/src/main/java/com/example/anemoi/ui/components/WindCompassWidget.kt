package com.example.anemoi.ui.components

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.anemoi.data.WindUnit
import java.util.Locale
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

@Composable
fun WindCompassWidget(
    windSpeedKmh: Double?,
    windDirectionDegrees: Double?,
    gustSpeedKmh: Double?,
    maxGustKmh: Double?,
    unit: WindUnit,
    modifier: Modifier = Modifier
) {
    val windSpeedText = formatSpeedWithUnit(windSpeedKmh, unit)
    val headingText = windDirectionDegrees?.let { formatBearing(it) } ?: "--"
    val gustSpeedText = formatSpeedWithUnit(gustSpeedKmh, unit)
    val maxGustText = formatSpeedWithUnit(maxGustKmh, unit)
    val compassDialRotationDegrees = rememberSmoothedCompassDialRotationDegrees()

    BoxWithConstraints(
        modifier = modifier.fillMaxSize()
    ) {
        val density = LocalDensity.current
        val horizontalPadding = 12.dp
        val verticalPadding = 2.dp
        val sideGap = 8.dp
        val minSideColumnWidth = 84.dp
        val lineToDialGap = 12.dp
        val lineHeight = 24.dp
        val pairLineGap = 4.dp
        val dialSizeByHeight = (maxHeight - (verticalPadding * 2)).coerceAtLeast(64.dp)
        val dialSizeByWidth = (
            maxWidth -
                (horizontalPadding * 2) -
                (sideGap * 2) -
                (minSideColumnWidth * 2)
            ).coerceAtLeast(64.dp)
        val dialSize = minOf(dialSizeByHeight, dialSizeByWidth)
        val dialRadiusPx = with(density) { dialSize.toPx() / 2f }
        val widthPx = with(density) { maxWidth.toPx() }
        val heightPx = with(density) { maxHeight.toPx() }
        val centerXPx = widthPx / 2f
        val centerYPx = heightPx / 2f
        val horizontalPaddingPx = with(density) { horizontalPadding.toPx() }
        val verticalPaddingPx = with(density) { verticalPadding.toPx() }
        val lineToDialGapPx = with(density) { lineToDialGap.toPx() }
        val lineHeightPx = with(density) { lineHeight.toPx() }
        val pairLineGapPx = with(density) { pairLineGap.toPx() }
        // Place every text row by its centerline distance from the divider (widget vertical center).
        val nearLineCenterOffsetPx = (dialRadiusPx * 0.29f).coerceAtLeast(lineHeightPx / 2f + 2f)
        val farLineCenterOffsetPx = nearLineCenterOffsetPx + lineHeightPx + pairLineGapPx

        fun circleHalfWidthAt(yCenterPx: Float): Float {
            val dy = abs(yCenterPx - centerYPx)
            if (dy >= dialRadiusPx) return 0f
            return sqrt((dialRadiusPx * dialRadiusPx) - (dy * dy))
        }

        fun clampLineCenter(yCenterPx: Float): Float {
            val minCenter = verticalPaddingPx + (lineHeightPx / 2f)
            val maxCenter = heightPx - verticalPaddingPx - (lineHeightPx / 2f)
            return yCenterPx.coerceIn(minCenter, maxCenter)
        }

        fun leftSpec(yCenterPxRaw: Float, curveSampleYOffsetPx: Float): MetricLineSpec {
            val yCenterPx = clampLineCenter(yCenterPxRaw)
            val yForCurve = (yCenterPx + curveSampleYOffsetPx).coerceIn(0f, heightPx)
            val dialWallX = centerXPx - circleHalfWidthAt(yForCurve)
            val rightEdgePx = dialWallX - lineToDialGapPx
            val widthPxLocal = (rightEdgePx - horizontalPaddingPx).coerceAtLeast(1f)
            return MetricLineSpec(
                xPx = horizontalPaddingPx,
                yTopPx = yCenterPx - (lineHeightPx / 2f),
                widthPx = widthPxLocal,
                heightPx = lineHeightPx
            )
        }

        fun rightSpec(yCenterPxRaw: Float, curveSampleYOffsetPx: Float): MetricLineSpec {
            val yCenterPx = clampLineCenter(yCenterPxRaw)
            val yForCurve = (yCenterPx + curveSampleYOffsetPx).coerceIn(0f, heightPx)
            val dialWallX = centerXPx + circleHalfWidthAt(yForCurve)
            val leftEdgePx = dialWallX + lineToDialGapPx
            val widthPxLocal = (widthPx - horizontalPaddingPx - leftEdgePx).coerceAtLeast(1f)
            return MetricLineSpec(
                xPx = leftEdgePx,
                yTopPx = yCenterPx - (lineHeightPx / 2f),
                widthPx = widthPxLocal,
                heightPx = lineHeightPx
            )
        }

        val topTitleCenterY = centerYPx - nearLineCenterOffsetPx
        val topValueCenterY = centerYPx - farLineCenterOffsetPx
        val bottomTitleCenterY = centerYPx + nearLineCenterOffsetPx
        val bottomValueCenterY = centerYPx + farLineCenterOffsetPx

        val topCurveSampleYOffsetPx = lineHeightPx / 2f
        val bottomCurveSampleYOffsetPx = -lineHeightPx / 2f

        val leftTopTitleSpec = leftSpec(topTitleCenterY, topCurveSampleYOffsetPx)
        val leftTopValueSpec = leftSpec(topValueCenterY, topCurveSampleYOffsetPx)
        val leftBottomTitleSpec = leftSpec(bottomTitleCenterY, bottomCurveSampleYOffsetPx)
        val leftBottomValueSpec = leftSpec(bottomValueCenterY, bottomCurveSampleYOffsetPx)
        val rightTopTitleSpec = rightSpec(topTitleCenterY, topCurveSampleYOffsetPx)
        val rightTopValueSpec = rightSpec(topValueCenterY, topCurveSampleYOffsetPx)
        val rightBottomTitleSpec = rightSpec(bottomTitleCenterY, bottomCurveSampleYOffsetPx)
        val rightBottomValueSpec = rightSpec(bottomValueCenterY, bottomCurveSampleYOffsetPx)

        Box(modifier = Modifier.fillMaxSize()) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val center = Offset(size.width / 2f, size.height / 2f)
                val dialRadius = dialSize.toPx() / 2f
                val linePadding = 20.dp.toPx()
                val lineGap = 12.dp.toPx()
                val strokeWidth = 1.4.dp.toPx()
                val lineColor = Color.White.copy(alpha = 0.26f)

                val leftEndX = center.x - dialRadius - lineGap
                if (leftEndX > linePadding) {
                    drawLine(
                        color = lineColor,
                        start = Offset(linePadding, center.y),
                        end = Offset(leftEndX, center.y),
                        strokeWidth = strokeWidth,
                        cap = StrokeCap.Round
                    )
                }

                val rightStartX = center.x + dialRadius + lineGap
                val rightEndX = size.width - linePadding
                if (rightEndX > rightStartX) {
                    drawLine(
                        color = lineColor,
                        start = Offset(rightStartX, center.y),
                        end = Offset(rightEndX, center.y),
                        strokeWidth = strokeWidth,
                        cap = StrokeCap.Round
                    )
                }
            }

            CompassDial(
                headingDegrees = windDirectionDegrees,
                showArrow = windDirectionDegrees != null,
                dialRotationDegrees = compassDialRotationDegrees,
                modifier = Modifier
                    .size(dialSize)
                    .align(Alignment.Center)
            )

            MetricLine(
                text = "WIND SPEED",
                spec = leftTopTitleSpec,
                isTitle = true,
                alignEnd = true
            )
            MetricLine(
                text = windSpeedText,
                spec = leftTopValueSpec,
                isTitle = false,
                alignEnd = true
            )
            MetricLine(
                text = "HEADING",
                spec = leftBottomTitleSpec,
                isTitle = true,
                alignEnd = true
            )
            MetricLine(
                text = headingText,
                spec = leftBottomValueSpec,
                isTitle = false,
                alignEnd = true
            )

            MetricLine(
                text = "GUST SPEED",
                spec = rightTopTitleSpec,
                isTitle = true,
                alignEnd = false
            )
            MetricLine(
                text = gustSpeedText,
                spec = rightTopValueSpec,
                isTitle = false,
                alignEnd = false
            )
            MetricLine(
                text = "MAX GUST",
                spec = rightBottomTitleSpec,
                isTitle = true,
                alignEnd = false
            )
            MetricLine(
                text = maxGustText,
                spec = rightBottomValueSpec,
                isTitle = false,
                alignEnd = false
            )
        }
    }
}

@Composable
private fun rememberSmoothedCompassDialRotationDegrees(): Float {
    val context = LocalContext.current
    var hasCompassData by remember { mutableStateOf(false) }
    var renderedRotationDegrees by remember { mutableStateOf(0f) }

    DisposableEffect(context) {
        val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        if (sensorManager == null) {
            hasCompassData = false
            renderedRotationDegrees = 0f
            onDispose { }
        } else {
            val rotationVectorSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
            val accelerometerSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
            val magneticSensor = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)

            val rotationMatrix = FloatArray(9)
            val orientation = FloatArray(3)
            val accelerometerReading = FloatArray(3)
            val magneticReading = FloatArray(3)
            var hasAccelerometer = false
            var hasMagnetic = false

            fun updateFromAzimuth(azimuthDegrees: Float) {
                hasCompassData = true
                renderedRotationDegrees = normalizeDegrees(-azimuthDegrees)
            }

            val listener = object : SensorEventListener {
                override fun onSensorChanged(event: SensorEvent) {
                    when (event.sensor.type) {
                        Sensor.TYPE_ROTATION_VECTOR -> {
                            SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
                            SensorManager.getOrientation(rotationMatrix, orientation)
                            val azimuthDegrees = Math.toDegrees(orientation[0].toDouble()).toFloat()
                            updateFromAzimuth(azimuthDegrees)
                        }

                        Sensor.TYPE_ACCELEROMETER -> {
                            if (event.values.size >= 3) {
                                event.values.copyInto(accelerometerReading, endIndex = 3)
                                hasAccelerometer = true
                            }
                        }

                        Sensor.TYPE_MAGNETIC_FIELD -> {
                            if (event.values.size >= 3) {
                                event.values.copyInto(magneticReading, endIndex = 3)
                                hasMagnetic = true
                            }
                        }
                    }

                    if (rotationVectorSensor == null &&
                        hasAccelerometer &&
                        hasMagnetic &&
                        SensorManager.getRotationMatrix(
                            rotationMatrix,
                            null,
                            accelerometerReading,
                            magneticReading
                        )
                    ) {
                        SensorManager.getOrientation(rotationMatrix, orientation)
                        val azimuthDegrees = Math.toDegrees(orientation[0].toDouble()).toFloat()
                        updateFromAzimuth(azimuthDegrees)
                    }
                }

                override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
            }

            if (rotationVectorSensor != null) {
                sensorManager.registerListener(listener, rotationVectorSensor, SensorManager.SENSOR_DELAY_UI)
            } else if (accelerometerSensor != null && magneticSensor != null) {
                sensorManager.registerListener(listener, accelerometerSensor, SensorManager.SENSOR_DELAY_UI)
                sensorManager.registerListener(listener, magneticSensor, SensorManager.SENSOR_DELAY_UI)
            } else {
                hasCompassData = false
                renderedRotationDegrees = 0f
            }

            onDispose {
                sensorManager.unregisterListener(listener)
            }
        }
    }

    return if (hasCompassData) renderedRotationDegrees else 0f
}

private data class MetricLineSpec(
    val xPx: Float,
    val yTopPx: Float,
    val widthPx: Float,
    val heightPx: Float
)

@Composable
private fun MetricLine(
    text: String,
    spec: MetricLineSpec,
    isTitle: Boolean,
    alignEnd: Boolean
) {
    val density = LocalDensity.current
    val textAlign = if (alignEnd) TextAlign.End else TextAlign.Start
    val xDp = with(density) { spec.xPx.toDp() }
    val yDp = with(density) { spec.yTopPx.toDp() }
    val widthDp = with(density) { spec.widthPx.toDp() }
    val heightDp = with(density) { spec.heightPx.toDp() }

    Box(
        modifier = Modifier
            .offset(x = xDp, y = yDp)
            .width(widthDp)
            .height(heightDp),
        contentAlignment = if (alignEnd) Alignment.CenterEnd else Alignment.CenterStart
    ) {
        if (isTitle) {
            MetricLineText(
                text = text,
                color = Color.White.copy(alpha = 0.44f),
                maxFontSize = 10.sp,
                minFontSize = 6.5.sp,
                fontWeight = FontWeight.Medium,
                letterSpacingEm = 0.06f,
                textAlign = textAlign,
                modifier = Modifier.fillMaxWidth()
            )
        } else {
            MetricLineText(
                text = text,
                color = Color.White.copy(alpha = 0.9f),
                maxFontSize = 20.sp,
                minFontSize = 9.5.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = textAlign,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun MetricLineText(
    text: String,
    color: Color,
    maxFontSize: TextUnit,
    minFontSize: TextUnit,
    fontWeight: FontWeight,
    textAlign: TextAlign,
    modifier: Modifier = Modifier,
    letterSpacingEm: Float = 0f
) {
    Canvas(modifier = modifier.fillMaxSize()) {
        val alignEnd = textAlign == TextAlign.End
        val paint = Paint().apply {
            isAntiAlias = true
            this.color = color.toArgb()
            this.textAlign = if (alignEnd) Paint.Align.RIGHT else Paint.Align.LEFT
            typeface = Typeface.create(
                Typeface.DEFAULT,
                if (fontWeight >= FontWeight.SemiBold) Typeface.BOLD else Typeface.NORMAL
            )
            letterSpacing = letterSpacingEm
        }

        val minPx = minFontSize.toPx()
        val stepPx = 0.75.sp.toPx()
        var textSizePx = maxFontSize.toPx()
        paint.textSize = textSizePx

        while (paint.measureText(text) > size.width && textSizePx > minPx) {
            textSizePx = (textSizePx - stepPx).coerceAtLeast(minPx)
            paint.textSize = textSizePx
        }

        val baselineY = (size.height / 2f) - ((paint.fontMetrics.ascent + paint.fontMetrics.descent) / 2f)
        val textX = if (alignEnd) size.width else 0f
        drawContext.canvas.nativeCanvas.drawText(text, textX, baselineY, paint)
    }
}

@Composable
private fun CompassDial(
    headingDegrees: Double?,
    showArrow: Boolean,
    dialRotationDegrees: Float,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val radius = (size.minDimension / 2f) - 9.dp.toPx()
            val dialRotation = normalizeDegrees(dialRotationDegrees)
            val highlightCenters = floatArrayOf(45f, 135f, 225f, 315f) // NE, SE, SW, NW
            val dashCount = 84 // 21 dashes per quarter
            val dashStep = 360f / dashCount
            val dashLength = 10.dp.toPx()
            val baseDashColor = Color(0xFFC9CED5).copy(alpha = 0.24f)
            val highlightDashColor = Color.White.copy(alpha = 0.62f)
            val baseDashThickness = 1.2.dp.toPx()
            val cutoutDashesPerCardinal = 3
            val highlightIndices = highlightCenters.map { centerBearing ->
                (((centerBearing / dashStep).roundToInt() % dashCount) + dashCount) % dashCount
            }.toSet()
            val cutoutIndices = buildSet<Int> {
                fun addCutout(centerBearing: Float, count: Int) {
                    val centerIndex = (((centerBearing / dashStep).roundToInt() % dashCount) + dashCount) % dashCount
                    val startOffset = -((count - 1) / 2)
                    val endOffset = if (count % 2 == 0) (count / 2) else ((count - 1) / 2)
                    for (offset in startOffset..endOffset) {
                        add((centerIndex + offset).floorMod(dashCount))
                    }
                }
                addCutout(centerBearing = 0f, count = cutoutDashesPerCardinal)   // N
                addCutout(centerBearing = 180f, count = cutoutDashesPerCardinal) // S
                addCutout(centerBearing = 90f, count = cutoutDashesPerCardinal)  // E
                addCutout(centerBearing = 270f, count = cutoutDashesPerCardinal) // W
            }

            val labelPaint = Paint().apply {
                isAntiAlias = true
                color = Color(0xFFD0D5DB).copy(alpha = 0.68f).toArgb()
                textAlign = Paint.Align.CENTER
                textSize = 10.5.sp.toPx()
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }
            val labelYOffset = -((labelPaint.fontMetrics.ascent + labelPaint.fontMetrics.descent) / 2f)
            val arrowBoundaryRadius = radius + (dashLength / 2f)
            val arrowBodyColor = Color.White.copy(alpha = 0.84f)
            val arrowHeadColor = Color.White.copy(alpha = 0.92f)
            val arrowTailStrokeColor = arrowBodyColor
            val centerGlassRadius = 13.5.dp.toPx()
            val centerLineGapRadius = centerGlassRadius + 0.9.dp.toPx()

            fun drawDash(
                bearing: Float,
                dashLength: Float,
                dashThickness: Float,
                dashColor: Color
            ) {
                val rotatedBearing = bearing + dialRotation
                val angleRadians = Math.toRadians((rotatedBearing - 90f).toDouble())

                val inner = radius - dashLength / 2f
                val outer = radius + dashLength / 2f
                drawLine(
                    color = dashColor,
                    start = Offset(
                        x = center.x + inner * cos(angleRadians).toFloat(),
                        y = center.y + inner * sin(angleRadians).toFloat()
                    ),
                    end = Offset(
                        x = center.x + outer * cos(angleRadians).toFloat(),
                        y = center.y + outer * sin(angleRadians).toFloat()
                    ),
                    strokeWidth = dashThickness,
                    cap = StrokeCap.Round
                )
            }

            repeat(dashCount) { index ->
                if (cutoutIndices.contains(index)) return@repeat
                val bearing = index * dashStep // 0=N, 90=E, 180=S, 270=W
                val isHighlight = highlightIndices.contains(index)
                drawDash(
                    bearing = bearing,
                    dashLength = dashLength,
                    dashThickness = baseDashThickness,
                    dashColor = if (isHighlight) highlightDashColor else baseDashColor
                )
            }

            if (showArrow && headingDegrees != null) {
                val bearing = normalizeBearing(headingDegrees).toFloat() + dialRotation
                val angleRadians = Math.toRadians((bearing - 90f).toDouble())
                val unitX = cos(angleRadians).toFloat()
                val unitY = sin(angleRadians).toFloat()
                val perpX = -unitY
                val perpY = unitX

                val headLength = 16.dp.toPx()
                val headHalfWidth = 7.8.dp.toPx()
                val bodyStroke = 3.dp.toPx()
                val tailRadius = 6.2.dp.toPx()
                val tailStroke = 2.4.dp.toPx()

                val tip = Offset(
                    x = center.x + arrowBoundaryRadius * unitX,
                    y = center.y + arrowBoundaryRadius * unitY
                )
                val headBaseCenter = Offset(
                    x = tip.x - headLength * unitX,
                    y = tip.y - headLength * unitY
                )
                val headLeft = Offset(
                    x = headBaseCenter.x + headHalfWidth * perpX,
                    y = headBaseCenter.y + headHalfWidth * perpY
                )
                val headRight = Offset(
                    x = headBaseCenter.x - headHalfWidth * perpX,
                    y = headBaseCenter.y - headHalfWidth * perpY
                )

                val tailCenter = Offset(
                    x = center.x - (arrowBoundaryRadius - tailRadius) * unitX,
                    y = center.y - (arrowBoundaryRadius - tailRadius) * unitY
                )
                val bodyStart = Offset(
                    x = tailCenter.x + tailRadius * unitX,
                    y = tailCenter.y + tailRadius * unitY
                )
                val bodyEnd = Offset(
                    x = headBaseCenter.x - 1.2.dp.toPx() * unitX,
                    y = headBaseCenter.y - 1.2.dp.toPx() * unitY
                )

                val centerGapStart = Offset(
                    x = center.x - centerLineGapRadius * unitX,
                    y = center.y - centerLineGapRadius * unitY
                )
                val centerGapEnd = Offset(
                    x = center.x + centerLineGapRadius * unitX,
                    y = center.y + centerLineGapRadius * unitY
                )

                fun projection(point: Offset): Float {
                    return (point.x - center.x) * unitX + (point.y - center.y) * unitY
                }
                val bodyStartT = projection(bodyStart)
                val bodyEndT = projection(bodyEnd)
                val gapStartT = projection(centerGapStart)
                val gapEndT = projection(centerGapEnd)

                if (bodyStartT < gapStartT - 0.5f) {
                    drawLine(
                        color = arrowBodyColor,
                        start = bodyStart,
                        end = centerGapStart,
                        strokeWidth = bodyStroke,
                        cap = StrokeCap.Round
                    )
                }
                if (bodyEndT > gapEndT + 0.5f) {
                    drawLine(
                        color = arrowBodyColor,
                        start = centerGapEnd,
                        end = bodyEnd,
                        strokeWidth = bodyStroke,
                        cap = StrokeCap.Round
                    )
                }

                drawPath(
                    path = androidx.compose.ui.graphics.Path().apply {
                        moveTo(tip.x, tip.y)
                        lineTo(headLeft.x, headLeft.y)
                        lineTo(headRight.x, headRight.y)
                        close()
                    },
                    color = arrowHeadColor
                )

                drawCircle(
                    color = arrowTailStrokeColor,
                    radius = tailRadius,
                    center = tailCenter,
                    style = Stroke(width = tailStroke)
                )
            }

            drawCircle(
                color = Color.White.copy(alpha = 0.16f),
                radius = centerGlassRadius,
                center = center
            )
            drawCircle(
                brush = Brush.linearGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.32f),
                        Color.White.copy(alpha = 0.08f)
                    ),
                    start = Offset(center.x - centerGlassRadius, center.y - centerGlassRadius),
                    end = Offset(center.x + centerGlassRadius, center.y + centerGlassRadius)
                ),
                radius = centerGlassRadius,
                center = center,
                style = Stroke(width = 1.6.dp.toPx())
            )
            val nativeCanvas = drawContext.canvas.nativeCanvas

            fun drawCardinalLabel(text: String, point: Offset) {
                nativeCanvas.drawText(text, point.x, point.y + labelYOffset, labelPaint)
            }

            fun labelPoint(bearing: Float): Offset {
                val angleRadians = Math.toRadians((bearing + dialRotation - 90f).toDouble())
                return Offset(
                    x = center.x + radius * cos(angleRadians).toFloat(),
                    y = center.y + radius * sin(angleRadians).toFloat()
                )
            }

            drawCardinalLabel("N", labelPoint(0f))
            drawCardinalLabel("E", labelPoint(90f))
            drawCardinalLabel("S", labelPoint(180f))
            drawCardinalLabel("W", labelPoint(270f))
        }
    }
}

private fun Int.floorMod(modulus: Int): Int {
    if (modulus <= 0) return this
    val rem = this % modulus
    return if (rem < 0) rem + modulus else rem
}

private fun convertWindSpeed(kmh: Double, unit: WindUnit): Double {
    return when (unit) {
        WindUnit.KMH -> kmh
        WindUnit.MPH -> kmh * 0.621371
        WindUnit.MS -> kmh / 3.6
        WindUnit.KNOTS -> kmh * 0.539957
    }
}

private fun formatBearing(directionDegrees: Double): String {
    val normalized = normalizeBearing(directionDegrees)
    val cardinal = directionToCardinal(normalized)
    return "$cardinal ${normalized.roundToInt()}°"
}

private fun formatSpeedWithUnit(speedKmh: Double?, unit: WindUnit): String {
    if (speedKmh == null) {
        return "-- ${unit.label}"
    }

    val converted = convertWindSpeed(speedKmh, unit)
    val number = when (unit) {
        WindUnit.MS -> String.format(Locale.getDefault(), "%.1f", converted)
        else -> converted.roundToInt().toString()
    }
    return "$number ${unit.label}"
}

private fun normalizeBearing(directionDegrees: Double): Double {
    return ((directionDegrees % 360.0) + 360.0) % 360.0
}

private fun directionToCardinal(directionDegrees: Double): String {
    val points = arrayOf("N", "NE", "E", "SE", "S", "SW", "W", "NW")
    val index = ((directionDegrees + 22.5) / 45.0).toInt() % points.size
    return points[index]
}

private fun normalizeDegrees(angle: Float): Float {
    return ((angle % 360f) + 360f) % 360f
}

private fun shortestAngleDeltaDegrees(from: Float, to: Float): Float {
    return ((to - from + 540f) % 360f) - 180f
}
