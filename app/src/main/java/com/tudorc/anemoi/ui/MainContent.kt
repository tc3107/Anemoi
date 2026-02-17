package com.tudorc.anemoi.ui

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalFocusManager
import com.tudorc.anemoi.ui.screens.WeatherScreen
import com.tudorc.anemoi.viewmodel.WeatherViewModel

@Composable
fun MainContent(viewModel: WeatherViewModel) {
    val uiState by viewModel.uiState.collectAsState()
    val focusManager = LocalFocusManager.current

    // Back action handler for global states
    BackHandler(enabled = uiState.isSettingsOpen || uiState.isOrganizerMode || uiState.searchQuery.isNotEmpty()) {
        if (uiState.isSettingsOpen) {
            viewModel.toggleSettings(false)
        } else if (uiState.isOrganizerMode) {
            viewModel.toggleOrganizerMode(false)
        } else if (uiState.searchQuery.isNotEmpty()) {
            viewModel.onSearchQueryChanged("")
            focusManager.clearFocus()
        }
    }

    // We always show WeatherScreen so overlays (Settings, Organizer)
    // layer over the same weather surface.
    WeatherScreen(viewModel = viewModel)
}
