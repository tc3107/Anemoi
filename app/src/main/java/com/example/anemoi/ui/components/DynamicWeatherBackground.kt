package com.example.anemoi.ui.components

import android.graphics.Bitmap
import android.graphics.Canvas as AndroidCanvas
import android.graphics.Color as AndroidColor
import android.graphics.Paint as AndroidPaint
import android.graphics.RectF
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.anemoi.util.PerformanceProfiler
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

@Composable
fun DynamicWeatherBackground(
    weatherCode: Int?,
    weatherTimeIso: String?,
    windSpeedKmh: Double = 0.0,
    pageKey: Any? = null,
    modifier: Modifier = Modifier
) {
    val style = remember(weatherCode, weatherTimeIso) {
        resolveBackgroundStyle(
            weatherCode = weatherCode,
            weatherTimeIso = weatherTimeIso
        )
    }

    Box(modifier = modifier.fillMaxSize()) {
        AnimatedGradientSky(style = style)
        SunRayLayer(style = style)
        AuroraBlobLayer(style = style)
        WeatherParticleLayer(
            mode = style.particleMode,
            windSpeedKmh = windSpeedKmh,
            rainIntensity = style.rainIntensity,
            pageKey = pageKey
        )
        NoiseLayer(alpha = style.noiseAlpha)
        if (style.overallDimAlpha > 0f) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = style.overallDimAlpha))
            )
        }
    }
}

@Composable
private fun SunRayLayer(style: BackgroundStyle) {
    val rays = style.sunRays ?: return
    val transition = rememberInfiniteTransition(label = "sun-rays")
    val pulse = transition.animateFloat(
        initialValue = 0.9f,
        targetValue = 1.04f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = rays.pulseDurationMs, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "sun-rays-pulse"
    )
    val sway = transition.animateFloat(
        initialValue = -1.4f,
        targetValue = 1.4f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = rays.swayDurationMs, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "sun-rays-sway"
    )

    Canvas(modifier = Modifier.fillMaxSize()) {
        PerformanceProfiler.measure(name = "Background/SunRays/Draw", category = "background-draw") {
            val source = Offset(
                x = size.width * rays.sourceXFraction,
                y = size.height * rays.sourceYFraction
            )
            val coreAlpha = (rays.coreAlpha * pulse.value).coerceIn(0f, 1f)
            val glowRadius = size.minDimension * rays.glowRadiusFraction
            drawRect(
                brush = Brush.radialGradient(
                    colors = listOf(
                        rays.color.copy(alpha = coreAlpha * 0.46f),
                        Color.Transparent
                    ),
                    center = source,
                    radius = glowRadius
                )
            )
            drawRect(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color.White.copy(alpha = coreAlpha * 0.26f),
                        Color.Transparent
                    ),
                    center = source,
                    radius = glowRadius * 1.45f
                )
            )

            val fanStep = if (rays.rayCount <= 1) 0f else rays.fanSweepDegrees / (rays.rayCount - 1)
            repeat(rays.rayCount) { index ->
                val angleDeg = rays.fanStartDegrees + fanStep * index + sway.value
                val angleRad = angleDeg * PI.toFloat() / 180f
                val end = Offset(
                    x = source.x + cos(angleRad) * size.minDimension * rays.rayLengthFraction,
                    y = source.y + sin(angleRad) * size.minDimension * rays.rayLengthFraction
                )
                val widthScale = 1f - abs(index - (rays.rayCount - 1) * 0.5f) / rays.rayCount.toFloat()
                val rayWidth = size.minDimension * rays.rayWidthFraction * (0.9f + widthScale * 0.8f)
                drawLine(
                    brush = Brush.linearGradient(
                        colors = listOf(
                            rays.color.copy(alpha = (rays.rayAlpha * pulse.value).coerceIn(0f, 1f)),
                            rays.color.copy(alpha = (rays.rayAlpha * 0.35f * pulse.value).coerceIn(0f, 1f)),
                            Color.Transparent
                        ),
                        start = source,
                        end = end
                    ),
                    start = source,
                    end = end,
                    strokeWidth = rayWidth,
                    cap = StrokeCap.Round
                )
            }
        }
    }
}

@Composable
private fun AnimatedGradientSky(style: BackgroundStyle) {
    val transition = rememberInfiniteTransition(label = "weather-gradient")
    val shift = transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = style.gradientDurationMs, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "weather-gradient-shift"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .drawWithCache {
                onDrawBehind {
                    PerformanceProfiler.measure(name = "Background/Gradient/Draw", category = "background-draw") {
                        val dx = (shift.value - 0.5f) * size.width * 0.36f
                        val dy = sin(shift.value * (2f * PI.toFloat())) * size.height * 0.08f
                        drawRect(
                            brush = Brush.linearGradient(
                                colors = style.gradientColors,
                                start = Offset(dx, dy),
                                end = Offset(size.width + dx, size.height + dy)
                            )
                        )

                        style.glowColor?.let { glow ->
                            drawRect(
                                brush = Brush.radialGradient(
                                    colors = listOf(glow, Color.Transparent),
                                    center = Offset(size.width * 0.78f, size.height * 0.12f),
                                    radius = size.minDimension * 0.92f
                                )
                            )
                        }
                    }
                }
            }
    )
}

