package com.example.anemoi.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.anemoi.viewmodel.WeatherUiState
import java.util.Calendar

@Composable
fun WeatherDetailsSheet(
    uiState: WeatherUiState,
    handleHeight: Dp,
    onHandleClick: () -> Unit,
    isExpanded: Boolean = false,
    modifier: Modifier = Modifier
) {
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val widgetGap = 20.dp
        val horizontalPadding = 24.dp
        val availableWidth = maxWidth - (horizontalPadding * 2)
        val squareSize = (availableWidth - widgetGap) / 2

        Column(modifier = Modifier.fillMaxSize()) {
            // Drag handle area (Fixed at the top of the sheet)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(handleHeight)
                    .clickable { onHandleClick() },
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .width(48.dp)
                        .height(5.dp)
                        .clip(RoundedCornerShape(2.5.dp))
                        .background(Color.White.copy(alpha = 0.6f))
                )
            }
            
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = horizontalPadding),
                contentPadding = PaddingValues(top = 8.dp, bottom = 180.dp),
                verticalArrangement = Arrangement.spacedBy(widgetGap)
            ) {
                val selectedLoc = uiState.selectedLocation
                val weather = selectedLoc?.let { uiState.weatherMap["${it.lat},${it.lon}"] }
                
                // Hourly Forecast Widget at the top - Now called directly to be taller and without title
                item {
                    HourlyForecastWidget(
                        times = weather?.hourly?.time ?: emptyList(),
                        weatherCodes = weather?.hourly?.weatherCodes ?: emptyList(),
                        temperatures = weather?.hourly?.temperatures ?: emptyList(),
                        tempUnit = uiState.tempUnit,
                        isExpanded = isExpanded,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(115.dp)
                    )
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
                
                val riseMin = parseToMinutes(daily?.sunrise?.firstOrNull())
                val setMin = parseToMinutes(daily?.sunset?.firstOrNull())
                
                val locationMinutes = parseToMinutes(weather?.currentWeather?.time) ?: run {
                    val now = Calendar.getInstance()
                    now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE)
                }

                val daylightLabel = when {
                    riseMin == null || setMin == null -> "SUNRISE / SUNSET"
                    locationMinutes < (riseMin ?: 0) -> "SUNRISE"
                    locationMinutes < (setMin ?: 1440) -> "SUNSET"
                    else -> "SUNRISE"
                }
                
                item {
                    DetailWidgetContainer(
                        label = "TEMPERATURE",
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(squareSize),
                        contentTopGap = 0.dp
                    ) {
                        TemperatureGraph(
                            times = weather?.hourly?.time ?: emptyList(),
                            temperatures = weather?.hourly?.temperatures ?: emptyList(),
                            currentTemp = weather?.currentWeather?.temperature,
                            currentTimeIso = weather?.currentWeather?.time,
                            tempUnit = uiState.tempUnit,
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
                    ) {
                        PrecipitationGraph(
                            times = weather?.hourly?.time ?: emptyList(),
                            probabilities = weather?.hourly?.precipitationProbability ?: emptyList(),
                            precipitations = weather?.hourly?.precipitation ?: emptyList(),
                            currentTimeIso = weather?.currentWeather?.time,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }

                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(widgetGap)
                    ) {
                        DetailWidgetContainer(
                            label = "UV INDEX",
                            modifier = Modifier.size(squareSize),
                            contentTopGap = 0.dp
                        ) {
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
                            contentTopGap = 0.dp
                        ) {
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
                        label = daylightLabel,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(squareSize),
                        contentTopGap = 0.dp
                    ) {
                        DaylightGraph(
                            daylightHours = h,
                            nowMinutes = locationMinutes,
                            sunriseMinutes = riseMin,
                            sunsetMinutes = setMin,
                            modifier = Modifier.fillMaxSize()
                        )
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
    content: @Composable BoxScope.() -> Unit
) {
    val outerGap = 12.dp 
    
    Box(modifier = modifier) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(28.dp))
                .background(Color.White.copy(alpha = 0.1f))
        )
        
        Column(modifier = Modifier.fillMaxSize()) {
            Spacer(modifier = Modifier.height(outerGap))
            Text(
                text = label,
                color = Color.White.copy(alpha = 0.4f),
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
            Spacer(modifier = Modifier.height(contentTopGap))
            Box(
                modifier = Modifier.weight(1f),
                content = content
            )
            Spacer(modifier = Modifier.height(outerGap))
        }
    }
}
