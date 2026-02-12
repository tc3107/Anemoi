package com.example.anemoi.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.anemoi.data.PressureUnit
import com.example.anemoi.data.TempUnit
import com.example.anemoi.ui.components.GlassEntryCard
import com.example.anemoi.ui.components.SegmentedSelector
import com.example.anemoi.util.ObfuscationMode
import com.example.anemoi.viewmodel.WeatherViewModel
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(viewModel: WeatherViewModel, onBack: () -> Unit) {
    val uiState by viewModel.uiState.collectAsState()
    val haptic = LocalHapticFeedback.current
    var draftObfuscationMode by remember(uiState.isSettingsOpen) { mutableStateOf(uiState.obfuscationMode) }
    var draftGridKm by remember(uiState.isSettingsOpen) { mutableStateOf(uiState.gridKm) }
    val settingsScrollState = rememberScrollState()
    var didAutoScrollToWarnings by remember(uiState.isSettingsOpen) { mutableStateOf(false) }

    val currentBlurStrength = if (uiState.customValuesEnabled) uiState.sheetBlurStrength else 16f
    val currentTintAlpha = if (uiState.customValuesEnabled) uiState.searchBarTintAlpha else 0.15f
    val surfaceShape = RoundedCornerShape(32.dp)
    val staleServeWindowMs = 12 * 60 * 60 * 1000L
    val currentThresholdMs = 5 * 60 * 1000L
    val hourlyThresholdMs = 20 * 60 * 1000L
    val dailyThresholdMs = 2 * 60 * 60 * 1000L
    val now = System.currentTimeMillis()
    val key = uiState.selectedLocation?.let { "${it.lat},${it.lon}" }
    val isSignatureMatch = key != null && uiState.cacheSignatureMap[key] == uiState.activeRequestSignature
    val rawWeather = key?.let { uiState.weatherMap[it] }
    val currentUpdatedAt = key?.let { uiState.currentUpdateTimeMap[it] } ?: 0L
    val hourlyUpdatedAt = key?.let { uiState.hourlyUpdateTimeMap[it] } ?: 0L
    val dailyUpdatedAt = key?.let { uiState.dailyUpdateTimeMap[it] } ?: 0L

    val warningDetails = buildList {
        buildFreshnessWarningLine(
            label = "Current conditions",
            hasData = rawWeather?.currentWeather != null,
            updatedAtMs = currentUpdatedAt,
            nowMs = now,
            thresholdMs = currentThresholdMs,
            staleServeWindowMs = staleServeWindowMs
        )?.let { add(it) }

        buildFreshnessWarningLine(
            label = "Hourly forecast",
            hasData = rawWeather?.hourly != null,
            updatedAtMs = hourlyUpdatedAt,
            nowMs = now,
            thresholdMs = hourlyThresholdMs,
            staleServeWindowMs = staleServeWindowMs
        )?.let { add(it) }

        buildFreshnessWarningLine(
            label = "Daily forecast",
            hasData = rawWeather?.daily != null,
            updatedAtMs = dailyUpdatedAt,
            nowMs = now,
            thresholdMs = dailyThresholdMs,
            staleServeWindowMs = staleServeWindowMs
        )?.let { add(it) }

        if (key != null && !isSignatureMatch) {
            add("Data was fetched with different privacy settings.")
        }
    }
    val hasOutdatedData = warningDetails.isNotEmpty()

    val closeSettingsAndApplyPrivacyChanges: () -> Unit = {
        val modeChanged = draftObfuscationMode != uiState.obfuscationMode
        val gridChanged = draftGridKm != uiState.gridKm
        onBack()
        if (modeChanged || gridChanged) {
            viewModel.applyPrivacySettings(
                mode = draftObfuscationMode,
                gridKm = draftGridKm
            )
        }
    }

    BackHandler {
        closeSettingsAndApplyPrivacyChanges()
    }

    LaunchedEffect(uiState.isSettingsOpen, settingsScrollState.maxValue, didAutoScrollToWarnings) {
        if (
            uiState.isSettingsOpen &&
            !didAutoScrollToWarnings &&
            settingsScrollState.maxValue > 0
        ) {
            didAutoScrollToWarnings = true
            settingsScrollState.animateScrollTo(settingsScrollState.maxValue)
        }
    }

    Box(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.85f)
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
            // Glassmorphic background
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .blur(currentBlurStrength.dp)
                    .background(Color.White.copy(alpha = currentTintAlpha))
            )
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
                    .fillMaxSize()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    "Settings",
                    color = Color.White,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(vertical = 16.dp)
                )

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(settingsScrollState)
                ) {
                    Text(
                        "Units",
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )

                    GlassEntryCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp)
                    ) {
                        Column {
                            Text("Temperature", color = Color.White, fontWeight = FontWeight.Medium)
                            Spacer(modifier = Modifier.height(8.dp))
                            
                            val tempOptions = TempUnit.values().map { 
                                when(it) {
                                    TempUnit.CELSIUS -> "°C"
                                    TempUnit.FAHRENHEIT -> "°F"
                                    TempUnit.KELVIN -> "K"
                                }
                            }
                            SegmentedSelector(
                                options = tempOptions,
                                selectedIndex = TempUnit.values().indexOf(uiState.tempUnit),
                                onOptionSelected = { viewModel.setTempUnit(TempUnit.values()[it]) }
                            )

                            Spacer(modifier = Modifier.height(16.dp))
                            Text("Pressure", color = Color.White, fontWeight = FontWeight.Medium)
                            Spacer(modifier = Modifier.height(8.dp))
                            
                            SegmentedSelector(
                                options = PressureUnit.values().map { it.label },
                                selectedIndex = PressureUnit.values().indexOf(uiState.pressureUnit),
                                onOptionSelected = { viewModel.setPressureUnit(PressureUnit.values()[it]) }
                            )
                        }
                    }

                    Text(
                        "Location Privacy",
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(top = 16.dp, bottom = 8.dp)
                    )

                    GlassEntryCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp)
                    ) {
                        Column {
                            Text("Mode", color = Color.White, fontWeight = FontWeight.Medium)
                            Spacer(modifier = Modifier.height(8.dp))
                            
                            SegmentedSelector(
                                options = listOf("Precise", "Obfuscated"),
                                selectedIndex = if (draftObfuscationMode == ObfuscationMode.PRECISE) 0 else 1,
                                onOptionSelected = {
                                    draftObfuscationMode = if (it == 0) {
                                        ObfuscationMode.PRECISE
                                    } else {
                                        ObfuscationMode.GRID
                                    }
                                }
                            )

                            if (draftObfuscationMode == ObfuscationMode.GRID) {
                                Spacer(modifier = Modifier.height(16.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("Grid Size", color = Color.White)
                                    Text("${draftGridKm.roundToInt()} km", color = Color.White, fontWeight = FontWeight.Bold)
                                }
                                val steps = listOf(1f, 2f, 5f, 10f, 20f, 50f)
                                Slider(
                                    value = steps.indexOf(draftGridKm).toFloat().coerceAtLeast(0f),
                                    onValueChange = {
                                        val index = it.roundToInt().coerceIn(0, steps.size - 1)
                                        draftGridKm = steps[index]
                                    },
                                    valueRange = 0f..(steps.size - 1).toFloat(),
                                    steps = steps.size - 2,
                                    colors = SliderDefaults.colors(
                                        thumbColor = Color.White,
                                        activeTrackColor = Color.White,
                                        inactiveTrackColor = Color.White.copy(alpha = 0.3f)
                                    )
                                )
                                val label = when (draftGridKm) {
                                    1f -> "Neighborhood"
                                    2f -> "Small Town"
                                    5f -> "District"
                                    10f -> "City-ish"
                                    20f -> "Metropolitan Area"
                                    50f -> "Region"
                                    else -> ""
                                }
                                Text(
                                    label, 
                                    color = Color.White.copy(alpha = 0.7f), 
                                    fontSize = 12.sp,
                                    modifier = Modifier.align(Alignment.CenterHorizontally)
                                )
                            }
                        }
                    }
                    
                    Text(
                        "About Privacy Mode",
                        color = Color.White.copy(alpha = 0.7f),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(top = 16.dp, bottom = 4.dp)
                    )
                    Text(
                        "Snaps your location to a grid and adds random jitter. This protects your exact coordinates while still providing relevant weather data for your general area.",
                        color = Color.White.copy(alpha = 0.5f),
                        fontSize = 12.sp,
                        lineHeight = 16.sp,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )

                    Text(
                        "Warnings",
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(top = 4.dp, bottom = 8.dp)
                    )

                    GlassEntryCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp)
                    ) {
                        Column {
                            if (hasOutdatedData) {
                                warningDetails.forEach { detail ->
                                    Text(
                                        text = "• $detail",
                                        color = Color(0xFFFFD27A),
                                        fontSize = 12.sp,
                                        lineHeight = 16.sp
                                    )
                                }
                            } else {
                                Text(
                                    text = "No active weather warnings.",
                                    color = Color.White,
                                    fontSize = 12.sp,
                                    lineHeight = 16.sp
                                )
                            }

                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Grey weather text means stale or old-mode data is shown while a refresh is pending.",
                                color = Color.White.copy(alpha = 0.78f),
                                fontSize = 11.sp,
                                lineHeight = 15.sp
                            )
                        }
                    }
                }

                Button(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        closeSettingsAndApplyPrivacyChanges()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.White.copy(alpha = 0.2f),
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Text("Close")
                }
            }
        }
    }
}

