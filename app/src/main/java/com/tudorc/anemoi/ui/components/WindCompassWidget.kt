package com.tudorc.anemoi.ui.components

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas as AndroidCanvas
import android.graphics.Color as AndroidColor
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.view.Surface
import android.view.WindowManager
import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.createBitmap
import com.tudorc.anemoi.data.WindUnit
import com.tudorc.anemoi.util.PerformanceProfiler
import java.util.Locale
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
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
    lockDialToNorth: Boolean = false,
    modifier: Modifier = Modifier
) {
    val windSpeedText = formatSpeedWithUnit(windSpeedKmh, unit)
    val headingText = windDirectionDegrees?.let { formatBearing(it) } ?: "--"
    val gustSpeedText = formatSpeedWithUnit(gustSpeedKmh, unit)
    val maxGustText = formatSpeedWithUnit(maxGustKmh, unit)
    val compassRotationState = rememberCompassRotationState(lockDialToNorth = lockDialToNorth)
    val animatedDialRotationDegrees = rememberAnimatedAngleDegrees(
        targetDegrees = if (compassRotationState.hasData) {
            compassRotationState.rotationDegrees
        } else {
            0f
        },
        stiffness = Spring.StiffnessMedium,
        dampingRatio = 0.95f,
        label = "dial-rotation"
    )

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
                dialRotationDegrees = animatedDialRotationDegrees,
                hasSensorData = compassRotationState.hasData,
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

private data class CompassRotationState(
    val rotationDegrees: Float,
    val hasData: Boolean
)

@Composable
private fun rememberCompassRotationState(lockDialToNorth: Boolean): CompassRotationState {
    if (lockDialToNorth) {
        return CompassRotationState(
            rotationDegrees = 0f,
            hasData = true
        )
    }

    val context = LocalContext.current
    var hasCompassData by remember { mutableStateOf(false) }
    var renderedRotationDegrees by remember { mutableFloatStateOf(0f) }

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
            val remappedRotationMatrix = FloatArray(9)
            val orientation = FloatArray(3)
            val accelerometerReading = FloatArray(3)
            val magneticReading = FloatArray(3)
            var hasAccelerometer = false
            var hasMagnetic = false
            var filteredAzimuthDegrees: Float? = null
            var lastPublishedRotationNs = 0L
            val minPublishIntervalNs = 33_000_000L

            fun currentDisplayRotation(): Int {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    return context.display?.rotation ?: Surface.ROTATION_0
                }
                @Suppress("DEPRECATION")
                val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
                @Suppress("DEPRECATION")
                return windowManager?.defaultDisplay?.rotation ?: Surface.ROTATION_0
            }

            fun orientationAxesForRotation(rotation: Int): Pair<Int, Int> {
                return when (rotation) {
                    Surface.ROTATION_90 -> SensorManager.AXIS_Y to SensorManager.AXIS_MINUS_X
                    Surface.ROTATION_180 -> SensorManager.AXIS_MINUS_X to SensorManager.AXIS_MINUS_Y
                    Surface.ROTATION_270 -> SensorManager.AXIS_MINUS_Y to SensorManager.AXIS_X
                    else -> SensorManager.AXIS_X to SensorManager.AXIS_Y
                }
            }

            fun updateFromRotationMatrix(
                matrix: FloatArray,
                sensorTimestampNs: Long
            ) {
                val (xAxis, yAxis) = orientationAxesForRotation(currentDisplayRotation())
                val remapSuccess = SensorManager.remapCoordinateSystem(
                    matrix,
                    xAxis,
                    yAxis,
                    remappedRotationMatrix
                )
                val matrixToUse = if (remapSuccess) remappedRotationMatrix else matrix
                SensorManager.getOrientation(matrixToUse, orientation)
                val azimuthDegrees = Math.toDegrees(orientation[0].toDouble()).toFloat()
                val normalizedAzimuth = normalizeDegrees(azimuthDegrees)
                val previousAzimuth = filteredAzimuthDegrees
                val smoothedAzimuth = if (previousAzimuth == null) {
                    normalizedAzimuth
                } else {
                    val delta = shortestAngleDeltaDegrees(previousAzimuth, normalizedAzimuth)
                    normalizeDegrees(previousAzimuth + (delta * adaptiveCompassAlpha(abs(delta))))
                }

                filteredAzimuthDegrees = smoothedAzimuth
                val nextRotation = normalizeDegrees(-smoothedAzimuth)
                val isFirstSample = !hasCompassData
                val intervalElapsed = (sensorTimestampNs - lastPublishedRotationNs) >= minPublishIntervalNs
                val changedEnough = abs(shortestAngleDeltaDegrees(renderedRotationDegrees, nextRotation)) >= 3f
                if (isFirstSample || intervalElapsed || changedEnough) {
                    hasCompassData = true
                    renderedRotationDegrees = nextRotation
                    lastPublishedRotationNs = sensorTimestampNs
                }
            }

            val listener = object : SensorEventListener {
                override fun onSensorChanged(event: SensorEvent) {
                    PerformanceProfiler.measure(name = "WindCompass/SensorUpdate", category = "sensor") {
                        when (event.sensor.type) {
                            Sensor.TYPE_ROTATION_VECTOR -> {
                                SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
                                updateFromRotationMatrix(rotationMatrix, event.timestamp)
                            }

                            Sensor.TYPE_ACCELEROMETER -> {
                                if (event.values.size >= 3) {
                                    sensorLowPass(
                                        input = event.values,
                                        output = accelerometerReading,
                                        hasOutput = hasAccelerometer,
                                        alpha = 0.2f
                                    )
                                    hasAccelerometer = true
                                }
                            }

                            Sensor.TYPE_MAGNETIC_FIELD -> {
                                if (event.values.size >= 3) {
                                    sensorLowPass(
                                        input = event.values,
                                        output = magneticReading,
                                        hasOutput = hasMagnetic,
                                        alpha = 0.26f
                                    )
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
                            updateFromRotationMatrix(rotationMatrix, event.timestamp)
                        }
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

    return CompassRotationState(
        rotationDegrees = if (hasCompassData) renderedRotationDegrees else 0f,
        hasData = hasCompassData
    )
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
        PerformanceProfiler.measure(name = "WindCompass/MetricText/Draw", category = "widget-draw") {
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
}

@Composable
private fun CompassDial(
    headingDegrees: Double?,
    showArrow: Boolean,
    dialRotationDegrees: Float,
    hasSensorData: Boolean,
    modifier: Modifier = Modifier
) {
    val animatedHeadingDegrees = rememberAnimatedAngleDegrees(
        targetDegrees = headingDegrees?.let { normalizeBearing(it).toFloat() } ?: 0f,
        stiffness = Spring.StiffnessMediumLow,
        dampingRatio = 0.88f,
        label = "heading-arrow"
    )
    val density = LocalDensity.current
    val densityScale = density.density
    val tickCount = 72
    val cardinalStep = tickCount / 4
    val cardinalCutoutRadius = 1

    BoxWithConstraints(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        val widthPx = with(density) { maxWidth.roundToPx().coerceAtLeast(1) }
        val heightPx = with(density) { maxHeight.roundToPx().coerceAtLeast(1) }
        val staticDialBitmap = remember(
            widthPx,
            heightPx,
            densityScale,
            tickCount,
            cardinalStep,
            cardinalCutoutRadius
        ) {
            PerformanceProfiler.measure(name = "WindCompass/Dial/TicksBitmapBuild", category = "widget-cache") {
                buildCompassDialStaticBitmap(
                    widthPx = widthPx,
                    heightPx = heightPx,
                    densityScale = densityScale,
                    tickCount = tickCount,
                    cardinalStep = cardinalStep,
                    cardinalCutoutRadius = cardinalCutoutRadius
                )
            }
        }
        val staticArrowBitmap = remember(widthPx, heightPx, densityScale) {
            PerformanceProfiler.measure(name = "WindCompass/Dial/ArrowBitmapBuild", category = "widget-cache") {
                buildCompassArrowBitmap(
                    widthPx = widthPx,
                    heightPx = heightPx,
                    densityScale = densityScale
                )
            }
        }

        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val radius = (size.minDimension / 2f) - 9.dp.toPx()
            val dialRotation = normalizeDegrees(dialRotationDegrees)
            val sensorAlpha = if (hasSensorData) 1f else 0.58f
            val centerDotRadius = 11.5.dp.toPx()

            PerformanceProfiler.measure(name = "WindCompass/Dial/Ticks", category = "widget-draw") {
                withTransform({
                    rotate(degrees = dialRotation, pivot = center)
                }) {
                    drawImage(
                        image = staticDialBitmap,
                        topLeft = Offset.Zero,
                        alpha = sensorAlpha
                    )
                }
            }

            if (showArrow && headingDegrees != null) {
                PerformanceProfiler.measure(name = "WindCompass/Dial/Arrow", category = "widget-draw") {
                    val bearing = animatedHeadingDegrees + dialRotation
                    withTransform({
                        rotate(degrees = bearing, pivot = center)
                    }) {
                        drawImage(
                            image = staticArrowBitmap,
                            topLeft = Offset.Zero,
                            alpha = sensorAlpha
                        )
                    }
                }
            }

            PerformanceProfiler.measure(name = "WindCompass/Dial/Center", category = "widget-draw") {
                drawCircle(
                    color = Color.White.copy(alpha = 0.16f * sensorAlpha),
                    radius = centerDotRadius,
                    center = center
                )
                drawCircle(
                    color = Color.White.copy(alpha = 0.28f * sensorAlpha),
                    radius = centerDotRadius,
                    center = center,
                    style = Stroke(width = 1.2.dp.toPx())
                )
            }
        }
    }
}

private fun buildCompassDialStaticBitmap(
    widthPx: Int,
    heightPx: Int,
    densityScale: Float,
    tickCount: Int,
    cardinalStep: Int,
    cardinalCutoutRadius: Int
): ImageBitmap {
    val bitmap = createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888)
    val canvas = AndroidCanvas(bitmap)
    val centerX = widthPx / 2f
    val centerY = heightPx / 2f
    val radius = (min(widthPx, heightPx) / 2f) - (9f * densityScale)
    val rimRadius = radius + (7f * densityScale)
    val tickStep = 360f / tickCount.toFloat()

    val baseFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = android.graphics.Color.WHITE
        alpha = (0.14f * 255f).roundToInt().coerceIn(0, 255)
    }
    val rimStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = android.graphics.Color.WHITE
        alpha = (0.52f * 255f).roundToInt().coerceIn(0, 255)
        strokeWidth = 1.3f * densityScale
    }
    val innerStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = android.graphics.Color.WHITE
        alpha = (0.08f * 255f).roundToInt().coerceIn(0, 255)
        strokeWidth = 0.9f * densityScale
    }
    canvas.drawCircle(centerX, centerY, rimRadius, baseFillPaint)
    canvas.drawCircle(centerX, centerY, rimRadius, rimStrokePaint)
    canvas.drawCircle(centerX, centerY, radius + (1.2f * densityScale), innerStrokePaint)

    val tickPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        color = android.graphics.Color.WHITE
    }
    repeat(tickCount) { index ->
        val cardinalIndex = index % cardinalStep == 0
        val nearCardinal = if (cardinalIndex) {
            true
        } else {
            val nearestCardinalIndex = ((index + (cardinalStep / 2)) / cardinalStep) * cardinalStep
            val distance = abs(index - nearestCardinalIndex)
            minOf(distance, tickCount - distance) <= cardinalCutoutRadius
        }
        if (nearCardinal) return@repeat

        val bearing = index * tickStep // 0=N, 90=E, 180=S, 270=W
        val isMajor = index % (tickCount / 12) == 0
        val tickLength = if (isMajor) 8.5f * densityScale else 5.2f * densityScale
        val tickThickness = if (isMajor) 1.35f * densityScale else 1.0f * densityScale
        val tickAlpha = if (isMajor) 0.46f else 0.2f
        val angleRadians = Math.toRadians((bearing - 90f).toDouble())
        val inner = radius - tickLength / 2f
        val outer = radius + tickLength / 2f
        val startX = centerX + inner * cos(angleRadians).toFloat()
        val startY = centerY + inner * sin(angleRadians).toFloat()
        val endX = centerX + outer * cos(angleRadians).toFloat()
        val endY = centerY + outer * sin(angleRadians).toFloat()

        tickPaint.strokeWidth = tickThickness
        tickPaint.alpha = (tickAlpha * 255f).roundToInt().coerceIn(0, 255)
        canvas.drawLine(startX, startY, endX, endY, tickPaint)
    }

    val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        textSize = 10.5f * densityScale
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }
    val labelYOffset = -((labelPaint.fontMetrics.ascent + labelPaint.fontMetrics.descent) / 2f)
    val labelRadius = radius - (2.2f * densityScale)
    fun drawCardinalLabel(text: String, bearing: Float, emphasize: Boolean = false) {
        val angleRadians = Math.toRadians((bearing - 90f).toDouble())
        val pointX = centerX + labelRadius * cos(angleRadians).toFloat()
        val pointY = centerY + labelRadius * kotlin.math.sin(angleRadians).toFloat()
        labelPaint.color = if (emphasize) {
            Color.White.copy(alpha = 0.9f).toArgb()
        } else {
            Color(0xFFD0D5DB).copy(alpha = 0.64f).toArgb()
        }
        canvas.drawText(text, pointX, pointY + labelYOffset, labelPaint)
    }
    drawCardinalLabel("N", 0f, emphasize = true)
    drawCardinalLabel("E", 90f)
    drawCardinalLabel("S", 180f)
    drawCardinalLabel("W", 270f)

    return bitmap.asImageBitmap()
}

