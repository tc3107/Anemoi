package com.example.anemoi.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.core.content.ContextCompat
import com.example.anemoi.data.LocationItem
import com.example.anemoi.ui.components.DynamicWeatherBackground
import com.example.anemoi.ui.components.SearchBar
import com.example.anemoi.ui.components.WeatherDetailsSheet
import com.example.anemoi.ui.components.WeatherDisplay
import com.example.anemoi.util.PerformanceProfiler
import com.example.anemoi.util.WeatherDatasetKind
import com.example.anemoi.util.WeatherFreshnessConfig
import com.example.anemoi.util.backgroundOverridePresetAt
import com.example.anemoi.util.backgroundOverrideTimeIso
import com.example.anemoi.viewmodel.WeatherViewModel
import kotlinx.coroutines.flow.distinctUntilChanged
import java.util.Locale

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun WeatherScreen(viewModel: WeatherViewModel) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val screenTapInteractionSource = remember { MutableInteractionSource() }
    
    val favorites = uiState.favorites
    val searchedLocation = remember(uiState.searchedLocation, uiState.selectedLocation, uiState.isFollowMode, favorites) {
        val persistedSearched = uiState.searchedLocation?.takeIf { loc -> favorites.none { it.name == loc.name } }
        persistedSearched ?: uiState.selectedLocation?.takeIf { selected ->
            !uiState.isFollowMode && favorites.none { it.name == selected.name }
        }
    }
    
    val totalPages = 1 + favorites.size + (if (searchedLocation != null) 1 else 0)
    val pagerState = rememberPagerState(pageCount = { totalPages })

    val targetPage = remember(uiState.selectedLocation, uiState.isFollowMode, favorites, searchedLocation) {
        if (uiState.isFollowMode) 0
        else {
            val favIndex = favorites.indexOfFirst { it.name == uiState.selectedLocation?.name }
            if (favIndex != -1) favIndex + 1
            else if (searchedLocation != null && uiState.selectedLocation?.name == searchedLocation.name) favorites.size + 1
            else 0
        }
    }
    val latestTargetPage by rememberUpdatedState(targetPage)
    val latestFavorites by rememberUpdatedState(favorites)
    val latestSearchedLocation by rememberUpdatedState(searchedLocation)
    val latestIsFollowMode by rememberUpdatedState(uiState.isFollowMode)
    var overlayProfilerSnapshot by remember {
        mutableStateOf(PerformanceProfiler.Snapshot.Empty)
    }

    var isReady by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions.entries.all { it.value }) {
            if (pagerState.currentPage == 0) {
                viewModel.setFollowMode(true, context)
            } else {
                viewModel.getCurrentLocation(context)
            }
        }
    }

    LaunchedEffect(uiState.selectedLocation) {
        if (!isReady && uiState.selectedLocation != null) {
            pagerState.scrollToPage(targetPage)
            isReady = true
        }
    }

    LaunchedEffect(pagerState, isReady) {
        if (!isReady) return@LaunchedEffect
        snapshotFlow { pagerState.settledPage }
            .distinctUntilChanged()
            .collect { settledPage ->
                if (settledPage == latestTargetPage) return@collect

                viewModel.clearStatuses()
                if (settledPage == 0) {
                    if (!latestIsFollowMode) {
                        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                            viewModel.setFollowMode(true, context)
                        } else {
                            permissionLauncher.launch(
                                arrayOf(
                                    Manifest.permission.ACCESS_FINE_LOCATION,
                                    Manifest.permission.ACCESS_COARSE_LOCATION
                                )
                            )
                        }
                    }
                } else {
                    if (latestIsFollowMode) {
                        viewModel.setFollowMode(false, context)
                    }
                    val location = if (settledPage <= latestFavorites.size) {
                        latestFavorites.getOrNull(settledPage - 1)
                    } else {
                        latestSearchedLocation
                    }

                    if (location != null) {
                        viewModel.onLocationSelected(location, isManualSearch = false)
                    }
                }
            }
    }

    LaunchedEffect(targetPage) {
        if (isReady && pagerState.currentPage != targetPage) {
            pagerState.animateScrollToPage(targetPage)
        }
    }

    LaunchedEffect(uiState.isPerformanceOverlayEnabled) {
        PerformanceProfiler.setEnabled(uiState.isPerformanceOverlayEnabled)
        if (!uiState.isPerformanceOverlayEnabled) {
            overlayProfilerSnapshot = PerformanceProfiler.Snapshot.Empty
            PerformanceProfiler.reset()
            return@LaunchedEffect
        }

        var windowStartNs = 0L
        var previousFrameNs = 0L

        while (true) {
            withFrameNanos { nowNs ->
                if (windowStartNs == 0L) {
                    windowStartNs = nowNs
                }
                if (previousFrameNs != 0L) {
                    val frameDurationNs = nowNs - previousFrameNs
                    PerformanceProfiler.recordFrameDuration(frameDurationNs)
                }
                previousFrameNs = nowNs

                val elapsedNs = nowNs - windowStartNs
                if (elapsedNs >= 1_000_000_000L) {
                    overlayProfilerSnapshot = PerformanceProfiler.snapshot(windowMs = 10_000L, topN = 80)
                    windowStartNs = nowNs
                }
            }
        }
    }

    val currentTintAlpha = if (uiState.customValuesEnabled) uiState.searchBarTintAlpha else 0.15f
    val currentBlurStrength = if (uiState.customValuesEnabled) uiState.sheetBlurStrength else 16f
    val textAlpha = if (uiState.customValuesEnabled) uiState.textAlpha else 0.8f
    val statusBarInsetTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val warningStaleServeWindowMs = WeatherFreshnessConfig.STALE_SERVE_WINDOW_MS
    val warningCurrentThresholdMs = WeatherFreshnessConfig.CURRENT_THRESHOLD_MS
    val warningHourlyThresholdMs = WeatherFreshnessConfig.HOURLY_THRESHOLD_MS
    val warningDailyThresholdMs = WeatherFreshnessConfig.DAILY_THRESHOLD_MS
    val warningNow = System.currentTimeMillis()
    val warningKey = uiState.selectedLocation?.let { "${it.lat},${it.lon}" }
    val warningSignatureMismatch = warningKey != null &&
        uiState.cacheSignatureMap[warningKey] != uiState.activeRequestSignature
    val warningWeather = warningKey?.let { uiState.weatherMap[it] }
    val warningCurrentUpdatedAt = warningKey?.let { uiState.currentUpdateTimeMap[it] } ?: 0L
    val warningHourlyUpdatedAt = warningKey?.let { uiState.hourlyUpdateTimeMap[it] } ?: 0L
    val warningDailyUpdatedAt = warningKey?.let { uiState.dailyUpdateTimeMap[it] } ?: 0L
    val hasWeatherWarnings = warningSignatureMismatch ||
        hasFreshnessWarning(
            hasData = warningWeather?.currentWeather != null,
            updatedAtMs = warningCurrentUpdatedAt,
            nowMs = warningNow,
            thresholdMs = warningCurrentThresholdMs,
            staleServeWindowMs = warningStaleServeWindowMs
        ) ||
        hasFreshnessWarning(
            hasData = warningWeather?.hourly != null,
            updatedAtMs = warningHourlyUpdatedAt,
            nowMs = warningNow,
            thresholdMs = warningHourlyThresholdMs,
            staleServeWindowMs = warningStaleServeWindowMs
        ) ||
        hasFreshnessWarning(
            hasData = warningWeather?.daily != null,
            updatedAtMs = warningDailyUpdatedAt,
            nowMs = warningNow,
            thresholdMs = warningDailyThresholdMs,
            staleServeWindowMs = warningStaleServeWindowMs
        )

    fun locationKey(location: LocationItem?): String? = location?.let { "${it.lat},${it.lon}" }

    fun locationForPage(page: Int): LocationItem? {
        return when {
            page == 0 -> {
                if (uiState.isFollowMode) {
                    uiState.selectedLocation
                } else {
                    val selected = uiState.selectedLocation
                    if (
                        selected != null &&
                        favorites.none { locationKey(it) == locationKey(selected) } &&
                        locationKey(searchedLocation) != locationKey(selected)
                    ) {
                        selected
                    } else {
                        null
                    }
                }
            }
            page <= favorites.size -> favorites.getOrNull(page - 1)
            else -> searchedLocation
        }
    }

    val isPagerSwitchInProgress =
        pagerState.isScrollInProgress || pagerState.currentPage != pagerState.settledPage
    val settledPageLocation =
        locationForPage(pagerState.settledPage) ?: uiState.selectedLocation
    val currentPageLocation =
        locationForPage(pagerState.currentPage) ?: uiState.selectedLocation
    val selectedLocationKey = locationKey(uiState.selectedLocation)
    val settledLocationKey = locationKey(settledPageLocation)
    val searchBarLocation = when {
        selectedLocationKey != null && selectedLocationKey != settledLocationKey ->
            uiState.selectedLocation
        isPagerSwitchInProgress ->
            currentPageLocation
        else ->
            uiState.selectedLocation
    }
    val organizerRecentLocation = uiState.searchedLocation?.takeIf { searched ->
        favorites.none { favorite -> favorite.lat == searched.lat && favorite.lon == searched.lon }
    }
    val overrideBackgroundPreset = backgroundOverridePresetAt(uiState.backgroundOverridePresetIndex)
    val overrideBackgroundTimeIso = backgroundOverrideTimeIso(overrideBackgroundPreset.hourOfDay)
    val backgroundPageKey = settledLocationKey ?: "page:${pagerState.settledPage}"
    val backgroundKey = settledLocationKey
    val backgroundRawWeather = backgroundKey?.let { uiState.weatherMap[it] }
    val backgroundCurrentWeather = backgroundRawWeather?.currentWeather
    val realWindSpeedKmh = backgroundCurrentWeather?.windSpeed ?: 0.0
    val backgroundWindSpeedKmh = if (uiState.isBackgroundOverrideEnabled) {
        uiState.backgroundOverrideWindSpeedKmh.toDouble()
    } else {
        realWindSpeedKmh
    }

    BackHandler(enabled = uiState.isOrganizerMode || uiState.isSettingsOpen) {
        if (uiState.isOrganizerMode) {
            viewModel.toggleOrganizerMode(false)
        } else if (uiState.isSettingsOpen) {
            viewModel.toggleSettings(false)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .combinedClickable(
                interactionSource = screenTapInteractionSource,
                indication = null,
                onClick = {},
                onLongClick = {
                    if (!uiState.isSettingsOpen) {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        viewModel.toggleOrganizerMode(true)
                    }
                }
            )
    ) {
        if (uiState.isBackgroundOverrideEnabled) {
            DynamicWeatherBackground(
                weatherCode = overrideBackgroundPreset.weatherCode,
                weatherTimeIso = overrideBackgroundTimeIso,
                windSpeedKmh = backgroundWindSpeedKmh,
                pageKey = backgroundPageKey,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            DynamicWeatherBackground(
                weatherCode = backgroundCurrentWeather?.weatherCode,
                weatherTimeIso = backgroundCurrentWeather?.time,
                windSpeedKmh = backgroundWindSpeedKmh,
                pageKey = backgroundPageKey,
                modifier = Modifier.fillMaxSize()
            )
        }

        AnimatedVisibility(
            visible = !uiState.isOrganizerMode && !uiState.isSettingsOpen,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                WeatherDetailsSheet(
                    uiState = uiState,
                    handleHeight = 0.dp,
                    onHandleClick = {},
                    isExpanded = true,
                    showHandle = false,
                    headerContent = {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = statusBarInsetTop + 2.dp)
                        ) {
                            SearchBar(
                                query = uiState.searchQuery,
                                onQueryChange = viewModel::onSearchQueryChanged,
                                suggestions = uiState.suggestions,
                                searchStatusMessage = uiState.searchStatusMessage,
                                favorites = favorites,
                                onLocationSelected = viewModel::onLocationSelected,
                                onSettingsClick = { viewModel.toggleSettings(true) },
                                onMenuClick = { viewModel.toggleOrganizerMode(true) },
                                onToggleFavorite = viewModel::toggleFavorite,
                                selectedLocation = searchBarLocation,
                                isLocating = uiState.isLocating,
                                isFollowMode = uiState.isFollowMode,
                                hasErrors = uiState.errors.isNotEmpty(),
                                hasWarnings = hasWeatherWarnings,
                                tintAlpha = currentTintAlpha,
                                blurStrength = currentBlurStrength,
                                onLocateClick = {
                                    if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                                        viewModel.getCurrentLocation(context)
                                    } else {
                                        permissionLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
                                    }
                                },
                                onLocateLongClick = {
                                    if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                                        viewModel.setFollowMode(!uiState.isFollowMode, context)
                                    } else {
                                        permissionLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
                                    }
                                }
                            )

                            // WeatherDetailsSheet contributes +8.dp (header footer) and +20.dp (item spacing)
                            // below this header block. Keep the temperature block vertically centered
                            // between search bar and first widget by balancing total top/bottom gaps.
                            Spacer(modifier = Modifier.height(28.dp))

                            HorizontalPager(
                                state = pagerState,
                                modifier = Modifier.fillMaxWidth(),
                                userScrollEnabled = true
                            ) { page ->
                                val pageLocation = locationForPage(page)

                                if (pageLocation != null) {
                                    val key = "${pageLocation.lat},${pageLocation.lon}"
                                    val now = System.currentTimeMillis()

                                    val isSignatureMatch = uiState.cacheSignatureMap[key] == uiState.activeRequestSignature
                                    val rawWeather = uiState.weatherMap[key]
                                    val currentUpdatedAt = uiState.currentUpdateTimeMap[key] ?: 0L
                                    val hourlyUpdatedAt = uiState.hourlyUpdateTimeMap[key] ?: 0L
                                    val dailyUpdatedAt = uiState.dailyUpdateTimeMap[key] ?: 0L

                                    val hasCurrent = rawWeather?.currentWeather != null
                                    val hasHourly = rawWeather?.hourly != null
                                    val hasDaily = rawWeather?.daily != null

                                    val currentThresholdMs = WeatherFreshnessConfig.thresholdMs(WeatherDatasetKind.CURRENT)
                                    val hourlyThresholdMs = WeatherFreshnessConfig.thresholdMs(WeatherDatasetKind.HOURLY)
                                    val dailyThresholdMs = WeatherFreshnessConfig.thresholdMs(WeatherDatasetKind.DAILY)
                                    val currentThresholdLabel = WeatherFreshnessConfig.thresholdLabel(WeatherDatasetKind.CURRENT)
                                    val hourlyThresholdLabel = WeatherFreshnessConfig.thresholdLabel(WeatherDatasetKind.HOURLY)
                                    val dailyThresholdLabel = WeatherFreshnessConfig.thresholdLabel(WeatherDatasetKind.DAILY)
                                    val staleServeWindowMs = WeatherFreshnessConfig.STALE_SERVE_WINDOW_MS

                                    val weather = rawWeather

                                    val hasUnknownFreshness = (hasCurrent && currentUpdatedAt <= 0L) ||
                                        (hasHourly && hourlyUpdatedAt <= 0L) ||
                                        (hasDaily && dailyUpdatedAt <= 0L)
                                    val isCurrentExpired = hasCurrent && currentUpdatedAt > 0L &&
                                        now - currentUpdatedAt > staleServeWindowMs
                                    val isHourlyExpired = hasHourly && hourlyUpdatedAt > 0L &&
                                        now - hourlyUpdatedAt > staleServeWindowMs
                                    val isDailyExpired = hasDaily && dailyUpdatedAt > 0L &&
                                        now - dailyUpdatedAt > staleServeWindowMs
                                    val isCurrentAged = hasCurrent && currentUpdatedAt > 0L &&
                                        now - currentUpdatedAt > currentThresholdMs
                                    val isHourlyAged = hasHourly && hourlyUpdatedAt > 0L &&
                                        now - hourlyUpdatedAt > hourlyThresholdMs
                                    val isDailyAged = hasDaily && dailyUpdatedAt > 0L &&
                                        now - dailyUpdatedAt > dailyThresholdMs
                                    val staleHintText = when {
                                        !isSignatureMatch -> "Refreshing after settings change"
                                        hasUnknownFreshness -> "Refreshing cached weather"
                                        isCurrentExpired || isHourlyExpired || isDailyExpired -> "Refreshing expired weather cache"
                                        isCurrentAged -> "Updating current conditions"
                                        isHourlyAged -> "Updating hourly forecast"
                                        isDailyAged -> "Updating daily forecast"
                                        else -> null
                                    }
                                    val staleDetailsLines = if (staleHintText != null) {
                                        fun datasetStatus(
                                            hasData: Boolean,
                                            updatedAt: Long,
                                            label: String,
                                            thresholdMs: Long,
                                            thresholdLabel: String
                                        ): String {
                                            if (!hasData) return "$label: missing"
                                            if (updatedAt <= 0L) return "$label: timestamp unavailable"
                                            val ageMs = (now - updatedAt).coerceAtLeast(0L)
                                            val ageText = formatOverlayAge(now, updatedAt)
                                            return if (ageMs > staleServeWindowMs) {
                                                "$label: $ageText old (expired past ${WeatherFreshnessConfig.STALE_WINDOW_SUMMARY} fallback window)"
                                            } else if (ageMs > thresholdMs) {
                                                "$label: $ageText old (past ${thresholdLabel} refresh threshold)"
                                            } else {
                                                "$label: $ageText old (within ${thresholdLabel} refresh threshold)"
                                            }
                                        }

                                        buildList {
                                            add("Location: ${pageLocation.name}")
                                            add("Status: $staleHintText")
                                            add(
                                                if (isSignatureMatch) {
                                                    "Request signature: matches current settings"
                                                } else {
                                                    "Request signature: mismatch (showing older-mode cache)"
                                                }
                                            )
                                            add(
                                                datasetStatus(
                                                    hasData = hasCurrent,
                                                    updatedAt = currentUpdatedAt,
                                                    label = "Current",
                                                    thresholdMs = currentThresholdMs,
                                                    thresholdLabel = currentThresholdLabel
                                                )
                                            )
                                            add(
                                                datasetStatus(
                                                    hasData = hasHourly,
                                                    updatedAt = hourlyUpdatedAt,
                                                    label = "Hourly",
                                                    thresholdMs = hourlyThresholdMs,
                                                    thresholdLabel = hourlyThresholdLabel
                                                )
                                            )
                                            add(
                                                datasetStatus(
                                                    hasData = hasDaily,
                                                    updatedAt = dailyUpdatedAt,
                                                    label = "Daily",
                                                    thresholdMs = dailyThresholdMs,
                                                    thresholdLabel = dailyThresholdLabel
                                                )
                                            )
                                            add("Refresh thresholds: ${WeatherFreshnessConfig.THRESHOLD_SUMMARY}")
                                            add("Stale fallback window: ${WeatherFreshnessConfig.STALE_WINDOW_SUMMARY}")
                                        }
                                    } else {
                                        emptyList()
                                    }

                                    WeatherDisplay(
                                        weather = weather,
                                        tempUnit = uiState.tempUnit,
                                        showDashesOverride = page == 0 && !uiState.locationFound,
                                        textAlpha = textAlpha,
                                        staleHintText = staleHintText,
                                        staleDetailsTitle = "Weather Refresh Status",
                                        staleDetailsLines = staleDetailsLines
                                    )
                                } else {
                                    WeatherDisplay(
                                        weather = null,
                                        tempUnit = uiState.tempUnit,
                                        showDashesOverride = true,
                                        textAlpha = textAlpha
                                    )
                                }
                            }
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .zIndex(3f)
                ) {
                    TopPageStatusStrip(
                        favorites = favorites,
                        searchedLocation = searchedLocation,
                        pageStatuses = uiState.pageStatuses,
                        currentPage = pagerState.currentPage,
                        totalPages = totalPages,
                        locationFound = uiState.locationFound,
                        tintAlpha = currentTintAlpha,
                        blurStrength = currentBlurStrength,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Column(modifier = Modifier.statusBarsPadding()) {
                        if (uiState.isLoading) {
                            LinearProgressIndicator(
                                modifier = Modifier.fillMaxWidth().height(2.dp),
                                color = Color.White,
                                trackColor = Color.Transparent
                            )
                        }
                        if (uiState.isLocating) {
                            LinearProgressIndicator(
                                modifier = Modifier.fillMaxWidth().height(2.dp),
                                color = Color(0xFF4285F4),
                                trackColor = Color.Transparent
                            )
                        }
                    }
                }
            }
        }

        AnimatedVisibility(
            visible = uiState.isOrganizerMode,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.fillMaxSize()
        ) {
            OrganizerOverlay(
                favorites = favorites,
                recentLocation = organizerRecentLocation,
                onReorder = viewModel::reorderFavorites,
                onToggleFavorite = viewModel::toggleFavorite,
                onRenameLocation = viewModel::renameLocationDisplayName,
                onSelectCurrentLocation = {
                    viewModel.setFollowMode(true, context)
                    viewModel.toggleOrganizerMode(false)
                },
                onSelectRecentLocation = { location ->
                    if (uiState.isFollowMode) {
                        viewModel.setFollowMode(false, context)
                    }
                    viewModel.onLocationSelected(location)
                    viewModel.toggleOrganizerMode(false)
                },
                onSelect = { location ->
                    if (uiState.isFollowMode) {
                        viewModel.setFollowMode(false, context)
                    }
                    viewModel.onLocationSelected(location)
                    viewModel.toggleOrganizerMode(false)
                },
                onClose = { viewModel.toggleOrganizerMode(false) }
            )
        }

        AnimatedVisibility(
            visible = uiState.isSettingsOpen,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.fillMaxSize()
        ) {
            SettingsScreen(
                viewModel = viewModel,
                onBack = { viewModel.toggleSettings(false) }
            )
        }

        if (uiState.isPerformanceOverlayEnabled) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = statusBarInsetTop)
                    .width(18.dp)
                    .height(1.dp)
                    .background(Color.White.copy(alpha = 0.92f))
                    .zIndex(21f)
            )
            ResourceDistributionOverlay(
                snapshot = overlayProfilerSnapshot,
                onDisable = { viewModel.setPerformanceOverlayEnabled(false) },
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .statusBarsPadding()
                    .padding(end = 8.dp, top = 8.dp)
                    .zIndex(20f)
            )
        }
    }
}

