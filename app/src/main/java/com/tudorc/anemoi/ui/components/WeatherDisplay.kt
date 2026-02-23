package com.tudorc.anemoi.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.tudorc.anemoi.data.TempUnit
import com.tudorc.anemoi.data.WeatherResponse
import com.tudorc.anemoi.util.formatTemp

@Composable
fun WeatherDisplay(
    weather: WeatherResponse?,
    tempUnit: TempUnit,
    showDashesOverride: Boolean = false,
    textAlpha: Float = 0.8f,
    staleHintText: String? = null,
    staleDetailsTitle: String? = null,
    staleDetailsLines: List<String> = emptyList()
) {
    var showStaleDialog by remember { mutableStateOf(false) }
    var containerHeightPx by remember { mutableFloatStateOf(0f) }
    var highLowBottomPx by remember { mutableFloatStateOf(0f) }
    val density = LocalDensity.current
    val showDashes = showDashesOverride || weather == null

    val current = weather?.currentWeather
    val hourly = weather?.hourly
    val daily = weather?.daily

    val primaryTextColor = Color.White.copy(alpha = textAlpha)
    val secondaryTextColor = Color.White.copy(alpha = (textAlpha * 0.77f).coerceIn(0f, 1f))
    val hintTextColor = Color.White.copy(alpha = (textAlpha * 0.32f).coerceIn(0f, 1f))

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .onGloballyPositioned { coordinates ->
                containerHeightPx = coordinates.size.height.toFloat()
            }
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(22.dp))

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

            Spacer(modifier = Modifier.height(22.dp))

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
                modifier = Modifier
                    .width(IntrinsicSize.Max)
                    .onGloballyPositioned { coordinates ->
                        highLowBottomPx = coordinates.positionInParent().y + coordinates.size.height
                    },
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

            Spacer(modifier = Modifier.height(12.dp))
        }

        if (!staleHintText.isNullOrBlank()) {
            val overlayHeightPx = (containerHeightPx - highLowBottomPx).coerceAtLeast(0f)
            if (overlayHeightPx > 0f) {
                val staleHintContainerHeight = maxOf(with(density) { overlayHeightPx.toDp() }, 18.dp)
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(top = with(density) { highLowBottomPx.toDp() })
                        .fillMaxWidth()
                        .height(staleHintContainerHeight),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = staleHintText,
                        fontSize = 11.sp,
                        lineHeight = 13.sp,
                        color = hintTextColor,
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp)
                            .clickable(enabled = staleDetailsLines.isNotEmpty()) {
                            showStaleDialog = true
                        }
                    )
                }
            } else {
                Text(
                    text = staleHintText,
                    fontSize = 11.sp,
                    lineHeight = 13.sp,
                    color = hintTextColor,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp)
                        .clickable(enabled = staleDetailsLines.isNotEmpty()) {
                            showStaleDialog = true
                        }
                )
            }
        }
    }

    if (showStaleDialog && staleDetailsLines.isNotEmpty()) {
        val shape = RoundedCornerShape(20.dp)
        val panelBrush = Brush.verticalGradient(
            colors = listOf(
                Color(0xFF20344A).copy(alpha = 0.95f),
                Color(0xFF142333).copy(alpha = 0.96f)
            )
        )
        val borderBrush = Brush.linearGradient(
            colors = listOf(
                Color.White.copy(alpha = 0.42f),
                Color(0xFF8FD3FF).copy(alpha = 0.26f)
            )
        )

        Dialog(onDismissRequest = { showStaleDialog = false }) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(shape)
                    .background(panelBrush, shape)
                    .border(width = 1.dp, brush = borderBrush, shape = shape)
                    .padding(horizontal = 16.dp, vertical = 14.dp)
            ) {
                Column {
                    Text(
                        text = staleDetailsTitle ?: "Weather Refresh Status",
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    staleDetailsLines.forEach { line ->
                        Text(
                            text = line,
                            color = Color.White.copy(alpha = 0.86f),
                            fontSize = 12.sp,
                            lineHeight = 17.sp
                        )
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    TextButton(
                        onClick = { showStaleDialog = false },
                        colors = ButtonDefaults.textButtonColors(contentColor = Color.White)
                    ) {
                        Text("Close")
                    }
                }
            }
        }
    }
}