@Composable
private fun AuroraBlobLayer(style: BackgroundStyle) {
    Box(modifier = Modifier.fillMaxSize()) {
        style.blobs.forEachIndexed { index, blob ->
            FloatingBlob(blob = blob, key = index)
        }
    }
}

@Composable
private fun FloatingBlob(blob: BlobSpec, key: Int) {
    val transition = rememberInfiniteTransition(label = "blob-$key")
    val progress = transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = blob.durationMs, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "blob-progress-$key"
    )

    val wave = ((sin(progress.value * (2f * PI.toFloat())) + 1f) * 0.5f).coerceIn(0f, 1f)

    Canvas(modifier = Modifier.fillMaxSize()) {
        PerformanceProfiler.measure(name = "Background/Blob/Draw", category = "background-draw") {
            val radiusPx = blob.size.toPx() * 0.68f
            val center = Offset(
                x = blob.startX.toPx() + (blob.size.toPx() * 0.5f) + blob.driftX.toPx() * wave,
                y = blob.startY.toPx() + (blob.size.toPx() * 0.5f) + blob.driftY.toPx() * wave
            )
            drawRect(
                brush = Brush.radialGradient(
                    colors = listOf(
                        blob.color.copy(alpha = blob.alpha),
                        blob.color.copy(alpha = blob.alpha * 0.52f),
                        Color.Transparent
                    ),
                    center = center,
                    radius = radiusPx
                )
            )
        }
    }
}

@Composable
private fun WeatherParticleLayer(
    mode: ParticleMode,
    windSpeedKmh: Double,
    rainIntensity: Float,
    pageKey: Any?
) {
    if (mode == ParticleMode.NONE) return
    if (mode == ParticleMode.RAIN) {
        LiveRainParticleLayer(
            windSpeedKmh = windSpeedKmh,
            rainIntensity = rainIntensity,
            pageKey = pageKey
        )
        return
    }

    val seed = remember(mode, pageKey) {
        when (mode) {
            ParticleMode.STARS -> Random.nextInt()
            ParticleMode.SNOW -> snowParticleSeed
            ParticleMode.NONE -> noParticleSeed
            ParticleMode.RAIN -> rainParticleSeed
        }
    }
    val particles = remember(mode, seed) { buildParticles(mode, seed) }
    val transition = rememberInfiniteTransition(label = "particle-layer")
    val progress = transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = when (mode) {
                ParticleMode.RAIN -> 5600
                ParticleMode.SNOW -> 12000
                ParticleMode.STARS -> 9000
                ParticleMode.NONE -> 9000
            }, easing = LinearEasing)
        ),
        label = "particle-progress"
    )

    Canvas(modifier = Modifier.fillMaxSize()) {
        PerformanceProfiler.measure(name = "Background/Particles/Draw", category = "background-draw") {
            val width = size.width
            val height = size.height
            val cycle = progress.value

            particles.forEach { particle ->
                when (mode) {
                    ParticleMode.SNOW -> {
                        val y = ((particle.seedY + cycle * particle.speed) % 1f) * height
                        val sway = sin(((cycle * 1.8f + particle.phase) * 2f * PI).toFloat()) * particle.drift
                        val x = ((particle.seedX + sway).coerceIn(0f, 1f)) * width
                        val alpha = (0.16f + particle.alpha * 0.5f).coerceIn(0f, 1f)
                        drawCircle(
                            color = Color.White.copy(alpha = alpha),
                            radius = 1.8f + particle.size * 4.2f,
                            center = Offset(x, y)
                        )
                    }

                    ParticleMode.STARS -> {
                        val x = particle.seedX * width
                        val y = particle.seedY * height * 0.72f
                        val twinkle = abs(sin(((cycle * 2.2f + particle.phase) * 2f * PI).toFloat()))
                        val alpha = (0.14f + twinkle * 0.55f).coerceIn(0f, 1f)
                        drawCircle(
                            color = Color.White.copy(alpha = alpha),
                            radius = 1.15f + particle.size * 2.6f,
                            center = Offset(x, y)
                        )
                    }

                    ParticleMode.NONE -> Unit
                    ParticleMode.RAIN -> Unit
                }
            }
        }
    }
}