@Composable
private fun TopPageStatusStrip(
    favorites: List<LocationItem>,
    searchedLocation: LocationItem?,
    pageStatuses: Map<String, Boolean?>,
    currentPage: Int,
    totalPages: Int,
    locationFound: Boolean,
    tintAlpha: Float,
    blurStrength: Float,
    modifier: Modifier = Modifier
) {
    val sectionColors = buildList {
        val isLocationActive = currentPage == 0
        add(
            if (isLocationActive) {
                if (locationFound) Color(0xFF4285F4) else Color.White
            } else {
                Color(0xFF8FB8FF).copy(alpha = 0.64f)
            }
        )

        favorites.forEachIndexed { index, location ->
            val isActive = currentPage == index + 1
            val key = "${location.lat},${location.lon}"
            val status = pageStatuses[key]
            add(
                when {
                    isActive && status == true -> Color.Green
                    isActive -> Color.White
                    else -> Color.White.copy(alpha = 0.5f)
                }
            )
        }

        if (searchedLocation != null) {
            val isActive = currentPage == totalPages - 1
            val key = "${searchedLocation.lat},${searchedLocation.lon}"
            val status = pageStatuses[key]
            add(
                when {
                    isActive && status == true -> Color.Green
                    isActive -> Color.White
                    else -> Color.White.copy(alpha = 0.32f)
                }
            )
        }
    }

    val stripShape = RoundedCornerShape(bottomStart = 10.dp, bottomEnd = 10.dp)
    Box(
        modifier = modifier
            .height(10.dp)
            .clip(stripShape)
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
                shape = stripShape
            )
    ) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .blur((blurStrength * 0.55f).coerceAtLeast(0f).dp)
                .background(Color.White.copy(alpha = tintAlpha.coerceIn(0f, 1f) * 0.85f))
        )
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 2.dp, vertical = 1.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            sectionColors.forEach { sectionColor ->
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(1.5.dp))
                        .background(sectionColor)
                )
            }
        }
    }
}

