package com.example.anemoi.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
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
import kotlin.math.roundToInt

@OptIn(ExperimentalTextApi::class)
@Composable
fun DailyForecastWidget(
    dates: List<String>,
    weatherCodes: List<Int>,
    minTemperatures: List<Double>,
    maxTemperatures: List<Double>,
    precipitationProbabilityMax: List<Double>,
    hourlyTimes: List<String> = emptyList(),
    hourlyWeatherCodes: List<Int> = emptyList(),
    hourlyTemperatures: List<Double> = emptyList(),
    hourlyPrecipitationProbabilities: List<Int> = emptyList(),
    hourlyPrecipitations: List<Double> = emptyList(),
    currentTimeIso: String? = null,
    tempUnit: TempUnit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val textMeasurer = rememberTextMeasurer()
    var expandedRowIndex by rememberSaveable(
        dates,
        weatherCodes,
        minTemperatures,
        maxTemperatures,
        precipitationProbabilityMax,
        hourlyTimes,
        hourlyWeatherCodes,
        hourlyTemperatures,
        hourlyPrecipitationProbabilities,
        hourlyPrecipitations,
        currentTimeIso
    ) {
        mutableIntStateOf(-1)
    }
    var lastSelectedModeOrdinal by rememberSaveable {
        mutableIntStateOf(DailyDetailMode.TEMPERATURE.ordinal)
    }
    val selectedDetailMode = DailyDetailMode.entries
        .getOrNull(lastSelectedModeOrdinal)
        ?: DailyDetailMode.TEMPERATURE
    val dayLabelStyle = remember {
        TextStyle(
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
    val rows = remember(
        dates,
        weatherCodes,
        minTemperatures,
        maxTemperatures,
        precipitationProbabilityMax
    ) {
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
                maxTemp = maxTemperatures.getOrNull(index),
                precipitationProbability = precipitationProbabilityMax.getOrNull(index)
            )
        }
    }
    val dayLabelColumnWidth = remember(rows, density, textMeasurer, dayLabelStyle) {
        val maxWidthPx = rows
            .maxOfOrNull { row -> textMeasurer.measure(row.dayLabel, style = dayLabelStyle).size.width }
            ?: 0
        with(density) { maxWidthPx.toDp() + 2.dp }
    }
    val precipitationLaneWidth = 44.dp
    val iconLaneWidth = 30.dp
    val surfaceShape = RoundedCornerShape(28.dp)
    val minTempColor = Color.White.copy(alpha = 0.68f)
    val rainPercentColor = lerp(
        start = Color.White,
        stop = Color(0xFF6EC9F7),
        fraction = 0.16f
    ).copy(alpha = minTempColor.alpha)

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
            .clip(surfaceShape)
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.22f),
                        Color.White.copy(alpha = 0.12f),
                        Color.White.copy(alpha = 0.05f)
                    )
                )
            )
            .border(
                width = 1.dp,
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.32f),
                        Color.White.copy(alpha = 0.08f)
                    )
                ),
                shape = surfaceShape
            )
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .height(1.dp)
                .background(Color.White.copy(alpha = 0.26f))
        )
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(1.dp)
                .background(Color.White.copy(alpha = 0.12f))
        )

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
                val precipitationPercent = row.precipitationProbability
                    ?.takeIf { it > 0.0 }
                    ?.let { max(1, it.roundToInt()) }
                val isExpanded = expandedRowIndex == index
                val dayHourlyPoints = remember(
                    row.dateIso,
                    row.index,
                    hourlyTimes,
                    hourlyWeatherCodes,
                    hourlyTemperatures,
                    hourlyPrecipitationProbabilities,
                    hourlyPrecipitations
                ) {
                    buildDayHourlyPoints(
                        dateIso = row.dateIso,
                        dayOffset = row.index,
                        hourlyTimes = hourlyTimes,
                        hourlyWeatherCodes = hourlyWeatherCodes,
                        hourlyTemperatures = hourlyTemperatures,
                        hourlyPrecipitationProbabilities = hourlyPrecipitationProbabilities,
                        hourlyPrecipitations = hourlyPrecipitations
                    )
                }
                val dayGraphCurrentTimeIso = remember(row.dateIso, currentTimeIso, dayHourlyPoints) {
                    resolveDayGraphCurrentTimeIso(
                        dateIso = row.dateIso,
                        candidateCurrentTimeIso = currentTimeIso,
                        fallbackTimeIso = dayHourlyPoints.firstOrNull()?.timeIso
                    )
                }
                val rowBackgroundAlpha by animateFloatAsState(
                    targetValue = if (isExpanded) 0.12f else 0f,
                    label = "daily_row_background"
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(38.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.White.copy(alpha = rowBackgroundAlpha))
                        .clickable {
                            if (isExpanded) {
                                expandedRowIndex = -1
                            } else {
                                expandedRowIndex = index
                            }
                        },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = row.dayLabel,
                        color = Color.White.copy(alpha = 0.85f),
                        style = dayLabelStyle,
                        modifier = Modifier.width(dayLabelColumnWidth)
                    )

                    Spacer(modifier = Modifier.width(6.dp))

                    Box(
                        modifier = Modifier.width(precipitationLaneWidth),
                        contentAlignment = Alignment.CenterEnd
                    ) {
                        if (precipitationPercent != null) {
                            Text(
                                text = "$precipitationPercent%",
                                color = rainPercentColor,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                textAlign = TextAlign.End
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(4.dp))

                    Box(
                        modifier = Modifier.width(iconLaneWidth),
                        contentAlignment = Alignment.Center
                    ) {
                        if (iconResId != 0) {
                            Image(
                                painter = painterResource(id = iconResId),
                                contentDescription = null,
                                modifier = Modifier.size(20.dp),
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
                        color = minTempColor,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.width(48.dp)
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    TemperatureRangeTrack(
                        dayMin = row.minTemp,
                        dayMax = row.maxTemp,
                        globalMin = safeGlobalMin,
                        globalMax = safeGlobalMax,
                        modifier = Modifier
                            .weight(1f)
                            .height(10.dp)
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    Text(
                        text = maxTempText,
                        color = Color.White.copy(alpha = 0.9f),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.End,
                        modifier = Modifier.width(48.dp)
                    )
                }

                AnimatedVisibility(
                    visible = isExpanded,
                    enter = expandVertically(
                        animationSpec = spring(
                            stiffness = Spring.StiffnessMediumLow,
                            dampingRatio = Spring.DampingRatioNoBouncy
                        )
                    ) + fadeIn(),
                    exit = shrinkVertically(
                        animationSpec = spring(
                            stiffness = Spring.StiffnessMediumLow,
                            dampingRatio = Spring.DampingRatioNoBouncy
                        )
                    ) + fadeOut()
                ) {
                    DailyForecastDetailPanel(
                        dayHourlyPoints = dayHourlyPoints,
                        dayGraphCurrentTimeIso = dayGraphCurrentTimeIso,
                        mode = selectedDetailMode,
                        onModeSelected = { lastSelectedModeOrdinal = it.ordinal },
                        tempUnit = tempUnit,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 6.dp, bottom = 8.dp)
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
private fun DailyForecastDetailPanel(
    dayHourlyPoints: List<DayHourlyPoint>,
    dayGraphCurrentTimeIso: String?,
    mode: DailyDetailMode,
    onModeSelected: (DailyDetailMode) -> Unit,
    tempUnit: TempUnit,
    modifier: Modifier = Modifier
) {
    val panelShape = RoundedCornerShape(18.dp)

    Column(
        modifier = modifier
            .clip(panelShape)
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.12f),
                        Color.White.copy(alpha = 0.07f)
                    )
                )
            )
            .border(
                width = 1.dp,
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.2f),
                        Color.White.copy(alpha = 0.08f)
                    )
                ),
                shape = panelShape
            )
            .padding(horizontal = 10.dp, vertical = 10.dp)
            .animateContentSize(
                animationSpec = spring(
                    stiffness = Spring.StiffnessMediumLow,
                    dampingRatio = Spring.DampingRatioNoBouncy
                )
            )
    ) {
        DailyDetailIconSlider(
            mode = mode,
            onModeSelected = onModeSelected,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(10.dp))

        Crossfade(
            targetState = mode,
            label = "daily_detail_widget"
        ) { selectedMode ->
            when (selectedMode) {
                DailyDetailMode.TEMPERATURE -> DailyTemperatureDetail(
                    dayHourlyPoints = dayHourlyPoints,
                    dayGraphCurrentTimeIso = dayGraphCurrentTimeIso,
                    tempUnit = tempUnit
                )
                DailyDetailMode.PRECIPITATION -> DailyPrecipitationDetail(
                    dayHourlyPoints = dayHourlyPoints,
                    dayGraphCurrentTimeIso = dayGraphCurrentTimeIso
                )
                DailyDetailMode.CONDITIONS -> DailyConditionsDetail(
                    dayHourlyPoints = dayHourlyPoints,
                    dayGraphCurrentTimeIso = dayGraphCurrentTimeIso,
                    tempUnit = tempUnit
                )
            }
        }
    }
}