@Composable
private fun LiveRainParticleLayer(
    windSpeedKmh: Double,
    rainIntensity: Float,
    pageKey: Any?
) {
    val simulationSeed = remember(pageKey) { Random.nextInt() }
    val simulation = remember(simulationSeed, rainIntensity) {
        createLiveRainSimulation(
            seed = simulationSeed,
            rainIntensity = rainIntensity
        )
    }
    val latestWindSpeedKmh by rememberUpdatedState(windSpeedKmh)
    var frameTick by remember { mutableLongStateOf(0L) }

    LaunchedEffect(simulation) {
        var lastFrameNs = 0L
        while (true) {
            withFrameNanos { nowNs ->
                if (lastFrameNs == 0L) {
                    lastFrameNs = nowNs
                    return@withFrameNanos
                }
                val dtSec = ((nowNs - lastFrameNs).toFloat() / 1_000_000_000f)
                    .coerceIn(1f / 240f, 1f / 20f)
                lastFrameNs = nowNs

                PerformanceProfiler.measure(name = "Background/Rain/SimStep", category = "background-sim") {
                    stepLiveRainSimulation(
                        simulation = simulation,
                        dtSec = dtSec,
                        windSpeedKmh = latestWindSpeedKmh
                    )
                }
                frameTick++
            }
        }
    }

    Canvas(modifier = Modifier.fillMaxSize()) {
        frameTick // Keep this draw scope subscribed to simulation frame updates.
        PerformanceProfiler.measure(name = "Background/Rain/Draw", category = "background-draw") {
            val width = size.width
            val height = size.height

            simulation.drops.forEach { drop ->
                val x = drop.xNorm * width
                val y = drop.yNorm * height
                val velocityX = drop.vxNormPerSec * width
                val velocityY = drop.vyNormPerSec * height
                val velocityMagnitude = sqrt(
                    velocityX * velocityX + velocityY * velocityY
                ).coerceAtLeast(1f)
                val dirX = velocityX / velocityMagnitude
                val dirY = velocityY / velocityMagnitude

                val speedFactor = (velocityMagnitude / (height * 2.4f)).coerceIn(0.4f, 1.8f)
                val streakLength = (drop.baseLengthNorm * height * speedFactor)
                    .coerceIn(8f, height * 0.28f)
                val head = Offset(x, y)
                val tail = Offset(
                    x = x - dirX * streakLength,
                    y = y - dirY * streakLength
                )
                val mid = Offset(
                    x = (head.x + tail.x) * 0.5f,
                    y = (head.y + tail.y) * 0.5f
                )

                val pulse = 0.85f + 0.15f * sin(simulation.timeSec * 4.5f + drop.shimmerPhase)
                val alpha = (drop.alpha * pulse).coerceIn(0.06f, 0.92f)

                drawLine(
                    color = Color(0xFFB6D5FF).copy(alpha = alpha * 0.24f),
                    start = tail,
                    end = head,
                    strokeWidth = drop.thickness * 2f,
                    cap = StrokeCap.Round
                )
                drawLine(
                    color = Color.White.copy(alpha = alpha),
                    start = tail,
                    end = head,
                    strokeWidth = drop.thickness,
                    cap = StrokeCap.Round
                )
                drawLine(
                    color = Color(0xFFEAF5FF).copy(alpha = (alpha * 1.06f).coerceAtMost(1f)),
                    start = mid,
                    end = head,
                    strokeWidth = (drop.thickness * 0.62f).coerceAtLeast(0.7f),
                    cap = StrokeCap.Round
                )
            }
        }
    }
}