@Composable
private fun ResourceDistributionOverlay(
    snapshot: PerformanceProfiler.Snapshot,
    onDisable: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()
    val tapInteractionSource = remember { MutableInteractionSource() }
    val categorySlices = remember(snapshot.categories, snapshot.totalSectionNs) {
        buildCategorySlices(snapshot)
    }
    val topSections = remember(snapshot.sections) {
        snapshot.sections.take(6)
    }
    val dialBreakdown = remember(snapshot.sections) {
        buildDialBreakdownEntries(snapshot.sections)
    }
    val dialSlices = remember(dialBreakdown) {
        dialBreakdown.map { entry ->
            DistributionSlice(
                label = entry.label,
                percent = entry.percentOfDial,
                color = entry.color
            )
        }
    }
    val noiseBreakdown = remember(snapshot.sections) {
        buildNoiseBreakdownEntries(snapshot.sections)
    }
    val noiseSlices = remember(noiseBreakdown) {
        noiseBreakdown.map { entry ->
            DistributionSlice(
                label = entry.label,
                percent = entry.percentOfNoise,
                color = entry.color
            )
        }
    }
    val noiseMetrics = remember(snapshot.sections) {
        buildNoiseMetricSummary(snapshot.sections)
    }
    val backgroundBreakdown = remember(snapshot.sections, snapshot.totalSectionNs) {
        buildBackgroundBreakdown(
            sections = snapshot.sections,
            totalSectionNs = snapshot.totalSectionNs
        )
    }
    val widgetDrawBreakdown = remember(snapshot.sections, snapshot.totalSectionNs) {
        buildWidgetDrawBreakdown(
            sections = snapshot.sections,
            totalSectionNs = snapshot.totalSectionNs
        )
    }
    val backgroundDrawGroupSlices = remember(backgroundBreakdown) {
        backgroundBreakdown?.drawGroupEntries?.map { entry ->
            DistributionSlice(
                label = "Draw/${entry.label}",
                percent = entry.percentOfDraw,
                color = entry.color
            )
        } ?: emptyList()
    }
    val widgetDrawGroupSlices = remember(widgetDrawBreakdown) {
        widgetDrawBreakdown?.groupEntries?.map { entry ->
            DistributionSlice(
                label = entry.label,
                percent = entry.percentOfWidgetDraw,
                color = entry.color
            )
        } ?: emptyList()
    }

    Box(
        modifier = modifier
            .widthIn(min = 240.dp, max = 320.dp)
            .fillMaxHeight(0.88f)
            .padding(horizontal = 10.dp, vertical = 8.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .clip(RoundedCornerShape(14.dp))
                .background(Color(0x66111822))
                .border(
                    width = 1.dp,
                    color = Color.White.copy(alpha = 0.14f),
                    shape = RoundedCornerShape(14.dp)
                )
                .clickable(
                    interactionSource = tapInteractionSource,
                    indication = null,
                    onClick = onDisable
                )
                .verticalScroll(scrollState)
                .padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "RESOURCE DISTRIBUTION",
                color = Color.White.copy(alpha = 0.9f),
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.SemiBold,
                fontSize = 11.sp
            )
            val frameSnapshot = snapshot.frame
            val averageDelayMs = frameSnapshot.averageFrameMs
            val fps = if (averageDelayMs > 0f) 1000f / averageDelayMs else 0f
            val perfLine = if (frameSnapshot.sampleCount > 0 && averageDelayMs > 0f) {
                "fps=${String.format(Locale.US, "%.1f", fps)}  delay=${String.format(Locale.US, "%.1f", averageDelayMs)}ms"
            } else {
                "fps=-  delay=-"
            }
            Text(
                text = perfLine,
                color = Color.White.copy(alpha = 0.78f),
                fontFamily = FontFamily.Monospace,
                fontSize = 10.sp
            )

            if (snapshot.totalSectionNs <= 0L || topSections.isEmpty()) {
                Text(
                    text = "Collecting profiler samples...",
                    color = Color.White.copy(alpha = 0.66f),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.sp
                )
                return@Column
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CategoryPieChart(
                    slices = categorySlices,
                    modifier = Modifier.size(92.dp)
                )
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    categorySlices.forEach { slice ->
                        DistributionLegendRow(slice = slice)
                    }
                }
            }

            Text(
                text = "Category Share",
                color = Color.White.copy(alpha = 0.75f),
                fontFamily = FontFamily.Monospace,
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold
            )

            categorySlices.forEach { slice ->
                val totalNs = ((snapshot.totalSectionNs.toDouble() * slice.percent.toDouble()) / 100.0)
                    .toLong()
                    .coerceAtLeast(0L)
                DistributionSectionRow(
                    label = compactProfilerLabel(slice.label),
                    percent = slice.percent,
                    totalMs = formatOverlayNsMs(totalNs),
                    color = slice.color
                )
            }

            Text(
                text = "Top Component Share",
                color = Color.White.copy(alpha = 0.75f),
                fontFamily = FontFamily.Monospace,
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold
            )

            ComponentShareBar(sections = topSections)

            topSections.take(3).forEachIndexed { index, section ->
                DistributionSectionRow(
                    label = compactProfilerLabel(section.name),
                    percent = section.sharePercent,
                    totalMs = formatOverlayNsMs(section.totalNs),
                    color = profilerColorForKey(section.name, index)
                )
            }

            widgetDrawBreakdown?.let { breakdown ->
                Text(
                    text = "Widget Draw Breakdown",
                    color = Color.White.copy(alpha = 0.75f),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "${formatOverlayPercent(breakdown.percentOfTotal)}% of total (${formatOverlayNsMs(breakdown.totalNs)} ms)",
                    color = Color.White.copy(alpha = 0.68f),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 9.sp
                )

                if (widgetDrawGroupSlices.isNotEmpty()) {
                    SliceShareBar(slices = widgetDrawGroupSlices)
                }

                breakdown.groupEntries.take(5).forEach { entry ->
                    DistributionSectionRow(
                        label = entry.label,
                        percent = entry.percentOfWidgetDraw,
                        totalMs = formatOverlayNsMs(entry.totalNs),
                        color = entry.color
                    )
                }

                Text(
                    text = "Top Widget Draw Hotspots",
                    color = Color.White.copy(alpha = 0.72f),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.SemiBold
                )

                breakdown.entries.take(6).forEach { entry ->
                    DistributionSectionRow(
                        label = "Widget/${compactWidgetLabel(entry.label)}",
                        percent = entry.percentOfWidgetDraw,
                        totalMs = formatOverlayNsMs(entry.totalNs),
                        color = entry.color
                    )
                }
            }

            backgroundBreakdown?.let { breakdown ->
                Text(
                    text = "Background Breakdown",
                    color = Color.White.copy(alpha = 0.75f),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "${formatOverlayPercent(breakdown.percentOfTotal)}% of total (${formatOverlayNsMs(breakdown.totalNs)} ms)",
                    color = Color.White.copy(alpha = 0.68f),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 9.sp
                )
                Text(
                    text = "draw ${formatOverlayPercent(breakdown.drawPercentOfBackground)}% of background (${formatOverlayPercent(breakdown.drawPercentOfTotal)}% total, ${formatOverlayNsMs(breakdown.drawTotalNs)} ms)",
                    color = Color.White.copy(alpha = 0.68f),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 9.sp
                )

                if (backgroundDrawGroupSlices.isNotEmpty()) {
                    SliceShareBar(slices = backgroundDrawGroupSlices)
                }

                breakdown.drawGroupEntries.take(5).forEach { entry ->
                    DistributionSectionRow(
                        label = "Draw/${entry.label}",
                        totalMs = formatOverlayNsMs(entry.totalNs),
                        percent = entry.percentOfDraw,
                        color = entry.color
                    )
                }

                Text(
                    text = "Top Draw Hotspots",
                    color = Color.White.copy(alpha = 0.72f),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.SemiBold
                )

                breakdown.drawEntries.take(6).forEach { entry ->
                    DistributionSectionRow(
                        label = "BgDraw/${compactBackgroundLabel(entry.label)}",
                        percent = entry.percentOfDraw,
                        totalMs = formatOverlayNsMs(entry.totalNs),
                        color = entry.color
                    )
                }
            }

            if (noiseBreakdown.isNotEmpty() || noiseMetrics != null) {
                Text(
                    text = "Noise Breakdown",
                    color = Color.White.copy(alpha = 0.75f),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold
                )

                if (noiseSlices.isNotEmpty()) {
                    SliceShareBar(slices = noiseSlices)
                }

                noiseBreakdown.take(4).forEach { entry ->
                    DistributionSectionRow(
                        label = "Noise/${entry.label}",
                        percent = entry.percentOfNoise,
                        totalMs = formatOverlayNsMs(entry.totalNs),
                        color = entry.color
                    )
                }

                noiseMetrics?.let { metrics ->
                    NoiseMetricSummaryRow(metrics = metrics)
                }
            }

            if (dialBreakdown.isNotEmpty()) {
                Text(
                    text = "Dial Breakdown",
                    color = Color.White.copy(alpha = 0.75f),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold
                )

                SliceShareBar(slices = dialSlices)

                dialBreakdown.take(4).forEach { entry ->
                    DistributionSectionRow(
                        label = "Dial/${entry.label}",
                        percent = entry.percentOfDial,
                        totalMs = formatOverlayNsMs(entry.totalNs),
                        color = entry.color
                    )
                }
            }
        }
    }
}

