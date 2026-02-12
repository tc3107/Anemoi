package com.example.anemoi.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

@Composable
fun DynamicWeatherBackground(
    weatherCode: Int?,
    weatherTimeIso: String?,
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
        AuroraBlobLayer(style = style)
        WeatherParticleLayer(mode = style.particleMode)
        NoiseLayer(alpha = style.noiseAlpha)
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

@Composable
private fun WeatherParticleLayer(mode: ParticleMode) {
    if (mode == ParticleMode.NONE) return

    val particles = remember(mode) { buildParticles(mode) }
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
        val width = size.width
        val height = size.height
        val cycle = progress.value

        particles.forEach { particle ->
            when (mode) {
                ParticleMode.RAIN -> {
                    val phase = ((cycle + particle.phase) * 2f * PI).toFloat()
                    val y = ((particle.seedY + cycle * particle.speed) % 1f) * height
                    val sway = sin(phase) * particle.drift
                    val x = ((particle.seedX + sway).coerceIn(0f, 1f)) * width

                    // Horizontal speed comes from the time derivative of the sway function.
                    val horizontalSpeedNorm = cos(phase) * (2f * PI.toFloat()) * particle.drift
                    val verticalSpeedNorm = particle.speed
                    val horizontalSpeedPx = horizontalSpeedNorm * width
                    val verticalSpeedPx = verticalSpeedNorm * height
                    val absoluteSpeedPx = sqrt(
                        horizontalSpeedPx * horizontalSpeedPx +
                            verticalSpeedPx * verticalSpeedPx
                    )

                    val referenceSpeedPx = height * 2.2f
                    val speedFactor = (absoluteSpeedPx / referenceSpeedPx).coerceIn(0.12f, 1f)
                    val length = (0.02f + speedFactor * 0.08f) * height
                    val slope = horizontalSpeedPx / verticalSpeedPx.coerceAtLeast(1f)
                    val alpha = (0.08f + particle.alpha * 0.28f).coerceIn(0f, 1f)
                    drawLine(
                        color = Color.White.copy(alpha = alpha),
                        start = Offset(x, y),
                        end = Offset(x + slope * length, y + length),
                        strokeWidth = (1.2f + particle.size * 1.6f),
                        cap = StrokeCap.Round
                    )
                }

                ParticleMode.SNOW -> {
                    val y = ((particle.seedY + cycle * particle.speed) % 1f) * height
                    val sway = sin(((cycle * 1.8f + particle.phase) * 2f * PI).toFloat()) * particle.drift
                    val x = ((particle.seedX + sway).coerceIn(0f, 1f)) * width
                    val alpha = (0.16f + particle.alpha * 0.5f).coerceIn(0f, 1f)
                    drawCircle(
                        color = Color.White.copy(alpha = alpha),
                        radius = 1.1f + particle.size * 2.8f,
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
                        radius = 0.8f + particle.size * 1.8f,
                        center = Offset(x, y)
                    )
                }

                ParticleMode.NONE -> Unit
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
                val densityFactor = (size.width * size.height / 13_500f).toInt().coerceIn(180, 620)
                val random = Random(9127)
                val points = List(densityFactor) {
                    Offset(
                        x = random.nextFloat() * size.width,
                        y = random.nextFloat() * size.height
                    )
                }
                val radii = List(densityFactor) { 0.4f + random.nextFloat() * 1.1f }
                val alphas = List(densityFactor) { (0.2f + random.nextFloat() * 0.8f) * alpha }

                onDrawBehind {
                    points.forEachIndexed { index, point ->
                        drawCircle(
                            color = Color.White.copy(alpha = alphas[index].coerceIn(0f, 1f)),
                            radius = radii[index],
                            center = point
                        )
                    }
                }
            }
    )
}

private fun buildParticles(mode: ParticleMode): List<ParticleSpec> {
    val seed = when (mode) {
        ParticleMode.RAIN -> 1183
        ParticleMode.SNOW -> 4079
        ParticleMode.STARS -> 7703
        ParticleMode.NONE -> 31
    }
    val count = when (mode) {
        ParticleMode.RAIN -> 54
        ParticleMode.SNOW -> 42
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
        Color(0xFFF6D365),
        Color(0xFFFDA085),
        Color(0xFFFBC2EB)
    ),
    glowColor = Color(0xFFFFF4C0).copy(alpha = 0.28f),
    particleMode = ParticleMode.NONE,
    noiseAlpha = 0.05f,
    gradientDurationMs = 17000,
    blobs = listOf(
        BlobSpec(
            color = Color(0xFFFFA69E),
            size = 360.dp,
            alpha = 0.52f,
            startX = (-120).dp,
            startY = (-100).dp,
            driftX = 70.dp,
            driftY = 30.dp,
            durationMs = 20000
        ),
        BlobSpec(
            color = Color(0xFFA18CD1),
            size = 420.dp,
            alpha = 0.46f,
            startX = 170.dp,
            startY = 430.dp,
            driftX = (-80).dp,
            driftY = (-55).dp,
            durationMs = 26000
        ),
        BlobSpec(
            color = Color(0xFFFFD5A8),
            size = 300.dp,
            alpha = 0.38f,
            startX = 160.dp,
            startY = (-90).dp,
            driftX = (-40).dp,
            driftY = 36.dp,
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
        Color(0xFFBFD8F0),
        Color(0xFF8FAFC6),
        Color(0xFF567189)
    ),
    glowColor = Color(0xFFEAF6FF).copy(alpha = 0.22f),
    particleMode = ParticleMode.SNOW,
    noiseAlpha = 0.06f,
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
        Color(0xFF8DA1B9),
        Color(0xFF667E96),
        Color(0xFF425466)
    ),
    glowColor = Color(0xFFDDE7F2).copy(alpha = 0.16f),
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
    val noiseAlpha: Float,
    val gradientDurationMs: Int,
    val blobs: List<BlobSpec>
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
