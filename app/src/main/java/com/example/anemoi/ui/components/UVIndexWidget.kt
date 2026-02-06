package com.example.anemoi.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

@Composable
fun UVIndexWidget(
    currentUV: Double?,
    modifier: Modifier = Modifier
) {
    val uvValue = currentUV ?: 0.0
    val displayValue = currentUV?.roundToInt()?.toString() ?: "--"
    
    val uvColors = listOf(
        Color(0xFF4CAF50), // Green (Low)
        Color(0xFFFBC02D), // Yellow (Moderate)
        Color(0xFFF57C00), // Orange (High)
        Color(0xFFD32F2F), // Red (Very High)
        Color(0xFF7B1FA2)  // Violet (Extreme)
    )

    val uvLevelText = when {
        currentUV == null -> ""
        uvValue < 3 -> "Low"
        uvValue < 6 -> "Moderate"
        uvValue < 8 -> "High"
        uvValue < 11 -> "Very High"
        else -> "Extreme"
    }

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val centerX = size.width / 2
            val centerY = size.height / 2
            
            val strokeWidth = 14.dp.toPx()
            val highlightWidth = strokeWidth + 4.dp.toPx()
            val radius = (size.minDimension / 2) - (highlightWidth / 2) - 2.dp.toPx()
            
            val startAngle = 135f
            val totalSweep = 270f

            // Helper to draw the track/tube with a specific width and alpha
            fun drawDialLayers(width: Float, alphaFactor: Float) {
                // Background Track
                drawArc(
                    color = Color.White.copy(alpha = 0.04f * alphaFactor),
                    startAngle = startAngle,
                    sweepAngle = totalSweep,
                    useCenter = false,
                    topLeft = Offset(centerX - radius, centerY - radius),
                    size = Size(radius * 2, radius * 2),
                    style = Stroke(width = width, cap = StrokeCap.Butt)
                )

                // Colored Tube
                rotate(startAngle, Offset(centerX, centerY)) {
                    drawArc(
                        brush = Brush.sweepGradient(
                            0.0000f to uvColors[0],
                            0.1875f to uvColors[1],
                            0.3750f to uvColors[2],
                            0.5625f to uvColors[3],
                            0.7500f to uvColors[4],
                            1.0000f to uvColors[4],
                            center = Offset(centerX, centerY)
                        ),
                        startAngle = 0f,
                        sweepAngle = totalSweep,
                        useCenter = false,
                        topLeft = Offset(centerX - radius, centerY - radius),
                        size = Size(radius * 2, radius * 2),
                        style = Stroke(width = width, cap = StrokeCap.Butt),
                        alpha = 0.35f * alphaFactor
                    )
                }
            }

            // Draw multiple layers with tapering width and alpha to simulate a "side blur" (soft edges)
            drawDialLayers(strokeWidth + 1.5.dp.toPx(), 0.2f)
            drawDialLayers(strokeWidth + 0.75.dp.toPx(), 0.5f)
            drawDialLayers(strokeWidth, 1.0f)
            drawDialLayers(strokeWidth - 0.75.dp.toPx(), 0.5f)
            drawDialLayers(strokeWidth - 1.5.dp.toPx(), 0.2f)

            // 3. Current Value Highlight and Arrow
            if (currentUV != null) {
                val sectionSweep = totalSweep / 11f
                // Position based on float value for precise movement
                val centerAngle = startAngle + (uvValue.toFloat() * sectionSweep).coerceIn(0f, totalSweep)
                val sectionStartAngle = centerAngle - (sectionSweep / 2f)

                // Helper to draw the highlight with side blur
                fun drawHighlightLayers(width: Float, alphaFactor: Float) {
                    drawArc(
                        color = Color.White.copy(alpha = 0.4f * alphaFactor),
                        startAngle = sectionStartAngle,
                        sweepAngle = sectionSweep,
                        useCenter = false,
                        topLeft = Offset(centerX - radius, centerY - radius),
                        size = Size(radius * 2, radius * 2),
                        style = Stroke(width = width, cap = StrokeCap.Butt)
                    )
                }

                // Apply side blur to highlight segment
                drawHighlightLayers(highlightWidth + 1.5.dp.toPx(), 0.2f)
                drawHighlightLayers(highlightWidth + 0.75.dp.toPx(), 0.5f)
                drawHighlightLayers(highlightWidth, 1.0f)
                drawHighlightLayers(highlightWidth - 0.75.dp.toPx(), 0.5f)
                drawHighlightLayers(highlightWidth - 1.5.dp.toPx(), 0.2f)

                // Arrow
                val angleRad = Math.toRadians(centerAngle.toDouble())
                val arrowTipDist = radius - (highlightWidth / 2) - 2.dp.toPx()
                val arrowLength = 8.dp.toPx()
                val arrowBaseDist = arrowTipDist - arrowLength
                val arrowWidthRad = 0.08

                val tipX = centerX + arrowTipDist * cos(angleRad).toFloat()
                val tipY = centerY + arrowTipDist * sin(angleRad).toFloat()
                val baseLeftX = centerX + arrowBaseDist * cos(angleRad - arrowWidthRad).toFloat()
                val baseLeftY = centerY + arrowBaseDist * sin(angleRad - arrowWidthRad).toFloat()
                val baseRightX = centerX + arrowBaseDist * cos(angleRad + arrowWidthRad).toFloat()
                val baseRightY = centerY + arrowBaseDist * sin(angleRad + arrowWidthRad).toFloat()

                val arrowPath = Path().apply {
                    moveTo(tipX, tipY)
                    lineTo(baseLeftX, baseLeftY)
                    lineTo(baseRightX, baseRightY)
                    close()
                }
                drawPath(arrowPath, color = Color.White)
            }
        }
        
        Column(
            modifier = Modifier.padding(bottom = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Spacer(modifier = Modifier.size(28.dp))
            Text(
                text = displayValue,
                color = Color.White,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = uvLevelText,
                color = Color.White.copy(alpha = 0.6f),
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}
