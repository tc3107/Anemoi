package com.tudorc.anemoi.ui.screens

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
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tudorc.anemoi.data.PressureUnit
import com.tudorc.anemoi.data.TempUnit
import com.tudorc.anemoi.data.WindUnit
import com.tudorc.anemoi.ui.components.GlassEntryCard
import com.tudorc.anemoi.ui.components.SegmentedSelector
import com.tudorc.anemoi.util.ObfuscationMode
import com.tudorc.anemoi.util.backgroundOverridePresets
import com.tudorc.anemoi.viewmodel.WeatherViewModel
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(viewModel: WeatherViewModel, onBack: () -> Unit) {
    val uiState by viewModel.uiState.collectAsState()
    val uriHandler = LocalUriHandler.current
    val haptic = LocalHapticFeedback.current
    var draftObfuscationMode by remember(uiState.isSettingsOpen) { mutableStateOf(uiState.obfuscationMode) }
    var draftGridKm by remember(uiState.isSettingsOpen) { mutableStateOf(uiState.gridKm) }
    val backgroundPresetMaxIndex = backgroundOverridePresets.lastIndex.coerceAtLeast(0)
    val selectedBackgroundPreset = if (backgroundOverridePresets.isNotEmpty()) {
        backgroundOverridePresets[uiState.backgroundOverridePresetIndex.coerceIn(0, backgroundPresetMaxIndex)]
    } else {
        null
    }
    val settingsScrollState = rememberScrollState()
    val surfaceShape = RoundedCornerShape(32.dp)
    val settingsSwitchColors = SwitchDefaults.colors(
        checkedThumbColor = Color.White,
        checkedTrackColor = Color.Transparent,
        checkedBorderColor = Color.White,
        uncheckedThumbColor = Color(0xFFB3B3B3),
        uncheckedTrackColor = Color.Transparent,
        uncheckedBorderColor = Color(0xFF8C8C8C)
    )
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
                        "Units",
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(top = 12.dp, bottom = 8.dp)
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
                                when (it) {
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
                        "About / Support",
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(top = 16.dp, bottom = 8.dp)
                    )

                    GlassEntryCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp)
                    ) {
                        Column {
                            Text(
                                text = "More info and updates:",
                                color = Color.White.copy(alpha = 0.85f),
                                fontSize = 12.sp
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            OutlinedButton(
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    uriHandler.openUri("https://github.com/tc3107/Anemoi")
                                },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    containerColor = Color.White.copy(alpha = 0.12f),
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
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text(
                                    text = "View Anemoi on GitHub",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            Text(
                                text = "Privacy",
                                color = Color.White,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Contacts: Open-Meteo (weather) and OpenStreetMap Nominatim (place search). Your location is only used to fetch local weather, saved places/settings stay on-device, and Anemoi itself collects no telemetry.",
                                color = Color.White.copy(alpha = 0.78f),
                                fontSize = 12.sp,
                                lineHeight = 16.sp
                            )
                            Spacer(modifier = Modifier.height(8.dp))

                            Text(
                                text = "Grid obfuscation hides your exact spot. Instead of using your precise location, the app uses a nearby area (based on your chosen grid size) so you still get local weather without sharing your exact coordinates.",
                                color = Color.White.copy(alpha = 0.78f),
                                fontSize = 12.sp,
                                lineHeight = 16.sp
                            )

                            Spacer(modifier = Modifier.height(10.dp))

                            OutlinedButton(
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    uriHandler.openUri("https://ko-fi.com/tc3107")
                                },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    containerColor = Color.White.copy(alpha = 0.12f),
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
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text(
                                    text = "Support Development",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
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
                                            text = "Freeze Compass North",
                                            color = Color.White,
                                            fontWeight = FontWeight.Medium
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = "Disables live compass sensor updates and pins the dial north-up.",
                                            color = Color.White.copy(alpha = 0.7f),
                                            fontSize = 12.sp,
                                            lineHeight = 16.sp
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Switch(
                                        checked = uiState.isCompassNorthLocked,
                                        onCheckedChange = viewModel::setCompassNorthLocked,
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
                                    Spacer(modifier = Modifier.height(10.dp))
                                    Text(
                                        text = "Forced Wind Speed",
                                        color = Color.White,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "${uiState.backgroundOverrideWindSpeedKmh.roundToInt()} km/h",
                                        color = Color.White,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Slider(
                                        value = uiState.backgroundOverrideWindSpeedKmh,
                                        onValueChange = viewModel::setBackgroundOverrideWindSpeedKmh,
                                        valueRange = 0f..100f,
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
