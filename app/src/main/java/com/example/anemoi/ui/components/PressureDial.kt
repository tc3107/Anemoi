package com.example.anemoi.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ArrowDropUp
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.anemoi.data.PressureUnit
import java.util.Locale
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

@Composable
fun PressureDial(
    currentPressure: Double?,
    minPressure: Double?,
    maxPressure: Double?,
    trend: Double?, // Difference over 3 hours
    unit: PressureUnit,
    modifier: Modifier = Modifier
) {
    val displayValue = currentPressure?.let { convertPressure(it, unit) }
    val unitLabel = unit.label
    
    val trendIcon = when {
        trend == null -> Icons.Default.Remove
        trend > 1.0 -> Icons.Default.ArrowDropUp
        trend < -1.0 -> Icons.Default.ArrowDropDown
        else -> Icons.Default.Remove
    }
    val trendColor = when {
        trend == null -> Color.White.copy(alpha = 0.2f)
        trend > 1.0 -> Color.White
        trend < -1.0 -> Color.White
        else -> Color.White.copy(alpha = 0.4f)
    }

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val centerX = size.width / 2
            val centerY = size.height / 2

            val startAngle = 135f
            val totalSweep = 270f
            val numTicks = 48
            val tickLength = 12.dp.toPx()
            val tickThickness = 1.35.dp.toPx()
            val indicatorLength = 20.dp.toPx()
            val indicatorThickness = 4.dp.toPx()
            // Keep outermost rendered geometry inside the same visual bounds as other dials.
            val maxProtrusion = max(tickLength, indicatorLength) / 2f
            val radius = (size.minDimension / 2f) - maxProtrusion - 2.dp.toPx()
            
            val progress = if (currentPressure != null && minPressure != null && maxPressure != null) {
                val range = maxPressure - minPressure
                if (range > 0) ((currentPressure - minPressure) / range).coerceIn(0.0, 1.0).toFloat() else 0.5f
            } else null
            
            val currentAngle = if (progress != null) startAngle + progress * totalSweep else null

            val tickColor = Color(0xFFC9CED5).copy(alpha = 0.24f)

            // Draw all regular ticks
            for (i in 0..numTicks) {
                val angleDeg = startAngle + i * (totalSweep / numTicks)
                val angleRad = Math.toRadians(angleDeg.toDouble())
                
                // Skip drawing regular tick if it's very close to the current indicator
                if (currentAngle != null && Math.abs(angleDeg - currentAngle) < (totalSweep / numTicks) * 0.5f) continue

                val startX = centerX + (radius - tickLength / 2) * cos(angleRad).toFloat()
                val startY = centerY + (radius - tickLength / 2) * sin(angleRad).toFloat()
                val endX = centerX + (radius + tickLength / 2) * cos(angleRad).toFloat()
                val endY = centerY + (radius + tickLength / 2) * sin(angleRad).toFloat()

                drawLine(
                    color = tickColor,
                    start = Offset(startX, startY),
                    end = Offset(endX, endY),
                    strokeWidth = tickThickness,
                    cap = StrokeCap.Round
                )
            }
            
            // Current value indicator (only if available)
            if (currentAngle != null) {
                val indicatorAngleRad = Math.toRadians(currentAngle.toDouble())
                
                val indStartX = centerX + (radius - indicatorLength / 2) * cos(indicatorAngleRad).toFloat()
                val indStartY = centerY + (radius - indicatorLength / 2) * sin(indicatorAngleRad).toFloat()
                val indEndX = centerX + (radius + indicatorLength / 2) * cos(indicatorAngleRad).toFloat()
                val indEndY = centerY + (radius + indicatorLength / 2) * sin(indicatorAngleRad).toFloat()
                
                drawLine(
                    color = Color.White,
                    start = Offset(indStartX, indStartY),
                    end = Offset(indEndX, indEndY),
                    strokeWidth = indicatorThickness,
                    cap = StrokeCap.Round
                )
            }
        }
        
        Column(
            modifier = Modifier.padding(bottom = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = trendIcon,
                contentDescription = null,
                tint = trendColor,
                modifier = Modifier.size(28.dp)
            )
            
            Text(
                text = displayValue?.let { formatPressure(it, unit) } ?: "--",
                color = Color.White.copy(alpha = 0.9f),
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold
            )
            
            Text(
                text = unitLabel,
                color = Color.White.copy(alpha = 0.6f),
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

private fun convertPressure(hpa: Double, unit: PressureUnit): Double {
    return when (unit) {
        PressureUnit.HPA -> hpa
        PressureUnit.MBAR -> hpa
        PressureUnit.MMHG -> hpa * 0.750062
        PressureUnit.INHG -> hpa * 0.02953
    }
}

private fun formatPressure(value: Double, unit: PressureUnit): String {
    return when (unit) {
        PressureUnit.INHG -> String.format(Locale.getDefault(), "%.2f", value)
        else -> value.toInt().toString()
    }
}