private data class DistributionSlice(
    val label: String,
    val percent: Float,
    val color: Color
)

@Composable
private fun CategoryPieChart(
    slices: List<DistributionSlice>,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        if (slices.isEmpty()) return@Canvas
        var startAngle = -90f
        slices.forEach { slice ->
            val sweep = (slice.percent.coerceIn(0f, 100f) / 100f) * 360f
            if (sweep > 0f) {
                drawArc(
                    color = slice.color,
                    startAngle = startAngle,
                    sweepAngle = sweep,
                    useCenter = true,
                    topLeft = Offset.Zero,
                    size = Size(size.width, size.height)
                )
                startAngle += sweep
            }
        }
        drawCircle(
            color = Color(0x55111822),
            radius = size.minDimension * 0.34f,
            center = Offset(size.width / 2f, size.height / 2f)
        )
    }
}

@Composable
private fun DistributionLegendRow(slice: DistributionSlice) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(slice.color)
        )
        Text(
            text = compactProfilerLabel(slice.label),
            color = Color.White.copy(alpha = 0.82f),
            fontFamily = FontFamily.Monospace,
            fontSize = 10.sp,
            maxLines = 1,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = "${formatOverlayPercent(slice.percent)}%",
            color = Color.White.copy(alpha = 0.78f),
            fontFamily = FontFamily.Monospace,
            fontSize = 10.sp
        )
    }
}

