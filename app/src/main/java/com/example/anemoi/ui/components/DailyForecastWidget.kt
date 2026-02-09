package com.example.anemoi.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.anemoi.data.TempUnit
import com.example.anemoi.util.formatTemp
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import kotlin.math.max
import kotlin.math.min

@OptIn(ExperimentalTextApi::class)
@Composable
fun DailyForecastWidget(
    dates: List<String>,
    weatherCodes: List<Int>,
    minTemperatures: List<Double>,
    maxTemperatures: List<Double>,
    tempUnit: TempUnit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val textMeasurer = rememberTextMeasurer()
    val dayLabelStyle = remember {
        TextStyle(
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
    val rows = remember(dates, weatherCodes, minTemperatures, maxTemperatures) {
        (0 until 10).map { index ->
            DailyForecastRow(
                index = index,
                dateIso = dates.getOrNull(index),
                dayLabel = if (index == 0) {
                    "Today"
                } else {
                    shortWeekdayLabel(dates.getOrNull(index), index)
                },
                weatherCode = weatherCodes.getOrNull(index),
                minTemp = minTemperatures.getOrNull(index),
                maxTemp = maxTemperatures.getOrNull(index)
            )
        }
    }
    val dayLabelColumnWidth = remember(rows, density, textMeasurer, dayLabelStyle) {
        val maxWidthPx = rows
            .maxOfOrNull { row -> textMeasurer.measure(row.dayLabel, style = dayLabelStyle).size.width }
            ?: 0
        with(density) { maxWidthPx.toDp() + 2.dp }
    }
    val iconLaneWidth = 40.dp

    val globalMin = rows.mapNotNull { it.minTemp }.minOrNull()
    val globalMax = rows.mapNotNull { it.maxTemp }.maxOrNull()
    val safeGlobalMin = globalMin ?: 0.0
    val safeGlobalMax = when {
        globalMax == null -> safeGlobalMin + 1.0
        globalMax <= safeGlobalMin -> safeGlobalMin + 1.0
        else -> globalMax
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(28.dp))
            .background(Color.White.copy(alpha = 0.1f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp)
        ) {
            Text(
                text = "10-DAY FORECAST",
                color = Color.White.copy(alpha = 0.4f),
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium
            )

            Spacer(modifier = Modifier.height(8.dp))

            rows.forEachIndexed { index, row ->
                val iconResId = row.weatherCode?.let { getDailyWeatherIconRes(it, context) } ?: 0
                val minTempText = row.minTemp?.let { formatTemp(it, tempUnit) } ?: "--°"
                val maxTempText = row.maxTemp?.let { formatTemp(it, tempUnit) } ?: "--°"

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(32.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = row.dayLabel,
                        color = Color.White.copy(alpha = 0.85f),
                        style = dayLabelStyle,
                        modifier = Modifier.width(dayLabelColumnWidth)
                    )

                    Box(
                        modifier = Modifier.width(iconLaneWidth),
                        contentAlignment = Alignment.Center
                    ) {
                        if (iconResId != 0) {
                            Image(
                                painter = painterResource(id = iconResId),
                                contentDescription = null,
                                modifier = Modifier.size(22.dp),
                                colorFilter = ColorFilter.tint(Color(0xFFD6D9DE))
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .background(Color.White.copy(alpha = 0.2f), CircleShape)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Text(
                        text = minTempText,
                        color = Color.White.copy(alpha = 0.68f),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.width(44.dp)
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    TemperatureRangeTrack(
                        dayMin = row.minTemp,
                        dayMax = row.maxTemp,
                        globalMin = safeGlobalMin,
                        globalMax = safeGlobalMax,
                        modifier = Modifier
                            .weight(1f)
                            .height(8.dp)
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    Text(
                        text = maxTempText,
                        color = Color.White.copy(alpha = 0.9f),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.End,
                        modifier = Modifier.width(44.dp)
                    )
                }

                if (index < rows.lastIndex) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(Color.White.copy(alpha = 0.08f))
                    )
                }
            }
        }
    }
}

@Composable
private fun TemperatureRangeTrack(
    dayMin: Double?,
    dayMax: Double?,
    globalMin: Double,
    globalMax: Double,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier.fillMaxWidth()) {
        val centerY = size.height / 2f
        val strokeWidth = max(2f, size.height * 0.55f)
        val baseGradient = Brush.horizontalGradient(
            colors = listOf(
                Color(0xFF81D4FA).copy(alpha = 0.28f),
                Color(0xFF0288D1).copy(alpha = 0.28f)
            ),
            startX = 0f,
            endX = size.width
        )
        val highlightGradient = Brush.horizontalGradient(
            colors = listOf(
                Color(0xFF81D4FA),
                Color(0xFF0288D1)
            ),
            startX = 0f,
            endX = size.width
        )

        drawLine(
            brush = baseGradient,
            start = Offset(0f, centerY),
            end = Offset(size.width, centerY),
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round
        )

        if (dayMin == null || dayMax == null) return@Canvas

        val range = (globalMax - globalMin).takeIf { it > 0.0 } ?: 1.0
        val dayLow = min(dayMin, dayMax)
        val dayHigh = max(dayMin, dayMax)

        val startX = (((dayLow - globalMin) / range).coerceIn(0.0, 1.0) * size.width).toFloat()
        val rawEndX = (((dayHigh - globalMin) / range).coerceIn(0.0, 1.0) * size.width).toFloat()
        val endX = max(rawEndX, (startX + strokeWidth).coerceAtMost(size.width))

        drawLine(
            brush = highlightGradient,
            start = Offset(startX, centerY),
            end = Offset(endX, centerY),
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round
        )
    }
}

private fun shortWeekdayLabel(dateIso: String?, dayOffset: Int): String {
    val parsedDate = parseDailyDate(dateIso)
    val fallback = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, dayOffset) }.time
    val dayDate = parsedDate ?: fallback
    val formatter = SimpleDateFormat("EEE", Locale.US)
    return formatter.format(dayDate)
}

private fun parseDailyDate(dateIso: String?): java.util.Date? {
    if (dateIso.isNullOrBlank()) return null
    val parser = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { isLenient = false }
    return runCatching { parser.parse(dateIso) }.getOrNull()
}

private fun getDailyWeatherIconRes(code: Int, context: android.content.Context): Int {
    @Suppress("DiscouragedApi")
    return context.resources.getIdentifier("wmo_$code", "drawable", context.packageName)
}

private data class DailyForecastRow(
    val index: Int,
    val dateIso: String?,
    val dayLabel: String,
    val weatherCode: Int?,
    val minTemp: Double?,
    val maxTemp: Double?
)