@Composable
private fun NoiseLayer(alpha: Float) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .drawWithCache {
                val alphaScale = (alpha / 0.06f).coerceIn(0.7f, 1.25f)
                val densityFactor = ((size.width * size.height / 16_000f) * alphaScale)
                    .toInt()
                    .coerceIn(110, 420)
                val densityBucket = noiseDensityBucket(densityFactor)
                val random = Random(9127)
                val bucketBands = PerformanceProfiler.measure(
                    name = "Background/Noise/Cache/Buckets",
                    category = "background-cache"
                ) {
                    val alphaBandCount = 6
                    val estimatedBandCapacity = (densityFactor / alphaBandCount).coerceAtLeast(4)
                    val tinyBands = List(alphaBandCount) { ArrayList<Offset>(estimatedBandCapacity) }
                    val mediumBands = List(alphaBandCount) { ArrayList<Offset>(estimatedBandCapacity) }
                    val largeBands = List(alphaBandCount) { ArrayList<Offset>(estimatedBandCapacity) }

                    repeat(densityFactor) {
                        val point = Offset(
                            x = random.nextFloat() * size.width,
                            y = random.nextFloat() * size.height
                        )
                        val radius = 0.4f + random.nextFloat() * 1.1f
                        val rawAlpha = 0.2f + random.nextFloat() * 0.8f
                        val alphaBand = (((rawAlpha - 0.2f) / 0.8f) * (alphaBandCount - 1))
                            .roundToInt()
                            .coerceIn(0, alphaBandCount - 1)

                        when {
                            radius <= 0.75f -> tinyBands[alphaBand] += point
                            radius <= 1.1f -> mediumBands[alphaBand] += point
                            else -> largeBands[alphaBand] += point
                        }
                    }

                    fun buildBuckets(
                        bands: List<List<Offset>>,
                        strokeWidth: Float
                    ): List<NoiseDrawBucket> {
                        return bands.mapIndexedNotNull { index, points ->
                            if (points.isEmpty()) return@mapIndexedNotNull null
                            val normalizedAlpha = 0.2f + ((index + 0.5f) / alphaBandCount.toFloat()) * 0.8f
                            NoiseDrawBucket(
                                points = points,
                                strokeWidth = strokeWidth,
                                color = Color.White.copy(alpha = (normalizedAlpha * alpha).coerceIn(0f, 1f))
                            )
                        }
                    }

                    NoiseBucketBands(
                        tiny = buildBuckets(tinyBands, strokeWidth = 1.2f),
                        medium = buildBuckets(mediumBands, strokeWidth = 1.95f),
                        large = buildBuckets(largeBands, strokeWidth = 2.8f),
                        tinyPointCount = tinyBands.sumOf { it.size },
                        mediumPointCount = mediumBands.sumOf { it.size },
                        largePointCount = largeBands.sumOf { it.size }
                    )
                }
                val noiseBitmap = PerformanceProfiler.measure(
                    name = "Background/Noise/Cache/Bitmap",
                    category = "background-cache"
                ) {
                    val textureScale = 0.55f
                    val textureWidth = (size.width * textureScale).roundToInt().coerceAtLeast(96)
                    val textureHeight = (size.height * textureScale).roundToInt().coerceAtLeast(96)
                    buildNoiseBitmap(
                        widthPx = textureWidth,
                        heightPx = textureHeight,
                        sourceWidthPx = size.width,
                        sourceHeightPx = size.height,
                        buckets = bucketBands.tiny + bucketBands.medium + bucketBands.large
                    )
                }
                val bitmapPaint = AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG).apply {
                    isFilterBitmap = false
                    isDither = true
                }
                val destinationRect = RectF(0f, 0f, size.width, size.height)

                onDrawBehind {
                    val drawStartNs = System.nanoTime()
                    PerformanceProfiler.measure(
                        name = "Background/Noise/Draw/$densityBucket/Composite",
                        category = "background-draw"
                    ) {
                        drawContext.canvas.nativeCanvas.drawBitmap(
                            noiseBitmap,
                            null,
                            destinationRect,
                            bitmapPaint
                        )
                    }
                    val drawDurationNs = System.nanoTime() - drawStartNs
                    val safePointCount = densityFactor.coerceAtLeast(1)
                    PerformanceProfiler.record(
                        name = "Background/Noise/Metric/PerPointNs",
                        durationNs = drawDurationNs / safePointCount.toLong(),
                        category = "background-metric"
                    )
                    PerformanceProfiler.record(
                        name = "Background/Noise/Metric/FramePointCount",
                        durationNs = safePointCount.toLong(),
                        category = "background-metric"
                    )
                    PerformanceProfiler.record(
                        name = "Background/Noise/Metric/TinyPointCount",
                        durationNs = bucketBands.tinyPointCount.toLong(),
                        category = "background-metric"
                    )
                    PerformanceProfiler.record(
                        name = "Background/Noise/Metric/MediumPointCount",
                        durationNs = bucketBands.mediumPointCount.toLong(),
                        category = "background-metric"
                    )
                    PerformanceProfiler.record(
                        name = "Background/Noise/Metric/LargePointCount",
                        durationNs = bucketBands.largePointCount.toLong(),
                        category = "background-metric"
                    )
                    PerformanceProfiler.record(
                        name = "Background/Noise/Metric/BatchCount",
                        durationNs = 1L,
                        category = "background-metric"
                    )
                }
            }
    )
}

private data class NoiseDrawBucket(
    val points: List<Offset>,
    val strokeWidth: Float,
    val color: Color
)

private data class NoiseBucketBands(
    val tiny: List<NoiseDrawBucket>,
    val medium: List<NoiseDrawBucket>,
    val large: List<NoiseDrawBucket>,
    val tinyPointCount: Int,
    val mediumPointCount: Int,
    val largePointCount: Int
)

private fun buildNoiseBitmap(
    widthPx: Int,
    heightPx: Int,
    sourceWidthPx: Float,
    sourceHeightPx: Float,
    buckets: List<NoiseDrawBucket>
): Bitmap {
    val bitmap = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888)
    val canvas = AndroidCanvas(bitmap)
    val paint = AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG).apply {
        style = AndroidPaint.Style.FILL
    }
    val scaleX = widthPx.toFloat() / sourceWidthPx.coerceAtLeast(1f)
    val scaleY = heightPx.toFloat() / sourceHeightPx.coerceAtLeast(1f)

    buckets.forEach { bucket ->
        if (bucket.points.isEmpty()) return@forEach
        val alphaInt = (bucket.color.alpha * 255f).roundToInt().coerceIn(0, 255)
        if (alphaInt <= 0) return@forEach
        paint.color = AndroidColor.argb(alphaInt, 255, 255, 255)
        val radiusPx = (bucket.strokeWidth * 0.52f).coerceAtLeast(0.55f)
        bucket.points.forEach { point ->
            canvas.drawCircle(
                point.x * scaleX,
                point.y * scaleY,
                radiusPx,
                paint
            )
        }
    }
    return bitmap
}

private fun noiseDensityBucket(pointCount: Int): String {
    return when {
        pointCount >= 360 -> "VeryHigh"
        pointCount >= 280 -> "High"
        pointCount >= 180 -> "Medium"
        else -> "Low"
    }
}