@Composable
private fun ComponentShareBar(
    sections: List<PerformanceProfiler.SectionSnapshot>,
    modifier: Modifier = Modifier
) {
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(10.dp)
            .clip(RoundedCornerShape(5.dp))
            .background(Color.White.copy(alpha = 0.08f))
    ) {
        var cursor = 0f
        sections.forEachIndexed { index, section ->
            val segmentWidth = (size.width * (section.sharePercent.coerceIn(0f, 100f) / 100f))
                .coerceAtLeast(0f)
            if (segmentWidth <= 0f) return@forEachIndexed
            drawRect(
                color = profilerColorForKey(section.name, index),
                topLeft = Offset(cursor, 0f),
                size = Size(segmentWidth, size.height)
            )
            cursor += segmentWidth
            if (cursor >= size.width) return@forEachIndexed
        }
    }
}

@Composable
private fun SliceShareBar(
    slices: List<DistributionSlice>,
    modifier: Modifier = Modifier
) {
    if (slices.isEmpty()) return
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(10.dp)
            .clip(RoundedCornerShape(5.dp))
            .background(Color.White.copy(alpha = 0.08f))
    ) {
        var cursor = 0f
        slices.forEach { slice ->
            val segmentWidth = (size.width * (slice.percent.coerceIn(0f, 100f) / 100f))
                .coerceAtLeast(0f)
            if (segmentWidth <= 0f) return@forEach
            drawRect(
                color = slice.color,
                topLeft = Offset(cursor, 0f),
                size = Size(segmentWidth, size.height)
            )
            cursor += segmentWidth
            if (cursor >= size.width) return@forEach
        }
    }
}

