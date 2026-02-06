package com.example.anemoi.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.anemoi.data.TempUnit
import com.example.anemoi.data.WeatherResponse
import com.example.anemoi.util.formatTemp

@Composable
fun WeatherDisplay(
    weather: WeatherResponse?,
    tempUnit: TempUnit,
    isStale: Boolean,
    showDashesOverride: Boolean = false,
    textAlpha: Float = 0.8f
) {
    // Condition: Use dashes if explicitly overridden (e.g. location not found)
    // OR if the data is stale OR if no weather data exists.
    val showDashes = showDashesOverride || isStale || weather == null
    
    val current = weather?.currentWeather
    val hourly = weather?.hourly
    val daily = weather?.daily

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Temperature
        val tempText = if (current != null && !showDashes) {
            formatTemp(current.temperature, tempUnit)
        } else {
            "--°"
        }
        Text(
            text = tempText,
            fontSize = 100.sp,
            fontWeight = FontWeight.Medium,
            color = Color.White.copy(alpha = textAlpha)
        )

        // Feels Like
        val feelsLikeVal = hourly?.apparentTemperatures?.firstOrNull()
        val feelsLikeText = if (feelsLikeVal != null && !showDashes) {
            "Feels like ${formatTemp(feelsLikeVal, tempUnit)}"
        } else {
            "Feels like --°"
        }
        Text(
            text = feelsLikeText,
            fontSize = 20.sp,
            color = Color.White.copy(alpha = textAlpha * 0.77f),
            textAlign = TextAlign.Center
        )

        // High / Low
        val hTemp = if (daily != null && daily.maxTemp.isNotEmpty() && !showDashes) {
            formatTemp(daily.maxTemp[0], tempUnit)
        } else {
            "--°"
        }
        val lTemp = if (daily != null && daily.minTemp.isNotEmpty() && !showDashes) {
            formatTemp(daily.minTemp[0], tempUnit)
        } else {
            "--°"
        }

        Row(
            modifier = Modifier.width(IntrinsicSize.Max),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "H: $hTemp",
                fontSize = 17.sp,
                color = Color.White.copy(alpha = textAlpha * 0.77f)
            )
            Spacer(modifier = Modifier.width(32.dp))
            Text(
                text = "L: $lTemp",
                fontSize = 17.sp,
                color = Color.White.copy(alpha = textAlpha * 0.77f)
            )
        }
    }
}
