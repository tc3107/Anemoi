package com.tudorc.anemoi.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.Velocity
import com.tudorc.anemoi.R
import com.tudorc.anemoi.data.TempUnit
import com.tudorc.anemoi.util.formatTemp
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import java.util.Calendar
import java.util.Locale

@Composable
fun HourlyForecastWidget(
    times: List<String>,
    weatherCodes: List<Int>,
    temperatures: List<Double>,
    currentTimeIso: String? = null,
    tempUnit: TempUnit,
    modifier: Modifier = Modifier,
    isExpanded: Boolean = false
) {
    val haptic = LocalHapticFeedback.current
    val fallbackHourKey = remember {
        val c = Calendar.getInstance()
        String.format(
            Locale.US,
            "%04d-%02d-%02dT%02d",
            c.get(Calendar.YEAR),
            c.get(Calendar.MONTH) + 1,
            c.get(Calendar.DAY_OF_MONTH),
            c.get(Calendar.HOUR_OF_DAY)
        )
    }
    val currentHourKey = remember(currentTimeIso, fallbackHourKey) {
        toHourKey(currentTimeIso) ?: fallbackHourKey
    }

    val forecastItems = remember(times, weatherCodes, temperatures, currentHourKey) {
        if (times.isEmpty()) return@remember emptyList()

        val startIndex = times.indexOfFirst { toHourKey(it) == currentHourKey }
            .takeIf { it >= 0 }
            ?: 0
        val endExclusive = (startIndex + 24).coerceAtMost(times.size)

        val rawItems = (startIndex until endExclusive).mapNotNull { index ->
            val timeStr = times[index]
            val hour = extractHour(timeStr) ?: return@mapNotNull null

            HourlyForecastItem(
                displayHour = if (index == startIndex) "Now" else String.format(Locale.US, "%02d", hour),
                fullTime = timeStr,
                weatherCode = weatherCodes.getOrNull(index) ?: -1,
                temperature = temperatures.getOrNull(index) ?: 0.0,
                isCurrent = index == startIndex
            )
        }
        collapseConditionRepeats(rawItems)
    }

    val currentIndex = remember(forecastItems) { forecastItems.indexOfFirst { it.isCurrent } }
    val initialVisibleItemIndex = remember(currentIndex, isExpanded) {
        if (!isExpanded || currentIndex == -1) {
            0
        } else {
            // +1 to account for the leading divider item.
            (currentIndex + 1 - 2).coerceAtLeast(0)
        }
    }
    val listState = remember(initialVisibleItemIndex) {
        LazyListState(firstVisibleItemIndex = initialVisibleItemIndex)
    }
    var lastEdgeHit by remember { mutableIntStateOf(0) } // -1 = start, 1 = end
    val iconResByCode = remember(forecastItems) {
        forecastItems
            .asSequence()
            .map { it.weatherCode }
            .distinct()
            .associateWith(::getWeatherIconRes)
    }
    val surfaceShape = RoundedCornerShape(28.dp)
    val blockParentPagerScroll = remember(haptic, listState) {
        object : NestedScrollConnection {
            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource
            ): Offset {
                if (source != NestedScrollSource.UserInput || available.x == 0f) {
                    return Offset.Zero
                }
                val edge = when {
                    !listState.canScrollBackward -> -1
                    !listState.canScrollForward -> 1
                    else -> 0
                }
                if (edge != 0 && edge != lastEdgeHit) {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    lastEdgeHit = edge
                }
                // Consume horizontal leftovers from edge drags so page swipes don't trigger.
                return Offset(x = available.x, y = 0f)
            }

            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                if (available.x == 0f) {
                    return Velocity.Zero
                }
                val edge = when {
                    !listState.canScrollBackward -> -1
                    !listState.canScrollForward -> 1
                    else -> 0
                }
                if (edge != 0 && edge != lastEdgeHit) {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    lastEdgeHit = edge
                }
                // Also absorb horizontal fling leftovers at row edges.
                return Velocity(x = available.x, y = 0f)
            }
        }
    }
    LaunchedEffect(listState) {
        snapshotFlow { Triple(listState.isScrollInProgress, listState.canScrollBackward, listState.canScrollForward) }
            .map { (isScrolling, canScrollBack, canScrollForward) ->
                if (!isScrolling) {
                    0
                } else {
                    when {
                        !canScrollBack -> -1
                        !canScrollForward -> 1
                        else -> 0
                    }
                }
            }
            .distinctUntilChanged()
            .filter { edge -> edge != 0 || lastEdgeHit != 0 }
            .collect { edge ->
                if (edge == 0) {
                    lastEdgeHit = 0
                } else if (edge != lastEdgeHit) {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    lastEdgeHit = edge
                }
            }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
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

        val itemWidth = 60.dp
        val contentPaddingStart = 10.dp
        val itemSpacing = 10.dp

        if (forecastItems.isEmpty()) {
            Column(modifier = Modifier.fillMaxSize()) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "HOURLY CONDITIONS",
                    color = Color.White.copy(alpha = 0.4f),
                    fontSize = 10.sp,
                    lineHeight = 12.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
                Spacer(modifier = Modifier.height(10.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Text("No forecast data available", color = Color.White.copy(alpha = 0.5f), fontSize = 12.sp)
                }
                Spacer(modifier = Modifier.height(12.dp))
            }
        } else {
            Box(modifier = Modifier.fillMaxSize()) {
                Column(modifier = Modifier.fillMaxSize()) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "HOURLY CONDITIONS",
                        color = Color.White.copy(alpha = 0.4f),
                        fontSize = 10.sp,
                        lineHeight = 12.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .nestedScroll(blockParentPagerScroll)
                    ) {
                        LazyRow(
                            state = listState,
                            modifier = Modifier.fillMaxSize(),
                            verticalAlignment = Alignment.CenterVertically,
                            contentPadding = PaddingValues(
                                start = contentPaddingStart,
                                end = contentPaddingStart,
                                top = 8.dp,
                                bottom = 8.dp
                            ),
                            horizontalArrangement = Arrangement.spacedBy(itemSpacing)
                        ) {
                            item { EndDivider() }

                            itemsIndexed(
                                items = forecastItems,
                                key = { _, item -> item.fullTime }
                            ) { _, item ->
                                val tileShape = RoundedCornerShape(16.dp)

                                Box(
                                    modifier = Modifier
                                        .width(itemWidth)
                                        .fillMaxHeight()
                                        .clip(tileShape)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .clip(tileShape)
                                            .background(
                                                if (item.isCurrent) Color.White.copy(alpha = 0.22f)
                                                else Color.White.copy(alpha = 0.1f)
                                            )
                                            .border(
                                                width = 1.dp,
                                                color = if (item.isCurrent) Color.White.copy(alpha = 0.28f)
                                                else Color.White.copy(alpha = 0.14f),
                                                shape = tileShape
                                            )
                                    ) {
                                        Column(
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .padding(vertical = 10.dp, horizontal = 6.dp)
                                                .offset(y = (-2).dp),
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

                                            val iconResId = iconResByCode[item.weatherCode] ?: 0

                                            Box(modifier = Modifier.size(32.dp), contentAlignment = Alignment.Center) {
                                                if (iconResId != 0) {
                                                    Image(
                                                        painter = painterResource(id = iconResId),
                                                        contentDescription = null,
                                                        modifier = Modifier.fillMaxSize(),
                                                        colorFilter = ColorFilter.tint(Color.White)
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
                            }

                            item { EndDivider() }
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }

                Box(
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .fillMaxHeight()
                        .width(14.dp)
                        .background(
                            Brush.horizontalGradient(
                                colors = listOf(
                                    Color.White.copy(alpha = 0.12f),
                                    Color.Transparent
                                )
                            )
                        )
                )
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .fillMaxHeight()
                        .width(14.dp)
                        .background(
                            Brush.horizontalGradient(
                                colors = listOf(
                                    Color.Transparent,
                                    Color.White.copy(alpha = 0.12f)
                                )
                            )
                        )
                )
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

private fun getWeatherIconRes(code: Int): Int {
    val normalizedCode = normalizeWeatherCode(code)
    return when (normalizedCode) {
        0 -> R.drawable.wmo_0
        1 -> R.drawable.wmo_1
        2 -> R.drawable.wmo_2
        3 -> R.drawable.wmo_3
        45 -> R.drawable.wmo_45
        48 -> R.drawable.wmo_48
        51 -> R.drawable.wmo_51
        53 -> R.drawable.wmo_53
        55 -> R.drawable.wmo_55
        61 -> R.drawable.wmo_61
        62 -> R.drawable.wmo_62
        65 -> R.drawable.wmo_65
        66 -> R.drawable.wmo_66
        67 -> R.drawable.wmo_67
        71 -> R.drawable.wmo_71
        73 -> R.drawable.wmo_73
        75 -> R.drawable.wmo_75
        77 -> R.drawable.wmo_77
        80 -> R.drawable.wmo_80
        81 -> R.drawable.wmo_81
        82 -> R.drawable.wmo_82
        85 -> R.drawable.wmo_85
        86 -> R.drawable.wmo_86
        95 -> R.drawable.wmo_95
        96 -> R.drawable.wmo_96
        99 -> R.drawable.wmo_99
        else -> 0
    }
}

private fun collapseConditionRepeats(items: List<HourlyForecastItem>): List<HourlyForecastItem> {
    if (items.isEmpty()) return emptyList()
    val result = ArrayList<HourlyForecastItem>(items.size)
    var lastConditionCode: Int? = null

    for (item in items) {
        val normalized = normalizeWeatherCode(item.weatherCode)
        if (lastConditionCode == null || normalized != lastConditionCode) {
            result += item
            lastConditionCode = normalized
        }
    }
    return result
}

private fun normalizeWeatherCode(code: Int): Int {
    return when (code) {
        56, 57 -> 55
        63 -> 62
        72 -> 73
        else -> code
    }
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
    val isCurrent: Boolean
)

private fun toHourKey(timeIso: String?): String? {
    if (timeIso.isNullOrBlank()) return null
    val separatorIndex = timeIso.indexOf('T')
    if (separatorIndex <= 0 || separatorIndex + 3 > timeIso.length) return null
    return timeIso.substring(0, separatorIndex + 3)
}

private fun extractHour(timeIso: String): Int? {
    val separatorIndex = timeIso.indexOf('T')
    if (separatorIndex <= 0 || separatorIndex + 3 > timeIso.length) return null
    return timeIso.substring(separatorIndex + 1, separatorIndex + 3).toIntOrNull()
}