private data class DialBreakdownEntry(
    val label: String,
    val percentOfDial: Float,
    val totalNs: Long,
    val color: Color
)

private data class NoiseBreakdownEntry(
    val label: String,
    val percentOfNoise: Float,
    val totalNs: Long,
    val color: Color
)

private data class NoiseMetricSummary(
    val averagePointCount: Long?,
    val averagePerPointNs: Long?,
    val averageTinyPointCount: Long?,
    val averageMediumPointCount: Long?,
    val averageLargePointCount: Long?
)

private data class BackgroundDrawGroupEntry(
    val label: String,
    val percentOfDraw: Float,
    val totalNs: Long,
    val color: Color
)

private data class WidgetDrawGroupEntry(
    val label: String,
    val percentOfWidgetDraw: Float,
    val totalNs: Long,
    val color: Color
)

private data class WidgetDrawEntry(
    val label: String,
    val percentOfWidgetDraw: Float,
    val totalNs: Long,
    val color: Color
)

private data class WidgetDrawBreakdown(
    val totalNs: Long,
    val percentOfTotal: Float,
    val groupEntries: List<WidgetDrawGroupEntry>,
    val entries: List<WidgetDrawEntry>
)

private data class BackgroundDrawEntry(
    val label: String,
    val percentOfDraw: Float,
    val totalNs: Long,
    val color: Color
)

private data class BackgroundBreakdown(
    val totalNs: Long,
    val percentOfTotal: Float,
    val drawTotalNs: Long,
    val drawPercentOfBackground: Float,
    val drawPercentOfTotal: Float,
    val drawGroupEntries: List<BackgroundDrawGroupEntry>,
    val drawEntries: List<BackgroundDrawEntry>
)

@Composable
private fun DistributionSectionRow(
    label: String,
    percent: Float,
    totalMs: String,
    color: Color
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                color = Color.White.copy(alpha = 0.84f),
                fontFamily = FontFamily.Monospace,
                fontSize = 10.sp,
                maxLines = 1,
                modifier = Modifier.weight(1f)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "${formatOverlayPercent(percent)}% ($totalMs ms)",
                color = Color.White.copy(alpha = 0.72f),
                fontFamily = FontFamily.Monospace,
                fontSize = 9.sp
            )
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(Color.White.copy(alpha = 0.07f))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(fraction = (percent / 100f).coerceIn(0f, 1f))
                    .clip(RoundedCornerShape(3.dp))
                    .background(color)
            )
        }
    }
}

@Composable
private fun NoiseMetricSummaryRow(metrics: NoiseMetricSummary) {
    val avgPoints = metrics.averagePointCount?.toString() ?: "n/a"
    val avgPerPoint = metrics.averagePerPointNs?.let { formatOverlayNsUs(it) } ?: "n/a"
    val radiusMix = buildList {
        metrics.averageTinyPointCount?.let { value -> add("tiny=$value") }
        metrics.averageMediumPointCount?.let { value -> add("mid=$value") }
        metrics.averageLargePointCount?.let { value -> add("large=$value") }
    }.joinToString(separator = "  ")

    Text(
        text = if (radiusMix.isNotEmpty()) {
            "avg points=$avgPoints  avg cost=$avgPerPoint/pt  $radiusMix"
        } else {
            "avg points=$avgPoints  avg cost=$avgPerPoint/pt"
        },
        color = Color.White.copy(alpha = 0.66f),
        fontFamily = FontFamily.Monospace,
        fontSize = 9.sp
    )
}

private fun hasFreshnessWarning(
    hasData: Boolean,
    updatedAtMs: Long,
    nowMs: Long,
    thresholdMs: Long,
    staleServeWindowMs: Long
): Boolean {
    if (!hasData || updatedAtMs <= 0L) {
        return false
    }
    val ageMs = (nowMs - updatedAtMs).coerceAtLeast(0L)
    return ageMs > thresholdMs || ageMs > staleServeWindowMs
}

