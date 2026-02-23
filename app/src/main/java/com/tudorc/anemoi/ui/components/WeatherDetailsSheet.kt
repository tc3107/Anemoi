package com.tudorc.anemoi.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.LocalOverscrollConfiguration
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.Velocity
import com.tudorc.anemoi.util.PerformanceProfiler
import com.tudorc.anemoi.util.WeatherFreshnessConfig
import com.tudorc.anemoi.viewmodel.WeatherUiState
import kotlinx.coroutines.flow.distinctUntilChanged
import java.util.Calendar

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun WeatherDetailsSheet(
    uiState: WeatherUiState,
    handleHeight: Dp,
    onHandleClick: () -> Unit,
    modifier: Modifier = Modifier,
    isExpanded: Boolean = false,
    showHandle: Boolean = true,
    resetScrollKey: Any? = null,
    headerContent: (@Composable () -> Unit)? = null
) {
    val haptic = LocalHapticFeedback.current
    val detailsScrollState = rememberScrollState()
    var lastVerticalEdgeHit by remember { mutableIntStateOf(0) } // -1 = top, 1 = bottom
    var edgeSessionStarted by remember { mutableStateOf(false) }
    if (resetScrollKey != null) {
        LaunchedEffect(resetScrollKey) {
            detailsScrollState.scrollTo(0)
        }
    }
    LaunchedEffect(detailsScrollState) {
        snapshotFlow {
            Triple(
                detailsScrollState.value,
                detailsScrollState.maxValue,
                detailsScrollState.isScrollInProgress
            )
        }
            .distinctUntilChanged()
            .collect { (value, maxValue, isScrolling) ->
                if (!isScrolling || maxValue <= 0) {
                    edgeSessionStarted = false
                    return@collect
                }
                val edge = when {
                    value <= 0 -> -1
                    value >= maxValue -> 1
                    else -> 0
                }
                if (!edgeSessionStarted) {
                    edgeSessionStarted = true
                    lastVerticalEdgeHit = edge
                    return@collect
                }
                if (edge == 0) {
                    lastVerticalEdgeHit = 0
                } else if (edge != lastVerticalEdgeHit) {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    lastVerticalEdgeHit = edge
                }
            }
    }
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val widgetGap = 20.dp
        val horizontalPadding = 24.dp
        val availableWidth = maxWidth - (horizontalPadding * 2)
        val squareSize = (availableWidth - widgetGap) / 2
        val handleSurfaceShape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp)
        val selectedLoc = uiState.selectedLocation
        val key = selectedLoc?.let { "${it.lat},${it.lon}" }
        val staleServeWindowMs = WeatherFreshnessConfig.STALE_SERVE_WINDOW_MS

        val rawWeather = key?.let { uiState.weatherMap[it] }
        val currentUpdatedAt = key?.let { uiState.currentUpdateTimeMap[it] } ?: 0L
        val hourlyUpdatedAt = key?.let { uiState.hourlyUpdateTimeMap[it] } ?: 0L
        val dailyUpdatedAt = key?.let { uiState.dailyUpdateTimeMap[it] } ?: 0L
        val airQualityUpdatedAt = key?.let { uiState.airQualityUpdateTimeMap[it] } ?: 0L

        val weather = remember(rawWeather, currentUpdatedAt, hourlyUpdatedAt, dailyUpdatedAt, airQualityUpdatedAt) {
            PerformanceProfiler.measure(name = "WeatherDetailsSheet/DeriveUsableWeather", category = "ui-state") {
                val now = System.currentTimeMillis()
                val currentUsable = rawWeather?.currentWeather != null &&
                    currentUpdatedAt > 0L &&
                    now - currentUpdatedAt <= staleServeWindowMs
                val hourlyUsable = rawWeather?.hourly != null &&
                    hourlyUpdatedAt > 0L &&
                    now - hourlyUpdatedAt <= staleServeWindowMs
                val dailyUsable = rawWeather?.daily != null &&
                    dailyUpdatedAt > 0L &&
                    now - dailyUpdatedAt <= staleServeWindowMs
                val airQualityUsable = rawWeather?.airQuality?.hourly != null &&
                    airQualityUpdatedAt > 0L &&
                    now - airQualityUpdatedAt <= staleServeWindowMs

                rawWeather?.copy(
                    currentWeather = if (currentUsable) rawWeather.currentWeather else null,
                    hourly = if (hourlyUsable) rawWeather.hourly else null,
                    daily = if (dailyUsable) rawWeather.daily else null,
                    airQuality = if (airQualityUsable) rawWeather.airQuality else null
                )
            }
        }

        val daily = weather?.daily
        val h = daily?.daylightDuration?.firstOrNull()?.div(3600.0) ?: 12.0

        fun parseToMinutes(iso: String?): Int? {
            return try {
                val timePart = iso?.split("T")?.getOrNull(1) ?: return null
                val parts = timePart.split(":")
                parts[0].toInt() * 60 + parts[1].toInt()
            } catch (e: Exception) { null }
        }

        val riseMin = remember(daily?.sunrise?.firstOrNull()) {
            parseToMinutes(daily?.sunrise?.firstOrNull())
        }
        val setMin = remember(daily?.sunset?.firstOrNull()) {
            parseToMinutes(daily?.sunset?.firstOrNull())
        }

        val currentTimeIso = weather?.currentWeather?.time
        val hourlyTimes = weather?.hourly?.time ?: emptyList()
        val currentHourPrefix = currentTimeIso?.substringBefore(":")
        val currentHourIdx = remember(currentHourPrefix, hourlyTimes) {
            if (currentHourPrefix.isNullOrEmpty()) {
                -1
            } else {
                hourlyTimes.indexOfFirst { it.startsWith(currentHourPrefix) }
            }
        }

        val locationMinutes = remember(currentTimeIso) {
            parseToMinutes(currentTimeIso) ?: run {
                val nowCal = Calendar.getInstance()
                nowCal.get(Calendar.HOUR_OF_DAY) * 60 + nowCal.get(Calendar.MINUTE)
            }
        }

        val daylightLabel = when {
            riseMin == null || setMin == null -> "SUNRISE / SUNSET"
            locationMinutes < (riseMin ?: 0) -> "SUNRISE"
            locationMinutes < (setMin ?: 1440) -> "SUNSET"
            else -> "SUNRISE"
        }

        val uvIndexValues = weather?.hourly?.uvIndex
        val currentUV = remember(currentHourIdx, uvIndexValues) {
            if (currentHourIdx != -1 &&
                uvIndexValues != null &&
                currentHourIdx < uvIndexValues.size
            ) {
                uvIndexValues[currentHourIdx]
            } else {
                null
            }
        }

        val hourlyPressures = weather?.hourly?.pressures ?: emptyList()
        val currentPressure = remember(currentHourIdx, hourlyPressures) {
            if (currentHourIdx != -1 && currentHourIdx < hourlyPressures.size) {
                hourlyPressures[currentHourIdx]
            } else {
                null
            }
        }
        val minPressure = remember(hourlyPressures) {
            if (hourlyPressures.isNotEmpty()) hourlyPressures.take(24).minOrNull() else null
        }
        val maxPressure = remember(hourlyPressures) {
            if (hourlyPressures.isNotEmpty()) hourlyPressures.take(24).maxOrNull() else null
        }
        val pressureTrend = remember(currentHourIdx, hourlyPressures) {
            if (currentHourIdx != -1 && currentHourIdx + 3 < hourlyPressures.size) {
                hourlyPressures[currentHourIdx + 3] - hourlyPressures[currentHourIdx]
            } else {
                null
            }
        }

        val hourlyWindSpeeds = weather?.hourly?.windSpeeds ?: emptyList()
        val hourlyWindDirections = weather?.hourly?.windDirections ?: emptyList()
        val hourlyWindGusts = weather?.hourly?.windGusts ?: emptyList()
        val fallbackWindSpeed = remember(currentHourIdx, hourlyWindSpeeds) {
            if (currentHourIdx != -1 && currentHourIdx < hourlyWindSpeeds.size) {
                hourlyWindSpeeds[currentHourIdx]
            } else {
                null
            }
        }
        val fallbackWindDirection = remember(currentHourIdx, hourlyWindDirections) {
            if (currentHourIdx != -1 && currentHourIdx < hourlyWindDirections.size) {
                hourlyWindDirections[currentHourIdx]
            } else {
                null
            }
        }
        val currentGust = remember(currentHourIdx, hourlyWindGusts) {
            if (currentHourIdx != -1 && currentHourIdx < hourlyWindGusts.size) {
                hourlyWindGusts[currentHourIdx]
            } else {
                null
            }
        }
        val todayPrefix = remember(currentTimeIso, hourlyTimes) {
            currentTimeIso?.substringBefore("T")
                ?: hourlyTimes.firstOrNull()?.substringBefore("T")
        }
        val maxGustToday = remember(todayPrefix, hourlyTimes, hourlyWindGusts) {
            if (!todayPrefix.isNullOrEmpty() && hourlyWindGusts.isNotEmpty()) {
                hourlyTimes
                    .mapIndexedNotNull { index, iso ->
                        if (index < hourlyWindGusts.size && iso.startsWith(todayPrefix)) {
                            hourlyWindGusts[index]
                        } else {
                            null
                        }
                    }
                    .maxOrNull()
            } else {
                null
            }
        }

        val airQualityHourly = weather?.airQuality?.hourly
        val airQualityTimes = airQualityHourly?.time ?: emptyList()
        val airQualityDayPrefix = remember(todayPrefix, airQualityTimes) {
            resolveAirQualityDayPrefix(
                preferredDayPrefix = todayPrefix,
                hourlyTimes = airQualityTimes
            )
        }
        val pollutionMetrics = remember(
            airQualityDayPrefix,
            currentHourPrefix,
            airQualityTimes,
            airQualityHourly
        ) {
            fun metric(label: String, values: List<Double?>?): ParticulateMetricBar {
                val (todayMax, rangeMax) = dayAndRangeMax(
                    dayPrefix = airQualityDayPrefix,
                    hourlyTimes = airQualityTimes,
                    values = values
                )
                return ParticulateMetricBar(
                    label = label,
                    currentValue = valueForCurrentHour(
                        currentHourPrefix = currentHourPrefix,
                        dayPrefix = airQualityDayPrefix,
                        hourlyTimes = airQualityTimes,
                        values = values
                    ),
                    todayMax = todayMax,
                    rangeMax = rangeMax
                )
            }

            listOf(
                metric("DUST", airQualityHourly?.dust),
                metric("PM10", airQualityHourly?.pm10),
                metric("PM2.5", airQualityHourly?.pm25)
            )
        }
        val pollenMetrics = remember(
            airQualityDayPrefix,
            currentHourPrefix,
            airQualityTimes,
            airQualityHourly
        ) {
            val treeSeries = mergedHourlyMax(
                airQualityHourly?.alderPollen,
                airQualityHourly?.birchPollen
            )
            val weedSeries = mergedHourlyMax(
                airQualityHourly?.mugwortPollen,
                airQualityHourly?.olivePollen,
                airQualityHourly?.ragweedPollen
            )

            fun metric(label: String, values: List<Double?>): ParticulateMetricBar {
                val (todayMax, rangeMax) = dayAndRangeMax(
                    dayPrefix = airQualityDayPrefix,
                    hourlyTimes = airQualityTimes,
                    values = values
                )
                return ParticulateMetricBar(
                    label = label,
                    currentValue = valueForCurrentHour(
                        currentHourPrefix = currentHourPrefix,
                        dayPrefix = airQualityDayPrefix,
                        hourlyTimes = airQualityTimes,
                        values = values
                    ),
                    todayMax = todayMax,
                    rangeMax = rangeMax
                )
            }

            listOf(
                metric("TREES", treeSeries),
                metric("GRASS", airQualityHourly?.grassPollen.orEmpty()),
                metric("WEEDS", weedSeries)
            )
        }

        Column(modifier = Modifier.fillMaxSize()) {
            if (showHandle) {
                // Drag handle area (Fixed at the top of the sheet)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(handleHeight)
                        .clip(handleSurfaceShape)
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
                            shape = handleSurfaceShape
                        )
                        .clickable {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onHandleClick()
                        },
                    contentAlignment = Alignment.Center
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
                            .width(48.dp)
                            .height(5.dp)
                            .clip(RoundedCornerShape(2.5.dp))
                            .background(Color.White.copy(alpha = 0.6f))
                    )
                }
            }
            
            CompositionLocalProvider(LocalOverscrollConfiguration provides null) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(
                            state = detailsScrollState,
                            enabled = isExpanded || !showHandle
                        )
                        .padding(horizontal = horizontalPadding),
                    verticalArrangement = Arrangement.spacedBy(widgetGap)
                ) {
                    Spacer(modifier = Modifier.height(if (showHandle) widgetGap else 0.dp))

                    if (headerContent != null) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            headerContent()
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                    }

                    DetailWidgetContainer(
                        label = "TEMPERATURE",
                        infoTitle = "Temperature",
                        infoMessage = "Shows the hourly air temperature trend. " +
                            "The chart tracks changes through the day around your current local time.",
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(squareSize),
                        contentTopGap = 0.dp
                    ) { widgetTopToGraphInset ->
                        TemperatureGraph(
                            times = hourlyTimes,
                            temperatures = weather?.hourly?.temperatures ?: emptyList(),
                            currentTemp = weather?.currentWeather?.temperature,
                            currentTimeIso = currentTimeIso,
                            tempUnit = uiState.tempUnit,
                            widgetTopToGraphTopInset = widgetTopToGraphInset,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                    DetailWidgetContainer(
                        label = "PRECIPITATION",
                        infoTitle = "Precipitation",
                        infoMessage = "Shows expected precipitation chance and amount through the day. " +
                            "Use it to spot likely rain windows and intensity changes.",
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(squareSize),
                        contentTopGap = 0.dp
                    ) { widgetTopToGraphInset ->
                        PrecipitationGraph(
                            times = hourlyTimes,
                            probabilities = weather?.hourly?.precipitationProbability ?: emptyList(),
                            precipitations = weather?.hourly?.precipitation ?: emptyList(),
                            currentTimeIso = currentTimeIso,
                            widgetTopToGraphTopInset = widgetTopToGraphInset,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                    DailyForecastWidget(
                        infoTitle = "10-Day Forecast",
                        infoMessage = "Summarizes the next ten days with min and max temperatures, " +
                            "weather condition icons, and precipitation chance. Tap any day for expanded details.",
                        dates = weather?.daily?.time ?: emptyList(),
                        weatherCodes = weather?.daily?.weatherCodes ?: emptyList(),
                        minTemperatures = weather?.daily?.minTemp ?: emptyList(),
                        maxTemperatures = weather?.daily?.maxTemp ?: emptyList(),
                        precipitationProbabilityMax = weather?.daily?.precipitationProbabilityMax ?: emptyList(),
                        hourlyTimes = hourlyTimes,
                        hourlyWeatherCodes = weather?.hourly?.weatherCodes ?: emptyList(),
                        hourlyTemperatures = weather?.hourly?.temperatures ?: emptyList(),
                        hourlyPrecipitationProbabilities = weather?.hourly?.precipitationProbability ?: emptyList(),
                        hourlyPrecipitations = weather?.hourly?.precipitation ?: emptyList(),
                        currentTimeIso = currentTimeIso,
                        tempUnit = uiState.tempUnit,
                        modifier = Modifier.fillMaxWidth()
                    )
                    HourlyForecastWidget(
                        infoTitle = "Hourly Condition Changes",
                        infoMessage = "Shows the next 24 hours but highlights when conditions actually change. " +
                            "Each tile includes time, weather icon, and temperature.",
                        times = hourlyTimes,
                        weatherCodes = weather?.hourly?.weatherCodes ?: emptyList(),
                        temperatures = weather?.hourly?.temperatures ?: emptyList(),
                        currentTimeIso = currentTimeIso,
                        tempUnit = uiState.tempUnit,
                        isExpanded = isExpanded,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(squareSize)
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(widgetGap)
                    ) {
                        DetailWidgetContainer(
                            label = "UV INDEX",
                            infoTitle = "UV Index",
                            infoMessage = "Displays current UV intensity and risk level. " +
                                "Higher values mean stronger sun exposure and shorter safe time outdoors.",
                            modifier = Modifier.size(squareSize),
                            contentTopGap = 8.dp
                        ) { _ ->
                            UVIndexWidget(
                                currentUV = currentUV,
                                modifier = Modifier.fillMaxSize()
                            )
                        }

                        DetailWidgetContainer(
                            label = "PRESSURE",
                            infoTitle = "Pressure",
                            infoMessage = "Shows current surface pressure with a local range and trend. " +
                                "Rising or falling pressure can signal weather pattern changes.",
                            modifier = Modifier.size(squareSize),
                            contentTopGap = 8.dp
                        ) { _ ->
                            PressureDial(
                                currentPressure = currentPressure,
                                minPressure = minPressure,
                                maxPressure = maxPressure,
                                trend = pressureTrend,
                                unit = uiState.pressureUnit,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
                    DetailWidgetContainer(
                        label = "PARTICULATES",
                        infoTitle = "Particulates",
                        infoMessage = "Left side shows Dust, PM10 and PM2.5. Right side shows pollen categories " +
                            "(Trees, Grass, Weeds). Bars represent today's max versus the rolling past 7 plus next 7 day range. " +
                            "Small gray numbers are the current readings.",
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(squareSize),
                        contentTopGap = 4.dp
                    ) { _ ->
                        ParticulatesWidget(
                            pollutionMetrics = pollutionMetrics,
                            pollenMetrics = pollenMetrics,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                    DetailWidgetContainer(
                        label = "WIND",
                        infoTitle = "Wind",
                        infoMessage = "Shows wind speed and heading, plus current gust and today's max gust. " +
                            "Compass orientation can be locked to north in settings.",
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(squareSize),
                        contentTopGap = 4.dp
                    ) { _ ->
                        WindCompassWidget(
                            windSpeedKmh = weather?.currentWeather?.windSpeed ?: fallbackWindSpeed,
                            windDirectionDegrees = weather?.currentWeather?.windDirection ?: fallbackWindDirection,
                            gustSpeedKmh = currentGust,
                            maxGustKmh = maxGustToday,
                            unit = uiState.windUnit,
                            lockDialToNorth = uiState.isCompassNorthLocked,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                    DetailWidgetContainer(
                        label = daylightLabel,
                        infoTitle = "Sunrise and Sunset",
                        infoMessage = "Shows daylight progression for the current day, including sunrise, sunset, " +
                            "and where the current local time sits in that cycle.",
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(squareSize),
                        contentTopGap = 0.dp
                    ) { _ ->
                        DaylightGraph(
                            daylightHours = h,
                            nowMinutes = locationMinutes,
                            sunriseMinutes = riseMin,
                            sunsetMinutes = setMin,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                    Spacer(modifier = Modifier.height(90.dp))
                }
            }
        }

    }
}

@Composable
fun DetailWidgetContainer(
    label: String,
    infoTitle: String? = null,
    infoMessage: String? = null,
    modifier: Modifier = Modifier,
    contentTopGap: Dp = 8.dp,
    content: @Composable BoxScope.(widgetTopToGraphInset: Dp) -> Unit
) {
    var showInfoDialog by remember(infoTitle, infoMessage) { mutableStateOf(false) }
    val outerGap = 12.dp 
    val labelLineHeight = 12.sp
    val density = LocalDensity.current
    val widgetTopToGraphInset = outerGap + with(density) { labelLineHeight.toDp() } + contentTopGap
    val surfaceShape = RoundedCornerShape(28.dp)
    
    Box(modifier = modifier) {
        Box(
            modifier = Modifier
                .fillMaxSize()
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
        }
        
        Column(modifier = Modifier.fillMaxSize()) {
            Spacer(modifier = Modifier.height(outerGap))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = label,
                    color = Color.White.copy(alpha = 0.4f),
                    fontSize = 10.sp,
                    lineHeight = labelLineHeight,
                    fontWeight = FontWeight.Medium
                )
                if (!infoTitle.isNullOrBlank() && !infoMessage.isNullOrBlank()) {
                    Spacer(modifier = Modifier.width(6.dp))
                    Box(
                        modifier = Modifier
                            .size(14.dp)
                            .clickable { showInfoDialog = true },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Info,
                            contentDescription = "Widget info",
                            tint = Color.White.copy(alpha = 0.55f),
                            modifier = Modifier.size(12.dp)
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(contentTopGap))
            Box(
                modifier = Modifier.weight(1f),
                content = { content(widgetTopToGraphInset) }
            )
            Spacer(modifier = Modifier.height(outerGap))
        }
    }

    if (showInfoDialog && !infoTitle.isNullOrBlank() && !infoMessage.isNullOrBlank()) {
        WidgetInfoPageDialog(
            title = infoTitle,
            message = infoMessage,
            onDismiss = { showInfoDialog = false }
        )
    }
}

private fun resolveAirQualityDayPrefix(
    preferredDayPrefix: String?,
    hourlyTimes: List<String>
): String? {
    if (!preferredDayPrefix.isNullOrBlank()) {
        return preferredDayPrefix
    }
    return hourlyTimes.firstOrNull()?.substringBefore("T")
}

private fun dayAndRangeMax(
    dayPrefix: String?,
    hourlyTimes: List<String>,
    values: List<Double?>?
): Pair<Double?, Double?> {
    if (values.isNullOrEmpty() || hourlyTimes.isEmpty()) {
        return null to null
    }

    val rangeMax = values
        .filterNotNull()
        .filter { it.isFinite() }
        .maxOrNull()

    val todayMax = if (dayPrefix.isNullOrBlank()) {
        null
    } else {
        hourlyTimes
            .mapIndexedNotNull { index, iso ->
                if (iso.startsWith(dayPrefix) && index < values.size) {
                    values[index]
                } else {
                    null
                }
            }
            .filter { it.isFinite() }
            .maxOrNull()
    }

    return todayMax to rangeMax
}

private fun valueForCurrentHour(
    currentHourPrefix: String?,
    dayPrefix: String?,
    hourlyTimes: List<String>,
    values: List<Double?>?
): Double? {
    if (values.isNullOrEmpty() || hourlyTimes.isEmpty()) return null

    if (!currentHourPrefix.isNullOrBlank()) {
        val currentIndex = hourlyTimes.indexOfFirst { iso -> iso.startsWith(currentHourPrefix) }
        if (currentIndex >= 0 && currentIndex < values.size) {
            val current = values[currentIndex]
            if (current != null && current.isFinite()) {
                return current
            }
        }
    }

    if (!dayPrefix.isNullOrBlank()) {
        val latestToday = hourlyTimes
            .mapIndexedNotNull { index, iso ->
                if (iso.startsWith(dayPrefix) && index < values.size) {
                    values[index]
                } else {
                    null
                }
            }
            .filter { it.isFinite() }
            .lastOrNull()
        if (latestToday != null) {
            return latestToday
        }
    }

    return values
        .filterNotNull()
        .filter { it.isFinite() }
        .lastOrNull()
}

private fun mergedHourlyMax(vararg series: List<Double?>?): List<Double?> {
    val maxSize = series.maxOfOrNull { it?.size ?: 0 } ?: 0
    if (maxSize == 0) return emptyList()

    return List(maxSize) { index ->
        series
            .mapNotNull { values ->
                values?.getOrNull(index)
            }
            .filter { it.isFinite() }
            .maxOrNull()
    }
}