private fun buildParticles(mode: ParticleMode, seed: Int): List<ParticleSpec> {
    val count = when (mode) {
        ParticleMode.RAIN -> 54
        ParticleMode.SNOW -> 52
        ParticleMode.STARS -> 72
        ParticleMode.NONE -> 0
    }

    val random = Random(seed)
    return List(count) {
        ParticleSpec(
            seedX = random.nextFloat(),
            seedY = random.nextFloat(),
            speed = when (mode) {
                ParticleMode.RAIN -> 0.85f + random.nextFloat() * 1.2f
                ParticleMode.SNOW -> 0.18f + random.nextFloat() * 0.3f
                ParticleMode.STARS -> 0f
                ParticleMode.NONE -> 0f
            },
            size = random.nextFloat(),
            drift = when (mode) {
                ParticleMode.RAIN -> 0.02f + random.nextFloat() * 0.02f
                ParticleMode.SNOW -> 0.03f + random.nextFloat() * 0.06f
                ParticleMode.STARS -> 0f
                ParticleMode.NONE -> 0f
            },
            phase = random.nextFloat(),
            alpha = random.nextFloat()
        )
    }
}

private fun createLiveRainSimulation(
    seed: Int,
    rainIntensity: Float
): LiveRainSimulation {
    val random = Random(seed)
    val particleCount = (liveRainParticleCount * rainIntensity).roundToInt()
        .coerceIn(24, 260)
    val drops = MutableList(particleCount) {
        randomLiveRainDrop(random)
    }
    return LiveRainSimulation(
        random = random,
        drops = drops,
        timeSec = random.nextFloat() * 25f
    )
}

private fun stepLiveRainSimulation(
    simulation: LiveRainSimulation,
    dtSec: Float,
    windSpeedKmh: Double
) {
    simulation.timeSec += dtSec
    val timeSec = simulation.timeSec
    val windNormPerSecond = windDriftNormPerSecond(windSpeedKmh)
    val gust = (
        sin(timeSec * 0.42f * twoPiF) * 0.09f +
            sin(timeSec * 0.19f * twoPiF + 1.7f) * 0.06f +
            sin(timeSec * 0.93f * twoPiF + 0.35f) * 0.03f
        )

    simulation.drops.indices.forEach { index ->
        val drop = simulation.drops[index]
        val turbulence = sin(timeSec * drop.turbulenceFreqHz * twoPiF + drop.turbulencePhase) *
            drop.turbulenceAmp
        val targetVx = windNormPerSecond * drop.windInfluence +
            gust * (0.32f + drop.depth * 0.52f) +
            turbulence
        val response = (dtSec * drop.horizontalResponse).coerceIn(0f, 1f)
        drop.vxNormPerSec += (targetVx - drop.vxNormPerSec) * response

        val fallAccel = drop.fallAccelerationNormPerSec2 * (1f + abs(gust) * 0.1f)
        drop.vyNormPerSec = (drop.vyNormPerSec + fallAccel * dtSec)
            .coerceIn(drop.baseFallNormPerSec * 0.25f, drop.terminalFallNormPerSec)
        drop.xNorm += drop.vxNormPerSec * dtSec
        drop.yNorm += drop.vyNormPerSec * dtSec

        if (
            drop.yNorm > (1f + rainSimDespawnMarginBottom) ||
            drop.xNorm < -rainSimDespawnMarginX ||
            drop.xNorm > (1f + rainSimDespawnMarginX)
        ) {
            simulation.drops[index] = respawnLiveRainDrop(
                random = simulation.random,
                windNormPerSecond = windNormPerSecond
            )
        }
    }
}

private fun respawnLiveRainDrop(
    random: Random,
    windNormPerSecond: Float
): LiveRainDrop {
    val drop = randomLiveRainDrop(random)
    val sideSpawnChance = 0.32f + abs(windNormPerSecond).coerceAtMost(0.5f) * 0.35f
    val spawnFromSide = random.nextFloat() < sideSpawnChance
    val windRight = windNormPerSecond >= 0f
    if (spawnFromSide) {
        drop.xNorm = if (windRight) {
            -rainSimSpawnMarginX * (0.6f + random.nextFloat() * 0.4f)
        } else {
            1f + rainSimSpawnMarginX * (0.6f + random.nextFloat() * 0.4f)
        }
        drop.yNorm = random.nextFloat() * (1f + rainSimSpawnMarginBottom * 0.5f) -
            rainSimSpawnMarginTop
    } else {
        drop.xNorm = random.nextFloat() * (1f + rainSimSpawnMarginX * 2f) - rainSimSpawnMarginX
        drop.yNorm = -random.nextFloat() * rainSimSpawnMarginTop
    }
    return drop
}

