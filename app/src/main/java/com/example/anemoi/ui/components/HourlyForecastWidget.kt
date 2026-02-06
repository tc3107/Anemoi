package com.example.anemoi.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.anemoi.data.TempUnit
import com.example.anemoi.util.formatTemp
import java.util.Calendar
import java.util.Locale
import kotlinx.coroutines.flow.first

@Composable
fun HourlyForecastWidget(
    times: List<String>,
    weatherCodes: List<Int>,
    temperatures: List<Double>,
    tempUnit: TempUnit,
    modifier: Modifier = Modifier,
    isExpanded: Boolean = false
) {
    val context = LocalContext.current
    val currentHour = remember { Calendar.getInstance().get(Calendar.HOUR_OF_DAY) }
    val todayDate = remember { 
        val c = Calendar.getInstance()
        String.format(Locale.US, "%04d-%02d-%02d", 
            c.get(Calendar.YEAR), 
            c.get(Calendar.MONTH) + 1, 
            c.get(Calendar.DAY_OF_MONTH)
        )
    }

    val forecastItems = remember(times, weatherCodes, temperatures, todayDate, currentHour) {
        times.mapIndexedNotNull { index, timeStr ->
            try {
                val parts = timeStr.split("T")
                val date = parts[0]
                val time = parts[1]
                val hour = time.split(":")[0].toInt()
                
                if (date == todayDate) {
                    HourlyForecastItem(
                        displayHour = if (hour == currentHour) "Now" else String.format(Locale.US, "%02d", hour),
                        fullTime = timeStr,
                        weatherCode = weatherCodes.getOrNull(index) ?: -1,
                        temperature = temperatures.getOrNull(index) ?: 0.0,
                        isToday = true,
                        hourInt = hour,
                        isCurrent = hour == currentHour
                    )
                } else null
            } catch (_: Exception) {
                null
            }
        }
    }

    val listState = rememberLazyListState()
    val currentIndex = remember(forecastItems) { forecastItems.indexOfFirst { it.isCurrent } }

    Box(modifier = modifier.fillMaxWidth()) {
        val itemWidth = 60.dp
        val contentPaddingStart = 10.dp
        val itemSpacing = 10.dp
        
        LaunchedEffect(currentIndex, isExpanded) {
            if (currentIndex != -1 && isExpanded) {
                val targetIndex = currentIndex + 1
                listState.scrollToItem(targetIndex)

                val itemInfo = snapshotFlow {
                    listState.layoutInfo.visibleItemsInfo.firstOrNull { it.index == targetIndex }
                }.first { it != null }

                val viewportCenter = listState.layoutInfo.viewportEndOffset / 2
                val itemCenter = itemInfo!!.offset + (itemInfo.size / 2)
                val delta = itemCenter - viewportCenter
                listState.scrollBy(delta.toFloat())
            }
        }

        if (forecastItems.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No forecast data available", color = Color.White.copy(alpha = 0.5f), fontSize = 12.sp)
            }
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
                    .drawWithContent {
                        drawContent()
                        drawRect(
                            brush = Brush.horizontalGradient(
                                0f to Color.Transparent,
                                0.08f to Color.Black,
                                0.92f to Color.Black,
                                1f to Color.Transparent
                            ),
                            blendMode = BlendMode.DstIn
                        )
                    }
            ) {
                LazyRow(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    verticalAlignment = Alignment.CenterVertically,
                    // Standard padding allows the dividers to be exactly 18dp from the edge
                    contentPadding = PaddingValues(horizontal = contentPaddingStart),
                    horizontalArrangement = Arrangement.spacedBy(itemSpacing)
                ) {
                    item { EndDivider() }

                    itemsIndexed(forecastItems) { _, item ->
                        Box(
                            modifier = Modifier
                                .width(itemWidth)
                                .fillMaxHeight()
                                .clip(RoundedCornerShape(16.dp))
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .blur(10.dp)
                                    .background(
                                        if (item.isCurrent) Color.White.copy(alpha = 0.22f)
                                        else Color.White.copy(alpha = 0.1f)
                                    )
                            )
                            
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.fillMaxSize(),
                                verticalArrangement = Arrangement.Center
                            ) {
                                Text(
                                    text = formatTemp(item.temperature, tempUnit),
                                    color = if (item.isCurrent) Color.White.copy(alpha = 0.9f) else Color.White.copy(alpha = 0.6f),
                                    fontSize = 13.sp,
                                    fontWeight = if (item.isCurrent) FontWeight.Bold else FontWeight.Medium,
                                    textAlign = TextAlign.Center
                                )

                                Spacer(modifier = Modifier.height(4.dp))

                                val iconResId = remember(item.weatherCode) {
                                    getWeatherIconRes(item.weatherCode, context)
                                }
                                
                                Box(modifier = Modifier.size(32.dp), contentAlignment = Alignment.Center) {
                                    if (iconResId != 0) {
                                        Image(
                                            painter = painterResource(id = iconResId),
                                            contentDescription = null,
                                            modifier = Modifier.fillMaxSize(),
                                            colorFilter = ColorFilter.tint(Color(0xFFD6D9DE))
                                        )
                                    } else {
                                        FallbackCircle()
                                    }
                                }

                                Spacer(modifier = Modifier.height(4.dp))

                                Text(
                                    text = item.displayHour,
                                    color = if (item.isCurrent) Color.White.copy(alpha = 0.9f) else Color.White.copy(alpha = 0.6f),
                                    fontSize = 14.sp,
                                    fontWeight = if (item.isCurrent) FontWeight.Bold else FontWeight.Medium,
                                    textAlign = TextAlign.Center,
                                    maxLines = 1,
                                    softWrap = false
                                )
                            }
                        }
                    }

                    item { EndDivider() }
                }
            }
        }
    }
}

@Composable
private fun EndDivider() {
    Box(
        modifier = Modifier
            .padding(horizontal = 8.dp)
            .width(1.dp)
            .fillMaxHeight(0.8f)
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color.Transparent,
                        Color.White.copy(alpha = 0.15f),
                        Color.Transparent
                    )
                )
            )
    )
}

private fun getWeatherIconRes(code: Int, context: android.content.Context): Int {
    @Suppress("DiscouragedApi")
    return context.resources.getIdentifier("wmo_$code", "drawable", context.packageName)
}

@Composable
private fun FallbackCircle() {
    Box(
        modifier = Modifier
            .size(6.dp)
            .background(Color.White.copy(alpha = 0.2f), CircleShape)
    )
}

private data class HourlyForecastItem(
    val displayHour: String,
    val fullTime: String,
    val weatherCode: Int,
    val temperature: Double,
    val isToday: Boolean,
    val hourInt: Int,
    val isCurrent: Boolean
)