private fun buildCompassArrowBitmap(
    widthPx: Int,
    heightPx: Int,
    densityScale: Float
): ImageBitmap {
    val bitmap = createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888)
    val canvas = AndroidCanvas(bitmap)
    val centerX = widthPx / 2f
    val centerY = heightPx / 2f
    val radius = (min(widthPx, heightPx) / 2f) - (9f * densityScale)
    val arrowBoundaryRadius = radius + (5f * densityScale)
    val centerDotRadius = 11.5f * densityScale
    val centerLineGapRadius = centerDotRadius + (1.0f * densityScale)

    val unitX = 0f
    val unitY = -1f
    val perpX = -unitY
    val perpY = unitX

    val headLength = 14f * densityScale
    val headHalfWidth = 6.5f * densityScale
    val bodyStroke = 2.6f * densityScale
    val tailRadius = 5.2f * densityScale
    val tailStroke = 2.0f * densityScale
    val headInset = 1.1f * densityScale

    val tipX = centerX + (arrowBoundaryRadius * unitX)
    val tipY = centerY + (arrowBoundaryRadius * unitY)
    val headBaseCenterX = tipX - (headLength * unitX)
    val headBaseCenterY = tipY - (headLength * unitY)
    val headLeftX = headBaseCenterX + (headHalfWidth * perpX)
    val headLeftY = headBaseCenterY + (headHalfWidth * perpY)
    val headRightX = headBaseCenterX - (headHalfWidth * perpX)
    val headRightY = headBaseCenterY - (headHalfWidth * perpY)

    val tailCenterX = centerX - ((arrowBoundaryRadius - tailRadius) * unitX)
    val tailCenterY = centerY - ((arrowBoundaryRadius - tailRadius) * unitY)
    val bodyStartX = tailCenterX + (tailRadius * unitX)
    val bodyStartY = tailCenterY + (tailRadius * unitY)
    val bodyEndX = headBaseCenterX - (headInset * unitX)
    val bodyEndY = headBaseCenterY - (headInset * unitY)

    val centerGapStartX = centerX - (centerLineGapRadius * unitX)
    val centerGapStartY = centerY - (centerLineGapRadius * unitY)
    val centerGapEndX = centerX + (centerLineGapRadius * unitX)
    val centerGapEndY = centerY + (centerLineGapRadius * unitY)

    fun projection(pointX: Float, pointY: Float): Float {
        return (pointX - centerX) * unitX + (pointY - centerY) * unitY
    }
    val bodyStartT = projection(bodyStartX, bodyStartY)
    val bodyEndT = projection(bodyEndX, bodyEndY)
    val gapStartT = projection(centerGapStartX, centerGapStartY)
    val gapEndT = projection(centerGapEndX, centerGapEndY)

    val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeWidth = bodyStroke
        color = AndroidColor.argb((0.86f * 255f).roundToInt().coerceIn(0, 255), 255, 255, 255)
    }
    if (bodyStartT < gapStartT - 0.5f) {
        canvas.drawLine(bodyStartX, bodyStartY, centerGapStartX, centerGapStartY, bodyPaint)
    }
    if (bodyEndT > gapEndT + 0.5f) {
        canvas.drawLine(centerGapEndX, centerGapEndY, bodyEndX, bodyEndY, bodyPaint)
    }

    val headPath = android.graphics.Path().apply {
        moveTo(tipX, tipY)
        lineTo(headLeftX, headLeftY)
        lineTo(headRightX, headRightY)
        close()
    }
    val headPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = AndroidColor.argb((0.95f * 255f).roundToInt().coerceIn(0, 255), 255, 255, 255)
    }
    canvas.drawPath(headPath, headPaint)

    val tailFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = AndroidColor.argb((0.16f * 255f).roundToInt().coerceIn(0, 255), 255, 255, 255)
    }
    canvas.drawCircle(tailCenterX, tailCenterY, tailRadius * 0.56f, tailFillPaint)

    val tailStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = tailStroke
        color = AndroidColor.argb((0.86f * 255f).roundToInt().coerceIn(0, 255), 255, 255, 255)
    }
    canvas.drawCircle(tailCenterX, tailCenterY, tailRadius, tailStrokePaint)

    return bitmap.asImageBitmap()
}

