package com.example.anemoi.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.core.content.ContextCompat
import com.example.anemoi.data.LocationItem
import com.example.anemoi.ui.components.DynamicWeatherBackground
import com.example.anemoi.ui.components.MapBackground
import com.example.anemoi.ui.components.SearchBar
import com.example.anemoi.ui.components.WeatherDetailsSheet
import com.example.anemoi.ui.components.WeatherDisplay
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
    var overlayFps by remember { mutableStateOf(0) }
    var overlayAvgFrameMs by remember { mutableStateOf(0f) }
    var overlayUsedMemMb by remember { mutableStateOf(0) }
    var overlayFreeMemMb by remember { mutableStateOf(0) }
    var overlayMaxMemMb by remember { mutableStateOf(0) }

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
        if (!uiState.isPerformanceOverlayEnabled) {
            overlayFps = 0
            overlayAvgFrameMs = 0f
            return@LaunchedEffect
        }

        val runtime = Runtime.getRuntime()
        val initialFreeBytes = runtime.freeMemory()
        val initialUsedBytes = runtime.totalMemory() - initialFreeBytes
        overlayUsedMemMb = (initialUsedBytes / (1024L * 1024L)).toInt()
        overlayFreeMemMb = (initialFreeBytes / (1024L * 1024L)).toInt()
        overlayMaxMemMb = (runtime.maxMemory() / (1024L * 1024L)).toInt()
        var windowStartNs = 0L
        var previousFrameNs = 0L
        var frameCount = 0
        var frameDurationsNs = 0L

        while (true) {
            withFrameNanos { nowNs ->
                if (windowStartNs == 0L) {
                    windowStartNs = nowNs
                }
                if (previousFrameNs != 0L) {
                    frameDurationsNs += nowNs - previousFrameNs
                }
                previousFrameNs = nowNs
                frameCount++

                val elapsedNs = nowNs - windowStartNs
                if (elapsedNs >= 1_000_000_000L) {
                    overlayFps = ((frameCount * 1_000_000_000L) / elapsedNs).toInt()
                    overlayAvgFrameMs = if (frameCount > 1) {
                        (frameDurationsNs.toDouble() / (frameCount - 1).toDouble() / 1_000_000.0).toFloat()
                    } else {
                        0f
                    }

                    val freeBytes = runtime.freeMemory()
                    val usedBytes = runtime.totalMemory() - freeBytes
                    overlayUsedMemMb = (usedBytes / (1024L * 1024L)).toInt()
                    overlayFreeMemMb = (freeBytes / (1024L * 1024L)).toInt()
                    overlayMaxMemMb = (runtime.maxMemory() / (1024L * 1024L)).toInt()

                    frameCount = 0
                    frameDurationsNs = 0L
                    windowStartNs = nowNs
                }
            }
        }
    }

    val currentTintAlpha = if (uiState.customValuesEnabled) uiState.searchBarTintAlpha else 0.15f
    val currentBlurStrength = if (uiState.customValuesEnabled) uiState.sheetBlurStrength else 16f
    val textAlpha = if (uiState.customValuesEnabled) uiState.textAlpha else 0.8f
    val warningStaleServeWindowMs = 12 * 60 * 60 * 1000L
    val warningCurrentThresholdMs = 5 * 60 * 1000L
    val warningHourlyThresholdMs = 20 * 60 * 1000L
    val warningDailyThresholdMs = 2 * 60 * 60 * 1000L
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

    fun locationForPage(page: Int): LocationItem? {
        return when {
            page == 0 -> if (uiState.isFollowMode) uiState.selectedLocation else null
            page <= favorites.size -> favorites.getOrNull(page - 1)
            else -> searchedLocation
        }
    }

    fun locationKey(location: LocationItem?): String? = location?.let { "${it.lat},${it.lon}" }

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
    val overrideBackgroundPreset = backgroundOverridePresetAt(uiState.backgroundOverridePresetIndex)
    val overrideBackgroundTimeIso = backgroundOverrideTimeIso(overrideBackgroundPreset.hourOfDay)
    val overlayNow = System.currentTimeMillis()
    val overlayKey = selectedLocationKey
    val overlayCurrentUpdatedAt = overlayKey?.let { uiState.currentUpdateTimeMap[it] } ?: 0L
    val overlayHourlyUpdatedAt = overlayKey?.let { uiState.hourlyUpdateTimeMap[it] } ?: 0L
    val overlayDailyUpdatedAt = overlayKey?.let { uiState.dailyUpdateTimeMap[it] } ?: 0L
    val overlaySignatureMatch = overlayKey != null &&
        uiState.cacheSignatureMap[overlayKey] == uiState.activeRequestSignature
    val overlayWeather = overlayKey?.let { uiState.weatherMap[it] }
    val overlayLocationLine = uiState.selectedLocation?.let { location ->
        "${location.name} (${String.format(Locale.US, "%.4f", location.lat)}, ${String.format(Locale.US, "%.4f", location.lon)})"
    } ?: "none"
    val overlayLines = buildList {
        add("PERFORMANCE")
        add("fps=$overlayFps frame=${formatFrameMs(overlayAvgFrameMs)}ms")
        add("mem used=$overlayUsedMemMb MB free=$overlayFreeMemMb MB max=$overlayMaxMemMb MB")
        add("page=${pagerState.currentPage + 1}/$totalPages settled=${pagerState.settledPage + 1}")
        add("loading=${uiState.isLoading} locating=${uiState.isLocating} follow=${uiState.isFollowMode}")
        add("map=${uiState.mapEnabled} settings=${uiState.isSettingsOpen} organizer=${uiState.isOrganizerMode}")
        add(
            "bgOverride=${uiState.isBackgroundOverrideEnabled} preset=${
                if (uiState.isBackgroundOverrideEnabled) overrideBackgroundPreset.label else "-"
            }"
        )
        add("favorites=${favorites.size} suggestions=${uiState.suggestions.size} cache=${uiState.weatherMap.size}")
        add("location=$overlayLocationLine")
        add(
            "signature=${
                when {
                    overlayKey == null -> "n/a"
                    overlaySignatureMatch -> "match"
                    else -> "mismatch"
                }
            }"
        )
        add(
            "age current=${formatOverlayAge(overlayNow, overlayCurrentUpdatedAt)} " +
                "hourly=${formatOverlayAge(overlayNow, overlayHourlyUpdatedAt)} " +
                "daily=${formatOverlayAge(overlayNow, overlayDailyUpdatedAt)}"
        )
        if (overlayWeather != null) {
            add(
                "datasets current=${overlayWeather.currentWeather != null} " +
                    "hourly=${overlayWeather.hourly != null} daily=${overlayWeather.daily != null}"
            )
        }
        if (uiState.errors.isNotEmpty()) {
            add("errors=${uiState.errors.size} first=${uiState.errors.first()}")
        }
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
                modifier = Modifier.fillMaxSize()
            )
        } else if (uiState.mapEnabled) {
            settledPageLocation?.let { location ->
                MapBackground(
                    lat = location.lat,
                    lon = location.lon,
                    zoom = if (uiState.customValuesEnabled) uiState.mapZoom else 16f,
                    blurStrength = if (uiState.customValuesEnabled) uiState.blurStrength else 6f,
                    tintAlpha = if (uiState.customValuesEnabled) uiState.tintAlpha else 0.1f,
                    obfuscationMode = uiState.obfuscationMode,
                    gridKm = uiState.gridKm.toDouble(),
                    lastResponseCoords = uiState.lastResponseCoords,
                    responseAnimTrigger = uiState.responseAnimTrigger,
                    shouldAnimate = !uiState.isLoading,
                    freezeCameraUpdates = isPagerSwitchInProgress,
                    interactionEnabled = !uiState.isOrganizerMode && !uiState.isSettingsOpen
                )
            }
        } else {
            DynamicWeatherBackground(
                weatherCode = warningWeather?.currentWeather?.weatherCode,
                weatherTimeIso = warningWeather?.currentWeather?.time,
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
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize(),
                    userScrollEnabled = true
                ) { page ->
                    val pageLocation = if (page == 0) {
                        if (uiState.isFollowMode) uiState.selectedLocation else null
                    } else if (page <= favorites.size) {
                        favorites.getOrNull(page - 1)
                    } else {
                        searchedLocation
                    }

                    WeatherDetailsSheet(
                        uiState = uiState,
                        handleHeight = 0.dp,
                        onHandleClick = {},
                        isExpanded = true,
                        showHandle = false,
                        resetScrollKey = pagerState.settledPage,
                        headerContent = {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .statusBarsPadding()
                            ) {
                                Spacer(modifier = Modifier.height(12.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.Center,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    val isLocActive = pagerState.currentPage == 0
                                    Icon(
                                        imageVector = Icons.Default.MyLocation,
                                        contentDescription = null,
                                        modifier = Modifier.size(14.dp),
                                        tint = if (uiState.locationFound && isLocActive) {
                                            Color.Green
                                        } else if (isLocActive) {
                                            Color.White
                                        } else {
                                            Color.White.copy(alpha = 0.5f)
                                        }
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))

                                    repeat(favorites.size) { index ->
                                        val location = favorites[index]
                                        val key = "${location.lat},${location.lon}"
                                        val status = uiState.pageStatuses[key]
                                        val isActive = pagerState.currentPage == index + 1
                                        val dotColor = when (status) {
                                            true -> if (isActive) Color.Green else Color.White.copy(alpha = 0.5f)
                                            false -> if (isActive) Color.Red else Color.White.copy(alpha = 0.5f)
                                            null -> if (isActive) Color.White else Color.White.copy(alpha = 0.5f)
                                        }
                                        Box(
                                            modifier = Modifier
                                                .size(8.dp)
                                                .clip(CircleShape)
                                                .background(dotColor)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                    }

                                    if (searchedLocation != null) {
                                        val isActive = pagerState.currentPage == totalPages - 1
                                        val key = "${searchedLocation.lat},${searchedLocation.lon}"
                                        val status = uiState.pageStatuses[key]
                                        Icon(
                                            imageVector = Icons.Default.Search,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp),
                                            tint = when (status) {
                                                true -> if (isActive) Color.Green else Color.White.copy(alpha = 0.5f)
                                                false -> if (isActive) Color.Red else Color.White.copy(alpha = 0.5f)
                                                null -> if (isActive) Color.White else Color.White.copy(alpha = 0.5f)
                                            }
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(8.dp))
                                SearchBar(
                                    query = uiState.searchQuery,
                                    onQueryChange = viewModel::onSearchQueryChanged,
                                    suggestions = uiState.suggestions,
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
                            }
                            Spacer(modifier = Modifier.height(20.dp))

                            if (pageLocation != null) {
                                val key = "${pageLocation.lat},${pageLocation.lon}"
                                val now = System.currentTimeMillis()
                                val staleServeWindowMs = 12 * 60 * 60 * 1000L
                                val grayThresholdMs = 60 * 60 * 1000L

                                val isSignatureMatch = uiState.cacheSignatureMap[key] == uiState.activeRequestSignature
                                val rawWeather = uiState.weatherMap[key]
                                val currentUpdatedAt = uiState.currentUpdateTimeMap[key] ?: 0L
                                val hourlyUpdatedAt = uiState.hourlyUpdateTimeMap[key] ?: 0L
                                val dailyUpdatedAt = uiState.dailyUpdateTimeMap[key] ?: 0L

                                val currentUsable = rawWeather?.currentWeather != null &&
                                    currentUpdatedAt > 0L &&
                                    now - currentUpdatedAt <= staleServeWindowMs
                                val hourlyUsable = rawWeather?.hourly != null &&
                                    hourlyUpdatedAt > 0L &&
                                    now - hourlyUpdatedAt <= staleServeWindowMs
                                val dailyUsable = rawWeather?.daily != null &&
                                    dailyUpdatedAt > 0L &&
                                    now - dailyUpdatedAt <= staleServeWindowMs

                                val weather = rawWeather?.copy(
                                    currentWeather = if (currentUsable) rawWeather.currentWeather else null,
                                    hourly = if (hourlyUsable) rawWeather.hourly else null,
                                    daily = if (dailyUsable) rawWeather.daily else null
                                )

                                val useStaleColor = !isSignatureMatch || listOfNotNull(
                                    if (currentUsable) now - currentUpdatedAt else null,
                                    if (hourlyUsable) now - hourlyUpdatedAt else null,
                                    if (dailyUsable) now - dailyUpdatedAt else null
                                ).any { it > grayThresholdMs }

                                WeatherDisplay(
                                    weather = weather,
                                    tempUnit = uiState.tempUnit,
                                    showDashesOverride = (page == 0 && !uiState.locationFound) || page != pagerState.currentPage,
                                    textAlpha = textAlpha,
                                    useStaleColor = useStaleColor
                                )
                            } else {
                                WeatherDisplay(
                                    weather = null,
                                    tempUnit = uiState.tempUnit,
                                    showDashesOverride = true,
                                    textAlpha = textAlpha
                                )
                            }
                            Spacer(modifier = Modifier.height(72.dp))
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .zIndex(3f)
                ) {
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

        AnimatedVisibility(
            visible = uiState.isOrganizerMode,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.fillMaxSize()
        ) {
            OrganizerOverlay(
                favorites = favorites,
                onReorder = viewModel::reorderFavorites,
                onToggleFavorite = viewModel::toggleFavorite,
                onRenameLocation = viewModel::renameLocationDisplayName,
                onSelect = { location ->
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
            PerformanceOverlay(
                lines = overlayLines,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .statusBarsPadding()
                    .padding(start = 8.dp, top = 8.dp)
                    .zIndex(20f)
            )
        }
    }
}

@Composable
private fun PerformanceOverlay(
    lines: List<String>,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .widthIn(max = 340.dp)
            .padding(horizontal = 10.dp, vertical = 8.dp)
    ) {
        Text(
            text = lines.joinToString(separator = "\n"),
            color = Color.White.copy(alpha = 0.78f),
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.SemiBold,
            fontSize = 11.sp,
            lineHeight = 14.sp,
            style = TextStyle(
                shadow = Shadow(
                    color = Color.Black,
                    offset = Offset(0f, 0f),
                    blurRadius = 6f
                )
            )
        )
    }
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

private fun formatFrameMs(frameMs: Float): String {
    if (frameMs <= 0f) return "-"
    return String.format(Locale.US, "%.1f", frameMs)
}