@Composable
private fun DailyDetailIconSlider(
    mode: DailyDetailMode,
    onModeSelected: (DailyDetailMode) -> Unit,
    modifier: Modifier = Modifier
) {
    val options = remember { List(DailyDetailMode.entries.size) { "" } }
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(42.dp)
    ) {
        SegmentedSelector(
            options = options,
            selectedIndex = mode.ordinal,
            onOptionSelected = { index ->
                onModeSelected(
                    DailyDetailMode.entries.getOrNull(index)
                        ?: DailyDetailMode.TEMPERATURE
                )
            },
            modifier = Modifier.fillMaxSize()
        )

        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        )
        {
            DailyDetailMode.entries.forEach { option ->
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = option.icon,
                        contentDescription = option.contentDescription,
                        tint = if (option == mode) {
                            Color.White.copy(alpha = 0.95f)
                        } else {
                            Color.White.copy(alpha = 0.65f)
                        },
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun DailyTemperatureDetail(
    dayHourlyPoints: List<DayHourlyPoint>,
    dayGraphCurrentTimeIso: String?,
    tempUnit: TempUnit
) {
    val graphTemperatures = remember(dayHourlyPoints) {
        dayHourlyPoints.mapNotNull { it.temperature }
    }
    if (graphTemperatures.size < 2) {
        DailyFallbackText("No hourly temperature data for this day.")
        return
    }

    val currentTemp = remember(dayHourlyPoints, dayGraphCurrentTimeIso) {
        val targetHourKey = toHourKey(dayGraphCurrentTimeIso)
        dayHourlyPoints
            .firstOrNull { toHourKey(it.timeIso) == targetHourKey }
            ?.temperature
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(dailyDetailWidgetHeight)
            .clip(RoundedCornerShape(14.dp))
            .background(Color.White.copy(alpha = 0.06f))
            .border(
                width = 1.dp,
                color = Color.White.copy(alpha = 0.1f),
                shape = RoundedCornerShape(14.dp)
            )
    ) {
        TemperatureGraph(
            times = dayHourlyPoints.map { it.timeIso },
            temperatures = graphTemperatures,
            currentTemp = currentTemp,
            currentTimeIso = dayGraphCurrentTimeIso,
            tempUnit = tempUnit,
            widgetTopToGraphTopInset = 0.dp,
            yAxisLabelCount = 5,
            showXAxisLabels = false,
            hudReadingTextSizeSp = 16f,
            hudClockTextSizeSp = 13f,
            yAxisLabelHorizontalGap = 12.dp,
            modifier = Modifier.fillMaxSize()
        )
    }
}

@Composable
private fun DailyPrecipitationDetail(
    dayHourlyPoints: List<DayHourlyPoint>,
    dayGraphCurrentTimeIso: String?
) {
    val hasAnyPrecipData = remember(dayHourlyPoints) {
        dayHourlyPoints.any {
            it.precipitationProbability != null || it.precipitation != null
        }
    }
    if (!hasAnyPrecipData) {
        DailyFallbackText("No hourly precipitation data for this day.")
        return
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(dailyDetailWidgetHeight)
            .clip(RoundedCornerShape(14.dp))
            .background(Color.White.copy(alpha = 0.06f))
            .border(
                width = 1.dp,
                color = Color.White.copy(alpha = 0.1f),
                shape = RoundedCornerShape(14.dp)
            )
    ) {
        PrecipitationGraph(
            times = dayHourlyPoints.map { it.timeIso },
            probabilities = dayHourlyPoints.map { it.precipitationProbability ?: 0 },
            precipitations = dayHourlyPoints.map { it.precipitation ?: 0.0 },
            currentTimeIso = dayGraphCurrentTimeIso,
            widgetTopToGraphTopInset = 0.dp,
            yAxisLabelCount = 5,
            showXAxisLabels = false,
            hudReadingTextSizeSp = 16f,
            hudClockTextSizeSp = 13f,
            yAxisLabelHorizontalGap = 12.dp,
            modifier = Modifier.fillMaxSize()
        )
    }
}

@Composable
private fun DailyConditionsDetail(
    dayHourlyPoints: List<DayHourlyPoint>,
    dayGraphCurrentTimeIso: String?,
    tempUnit: TempUnit
) {
    val context = LocalContext.current
    val compactPoints = remember(dayHourlyPoints) {
        sampleDayHourlyPoints(dayHourlyPoints, maxItems = 8)
    }
    if (compactPoints.isEmpty()) {
        DailyFallbackText("No hourly conditions data for this day.")
        return
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(dailyDetailWidgetHeight)
            .clip(RoundedCornerShape(14.dp))
            .background(Color.White.copy(alpha = 0.06f))
            .border(
                width = 1.dp,
                color = Color.White.copy(alpha = 0.1f),
                shape = RoundedCornerShape(14.dp)
            )
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 10.dp, vertical = 8.dp)
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            compactPoints.forEach { point ->
                val iconResId = normalizeWeatherCode(point.weatherCode)
                    ?.let { getDailyWeatherIconRes(it, context) }
                    ?: 0
                val isCurrent = toHourKey(point.timeIso) == toHourKey(dayGraphCurrentTimeIso)
                DailyConditionTile(
                    iconResId = iconResId,
                    displayHour = if (isCurrent) "Now" else hourLabel(point.timeIso),
                    isCurrent = isCurrent
                )
            }
        }
    }
}