private fun buildFreshnessWarningLine(
    label: String,
    hasData: Boolean,
    updatedAtMs: Long,
    nowMs: Long,
    thresholdMs: Long,
    staleServeWindowMs: Long
): String? {
    if (!hasData || updatedAtMs <= 0L) {
        return null
    }

    val ageMs = (nowMs - updatedAtMs).coerceAtLeast(0L)
    val ageLabel = formatDurationLabel(ageMs)
    return when {
        ageMs > staleServeWindowMs -> "$label expired ($ageLabel)."
        ageMs > thresholdMs -> "$label stale ($ageLabel > ${formatDurationLabel(thresholdMs)})."
        else -> null
    }
}

private fun formatDurationLabel(durationMs: Long): String {
    val minutes = (durationMs / 60_000L).coerceAtLeast(0L)
    return when {
        minutes <= 0L -> "under 1m"
        minutes < 60L -> "${minutes}m"
        minutes < 24 * 60L -> {
            val hours = minutes / 60L
            val remainingMinutes = minutes % 60L
            if (remainingMinutes == 0L) {
                "${hours}h"
            } else {
                "${hours}h ${remainingMinutes}m"
            }
        }

        else -> {
            val days = minutes / (24L * 60L)
            val remainingHours = (minutes % (24L * 60L)) / 60L
            if (remainingHours == 0L) {
                "${days}d"
            } else {
                "${days}d ${remainingHours}h"
            }
        }
    }
}
