package com.example.anemoi.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Error
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.anemoi.data.TempUnit
import com.example.anemoi.viewmodel.WeatherViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DebugScreen(onBack: () -> Unit, viewModel: WeatherViewModel) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val debugLogs by viewModel.debugLogs.collectAsState()
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings & Debug") },
                navigationIcon = {
                    IconButton(onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onBack()
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .padding(padding)
                .padding(16.dp)
                .fillMaxSize()
        ) {
            if (uiState.errors.isNotEmpty()) {
                item {
                    Text("Active Errors", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error)
                    Spacer(modifier = Modifier.height(8.dp))
                }
                items(uiState.errors) { error ->
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(12.dp)
                        ) {
                            Icon(Icons.Default.Error, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(error, color = MaterialTheme.colorScheme.onErrorContainer, fontSize = 14.sp)
                        }
                    }
                }
                item {
                    Spacer(modifier = Modifier.height(16.dp))
                    HorizontalDivider()
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }

            item {
                Text("Temperature Unit", fontWeight = FontWeight.Bold)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    TempUnit.entries.forEach { unit ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(
                                selected = uiState.tempUnit == unit,
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    viewModel.setTempUnit(unit)
                                }
                            )
                            Text(unit.name.lowercase().replaceFirstChar { it.uppercase() })
                        }
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(16.dp))
            }

            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Custom Visual Values", modifier = Modifier.weight(1f), fontWeight = FontWeight.Bold)
                    Switch(
                        checked = uiState.customValuesEnabled,
                        onCheckedChange = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            viewModel.toggleCustomValues(it)
                        }
                    )
                }
                
                AnimatedVisibility(visible = uiState.customValuesEnabled) {
                    Column {
                        Text("Text Transparency: ${((1f - uiState.textAlpha) * 100).toInt()}%", fontSize = 14.sp)
                        Slider(
                            value = uiState.textAlpha,
                            onValueChange = { viewModel.setTextAlpha(it) },
                            valueRange = 0.1f..1f
                        )

                        Spacer(modifier = Modifier.height(8.dp))
                        
                        Text("Sheet Blur: ${uiState.sheetBlurStrength.toInt()}dp", fontSize = 14.sp)
                        Slider(
                            value = uiState.sheetBlurStrength,
                            onValueChange = { viewModel.setSheetBlurStrength(it) },
                            valueRange = 0f..100f
                        )

                        Spacer(modifier = Modifier.height(8.dp))
                        
                        Text("Sheet Distortion: ${(uiState.sheetDistortion * 100).toInt()}%", fontSize = 14.sp)
                        Slider(
                            value = uiState.sheetDistortion,
                            onValueChange = { viewModel.setSheetDistortion(it) },
                            valueRange = 0f..1f
                        )

                        Spacer(modifier = Modifier.height(8.dp))
                        
                        Text("Search Bar Blueness: ${(uiState.searchBarTintAlpha * 100).toInt()}%", fontSize = 14.sp)
                        Slider(
                            value = uiState.searchBarTintAlpha,
                            onValueChange = { viewModel.setSearchBarTintAlpha(it) },
                            valueRange = 0f..1f
                        )
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(16.dp))
            }

            item {
                Button(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        viewModel.testNetwork(context)
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Test Network Connection")
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Button(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        viewModel.checkPermissions(context)
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Check Permissions")
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Button(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        viewModel.testApiConnections()
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Test API Connections")
                }

                Spacer(modifier = Modifier.height(8.dp))

                Button(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        viewModel.getCurrentLocation(context)
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Debug: Find Current Location")
                }

                Spacer(modifier = Modifier.height(16.dp))
                
                Text("Logs:", fontWeight = FontWeight.Bold)
                HorizontalDivider()
            }
            
            items(debugLogs) { log ->
                Text(log, fontSize = 12.sp, modifier = Modifier.padding(vertical = 2.dp))
            }
        }
    }
}