@Composable
private fun DailyConditionTile(
    iconResId: Int,
    displayHour: String,
    isCurrent: Boolean
) {
    BoxWithConstraints(
        modifier = Modifier
            .width(dailyConditionTileWidth)
            .height(dailyConditionTileHeight)
            .clip(RoundedCornerShape(12.dp))
            .background(
                if (isCurrent) Color.White.copy(alpha = 0.2f)
                else Color.White.copy(alpha = 0.1f)
            )
            .border(
                width = 1.dp,
                color = if (isCurrent) Color.White.copy(alpha = 0.26f)
                else Color.White.copy(alpha = 0.14f),
                shape = RoundedCornerShape(12.dp)
            )
            .padding(vertical = 10.dp, horizontal = 6.dp)
    ) {
        val density = LocalDensity.current
        val baseIconSize = 28.dp
        val baseTextSp = 13f
        val baseGap = 8.dp
        val scale = run {
            val availableHeightPx = with(density) { maxHeight.toPx() }
            val availableWidthPx = with(density) { maxWidth.toPx() }
            val baseIconPx = with(density) { baseIconSize.toPx() }
            val baseGapPx = with(density) { baseGap.toPx() }
            val baseTextPx = with(density) { baseTextSp.sp.toPx() }
            val estimatedTextWidthPx = baseTextPx * 2.2f
            val neededHeightPx = baseIconPx + baseGapPx + (baseTextPx * 1.25f)
            val neededWidthPx = max(baseIconPx, estimatedTextWidthPx)
            val heightScale = if (neededHeightPx > 0f) {
                (availableHeightPx / neededHeightPx).coerceAtMost(1f)
            } else {
                1f
            }
            val widthScale = if (neededWidthPx > 0f) {
                (availableWidthPx / neededWidthPx).coerceAtMost(1f)
            } else {
                1f
            }
            min(heightScale, widthScale).coerceAtLeast(0.6f)
        }
        val scaledIconSize = (baseIconSize * scale).coerceIn(16.dp, baseIconSize)
        val scaledTextSp = (baseTextSp * scale).coerceIn(10f, baseTextSp)
        val scaledGap = (baseGap * scale).coerceAtLeast(4.dp)

        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier.size(scaledIconSize),
                contentAlignment = Alignment.Center
            ) {
                if (iconResId != 0) {
                    Image(
                        painter = painterResource(id = iconResId),
                        contentDescription = null,
                        modifier = Modifier.size(scaledIconSize),
                        colorFilter = ColorFilter.tint(Color(0xFFD6D9DE))
                    )
                } else {
                    val fallbackDotSize = (scaledIconSize * 0.22f).coerceAtLeast(4.dp)
                    Box(
                        modifier = Modifier
                            .size(fallbackDotSize)
                            .background(Color.White.copy(alpha = 0.22f), CircleShape)
                    )
                }
            }
            Spacer(modifier = Modifier.height(scaledGap))
            Text(
                text = displayHour,
                color = if (isCurrent) Color.White.copy(alpha = 0.92f) else Color.White.copy(alpha = 0.7f),
                fontSize = scaledTextSp.sp,
                fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Medium,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun DailyFallbackText(message: String) {
    Text(
        text = message,
        color = Color.White.copy(alpha = 0.58f),
        fontSize = 12.sp,
        fontWeight = FontWeight.Medium
    )
}

private val dailyDetailWidgetHeight = 132.dp
private val dailyConditionTileWidth = 52.dp
private val dailyConditionTileHeight = 96.dp

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
        val strokeWidth = max(2f, size.height * 0.65f)
        val baseGradient = Brush.horizontalGradient(
            colors = listOf(
                Color(0xFF6EC9F7).copy(alpha = 0.28f),
                Color(0xFF0288D1).copy(alpha = 0.28f)
            ),
            startX = 0f,
            endX = size.width
        )
        val highlightGradient = Brush.horizontalGradient(
            colors = listOf(
                Color(0xFF6EC9F7),
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

private fun buildDayHourlyPoints(
    dateIso: String?,
    dayOffset: Int,
    hourlyTimes: List<String>,
    hourlyWeatherCodes: List<Int>,
    hourlyTemperatures: List<Double>,
    hourlyPrecipitationProbabilities: List<Int>,
    hourlyPrecipitations: List<Double>
): List<DayHourlyPoint> {
    if (hourlyTimes.isEmpty()) return emptyList()

    val dayKey = dateIso ?: fallbackDateIso(dayOffset)
    val dayPrefix = "${dayKey}T"
    val indices = hourlyTimes.indices.filter { index ->
        hourlyTimes[index].startsWith(dayPrefix)
    }
    if (indices.isEmpty()) return emptyList()

    return indices.map { index ->
        DayHourlyPoint(
            timeIso = hourlyTimes[index],
            weatherCode = hourlyWeatherCodes.getOrNull(index),
            temperature = hourlyTemperatures.getOrNull(index),
            precipitationProbability = hourlyPrecipitationProbabilities.getOrNull(index),
            precipitation = hourlyPrecipitations.getOrNull(index)
        )
    }
}

private fun fallbackDateIso(dayOffset: Int): String {
    val dayDate = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, dayOffset) }.time
    return SimpleDateFormat("yyyy-MM-dd", Locale.US).format(dayDate)
}

private fun resolveDayGraphCurrentTimeIso(
    dateIso: String?,
    candidateCurrentTimeIso: String?,
    fallbackTimeIso: String?
): String? {
    if (dateIso != null && candidateCurrentTimeIso?.startsWith(dateIso) == true) {
        return candidateCurrentTimeIso
    }
    return fallbackTimeIso
}

private fun sampleDayHourlyPoints(
    points: List<DayHourlyPoint>,
    maxItems: Int
): List<DayHourlyPoint> {
    if (points.isEmpty() || maxItems <= 0) return emptyList()
    if (points.size <= maxItems) return points

    val lastIndex = points.lastIndex
    val result = mutableListOf<DayHourlyPoint>()
    for (i in 0 until maxItems) {
        val mappedIndex = ((i.toFloat() / (maxItems - 1).coerceAtLeast(1)) * lastIndex)
            .roundToInt()
            .coerceIn(0, lastIndex)
        result += points[mappedIndex]
    }
    return result.distinctBy { it.timeIso }
}

private fun hourLabel(timeIso: String): String {
    return timeIso
        .substringAfter('T', "")
        .takeIf { it.length >= 2 }
        ?.substring(0, 2)
        ?: "--"
}

private fun toHourKey(timeIso: String?): String? {
    if (timeIso.isNullOrBlank()) return null
    val separatorIndex = timeIso.indexOf('T')
    if (separatorIndex <= 0 || separatorIndex + 3 > timeIso.length) return null
    return timeIso.substring(0, separatorIndex + 3)
}

private fun normalizeWeatherCode(code: Int?): Int? {
    return when (code) {
        null -> null
        56, 57 -> 55
        63 -> 62
        72 -> 73
        else -> code
    }
}

private fun getDailyWeatherIconRes(code: Int, context: android.content.Context): Int {
    @Suppress("DiscouragedApi")
    return context.resources.getIdentifier("wmo_$code", "drawable", context.packageName)
}

private enum class DailyDetailMode(
    val icon: ImageVector,
    val contentDescription: String
) {
    TEMPERATURE(
        icon = Icons.Filled.Thermostat,
        contentDescription = "Temperature"
    ),
    PRECIPITATION(
        icon = Icons.Filled.WaterDrop,
        contentDescription = "Precipitation"
    ),
    CONDITIONS(
        icon = Icons.Filled.Cloud,
        contentDescription = "Conditions"
    )
}

private data class DailyForecastRow(
    val index: Int,
    val dateIso: String?,
    val dayLabel: String,
    val weatherCode: Int?,
    val minTemp: Double?,
    val maxTemp: Double?,
    val precipitationProbability: Double?
)

private data class DayHourlyPoint(
    val timeIso: String,
    val weatherCode: Int?,
    val temperature: Double?,
    val precipitationProbability: Int?,
    val precipitation: Double?
)