private fun formatOverlayAge(nowMs: Long, updatedAtMs: Long): String {
    if (updatedAtMs <= 0L) return "n/a"

    val ageSec = ((nowMs - updatedAtMs).coerceAtLeast(0L) / 1000L)
    return when {
        ageSec < 60L -> "${ageSec}s"
        ageSec < 3600L -> "${ageSec / 60L}m"
        ageSec < 24L * 3600L -> {
            val hours = ageSec / 3600L
            val minutes = (ageSec % 3600L) / 60L
            if (minutes == 0L) "${hours}h" else "${hours}h${minutes}m"
        }

        else -> {
            val days = ageSec / (24L * 3600L)
            val hours = (ageSec % (24L * 3600L)) / 3600L
            if (hours == 0L) "${days}d" else "${days}d${hours}h"
        }
    }
}

private fun formatOverlayNsMs(durationNs: Long): String {
    if (durationNs <= 0L) return "-"
    val frameMs = durationNs.toDouble() / 1_000_000.0
    return String.format(Locale.US, "%.2f", frameMs)
}

private fun formatOverlayNsUs(durationNs: Long): String {
    if (durationNs <= 0L) return "-"
    return if (durationNs >= 1_000L) {
        String.format(Locale.US, "%.2f us", durationNs.toDouble() / 1_000.0)
    } else {
        "$durationNs ns"
    }
}

private fun formatOverlayPercent(value: Float): String {
    if (value <= 0f) return "0.0"
    return String.format(Locale.US, "%.1f", value)
}

private fun buildCategorySlices(
    snapshot: PerformanceProfiler.Snapshot
): List<DistributionSlice> {
    if (snapshot.totalSectionNs <= 0L || snapshot.categories.isEmpty()) {
        return emptyList()
    }

    val profilerCategory = snapshot.categories.firstOrNull { it.category == "profiler" }
    val nonProfilerCategories = snapshot.categories.filterNot { it.category == "profiler" }
    val primaryCategories = if (profilerCategory != null) {
        nonProfilerCategories.take(5) + profilerCategory
    } else {
        nonProfilerCategories.take(6)
    }

    val slices = primaryCategories
        .mapIndexed { index, category ->
            DistributionSlice(
                label = category.category,
                percent = category.sharePercent.coerceIn(0f, 100f),
                color = profilerColorForKey(category.category, index)
            )
        }
        .toMutableList()

    val remainingCategories = snapshot.categories
        .filterNot { candidate -> primaryCategories.any { it.category == candidate.category } }
        .sortedByDescending { it.sharePercent }

    if (remainingCategories.isNotEmpty()) {
        if (remainingCategories.size <= 3) {
            remainingCategories.forEach { category ->
                slices += DistributionSlice(
                    label = "other/${category.category}",
                    percent = category.sharePercent.coerceIn(0f, 100f),
                    color = profilerColorForKey("other/${category.category}", 0)
                )
            }
        } else {
            remainingCategories.take(2).forEach { category ->
                slices += DistributionSlice(
                    label = "other/${category.category}",
                    percent = category.sharePercent.coerceIn(0f, 100f),
                    color = profilerColorForKey("other/${category.category}", 0)
                )
            }
            val miscPercent = remainingCategories
                .drop(2)
                .sumOf { it.sharePercent.toDouble() }
                .toFloat()
                .coerceIn(0f, 100f)
            if (miscPercent >= 0.25f) {
                slices += DistributionSlice(
                    label = "other/misc",
                    percent = miscPercent,
                    color = profilerColorForKey("other/misc", 0)
                )
            }
        }
    }
    return slices
}

private fun buildBackgroundBreakdown(
    sections: List<PerformanceProfiler.SectionSnapshot>,
    totalSectionNs: Long
): BackgroundBreakdown? {
    val backgroundSections = sections
        .filter { section -> section.name.startsWith("Background/") }
        .sortedByDescending { it.totalNs }
    if (backgroundSections.isEmpty()) return null

    val totalBackgroundNs = backgroundSections.sumOf { it.totalNs }
    if (totalBackgroundNs <= 0L) return null

    val percentOfTotal = if (totalSectionNs > 0L) {
        (totalBackgroundNs.toDouble() / totalSectionNs.toDouble() * 100.0).toFloat()
    } else {
        0f
    }

    val drawSections = backgroundSections.filter { section ->
        section.category == "background-draw" || section.name.contains("/Draw")
    }
    val drawTotalNs = drawSections.sumOf { it.totalNs }
    val drawPercentOfBackground = if (totalBackgroundNs > 0L) {
        (drawTotalNs.toDouble() / totalBackgroundNs.toDouble() * 100.0).toFloat()
    } else {
        0f
    }
    val drawPercentOfTotal = if (totalSectionNs > 0L) {
        (drawTotalNs.toDouble() / totalSectionNs.toDouble() * 100.0).toFloat()
    } else {
        0f
    }

    val drawGroupEntries = if (drawTotalNs > 0L) {
        val drawGroupTotals = mutableMapOf<String, Long>()
        drawSections.forEach { section ->
            val group = drawGroupLabel(section.name)
            drawGroupTotals[group] = (drawGroupTotals[group] ?: 0L) + section.totalNs
        }
        drawGroupTotals.entries
            .sortedByDescending { it.value }
            .take(8)
            .mapIndexed { index, entry ->
                val percent = (entry.value.toDouble() / drawTotalNs.toDouble() * 100.0).toFloat()
                BackgroundDrawGroupEntry(
                    label = entry.key,
                    percentOfDraw = percent,
                    totalNs = entry.value,
                    color = profilerColorForKey("background-draw-group/${entry.key}", index)
                )
            }
    } else {
        emptyList()
    }

    val drawEntries = if (drawTotalNs > 0L) {
        drawSections
            .sortedByDescending { it.totalNs }
            .take(12)
            .mapIndexed { index, section ->
                val label = section.name.removePrefix("Background/")
                val percent = (section.totalNs.toDouble() / drawTotalNs.toDouble() * 100.0).toFloat()
                BackgroundDrawEntry(
                    label = label,
                    percentOfDraw = percent,
                    totalNs = section.totalNs,
                    color = profilerColorForKey("background-draw/$label", index)
                )
            }
    } else {
        emptyList()
    }

    return BackgroundBreakdown(
        totalNs = totalBackgroundNs,
        percentOfTotal = percentOfTotal,
        drawTotalNs = drawTotalNs,
        drawPercentOfBackground = drawPercentOfBackground,
        drawPercentOfTotal = drawPercentOfTotal,
        drawGroupEntries = drawGroupEntries,
        drawEntries = drawEntries
    )
}

