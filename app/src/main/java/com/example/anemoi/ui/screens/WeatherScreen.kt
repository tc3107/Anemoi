package com.example.anemoi.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.AnchoredDraggableState
import androidx.compose.foundation.gestures.DraggableAnchors
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.anchoredDraggable
import androidx.compose.foundation.gestures.animateTo
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.core.content.ContextCompat
import com.example.anemoi.ui.components.MapBackground
import com.example.anemoi.ui.components.SearchBar
import com.example.anemoi.ui.components.WeatherDetailsSheet
import com.example.anemoi.ui.components.WeatherDisplay
import com.example.anemoi.viewmodel.WeatherViewModel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

enum class SheetValue { Collapsed, Expanded }

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun WeatherScreen(viewModel: WeatherViewModel) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val density = LocalDensity.current
    val coroutineScope = rememberCoroutineScope()
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

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val sheetMaxHeight = maxHeight
        val fullHeightPx = constraints.maxHeight.toFloat()
        val handleHeight = 72.dp
        val handleHeightPx = with(density) { handleHeight.toPx() }
        val collapsedAnchor = fullHeightPx - handleHeightPx
        val topPadding = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
        val expandedAnchor = with(density) { (topPadding + 88.dp).toPx() }

        val decayAnimationSpec = rememberSplineBasedDecay<Float>()
        val anchoredDraggableState = remember(collapsedAnchor, expandedAnchor, decayAnimationSpec) {
            AnchoredDraggableState(
                initialValue = SheetValue.Collapsed,
                anchors = DraggableAnchors {
                    SheetValue.Collapsed at collapsedAnchor
                    SheetValue.Expanded at expandedAnchor
                },
                positionalThreshold = { distance: Float -> distance * 0.5f },
                velocityThreshold = { with(density) { 100.dp.toPx() } },
                snapAnimationSpec = spring(
                    dampingRatio = Spring.DampingRatioNoBouncy,
                    stiffness = Spring.StiffnessLow
                ),
                decayAnimationSpec = decayAnimationSpec
            )
        }
        val latestSearchQuery by rememberUpdatedState(uiState.searchQuery)

        LaunchedEffect(anchoredDraggableState) {
            snapshotFlow { anchoredDraggableState.currentValue }
                .distinctUntilChanged()
                .drop(1)
                .collect {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                }
        }

        LaunchedEffect(anchoredDraggableState) {
            snapshotFlow { anchoredDraggableState.currentValue }
                .distinctUntilChanged()
                .collect { sheetValue ->
                    if (sheetValue == SheetValue.Expanded && latestSearchQuery.isNotEmpty()) {
                        viewModel.onSearchQueryChanged("")
                    }
                }
        }

        val nestedScrollConnection = remember(anchoredDraggableState) {
            object : NestedScrollConnection {
                override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                    val delta = available.y
                    return if (delta < 0 && source == NestedScrollSource.UserInput) {
                        val consumed = anchoredDraggableState.dispatchRawDelta(delta)
                        Offset(0f, consumed)
                    } else {
                        Offset.Zero
                    }
                }

                override fun onPostScroll(
                    consumed: Offset,
                    available: Offset,
                    source: NestedScrollSource
                ): Offset {
                    val delta = available.y
                    return if (source == NestedScrollSource.UserInput) {
                        val consumedByDraggable = anchoredDraggableState.dispatchRawDelta(delta)
                        Offset(0f, consumedByDraggable)
                    } else {
                        Offset.Zero
                    }
                }

                override suspend fun onPreFling(available: Velocity): Velocity {
                    val toFling = available.y
                    return if (toFling < 0 && anchoredDraggableState.offset > anchoredDraggableState.anchors.minAnchor()) {
                        anchoredDraggableState.settle(toFling)
                        available
                    } else {
                        Velocity.Zero
                    }
                }

                override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                    anchoredDraggableState.settle(available.y)
                    return available
                }
            }
        }

        BackHandler(enabled = uiState.isOrganizerMode || uiState.isSettingsOpen || anchoredDraggableState.currentValue == SheetValue.Expanded) {
            if (uiState.isOrganizerMode) {
                viewModel.toggleOrganizerMode(false)
            } else if (uiState.isSettingsOpen) {
                viewModel.toggleSettings(false)
            } else {
                coroutineScope.launch { anchoredDraggableState.animateTo(SheetValue.Collapsed) }
            }
        }

        val currentOffset = if (anchoredDraggableState.offset.isNaN()) collapsedAnchor else anchoredDraggableState.offset
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
        val enableGlobalSheetDragGesture =
            anchoredDraggableState.currentValue == SheetValue.Collapsed &&
                anchoredDraggableState.targetValue == SheetValue.Collapsed

        Box(modifier = Modifier
            .fillMaxSize()
            .pointerInput(enableGlobalSheetDragGesture, uiState.isOrganizerMode, uiState.isSettingsOpen) {
                if (uiState.isOrganizerMode || uiState.isSettingsOpen || !enableGlobalSheetDragGesture) {
                    return@pointerInput
                }

                var upwardDragAccum = 0f
                var accumDx = 0f
                var accumDy = 0f
                var isSheetGesture = false
                var smoothedDragY = 0f
                var lastFilteredDragY = 0f
                val rampDistance = with(density) { 220.dp.toPx() }
                val commitExpandThreshold = with(density) { 24.dp.toPx() }
                val intentDistance = with(density) { 18.dp.toPx() }
                val verticalIntentRatio = 0.75f
                val dragSmoothingAlpha = 0.28f
                val dragReverseAlpha = 0.52f
                val dragDeadzonePx = with(density) { 0.7.dp.toPx() }
                val maxDispatchStepPx = with(density) { 36.dp.toPx() }
                val velocityTracker = VelocityTracker()

                detectDragGestures(
                    onDragStart = {
                        upwardDragAccum = 0f
                        accumDx = 0f
                        accumDy = 0f
                        isSheetGesture = false
                        smoothedDragY = 0f
                        lastFilteredDragY = 0f
                        velocityTracker.resetTracking()
                    },
                    onDrag = { change, dragAmount ->
                        accumDx += abs(dragAmount.x)
                        accumDy += abs(dragAmount.y)
                        velocityTracker.addPosition(change.uptimeMillis, change.position)

                        if (!isSheetGesture) {
                            val movedEnough = (accumDx + accumDy) >= intentDistance
                            if (movedEnough) {
                                // More forgiving than strict vertical: allows moderate horizontal drift.
                                isSheetGesture = accumDy >= accumDx * verticalIntentRatio
                            }
                        }

                        if (!isSheetGesture) return@detectDragGestures

                        val amplifiedDragY = if (dragAmount.y < 0f) {
                            // Progressive curve: starts gentle, ramps up as user keeps pulling upward.
                            upwardDragAccum += -dragAmount.y
                            val progress = (upwardDragAccum / rampDistance).coerceIn(0f, 1f)
                            val gain = 1.05f + (1.95f - 1.05f) * progress
                            dragAmount.y * gain
                        } else {
                            upwardDragAccum = (upwardDragAccum - dragAmount.y).coerceAtLeast(0f)
                            dragAmount.y * 1.15f
                        }

                        val filteredDragY = if (abs(amplifiedDragY) < dragDeadzonePx) 0f else amplifiedDragY
                        val alpha = if (
                            filteredDragY != 0f &&
                            lastFilteredDragY != 0f &&
                            filteredDragY * lastFilteredDragY < 0f
                        ) dragReverseAlpha else dragSmoothingAlpha
                        smoothedDragY += (filteredDragY - smoothedDragY) * alpha
                        lastFilteredDragY = filteredDragY

                        val dispatchDragY = smoothedDragY.coerceIn(-maxDispatchStepPx, maxDispatchStepPx)
                        if (abs(dispatchDragY) < dragDeadzonePx) return@detectDragGestures

                        val consumed = anchoredDraggableState.dispatchRawDelta(dispatchDragY)
                        if (consumed != 0f) change.consume()
                    },
                    onDragEnd = {
                        if (!isSheetGesture) return@detectDragGestures

                        val releaseVelocityY = velocityTracker.calculateVelocity().y
                        val shouldCommitExpanded =
                            (upwardDragAccum >= commitExpandThreshold || releaseVelocityY <= -1200f) &&
                                anchoredDraggableState.currentValue != SheetValue.Expanded
                        upwardDragAccum = 0f
                        accumDx = 0f
                        accumDy = 0f
                        isSheetGesture = false
                        smoothedDragY = 0f
                        lastFilteredDragY = 0f

                        coroutineScope.launch {
                            if (shouldCommitExpanded) {
                                anchoredDraggableState.animateTo(SheetValue.Expanded)
                            } else {
                                // Feed release velocity so the sheet carries momentum after finger lift.
                                anchoredDraggableState.settle(releaseVelocityY * 1.25f)
                            }
                        }
                    },
                    onDragCancel = {
                        val hadSheetGesture = isSheetGesture
                        upwardDragAccum = 0f
                        accumDx = 0f
                        accumDy = 0f
                        isSheetGesture = false
                        smoothedDragY = 0f
                        lastFilteredDragY = 0f
                        if (hadSheetGesture) {
                            coroutineScope.launch {
                                anchoredDraggableState.settle(0f)
                            }
                        }
                    }
                )
            }
            .combinedClickable(
                interactionSource = screenTapInteractionSource,
                indication = null,
                onClick = {},
                onLongClick = { 
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    viewModel.toggleOrganizerMode(true) 
                }
            )
        ) {
            uiState.selectedLocation?.let { location ->
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
                    interactionEnabled = !uiState.isOrganizerMode && !uiState.isSettingsOpen
                )
            }

            AnimatedVisibility(
                visible = !uiState.isOrganizerMode && !uiState.isSettingsOpen,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.fillMaxSize()
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .drawWithContent {
                                clipRect(bottom = currentOffset) {
                                    this@drawWithContent.drawContent()
                                }
                            }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 80.dp)
                                .align(Alignment.BottomCenter),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val isLocActive = pagerState.currentPage == 0
                            Icon(
                                imageVector = Icons.Default.MyLocation,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp),
                                tint = if (uiState.locationFound && isLocActive) Color.Green 
                                       else if (isLocActive) Color.White 
                                       else Color.White.copy(alpha = 0.5f)
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
                    }

                    Column(modifier = Modifier.fillMaxWidth().statusBarsPadding().zIndex(2f)) {
                        if (uiState.isLoading) LinearProgressIndicator(modifier = Modifier.fillMaxWidth().height(2.dp), color = Color.White, trackColor = Color.Transparent)
                        if (uiState.isLocating) LinearProgressIndicator(modifier = Modifier.fillMaxWidth().height(2.dp), color = Color(0xFF4285F4), trackColor = Color.Transparent)
                    }

                    HorizontalPager(
                        state = pagerState,
                        modifier = Modifier.fillMaxSize(),
                        userScrollEnabled = anchoredDraggableState.currentValue == SheetValue.Collapsed
                    ) { page ->
                        val pageLocation = if (page == 0) {
                            if (uiState.isFollowMode) uiState.selectedLocation else null
                        } else if (page <= favorites.size) {
                            favorites.getOrNull(page - 1)
                        } else {
                            searchedLocation
                        }

                        Box(modifier = Modifier.fillMaxSize().drawWithContent { clipRect(bottom = currentOffset) { this@drawWithContent.drawContent() } }) {
                            Column(modifier = Modifier.fillMaxSize().statusBarsPadding().padding(horizontal = 16.dp)) {
                                Spacer(modifier = Modifier.height(152.dp))
                                
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
                            }
                        }
                    }

                    Column(modifier = Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 16.dp)) {
                        Spacer(modifier = Modifier.height(16.dp))
                        SearchBar(
                            query = uiState.searchQuery,
                            onQueryChange = { query ->
                                if (query.isNotEmpty() && anchoredDraggableState.targetValue == SheetValue.Expanded) {
                                    coroutineScope.launch { anchoredDraggableState.animateTo(SheetValue.Collapsed) }
                                }
                                viewModel.onSearchQueryChanged(query)
                            },
                            suggestions = uiState.suggestions,
                            favorites = favorites,
                            onLocationSelected = viewModel::onLocationSelected,
                            onSettingsClick = { viewModel.toggleSettings(true) },
                            onMenuClick = { viewModel.toggleOrganizerMode(true) },
                            onToggleFavorite = viewModel::toggleFavorite,
                            selectedLocation = uiState.selectedLocation,
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

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(sheetMaxHeight)
                            .offset { IntOffset(0, currentOffset.roundToInt()) }
                            .anchoredDraggable(anchoredDraggableState, Orientation.Vertical)
                            .nestedScroll(nestedScrollConnection)
                            .clip(RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp))
                            .zIndex(1f)
                    ) {
                        Box(modifier = Modifier.fillMaxSize().blur(currentBlurStrength.dp).graphicsLayer {
                            val distortion = if (uiState.customValuesEnabled) uiState.sheetDistortion else 0.3f
                            scaleX = 1f + distortion * 0.4f; scaleY = 1f + distortion * 0.4f; translationY = distortion * 50f
                        }.background(Color.White.copy(alpha = currentTintAlpha)))

                        WeatherDetailsSheet(
                            uiState = uiState,
                            handleHeight = handleHeight,
                            onHandleClick = {
                                coroutineScope.launch { 
                                    anchoredDraggableState.animateTo(
                                        if (anchoredDraggableState.targetValue == SheetValue.Collapsed) SheetValue.Expanded 
                                        else SheetValue.Collapsed
                                    ) 
                                }
                            },
                            isExpanded = anchoredDraggableState.targetValue == SheetValue.Expanded
                        )
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
                    onClose = { viewModel.toggleOrganizerMode(false) },
                    blurStrength = currentBlurStrength,
                    tintAlpha = currentTintAlpha
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
        }
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