private fun randomLiveRainDrop(random: Random): LiveRainDrop {
    val depth = random.nextFloat().let { it * it }
    return LiveRainDrop(
        xNorm = random.nextFloat() * (1f + rainSimSpawnMarginX * 2f) - rainSimSpawnMarginX,
        yNorm = -random.nextFloat() * rainSimSpawnMarginTop,
        vxNormPerSec = 0f,
        vyNormPerSec = 0.28f + depth * 0.30f + random.nextFloat() * 0.14f,
        depth = depth,
        baseFallNormPerSec = 0.78f + depth * 1.1f + random.nextFloat() * 0.3f,
        fallAccelerationNormPerSec2 = 1.9f + depth * 2.7f + random.nextFloat() * 0.9f,
        terminalFallNormPerSec = 1.6f + depth * 2.05f + random.nextFloat() * 0.4f,
        windInfluence = 0.62f + depth * 1.72f + random.nextFloat() * 0.36f,
        turbulenceAmp = 0.008f + (1f - depth) * 0.012f + random.nextFloat() * 0.006f,
        turbulenceFreqHz = 0.35f + depth * 1.6f + random.nextFloat() * 0.9f,
        turbulencePhase = random.nextFloat() * twoPiF,
        horizontalResponse = 4.5f + depth * 3.4f + random.nextFloat() * 1.1f,
        thickness = 0.72f + depth * 2.35f + random.nextFloat() * 0.65f,
        baseLengthNorm = 0.016f + depth * 0.09f + random.nextFloat() * 0.02f,
        alpha = 0.12f + depth * 0.5f + random.nextFloat() * 0.14f,
        shimmerPhase = random.nextFloat() * twoPiF
    )
}

private fun windDriftNormPerSecond(windSpeedKmh: Double): Float {
    val clampedSpeed = windSpeedKmh.coerceIn(0.0, 100.0)
    val normalized = (clampedSpeed / 100.0).coerceIn(0.0, 1.0)
    val baseline = normalized * normalized * 0.58 + normalized * 0.06
    return (baseline * 3.0).toFloat()
}

private const val rainParticleSeed = 1183
private const val snowParticleSeed = 4079
private const val noParticleSeed = 31
private const val liveRainParticleCount = 108
private const val twoPiF = (2f * PI.toFloat())
private const val rainSimSpawnMarginX = 0.7f
private const val rainSimSpawnMarginTop = 1.45f
private const val rainSimSpawnMarginBottom = 0.7f
private const val rainSimDespawnMarginX = 1.0f
private const val rainSimDespawnMarginBottom = 1.0f

private fun resolveBackgroundStyle(weatherCode: Int?, weatherTimeIso: String?): BackgroundStyle {
    val isNight = isNightFromTime(weatherTimeIso)
    val family = mapWeatherFamily(weatherCode)

    return when {
        isNight && (family == WeatherFamily.CLEAR || family == WeatherFamily.CLOUDY) -> nightClearStyle()
        isNight && family == WeatherFamily.SNOW -> nightSnowStyle()
        isNight && family == WeatherFamily.STORM -> nightStormStyle()
        isNight && family == WeatherFamily.RAIN -> nightRainStyle()
        family == WeatherFamily.CLEAR -> sunnyStyle()
        family == WeatherFamily.SNOW -> snowyStyle()
        family == WeatherFamily.STORM -> stormStyle()
        family == WeatherFamily.RAIN -> rainyStyle()
        else -> cloudyStyle()
    }
}

private fun isNightFromTime(weatherTimeIso: String?): Boolean {
    if (weatherTimeIso.isNullOrBlank()) return false
    val index = weatherTimeIso.indexOf('T')
    if (index == -1 || index + 3 > weatherTimeIso.length) return false
    val hour = weatherTimeIso.substring(index + 1, index + 3).toIntOrNull() ?: return false
    return hour !in 6..18
}

private fun mapWeatherFamily(weatherCode: Int?): WeatherFamily {
    return when (weatherCode) {
        95, 96, 99 -> WeatherFamily.STORM

        71, 73, 75, 77, 85, 86 -> WeatherFamily.SNOW

        51, 53, 55, 56, 57,
        61, 63, 65, 66, 67,
        80, 81, 82 -> WeatherFamily.RAIN

        0, 1 -> WeatherFamily.CLEAR

        2, 3, 45, 48 -> WeatherFamily.CLOUDY

        else -> WeatherFamily.CLOUDY
    }
}

private fun sunnyStyle() = BackgroundStyle(
    gradientColors = listOf(
        Color(0xFF163B74),
        Color(0xFF2E5C9A),
        Color(0xFF4A78B2),
        Color(0xFFE3924C)
    ),
    glowColor = Color(0xFFFFB56A).copy(alpha = 0.1f),
    particleMode = ParticleMode.NONE,
    noiseAlpha = 0.05f,
    gradientDurationMs = 17000,
    blobs = listOf(
        BlobSpec(
            color = Color(0xFFB1C7E4),
            size = 360.dp,
            alpha = 0.31f,
            startX = (-110).dp,
            startY = (-85).dp,
            driftX = 50.dp,
            driftY = 26.dp,
            durationMs = 20000
        ),
        BlobSpec(
            color = Color(0xFF8AA4C8),
            size = 390.dp,
            alpha = 0.25f,
            startX = 200.dp,
            startY = 430.dp,
            driftX = (-58).dp,
            driftY = (-42).dp,
            durationMs = 26000
        ),
        BlobSpec(
            color = Color(0xFFFFB766),
            size = 280.dp,
            alpha = 0.23f,
            startX = 220.dp,
            startY = (-120).dp,
            driftX = (-34).dp,
            driftY = 24.dp,
            durationMs = 24000
        )
    )
)

