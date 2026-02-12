package com.example.anemoi.ui.screens

import android.content.Context
import android.content.Intent
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.anemoi.data.PressureUnit
import com.example.anemoi.data.TempUnit
import com.example.anemoi.data.WindUnit
import com.example.anemoi.ui.components.GlassEntryCard
import com.example.anemoi.ui.components.SegmentedSelector
import com.example.anemoi.util.ObfuscationMode
import com.example.anemoi.util.backgroundOverridePresets
import com.example.anemoi.viewmodel.WeatherViewModel
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(viewModel: WeatherViewModel, onBack: () -> Unit) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val compassSensorAccessState = rememberCompassSensorAccessState()
    var draftObfuscationMode by remember(uiState.isSettingsOpen) { mutableStateOf(uiState.obfuscationMode) }
    var draftGridKm by remember(uiState.isSettingsOpen) { mutableStateOf(uiState.gridKm) }
    val backgroundPresetMaxIndex = backgroundOverridePresets.lastIndex.coerceAtLeast(0)
    val selectedBackgroundPreset = if (backgroundOverridePresets.isNotEmpty()) {
        backgroundOverridePresets[uiState.backgroundOverridePresetIndex.coerceIn(0, backgroundPresetMaxIndex)]
    } else {
        null
    }
    val settingsScrollState = rememberScrollState()
    var didAutoScrollToWarnings by remember(uiState.isSettingsOpen) { mutableStateOf(false) }

    val surfaceShape = RoundedCornerShape(32.dp)
    val settingsSwitchColors = SwitchDefaults.colors(
        checkedThumbColor = Color.White,
        checkedTrackColor = Color.Transparent,
        checkedBorderColor = Color.White,
        uncheckedThumbColor = Color(0xFFB3B3B3),
        uncheckedTrackColor = Color.Transparent,
        uncheckedBorderColor = Color(0xFF8C8C8C)
    )
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

    val compassWarningLine = when (compassSensorAccessState) {
        CompassSensorAccessState.Checking,
        CompassSensorAccessState.Available -> null
        CompassSensorAccessState.UnavailableNoHardware ->
            "Compass sensors are not available on this device."
        CompassSensorAccessState.UnavailableNoReadings ->
            "Compass sensor access is unavailable. Wind dial stays north-up."
    }
    val showCompassAccessButton = compassWarningLine != null

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
        compassWarningLine?.let { add(it) }
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

                            Spacer(modifier = Modifier.height(16.dp))
                            Text("Wind Speed", color = Color.White, fontWeight = FontWeight.Medium)
                            Spacer(modifier = Modifier.height(8.dp))

                            SegmentedSelector(
                                options = WindUnit.values().map { it.label },
                                selectedIndex = WindUnit.values().indexOf(uiState.windUnit),
                                onOptionSelected = { viewModel.setWindUnit(WindUnit.values()[it]) }
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
                                    color = Color.White,
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

                            if (showCompassAccessButton) {
                                Spacer(modifier = Modifier.height(12.dp))
                                OutlinedButton(
                                    onClick = {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        requestCompassSensorAccess(context)
                                    },
                                    colors = ButtonDefaults.outlinedButtonColors(
                                        containerColor = Color.Transparent,
                                        contentColor = Color.White
                                    ),
                                    border = BorderStroke(
                                        width = 1.dp,
                                        brush = Brush.horizontalGradient(
                                            colors = listOf(
                                                Color.White.copy(alpha = 0.45f),
                                                Color.White.copy(alpha = 0.20f)
                                            )
                                        )
                                    ),
                                    shape = RoundedCornerShape(12.dp),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                                ) {
                                    Text(
                                        text = "Request Sensor Access",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                        }
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                        horizontalArrangement = Arrangement.End
                    ) {
                        OutlinedButton(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                viewModel.setDebugOptionsVisible(!uiState.isDebugOptionsVisible)
                            },
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = Color.Transparent,
                                contentColor = Color.White
                            ),
                            border = BorderStroke(
                                width = 1.dp,
                                brush = Brush.horizontalGradient(
                                    colors = listOf(
                                        Color.White.copy(alpha = 0.45f),
                                        Color.White.copy(alpha = 0.20f)
                                    )
                                )
                            ),
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = if (uiState.isDebugOptionsVisible) "Hide Debug" else "Debug",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    AnimatedVisibility(visible = uiState.isDebugOptionsVisible) {
                        GlassEntryCard(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp)
                        ) {
                            Column {
                                Text(
                                    text = "Debug Options",
                                    color = Color.White,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Spacer(modifier = Modifier.height(10.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "Map Background",
                                            color = Color.White,
                                            fontWeight = FontWeight.Medium
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = if (uiState.mapEnabled) {
                                                "On: live map tiles are shown"
                                            } else {
                                                "Off: use a static gradient background"
                                            },
                                            color = Color.White.copy(alpha = 0.7f),
                                            fontSize = 12.sp,
                                            lineHeight = 16.sp
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Switch(
                                        checked = uiState.mapEnabled,
                                        onCheckedChange = viewModel::setMapEnabled,
                                        colors = settingsSwitchColors
                                    )
                                }
                                Spacer(modifier = Modifier.height(12.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "Performance Overlay",
                                            color = Color.White,
                                            fontWeight = FontWeight.Medium
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = "Shows live UI/runtime stats in the top-left overlay.",
                                            color = Color.White.copy(alpha = 0.7f),
                                            fontSize = 12.sp,
                                            lineHeight = 16.sp
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Switch(
                                        checked = uiState.isPerformanceOverlayEnabled,
                                        onCheckedChange = viewModel::setPerformanceOverlayEnabled,
                                        colors = settingsSwitchColors
                                    )
                                }
                                Spacer(modifier = Modifier.height(12.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "Override Background",
                                            color = Color.White,
                                            fontWeight = FontWeight.Medium
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = "Forces a chosen gradient scene for visual testing.",
                                            color = Color.White.copy(alpha = 0.7f),
                                            fontSize = 12.sp,
                                            lineHeight = 16.sp
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Switch(
                                        checked = uiState.isBackgroundOverrideEnabled,
                                        onCheckedChange = viewModel::setBackgroundOverrideEnabled,
                                        colors = settingsSwitchColors
                                    )
                                }

                                if (uiState.isBackgroundOverrideEnabled && selectedBackgroundPreset != null) {
                                    Spacer(modifier = Modifier.height(10.dp))
                                    Text(
                                        text = "Background Preset",
                                        color = Color.White,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = selectedBackgroundPreset.label,
                                        color = Color.White,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Slider(
                                        value = uiState.backgroundOverridePresetIndex.toFloat(),
                                        onValueChange = {
                                            viewModel.setBackgroundOverridePresetIndex(it.roundToInt())
                                        },
                                        valueRange = 0f..backgroundPresetMaxIndex.toFloat(),
                                        steps = (backgroundPresetMaxIndex - 1).coerceAtLeast(0),
                                        colors = SliderDefaults.colors(
                                            thumbColor = Color.White,
                                            activeTrackColor = Color.White,
                                            inactiveTrackColor = Color.White.copy(alpha = 0.35f)
                                        )
                                    )
                                }
                            }
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

private enum class CompassSensorAccessState {
    Checking,
    Available,
    UnavailableNoHardware,
    UnavailableNoReadings
}

@Composable
private fun rememberCompassSensorAccessState(): CompassSensorAccessState {
    val context = LocalContext.current
    return produceState(
        initialValue = CompassSensorAccessState.Checking,
        key1 = context
    ) {
        val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        if (sensorManager == null) {
            value = CompassSensorAccessState.UnavailableNoHardware
            return@produceState
        }

        val rotationVectorSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
        val accelerometerSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        val magneticSensor = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)

        if (rotationVectorSensor == null && (accelerometerSensor == null || magneticSensor == null)) {
            value = CompassSensorAccessState.UnavailableNoHardware
            return@produceState
        }

        var hasReading = false
        val timeoutHandler = Handler(Looper.getMainLooper())
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                if (!hasReading) {
                    hasReading = true
                    value = CompassSensorAccessState.Available
                }
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }

        if (rotationVectorSensor != null) {
            sensorManager.registerListener(listener, rotationVectorSensor, SensorManager.SENSOR_DELAY_UI)
        } else {
            sensorManager.registerListener(listener, accelerometerSensor, SensorManager.SENSOR_DELAY_UI)
            sensorManager.registerListener(listener, magneticSensor, SensorManager.SENSOR_DELAY_UI)
        }

        val timeoutRunnable = Runnable {
            if (!hasReading) {
                value = CompassSensorAccessState.UnavailableNoReadings
            }
        }
        timeoutHandler.postDelayed(timeoutRunnable, 1500L)

        awaitDispose {
            timeoutHandler.removeCallbacks(timeoutRunnable)
            sensorManager.unregisterListener(listener)
        }
    }.value
}

private fun requestCompassSensorAccess(context: Context) {
    val appDetailsIntent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
        data = Uri.fromParts("package", context.packageName, null)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    val privacyIntent = Intent(Settings.ACTION_PRIVACY_SETTINGS).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    val target = if (privacyIntent.resolveActivity(context.packageManager) != null) {
        privacyIntent
    } else {
        appDetailsIntent
    }
    runCatching {
        context.startActivity(target)
    }.onFailure {
        context.startActivity(appDetailsIntent)
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