private fun buildWidgetDrawBreakdown(
    sections: List<PerformanceProfiler.SectionSnapshot>,
    totalSectionNs: Long
): WidgetDrawBreakdown? {
    val widgetDrawSections = sections
        .filter { section -> section.category == "widget-draw" }
        .sortedByDescending { it.totalNs }
    if (widgetDrawSections.isEmpty()) return null

    val totalWidgetDrawNs = widgetDrawSections.sumOf { it.totalNs }
    if (totalWidgetDrawNs <= 0L) return null

    val percentOfTotal = if (totalSectionNs > 0L) {
        (totalWidgetDrawNs.toDouble() / totalSectionNs.toDouble() * 100.0).toFloat()
    } else {
        0f
    }

    fun buildGroupTotals(depth: Int): Map<String, Long> {
        val groupTotals = mutableMapOf<String, Long>()
        widgetDrawSections.forEach { section ->
            val component = widgetDrawGroupLabel(section.name, depth = depth)
            groupTotals[component] = (groupTotals[component] ?: 0L) + section.totalNs
        }
        return groupTotals
    }

    var groupTotals = buildGroupTotals(depth = 1)
    if (groupTotals.size == 1) {
        val deeperTotals = buildGroupTotals(depth = 2)
        if (deeperTotals.size > 1) {
            groupTotals = deeperTotals
        }
    }
    if (groupTotals.size == 1) {
        val evenDeeperTotals = buildGroupTotals(depth = 3)
        if (evenDeeperTotals.size > 1) {
            groupTotals = evenDeeperTotals
        }
    }

    val groupEntries = groupTotals.entries
        .sortedByDescending { it.value }
        .take(8)
        .mapIndexed { index, entry ->
            WidgetDrawGroupEntry(
                label = entry.key,
                percentOfWidgetDraw = (entry.value.toDouble() / totalWidgetDrawNs.toDouble() * 100.0).toFloat(),
                totalNs = entry.value,
                color = profilerColorForKey("widget-group/${entry.key}", index)
            )
        }

    val entries = widgetDrawSections
        .take(12)
        .mapIndexed { index, section ->
            WidgetDrawEntry(
                label = section.name,
                percentOfWidgetDraw = (section.totalNs.toDouble() / totalWidgetDrawNs.toDouble() * 100.0).toFloat(),
                totalNs = section.totalNs,
                color = profilerColorForKey("widget-draw/${section.name}", index)
            )
        }

    return WidgetDrawBreakdown(
        totalNs = totalWidgetDrawNs,
        percentOfTotal = percentOfTotal,
        groupEntries = groupEntries,
        entries = entries
    )
}

private fun widgetDrawGroupLabel(sectionName: String, depth: Int): String {
    val parts = sectionName
        .split("/")
        .filter { it.isNotBlank() }
    if (parts.isEmpty()) return "Other"
    val safeDepth = depth.coerceIn(1, parts.size)
    return parts.take(safeDepth).joinToString("/")
}

private fun drawGroupLabel(sectionName: String): String {
    val label = sectionName.removePrefix("Background/")
    return when {
        label.contains("/Draw/") -> label.substringBefore("/Draw/")
        label.endsWith("/Draw") -> label.substringBefore("/Draw")
        else -> label.substringBefore("/")
    }.ifBlank { "Other" }
}

private fun buildDialBreakdownEntries(
    sections: List<PerformanceProfiler.SectionSnapshot>
): List<DialBreakdownEntry> {
    val dialSections = sections
        .filter { section -> section.name.startsWith("WindCompass/Dial/") }
        .sortedByDescending { it.totalNs }
    if (dialSections.isEmpty()) return emptyList()

    val totalDialNs = dialSections.sumOf { it.totalNs }
    if (totalDialNs <= 0L) return emptyList()

    return dialSections.take(8).mapIndexed { index, section ->
        val label = section.name.substringAfter("WindCompass/Dial/")
        val percent = (section.totalNs.toDouble() / totalDialNs.toDouble() * 100.0).toFloat()
        DialBreakdownEntry(
            label = label,
            percentOfDial = percent,
            totalNs = section.totalNs,
            color = profilerColorForKey("dial/$label", index)
        )
    }
}

private fun buildNoiseBreakdownEntries(
    sections: List<PerformanceProfiler.SectionSnapshot>
): List<NoiseBreakdownEntry> {
    val noiseSections = sections
        .filter { section ->
            section.name.startsWith("Background/Noise/Draw/") ||
                section.name.startsWith("Background/Noise/Cache/")
        }
        .sortedByDescending { it.totalNs }
    if (noiseSections.isEmpty()) return emptyList()

    val totalNoiseNs = noiseSections.sumOf { it.totalNs }
    if (totalNoiseNs <= 0L) return emptyList()

    return noiseSections.take(8).mapIndexed { index, section ->
        val label = section.name.substringAfter("Background/Noise/")
        val percent = (section.totalNs.toDouble() / totalNoiseNs.toDouble() * 100.0).toFloat()
        NoiseBreakdownEntry(
            label = label,
            percentOfNoise = percent,
            totalNs = section.totalNs,
            color = profilerColorForKey("noise/$label", index)
        )
    }
}

private fun buildNoiseMetricSummary(
    sections: List<PerformanceProfiler.SectionSnapshot>
): NoiseMetricSummary? {
    fun average(name: String): Long? {
        return sections.firstOrNull { section -> section.name == name }?.averageNs
    }

    val averagePointCount = average("Background/Noise/Metric/FramePointCount")
    val averagePerPointNs = average("Background/Noise/Metric/PerPointNs")
    val averageTinyPointCount = average("Background/Noise/Metric/TinyPointCount")
    val averageMediumPointCount = average("Background/Noise/Metric/MediumPointCount")
    val averageLargePointCount = average("Background/Noise/Metric/LargePointCount")

    if (
        averagePointCount == null &&
        averagePerPointNs == null &&
        averageTinyPointCount == null &&
        averageMediumPointCount == null &&
        averageLargePointCount == null
    ) {
        return null
    }

    return NoiseMetricSummary(
        averagePointCount = averagePointCount,
        averagePerPointNs = averagePerPointNs,
        averageTinyPointCount = averageTinyPointCount,
        averageMediumPointCount = averageMediumPointCount,
        averageLargePointCount = averageLargePointCount
    )
}

private fun compactProfilerLabel(label: String): String {
    val parts = label.split("/")
    return when {
        parts.isEmpty() -> label
        parts.size <= 2 -> label
        else -> parts.takeLast(2).joinToString("/")
    }
}

private fun compactBackgroundLabel(label: String): String {
    val parts = label.split("/")
    return when {
        parts.isEmpty() -> label
        parts.size <= 3 -> label
        else -> parts.takeLast(3).joinToString("/")
    }
}

private fun compactWidgetLabel(label: String): String {
    val parts = label.split("/")
    return when {
        parts.isEmpty() -> label
        parts.size <= 3 -> label
        else -> parts.takeLast(3).joinToString("/")
    }
}

private fun profilerColorForKey(key: String, indexHint: Int): Color {
    val palette = profilerPalette
    if (palette.isEmpty()) return Color.White
    val seed = key.hashCode() and Int.MAX_VALUE
    return palette[seed % palette.size]
}

private val profilerPalette = listOf(
    Color(0xFF60A5FA),
    Color(0xFF34D399),
    Color(0xFFF59E0B),
    Color(0xFFF472B6),
    Color(0xFFA78BFA),
    Color(0xFFF87171),
    Color(0xFF22D3EE),
    Color(0xFF84CC16)
)