private fun rainyStyle() = BackgroundStyle(
    gradientColors = listOf(
        Color(0xFF4B79A1),
        Color(0xFF36536B),
        Color(0xFF283E51)
    ),
    glowColor = Color(0xFFA6D1FF).copy(alpha = 0.2f),
    particleMode = ParticleMode.RAIN,
    noiseAlpha = 0.055f,
    gradientDurationMs = 18000,
    blobs = listOf(
        BlobSpec(
            color = Color(0xFF5D8AA8),
            size = 380.dp,
            alpha = 0.42f,
            startX = (-130).dp,
            startY = (-120).dp,
            driftX = 58.dp,
            driftY = 24.dp,
            durationMs = 22000
        ),
        BlobSpec(
            color = Color(0xFF7FA6C6),
            size = 340.dp,
            alpha = 0.28f,
            startX = 210.dp,
            startY = 400.dp,
            driftX = (-62).dp,
            driftY = (-42).dp,
            durationMs = 25500
        )
    )
)

private fun snowyStyle() = BackgroundStyle(
    gradientColors = listOf(
        Color(0xFFA9C1D8),
        Color(0xFF8FAFC6),
        Color(0xFF567189)
    ),
    glowColor = Color(0xFFEAF6FF).copy(alpha = 0.16f),
    particleMode = ParticleMode.SNOW,
    noiseAlpha = 0.06f,
    overallDimAlpha = 0.08f,
    gradientDurationMs = 20000,
    blobs = listOf(
        BlobSpec(
            color = Color(0xFFCFEAF8),
            size = 360.dp,
            alpha = 0.44f,
            startX = (-120).dp,
            startY = (-100).dp,
            driftX = 44.dp,
            driftY = 26.dp,
            durationMs = 23000
        ),
        BlobSpec(
            color = Color(0xFFA9C9E8),
            size = 420.dp,
            alpha = 0.32f,
            startX = 160.dp,
            startY = 430.dp,
            driftX = (-56).dp,
            driftY = (-48).dp,
            durationMs = 29000
        )
    )
)

private fun stormStyle() = BackgroundStyle(
    gradientColors = listOf(
        Color(0xFF2C3E50),
        Color(0xFF1E2A35),
        Color(0xFF121A22)
    ),
    glowColor = Color(0xFFC7D6E6).copy(alpha = 0.14f),
    particleMode = ParticleMode.RAIN,
    rainIntensity = 1.55f,
    noiseAlpha = 0.06f,
    gradientDurationMs = 16000,
    blobs = listOf(
        BlobSpec(
            color = Color(0xFF4E6882),
            size = 430.dp,
            alpha = 0.4f,
            startX = (-150).dp,
            startY = (-130).dp,
            driftX = 76.dp,
            driftY = 28.dp,
            durationMs = 21000
        ),
        BlobSpec(
            color = Color(0xFF3A5168),
            size = 360.dp,
            alpha = 0.28f,
            startX = 190.dp,
            startY = 420.dp,
            driftX = (-70).dp,
            driftY = (-54).dp,
            durationMs = 25000
        )
    )
)

private fun cloudyStyle() = BackgroundStyle(
    gradientColors = listOf(
        Color(0xFF7489A0),
        Color(0xFF667E96),
        Color(0xFF425466)
    ),
    glowColor = Color(0xFFDDE7F2).copy(alpha = 0.11f),
    particleMode = ParticleMode.NONE,
    noiseAlpha = 0.05f,
    gradientDurationMs = 18500,
    blobs = listOf(
        BlobSpec(
            color = Color(0xFFB7C7D8),
            size = 360.dp,
            alpha = 0.34f,
            startX = (-110).dp,
            startY = (-85).dp,
            driftX = 50.dp,
            driftY = 26.dp,
            durationMs = 24000
        ),
        BlobSpec(
            color = Color(0xFF8EA5BE),
            size = 390.dp,
            alpha = 0.25f,
            startX = 200.dp,
            startY = 430.dp,
            driftX = (-58).dp,
            driftY = (-42).dp,
            durationMs = 28000
        )
    )
)

private fun nightClearStyle() = BackgroundStyle(
    gradientColors = listOf(
        Color(0xFF1E3C72),
        Color(0xFF1A2D56),
        Color(0xFF0F2027)
    ),
    glowColor = Color(0xFF89AFFF).copy(alpha = 0.26f),
    particleMode = ParticleMode.STARS,
    noiseAlpha = 0.07f,
    gradientDurationMs = 21000,
    blobs = listOf(
        BlobSpec(
            color = Color(0xFF4C6DA8),
            size = 360.dp,
            alpha = 0.34f,
            startX = (-100).dp,
            startY = (-120).dp,
            driftX = 42.dp,
            driftY = 34.dp,
            durationMs = 26000
        ),
        BlobSpec(
            color = Color(0xFF6A4DAF),
            size = 300.dp,
            alpha = 0.26f,
            startX = 210.dp,
            startY = 450.dp,
            driftX = (-50).dp,
            driftY = (-46).dp,
            durationMs = 30000
        )
    )
)