@Composable
private fun rememberAnimatedAngleDegrees(
    targetDegrees: Float,
    stiffness: Float,
    dampingRatio: Float,
    label: String
): Float {
    val normalizedTarget = normalizeDegrees(targetDegrees)
    var continuousTarget by remember { mutableFloatStateOf(normalizedTarget) }

    LaunchedEffect(normalizedTarget) {
        val currentNormalized = normalizeDegrees(continuousTarget)
        continuousTarget += shortestAngleDeltaDegrees(currentNormalized, normalizedTarget)
    }

    val animatedDegrees by animateFloatAsState(
        targetValue = continuousTarget,
        animationSpec = spring(stiffness = stiffness, dampingRatio = dampingRatio),
        label = label
    )
    return normalizeDegrees(animatedDegrees)
}

private fun sensorLowPass(
    input: FloatArray,
    output: FloatArray,
    hasOutput: Boolean,
    alpha: Float
) {
    if (!hasOutput) {
        input.copyInto(destination = output, endIndex = 3)
        return
    }
    for (index in 0..2) {
        output[index] = output[index] + (alpha * (input[index] - output[index]))
    }
}

private fun adaptiveCompassAlpha(deltaDegrees: Float): Float {
    return when {
        deltaDegrees >= 45f -> 0.34f
        deltaDegrees >= 20f -> 0.24f
        else -> 0.14f
    }
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
