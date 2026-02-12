package com.example.anemoi.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.LocalOverscrollConfiguration
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.anemoi.viewmodel.WeatherUiState
import java.util.Calendar

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun WeatherDetailsSheet(
    uiState: WeatherUiState,
    handleHeight: Dp,
    onHandleClick: () -> Unit,
    isExpanded: Boolean = false,
    showHandle: Boolean = true,
    resetScrollKey: Any? = null,
    headerContent: (@Composable () -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    val detailsListState = rememberLazyListState()
    if (resetScrollKey != null) {
        LaunchedEffect(resetScrollKey) {
            detailsListState.scrollToItem(0)
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
        val staleServeWindowMs = 12 * 60 * 60 * 1000L
        val now = System.currentTimeMillis()

        val rawWeather = key?.let { uiState.weatherMap[it] }
        val currentUpdatedAt = key?.let { uiState.currentUpdateTimeMap[it] } ?: 0L
        val hourlyUpdatedAt = key?.let { uiState.hourlyUpdateTimeMap[it] } ?: 0L
        val dailyUpdatedAt = key?.let { uiState.dailyUpdateTimeMap[it] } ?: 0L

        val currentUsable = rawWeather?.currentWeather != null &&
            currentUpdatedAt > 0L &&
            now - currentUpdatedAt <= staleServeWindowMs
        val hourlyUsable = rawWeather?.hourly != null &&
            hourlyUpdatedAt > 0L &&
            now - hourlyUpdatedAt <= staleServeWindowMs
        val dailyUsable = rawWeather?.daily != null &&
            dailyUpdatedAt > 0L &&
            now - dailyUpdatedAt <= staleServeWindowMs

        val weather = rawWeather?.copy(
            currentWeather = if (currentUsable) rawWeather.currentWeather else null,
            hourly = if (hourlyUsable) rawWeather.hourly else null,
            daily = if (dailyUsable) rawWeather.daily else null
        )

        val daily = weather?.daily
        val h = daily?.daylightDuration?.firstOrNull()?.div(3600.0) ?: 12.0

        fun parseToMinutes(iso: String?): Int? {
            return try {
                val timePart = iso?.split("T")?.getOrNull(1) ?: return null
                val parts = timePart.split(":")
                parts[0].toInt() * 60 + parts[1].toInt()
            } catch (e: Exception) { null }
        }

        val riseMin = parseToMinutes(daily?.sunrise?.firstOrNull())
        val setMin = parseToMinutes(daily?.sunset?.firstOrNull())

        val locationMinutes = parseToMinutes(weather?.currentWeather?.time) ?: run {
            val nowCal = Calendar.getInstance()
            nowCal.get(Calendar.HOUR_OF_DAY) * 60 + nowCal.get(Calendar.MINUTE)
        }

        val daylightLabel = when {
            riseMin == null || setMin == null -> "SUNRISE / SUNSET"
            locationMinutes < (riseMin ?: 0) -> "SUNRISE"
            locationMinutes < (setMin ?: 1440) -> "SUNSET"
            else -> "SUNRISE"
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
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = horizontalPadding),
                    state = detailsListState,
                    userScrollEnabled = isExpanded || !showHandle,
                    verticalArrangement = Arrangement.spacedBy(widgetGap)
                ) {
                    item {
                        Spacer(modifier = Modifier.height(if (showHandle) widgetGap else 0.dp))
                    }

                    if (headerContent != null) {
                        item {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                headerContent()
                                Spacer(modifier = Modifier.height(8.dp))
                            }
                        }
                    }

                    item {
                        DetailWidgetContainer(
                            label = "TEMPERATURE",
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(squareSize),
                            contentTopGap = 0.dp
                        ) { widgetTopToGraphInset ->
                            TemperatureGraph(
                                times = weather?.hourly?.time ?: emptyList(),
                                temperatures = weather?.hourly?.temperatures ?: emptyList(),
                                currentTemp = weather?.currentWeather?.temperature,
                                currentTimeIso = weather?.currentWeather?.time,
                                tempUnit = uiState.tempUnit,
                                widgetTopToGraphTopInset = widgetTopToGraphInset,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
                    item {
                        DetailWidgetContainer(
                            label = "PRECIPITATION",
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(squareSize),
                            contentTopGap = 0.dp
                        ) { widgetTopToGraphInset ->
                            PrecipitationGraph(
                                times = weather?.hourly?.time ?: emptyList(),
                                probabilities = weather?.hourly?.precipitationProbability ?: emptyList(),
                                precipitations = weather?.hourly?.precipitation ?: emptyList(),
                                currentTimeIso = weather?.currentWeather?.time,
                                widgetTopToGraphTopInset = widgetTopToGraphInset,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
                    item {
                        DailyForecastWidget(
                            dates = weather?.daily?.time ?: emptyList(),
                            weatherCodes = weather?.daily?.weatherCodes ?: emptyList(),
                            minTemperatures = weather?.daily?.minTemp ?: emptyList(),
                            maxTemperatures = weather?.daily?.maxTemp ?: emptyList(),
                            precipitationProbabilityMax = weather?.daily?.precipitationProbabilityMax ?: emptyList(),
                            tempUnit = uiState.tempUnit,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    item {
                        HourlyForecastWidget(
                            times = weather?.hourly?.time ?: emptyList(),
                            weatherCodes = weather?.hourly?.weatherCodes ?: emptyList(),
                            temperatures = weather?.hourly?.temperatures ?: emptyList(),
                            currentTimeIso = weather?.currentWeather?.time,
                            tempUnit = uiState.tempUnit,
                            isExpanded = isExpanded,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(squareSize)
                        )
                    }
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(widgetGap)
                        ) {
                            DetailWidgetContainer(
                                label = "UV INDEX",
                                modifier = Modifier.size(squareSize),
                                contentTopGap = 8.dp
                            ) { _ ->
                                val currentTimeStr = weather?.currentWeather?.time ?: ""
                                val currentHourIdx = weather?.hourly?.time?.indexOfFirst { it.startsWith(currentTimeStr.substringBefore(":")) } ?: -1
                                val currentUV = if (currentHourIdx != -1 && weather?.hourly?.uvIndex != null && currentHourIdx < weather.hourly.uvIndex.size) {
                                    weather.hourly.uvIndex[currentHourIdx]
                                } else null

                                UVIndexWidget(
                                    currentUV = currentUV,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }

                            DetailWidgetContainer(
                                label = "PRESSURE",
                                modifier = Modifier.size(squareSize),
                                contentTopGap = 8.dp
                            ) { _ ->
                                val hourlyPressures = weather?.hourly?.pressures ?: emptyList()
                                val currentTimeStr = weather?.currentWeather?.time ?: ""
                                val currentHourIdx = weather?.hourly?.time?.indexOfFirst { it.startsWith(currentTimeStr.substringBefore(":")) } ?: -1

                                val currentPressure = if (currentHourIdx != -1 && currentHourIdx < hourlyPressures.size) {
                                    hourlyPressures[currentHourIdx]
                                } else {
                                    null
                                }

                                val minP = if (hourlyPressures.isNotEmpty()) hourlyPressures.take(24).minOrNull() else null
                                val maxP = if (hourlyPressures.isNotEmpty()) hourlyPressures.take(24).maxOrNull() else null

                                val trend = if (currentHourIdx != -1 && currentHourIdx + 3 < hourlyPressures.size) {
                                    hourlyPressures[currentHourIdx + 3] - hourlyPressures[currentHourIdx]
                                } else null

                                PressureDial(
                                    currentPressure = currentPressure,
                                    minPressure = minP,
                                    maxPressure = maxP,
                                    trend = trend,
                                    unit = uiState.pressureUnit,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                        }
                    }
                    item {
                        DetailWidgetContainer(
                            label = "WIND",
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(squareSize),
                            contentTopGap = 4.dp
                        ) { _ ->
                            val currentTimeIso = weather?.currentWeather?.time
                            val currentHourPrefix = currentTimeIso?.substringBefore(":")
                            val currentHourIdx = if (!currentHourPrefix.isNullOrEmpty()) {
                                weather?.hourly?.time?.indexOfFirst {
                                    it.startsWith(currentHourPrefix)
                                } ?: -1
                            } else {
                                -1
                            }

                            val hourlyWindSpeeds = weather?.hourly?.windSpeeds ?: emptyList()
                            val hourlyWindDirections = weather?.hourly?.windDirections ?: emptyList()
                            val hourlyWindGusts = weather?.hourly?.windGusts ?: emptyList()

                            val fallbackWindSpeed = if (currentHourIdx != -1 && currentHourIdx < hourlyWindSpeeds.size) {
                                hourlyWindSpeeds[currentHourIdx]
                            } else {
                                null
                            }

                            val fallbackWindDirection = if (currentHourIdx != -1 && currentHourIdx < hourlyWindDirections.size) {
                                hourlyWindDirections[currentHourIdx]
                            } else {
                                null
                            }

                            val currentGust = if (currentHourIdx != -1 && currentHourIdx < hourlyWindGusts.size) {
                                hourlyWindGusts[currentHourIdx]
                            } else {
                                null
                            }

                            val todayPrefix = currentTimeIso?.substringBefore("T")
                                ?: weather?.hourly?.time?.firstOrNull()?.substringBefore("T")

                            val maxGustToday = if (!todayPrefix.isNullOrEmpty() && hourlyWindGusts.isNotEmpty()) {
                                weather?.hourly?.time
                                    ?.mapIndexedNotNull { index, iso ->
                                        if (index < hourlyWindGusts.size && iso.startsWith(todayPrefix)) {
                                            hourlyWindGusts[index]
                                        } else {
                                            null
                                        }
                                    }
                                    ?.maxOrNull()
                            } else {
                                null
                            }

                            WindCompassWidget(
                                windSpeedKmh = weather?.currentWeather?.windSpeed ?: fallbackWindSpeed,
                                windDirectionDegrees = weather?.currentWeather?.windDirection ?: fallbackWindDirection,
                                gustSpeedKmh = currentGust,
                                maxGustKmh = maxGustToday,
                                unit = uiState.windUnit,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
                    item {
                        DetailWidgetContainer(
                            label = daylightLabel,
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
                    }
                    item {
                        Spacer(modifier = Modifier.height(90.dp))
                    }
                }
            }
        }

    }
}

@Composable
fun DetailWidgetContainer(
    label: String,
    modifier: Modifier = Modifier,
    contentTopGap: Dp = 8.dp,
    content: @Composable BoxScope.(widgetTopToGraphInset: Dp) -> Unit
) {
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
            Text(
                text = label,
                color = Color.White.copy(alpha = 0.4f),
                fontSize = 10.sp,
                lineHeight = labelLineHeight,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
            Spacer(modifier = Modifier.height(contentTopGap))
            Box(
                modifier = Modifier.weight(1f),
                content = { content(widgetTopToGraphInset) }
            )
            Spacer(modifier = Modifier.height(outerGap))
        }
    }
}