private fun nightRainStyle() = BackgroundStyle(
    gradientColors = listOf(
        Color(0xFF1B3553),
        Color(0xFF162739),
        Color(0xFF0B1620)
    ),
    glowColor = Color(0xFF7EA5D6).copy(alpha = 0.16f),
    particleMode = ParticleMode.RAIN,
    noiseAlpha = 0.07f,
    gradientDurationMs = 18000,
    blobs = listOf(
        BlobSpec(
            color = Color(0xFF3C597A),
            size = 380.dp,
            alpha = 0.3f,
            startX = (-120).dp,
            startY = (-140).dp,
            driftX = 56.dp,
            driftY = 40.dp,
            durationMs = 23500
        ),
        BlobSpec(
            color = Color(0xFF314A63),
            size = 340.dp,
            alpha = 0.22f,
            startX = 220.dp,
            startY = 430.dp,
            driftX = (-62).dp,
            driftY = (-44).dp,
            durationMs = 28000
        )
    )
)

private fun nightSnowStyle() = BackgroundStyle(
    gradientColors = listOf(
        Color(0xFF253A5A),
        Color(0xFF1A2940),
        Color(0xFF101A28)
    ),
    glowColor = Color(0xFFA9C6E8).copy(alpha = 0.2f),
    particleMode = ParticleMode.SNOW,
    noiseAlpha = 0.075f,
    gradientDurationMs = 20500,
    blobs = listOf(
        BlobSpec(
            color = Color(0xFF45678A),
            size = 350.dp,
            alpha = 0.3f,
            startX = (-100).dp,
            startY = (-120).dp,
            driftX = 46.dp,
            driftY = 30.dp,
            durationMs = 24000
        ),
        BlobSpec(
            color = Color(0xFF6B83A8),
            size = 320.dp,
            alpha = 0.24f,
            startX = 210.dp,
            startY = 440.dp,
            driftX = (-50).dp,
            driftY = (-40).dp,
            durationMs = 28500
        )
    )
)

private fun nightStormStyle() = BackgroundStyle(
    gradientColors = listOf(
        Color(0xFF19283C),
        Color(0xFF111A27),
        Color(0xFF0A1018)
    ),
    glowColor = Color(0xFF9BB6D3).copy(alpha = 0.12f),
    particleMode = ParticleMode.RAIN,
    rainIntensity = 1.55f,
    noiseAlpha = 0.075f,
    gradientDurationMs = 16500,
    blobs = listOf(
        BlobSpec(
            color = Color(0xFF39506A),
            size = 390.dp,
            alpha = 0.3f,
            startX = (-130).dp,
            startY = (-130).dp,
            driftX = 62.dp,
            driftY = 34.dp,
            durationMs = 22000
        ),
        BlobSpec(
            color = Color(0xFF2C3F55),
            size = 340.dp,
            alpha = 0.22f,
            startX = 200.dp,
            startY = 430.dp,
            driftX = (-56).dp,
            driftY = (-44).dp,
            durationMs = 26500
        )
    )
)

private enum class WeatherFamily {
    CLEAR,
    CLOUDY,
    RAIN,
    SNOW,
    STORM
}

private enum class ParticleMode {
    NONE,
    RAIN,
    SNOW,
    STARS
}

private data class BackgroundStyle(
    val gradientColors: List<Color>,
    val glowColor: Color?,
    val particleMode: ParticleMode,
    val rainIntensity: Float = 1f,
    val sunRays: SunRaysSpec? = null,
    val noiseAlpha: Float,
    val overallDimAlpha: Float = 0f,
    val gradientDurationMs: Int,
    val blobs: List<BlobSpec>
)

private data class SunRaysSpec(
    val color: Color,
    val coreAlpha: Float,
    val rayAlpha: Float,
    val sourceXFraction: Float,
    val sourceYFraction: Float,
    val glowRadiusFraction: Float,
    val rayCount: Int,
    val rayLengthFraction: Float,
    val rayWidthFraction: Float,
    val fanStartDegrees: Float,
    val fanSweepDegrees: Float,
    val pulseDurationMs: Int,
    val swayDurationMs: Int
)

private data class BlobSpec(
    val color: Color,
    val size: Dp,
    val alpha: Float,
    val startX: Dp,
    val startY: Dp,
    val driftX: Dp,
    val driftY: Dp,
    val durationMs: Int
)

private data class ParticleSpec(
    val seedX: Float,
    val seedY: Float,
    val speed: Float,
    val size: Float,
    val drift: Float,
    val phase: Float,
    val alpha: Float
)

private data class LiveRainSimulation(
    val random: Random,
    val drops: MutableList<LiveRainDrop>,
    var timeSec: Float
)

private data class LiveRainDrop(
    var xNorm: Float,
    var yNorm: Float,
    var vxNormPerSec: Float,
    var vyNormPerSec: Float,
    val depth: Float,
    val baseFallNormPerSec: Float,
    val fallAccelerationNormPerSec2: Float,
    val terminalFallNormPerSec: Float,
    val windInfluence: Float,
    val turbulenceAmp: Float,
    val turbulenceFreqHz: Float,
    val turbulencePhase: Float,
    val horizontalResponse: Float,
    val thickness: Float,
    val baseLengthNorm: Float,
    val alpha: Float,
    val shimmerPhase: Float
)
