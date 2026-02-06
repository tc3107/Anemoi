package com.example.anemoi.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.anemoi.data.PressureUnit
import com.example.anemoi.data.TempUnit
import com.example.anemoi.ui.components.SegmentedSelector
import com.example.anemoi.util.ObfuscationMode
import com.example.anemoi.viewmodel.WeatherViewModel
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(viewModel: WeatherViewModel, onBack: () -> Unit) {
    val uiState by viewModel.uiState.collectAsState()
    
    val currentBlurStrength = if (uiState.customValuesEnabled) uiState.sheetBlurStrength else 16f
    val currentTintAlpha = if (uiState.customValuesEnabled) uiState.searchBarTintAlpha else 0.15f

    Box(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.85f)
                .clip(RoundedCornerShape(32.dp))
        ) {
            // Glassmorphic background
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .blur(currentBlurStrength.dp)
                    .background(Color.White.copy(alpha = currentTintAlpha))
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
                        .verticalScroll(rememberScrollState())
                ) {
                    Text(
                        "Units",
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )

                    Card(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.1f))
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
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

                    Card(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.1f))
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text("Mode", color = Color.White, fontWeight = FontWeight.Medium)
                            Spacer(modifier = Modifier.height(8.dp))
                            
                            SegmentedSelector(
                                options = listOf("Precise", "Obfuscated"),
                                selectedIndex = if (uiState.obfuscationMode == ObfuscationMode.PRECISE) 0 else 1,
                                onOptionSelected = { 
                                    viewModel.setObfuscationMode(if (it == 0) ObfuscationMode.PRECISE else ObfuscationMode.GRID)
                                }
                            )

                            if (uiState.obfuscationMode == ObfuscationMode.GRID) {
                                Spacer(modifier = Modifier.height(16.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("Grid Size", color = Color.White)
                                    Text("${uiState.gridKm.roundToInt()} km", color = Color.White, fontWeight = FontWeight.Bold)
                                }
                                val steps = listOf(1f, 2f, 5f, 10f, 20f, 50f)
                                Slider(
                                    value = steps.indexOf(uiState.gridKm).toFloat().coerceAtLeast(0f),
                                    onValueChange = { 
                                        val index = it.roundToInt().coerceIn(0, steps.size - 1)
                                        viewModel.setGridKm(steps[index])
                                    },
                                    valueRange = 0f..(steps.size - 1).toFloat(),
                                    steps = steps.size - 2,
                                    colors = SliderDefaults.colors(
                                        thumbColor = Color.White,
                                        activeTrackColor = Color.White,
                                        inactiveTrackColor = Color.White.copy(alpha = 0.3f)
                                    )
                                )
                                val label = when (uiState.gridKm) {
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
                        "Experimental Features",
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(top = 16.dp, bottom = 8.dp)
                    )

                    Card(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.1f))
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Experimental Mode", color = Color.White, fontWeight = FontWeight.Medium)
                                Text("Enable simulated pings and wind-powder blip effects.", color = Color.White.copy(alpha = 0.6f), fontSize = 12.sp)
                            }
                            Switch(
                                checked = uiState.experimentalEnabled,
                                onCheckedChange = viewModel::setExperimentalEnabled,
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color.White,
                                    checkedTrackColor = Color.White.copy(alpha = 0.4f),
                                    uncheckedThumbColor = Color.White.copy(alpha = 0.4f),
                                    uncheckedTrackColor = Color.Transparent
                                )
                            )
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
                }

                Button(
                    onClick = onBack,
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
