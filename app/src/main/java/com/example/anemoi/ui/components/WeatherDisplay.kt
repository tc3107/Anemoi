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
    showDashesOverride: Boolean = false,
    textAlpha: Float = 0.8f,
    useStaleColor: Boolean = false
) {
    val showDashes = showDashesOverride || weather == null

    val current = weather?.currentWeather
    val hourly = weather?.hourly
    val daily = weather?.daily

    val baseTextColor = if (useStaleColor) Color(0xFFB0B0B0) else Color.White
    val primaryTextColor = baseTextColor.copy(alpha = textAlpha)
    val secondaryTextColor = baseTextColor.copy(alpha = (textAlpha * 0.77f).coerceIn(0f, 1f))

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
            color = primaryTextColor
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
            color = secondaryTextColor,
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
                color = secondaryTextColor
            )
            Spacer(modifier = Modifier.width(32.dp))
            Text(
                text = "L: $lTemp",
                fontSize = 17.sp,
                color = secondaryTextColor
            )
        }
    }
}
