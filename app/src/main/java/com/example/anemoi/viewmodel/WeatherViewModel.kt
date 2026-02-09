package com.example.anemoi.viewmodel

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.Location
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.anemoi.data.CurrentWeather
import com.example.anemoi.data.DailyData
import com.example.anemoi.data.GeocodingResponse
import com.example.anemoi.data.HourlyData
import com.example.anemoi.data.LocationItem
import com.example.anemoi.data.NominatimService
import com.example.anemoi.data.OpenMeteoService
import com.example.anemoi.data.PressureUnit
import com.example.anemoi.data.TempUnit
import com.example.anemoi.data.WeatherResponse
import com.example.anemoi.util.ObfuscationMode
import com.example.anemoi.util.dataStore
import com.example.anemoi.util.obfuscateLocation
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import org.osmdroid.tileprovider.cachemanager.CacheManager
import org.osmdroid.util.BoundingBox
import org.osmdroid.views.MapView
import retrofit2.HttpException
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.io.File
import java.io.IOException
import java.net.SocketTimeoutException
import java.util.ArrayDeque
import java.util.Calendar
import java.util.Locale
import kotlin.math.max

data class WeatherUiState(
    val searchQuery: String = "",
    val suggestions: List<LocationItem> = emptyList(),
    val favorites: List<LocationItem> = emptyList(),
    val searchedLocation: LocationItem? = null,
    val selectedLocation: LocationItem? = null,
    val lastLiveLocation: LocationItem? = null,
    val weatherMap: Map<String, WeatherResponse> = emptyMap(),
    val updateTimeMap: Map<String, Long> = emptyMap(),
    val currentUpdateTimeMap: Map<String, Long> = emptyMap(),
    val hourlyUpdateTimeMap: Map<String, Long> = emptyMap(),
    val dailyUpdateTimeMap: Map<String, Long> = emptyMap(),
    val cacheSignatureMap: Map<String, String> = emptyMap(),
    val activeRequestSignature: String = "",
    val isLoading: Boolean = false,
    val isLocating: Boolean = false,
    val locationFound: Boolean = false,
    val pageStatuses: Map<String, Boolean> = emptyMap(),
    val isFollowMode: Boolean = false,
    val isSettingsOpen: Boolean = false,
    val isOrganizerMode: Boolean = false,
    val errors: List<String> = emptyList(),
    val tempUnit: TempUnit = TempUnit.CELSIUS,
    val pressureUnit: PressureUnit = PressureUnit.HPA,
    val customValuesEnabled: Boolean = false,
    val mapZoom: Float = 16f,
    val blurStrength: Float = 6f,
    val tintAlpha: Float = 0.1f,
    val textAlpha: Float = 0.8f,
    val sheetBlurStrength: Float = 16f,
    val sheetDistortion: Float = 0.2f,
    val searchBarTintAlpha: Float = 0.15f,
    val obfuscationMode: ObfuscationMode = ObfuscationMode.PRECISE,
    val gridKm: Float = 5.0f,
    val lastResponseCoords: Pair<Double, Double>? = null,
    val responseAnimTrigger: Long = 0L,
    val experimentalEnabled: Boolean = false
)

private enum class RefreshTrigger {
    USER_INTERACTION,
    BACKGROUND,
    STARTUP
}

private data class SearchCacheEntry(
    val normalizedQuery: String,
    val cachedAt: Long,
    val suggestions: List<CachedSuggestion>
)

private data class PlaceCacheEntry(
    val displayName: String,
    val lat: Double,
    val lon: Double,
    val cachedAt: Long
)

private data class BackoffState(
    var failureCount: Int = 0,
    var retryAfterMs: Long = 0L
)

@Serializable
private data class CachedSuggestion(
    val placeId: Long,
    val displayName: String,
    val lat: Double,
    val lon: Double
)

@Serializable
private data class PersistedWeatherEntry(
    val key: String,
    val weather: WeatherResponse,
    val currentUpdatedAt: Long = 0L,
    val hourlyUpdatedAt: Long = 0L,
    val dailyUpdatedAt: Long = 0L,
    val signature: String = ""
)

@Serializable
private data class PersistedSearchEntry(
    val query: String,
    val normalizedQuery: String,
    val cachedAt: Long,
    val suggestions: List<CachedSuggestion>
)

@Serializable
private data class PersistedPlaceEntry(
    val placeId: Long,
    val displayName: String,
    val lat: Double,
    val lon: Double,
    val cachedAt: Long
)

@Serializable
private data class PersistedWeatherState(
    val weatherEntries: List<PersistedWeatherEntry> = emptyList(),
    val searchEntries: List<PersistedSearchEntry> = emptyList(),
    val placeEntries: List<PersistedPlaceEntry> = emptyList()
)

class WeatherViewModel(private val applicationContext: Context) : ViewModel() {
    private val tag = "WeatherViewModel"
    private val json = Json { ignoreUnknownKeys = true }
    private val okHttpClient = OkHttpClient.Builder()
        .addInterceptor(HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.NONE })
        .build()

    private val nominatimService = Retrofit.Builder()
        .baseUrl("https://nominatim.openstreetmap.org/")
        .client(okHttpClient)
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()
        .create(NominatimService::class.java)

    private val openMeteoService = Retrofit.Builder()
        .baseUrl("https://api.open-meteo.com/")
        .client(okHttpClient)
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()
        .create(OpenMeteoService::class.java)

    private val fusedLocationClient: FusedLocationProviderClient =
        LocationServices.getFusedLocationProviderClient(applicationContext)

    private val _uiState = MutableStateFlow(WeatherUiState())
    val uiState: StateFlow<WeatherUiState> = _uiState.asStateFlow()

    private val _debugLogs = MutableStateFlow<List<String>>(emptyList())
    val debugLogs: StateFlow<List<String>> = _debugLogs.asStateFlow()

    private var searchJob: Job? = null
    private var locationJob: Job? = null
    private var weatherJob: Job? = null
    private var autoUpdateJob: Job? = null
    private var stalenessCheckJob: Job? = null
    private var followModeJob: Job? = null
    private var startupPrefetchJob: Job? = null
    private var persistCachesJob: Job? = null
    private val inFlightWeatherRequests = mutableMapOf<String, Deferred<Boolean>>()

    private var lastLocation: LocationItem? = null
    private var lastMeaningfulSearchQuery = ""

    private val searchQueryCache = mutableMapOf<String, SearchCacheEntry>()
    private val placeIdCache = mutableMapOf<Long, PlaceCacheEntry>()

    private val locationRequestHistory = mutableMapOf<String, ArrayDeque<Long>>()
    private val globalRequestHistory = ArrayDeque<Long>()
    private val locationBackoff = mutableMapOf<String, BackoffState>()

    private val lastLocationKey = stringPreferencesKey("last_location")
    private val searchedLocationKey = stringPreferencesKey("searched_location")
    private val liveLocationKey = stringPreferencesKey("live_location")
    private val followModeKey = booleanPreferencesKey("follow_mode")
    private val tempUnitKey = stringPreferencesKey("temp_unit")
    private val pressureUnitKey = stringPreferencesKey("pressure_unit")
    private val customValuesKey = booleanPreferencesKey("custom_values_enabled")
    private val mapZoomKey = floatPreferencesKey("map_zoom")
    private val blurStrengthKey = floatPreferencesKey("blur_strength")
    private val tintAlphaKey = floatPreferencesKey("tint_alpha")
    private val textAlphaKey = floatPreferencesKey("text_alpha")
    private val sheetBlurKey = floatPreferencesKey("sheet_blur")
    private val sheetDistortionKey = floatPreferencesKey("sheet_distortion")
    private val searchBarTintKey = floatPreferencesKey("search_bar_tint")
    private val favoritesKey = stringPreferencesKey("favorites_json")
    private val obfuscationModeKey = stringPreferencesKey("obfuscation_mode")
    private val gridKmKey = floatPreferencesKey("grid_km")
    private val experimentalKey = booleanPreferencesKey("experimental_enabled")
    private val persistedCacheKey = stringPreferencesKey("persisted_cache_v2")

    private val whitespaceRegex = Regex("\\s+")

    private val currentFreshnessMs = 5 * 60 * 1000L
    private val hourlyFreshnessMs = 20 * 60 * 1000L
    private val dailyFreshnessMs = 2 * 60 * 60 * 1000L
    private val staleServeWindowMs = 12 * 60 * 60 * 1000L
    private val staleCheckIntervalMs = 60 * 1000L
    private val backgroundRefreshIntervalMs = 60 * 60 * 1000L
    private val perLocationGateBypassDistanceKm = 15.0

    private val locationMinRequestIntervalMs = 60 * 1000L
    private val globalRequestWindowMs = 60 * 1000L
    private val globalRequestLimitPerWindow = 30

    private val queryCacheTtlMs = 24 * 60 * 60 * 1000L
    private val placeCacheTtlMs = 30L * 24 * 60 * 60 * 1000L
    private val searchDebounceMs = 350L

    private val backoffStepsMs = longArrayOf(5_000L, 15_000L, 60_000L, 5 * 60_000L)

    private val hourlyFields = "temperature_2m,weathercode,apparent_temperature,surface_pressure,precipitation_probability,precipitation,uv_index"
    private val dailyFields = "temperature_2m_max,temperature_2m_min,weather_code,precipitation_probability_max,sunrise,sunset,daylight_duration,uv_index_max"
    private val staleDataError = "Weather cache is missing or older than 12 hours"

    private val defaultLocation = LocationItem(
        name = "New York",
        lat = 40.7128,
        lon = -74.0060
    )

    init {
        _uiState.update { state ->
            state.copy(activeRequestSignature = buildRequestSignature(state))
        }
        loadSettings()
        startAutoUpdate()
        startStalenessCheck()
    }

    private fun buildRequestSignature(state: WeatherUiState): String {
        val localeTag = Locale.getDefault().toLanguageTag()
        val gridString = String.format(Locale.US, "%.3f", state.gridKm)
        return listOf(
            "tz=auto",
            "locale=$localeTag",
            "hourly=$hourlyFields",
            "daily=$dailyFields",
            "obf=${state.obfuscationMode}",
            "grid=$gridString"
        ).joinToString("|")
    }

    private fun activeRequestSignature(state: WeatherUiState = _uiState.value): String {
        return buildRequestSignature(state)
    }

    private fun refreshActiveRequestSignature() {
        _uiState.update { state ->
            val signature = buildRequestSignature(state)
            if (signature == state.activeRequestSignature) {
                state
            } else {
                state.copy(activeRequestSignature = signature)
            }
        }
    }

    private fun loadSettings() {
        viewModelScope.launch {
            val prefs = applicationContext.dataStore.data.firstOrNull()
            if (prefs == null) {
                addLog("Preferences unavailable, defaulting to New York")
                lastLocation = defaultLocation
                _uiState.update { it.copy(selectedLocation = defaultLocation) }
                saveLastLocation(defaultLocation)
                prefetchWeatherOnStartup(listOf(defaultLocation))
                return@launch
            }

            val favs = prefs[favoritesKey]?.let {
                try {
                    json.decodeFromString<List<LocationItem>>(it)
                } catch (_: Exception) {
                    emptyList()
                }
            } ?: emptyList()

            val searched = prefs[searchedLocationKey]?.let {
                try {
                    json.decodeFromString<LocationItem>(it)
                } catch (_: Exception) {
                    null
                }
            }

            val live = prefs[liveLocationKey]?.let {
                try {
                    json.decodeFromString<LocationItem>(it)
                } catch (_: Exception) {
                    null
                }
            }

            _uiState.update { state ->
                state.copy(
                    favorites = favs,
                    searchedLocation = searched,
                    lastLiveLocation = live,
                    isFollowMode = prefs[followModeKey] ?: false,
                    tempUnit = prefs[tempUnitKey]?.let { TempUnit.valueOf(it) } ?: TempUnit.CELSIUS,
                    pressureUnit = prefs[pressureUnitKey]?.let { PressureUnit.valueOf(it) } ?: PressureUnit.HPA,
                    customValuesEnabled = prefs[customValuesKey] ?: false,
                    mapZoom = prefs[mapZoomKey] ?: 16f,
                    blurStrength = prefs[blurStrengthKey] ?: 6f,
                    tintAlpha = prefs[tintAlphaKey] ?: 0.1f,
                    textAlpha = prefs[textAlphaKey] ?: 0.8f,
                    sheetBlurStrength = prefs[sheetBlurKey] ?: 16f,
                    sheetDistortion = prefs[sheetDistortionKey] ?: 0.2f,
                    searchBarTintAlpha = prefs[searchBarTintKey] ?: 0.15f,
                    obfuscationMode = prefs[obfuscationModeKey]?.let { ObfuscationMode.valueOf(it) } ?: ObfuscationMode.PRECISE,
                    gridKm = prefs[gridKmKey] ?: 5.0f,
                    experimentalEnabled = prefs[experimentalKey] ?: false
                )
            }
            refreshActiveRequestSignature()

            loadPersistedCaches(prefs)
            preCacheLocations(favs + listOfNotNull(searched, live))

            if (_uiState.value.isFollowMode) {
                startFollowMode(applicationContext)
            }

            prefs[lastLocationKey]?.let { jsonStr ->
                try {
                    val location = json.decodeFromString<LocationItem>(jsonStr)
                    lastLocation = location
                    _uiState.update { it.copy(selectedLocation = location) }
                } catch (e: Exception) {
                    addLog("Load failed: ${e.message}")
                }
            }

            if (_uiState.value.selectedLocation == null) {
                addLog("No saved location found, defaulting to New York")
                lastLocation = defaultLocation
                _uiState.update { it.copy(selectedLocation = defaultLocation) }
                saveLastLocation(defaultLocation)
            }

            val startupLocations = buildList {
                addAll(favs)
                searched?.let { add(it) }
                live?.let { add(it) }
                _uiState.value.selectedLocation?.let { add(it) }
            }
            prefetchWeatherOnStartup(startupLocations)
        }
    }

    private fun loadPersistedCaches(prefs: Preferences) {
        val encoded = prefs[persistedCacheKey] ?: return
        val now = System.currentTimeMillis()
        try {
            val persisted = json.decodeFromString<PersistedWeatherState>(encoded)
            val signature = activeRequestSignature()

            val weatherMap = mutableMapOf<String, WeatherResponse>()
            val currentMap = mutableMapOf<String, Long>()
            val hourlyMap = mutableMapOf<String, Long>()
            val dailyMap = mutableMapOf<String, Long>()
            val updateMap = mutableMapOf<String, Long>()
            val signatureMap = mutableMapOf<String, String>()

            persisted.weatherEntries.forEach { entry ->
                if (entry.signature.isNotBlank() && entry.signature != signature) {
                    return@forEach
                }
                weatherMap[entry.key] = entry.weather
                currentMap[entry.key] = entry.currentUpdatedAt
                hourlyMap[entry.key] = entry.hourlyUpdatedAt
                dailyMap[entry.key] = entry.dailyUpdatedAt
                updateMap[entry.key] = max(entry.currentUpdatedAt, max(entry.hourlyUpdatedAt, entry.dailyUpdatedAt))
                signatureMap[entry.key] = signature
            }

            _uiState.update { state ->
                state.copy(
                    weatherMap = weatherMap,
                    currentUpdateTimeMap = currentMap,
                    hourlyUpdateTimeMap = hourlyMap,
                    dailyUpdateTimeMap = dailyMap,
                    updateTimeMap = updateMap,
                    cacheSignatureMap = signatureMap
                )
            }

            searchQueryCache.clear()
            persisted.searchEntries.forEach { entry ->
                if (now - entry.cachedAt <= queryCacheTtlMs) {
                    searchQueryCache[entry.query] = SearchCacheEntry(
                        normalizedQuery = entry.normalizedQuery,
                        cachedAt = entry.cachedAt,
                        suggestions = entry.suggestions
                    )
                }
            }

            placeIdCache.clear()
            persisted.placeEntries.forEach { entry ->
                if (now - entry.cachedAt <= placeCacheTtlMs) {
                    placeIdCache[entry.placeId] = PlaceCacheEntry(
                        displayName = entry.displayName,
                        lat = entry.lat,
                        lon = entry.lon,
                        cachedAt = entry.cachedAt
                    )
                }
            }

            pruneExpiredWeatherEntries(now)
            addLog("Restored persisted cache state")
        } catch (e: Exception) {
            addLog("Failed to restore persisted cache: ${e.message}")
        }
    }

    private fun prefetchWeatherOnStartup(locations: List<LocationItem>) {
        val uniqueLocations = locations.distinctBy { locationKey(it) }
        if (uniqueLocations.isEmpty()) return

        startupPrefetchJob?.cancel()
        startupPrefetchJob = viewModelScope.launch {
            addLog("Startup weather prefetch: ${uniqueLocations.size} location(s)")
            uniqueLocations.forEach { location ->
                requestWeatherIfNeeded(
                    location = location,
                    force = false,
                    trigger = RefreshTrigger.STARTUP,
                    animateResponse = false,
                    bypassFollowModeGuard = true,
                    showLoading = false
                )
                delay(120)
            }
        }
    }

    private fun preCacheLocations(locations: List<LocationItem>) {
        if (locations.isEmpty()) return
        viewModelScope.launch {
            try {
                val cacheManager = withContext(Dispatchers.Main) {
                    val mapView = MapView(applicationContext)
                    CacheManager(mapView)
                }

                withContext(Dispatchers.IO) {
                    locations.distinctBy { locationKey(it) }.forEach { loc ->
                        addLog("Pre-caching tiles for: ${loc.name}")
                        val bb = BoundingBox(loc.lat + 0.08, loc.lon + 0.08, loc.lat - 0.08, loc.lon - 0.08)
                        cacheManager.downloadAreaAsync(applicationContext, bb, 13, 17)
                    }
                }
            } catch (e: Exception) {
                addLog("Pre-cache error: ${e.message}")
            }
        }
    }

    private fun startStalenessCheck() {
        stalenessCheckJob?.cancel()
        stalenessCheckJob = viewModelScope.launch {
            while (true) {
                delay(staleCheckIntervalMs)
                refreshActiveRequestSignature()
                runCatching {
                    refreshStaleWeatherForTrackedLocations()
                }.onFailure { error ->
                    addLog("Background stale refresh failed: ${error.message}")
                }

                val now = System.currentTimeMillis()
                pruneExpiredWeatherEntries(now)

                val selectedLoc = _uiState.value.selectedLocation
                if (selectedLoc == null) {
                    removeError(staleDataError)
                    continue
                }

                val key = locationKey(selectedLoc)
                val hasUsable = hasAnyUsableData(key, now)
                if (hasUsable) {
                    removeError(staleDataError)
                } else {
                    addError(staleDataError)
                }
            }
        }
    }

    private suspend fun refreshStaleWeatherForTrackedLocations() {
        val trackedLocations = trackedLocationsForStalenessRefresh()
        if (trackedLocations.isEmpty()) return

        trackedLocations.forEach { location ->
            requestWeatherIfNeeded(
                location = location,
                force = false,
                trigger = RefreshTrigger.BACKGROUND,
                animateResponse = false,
                bypassFollowModeGuard = false,
                showLoading = false,
                logNoFetch = false
            )
        }
    }

    private fun trackedLocationsForStalenessRefresh(
        state: WeatherUiState = _uiState.value
    ): List<LocationItem> {
        val locationsByKey = linkedMapOf<String, LocationItem>()

        fun add(location: LocationItem?) {
            if (location == null) return
            locationsByKey.putIfAbsent(locationKey(location), location)
        }

        add(state.selectedLocation)
        add(state.searchedLocation)
        add(state.lastLiveLocation)
        state.favorites.forEach(::add)

        state.weatherMap.keys.forEach { key ->
            if (locationsByKey.containsKey(key)) {
                return@forEach
            }
            parseLocationKey(key)?.let { parsed ->
                locationsByKey[key] = parsed
            }
        }

        return locationsByKey.values.toList()
    }

    private fun parseLocationKey(key: String): LocationItem? {
        val parts = key.split(",", limit = 2)
        if (parts.size != 2) return null

        val lat = parts[0].trim().toDoubleOrNull() ?: return null
        val lon = parts[1].trim().toDoubleOrNull() ?: return null
        return LocationItem(
            name = "Cached location",
            lat = lat,
            lon = lon
        )
    }

    private fun isSignatureMatchForLocation(
        locationKey: String,
        state: WeatherUiState = _uiState.value
    ): Boolean {
        val cachedSignature = state.cacheSignatureMap[locationKey] ?: return false
        return cachedSignature == activeRequestSignature(state)
    }

    private fun weatherForActiveSignature(
        locationKey: String,
        state: WeatherUiState = _uiState.value
    ): WeatherResponse? {
        if (!isSignatureMatchForLocation(locationKey, state)) {
            return null
        }
        return state.weatherMap[locationKey]
    }

    private fun hasAnyUsableData(key: String, now: Long): Boolean {
        val state = _uiState.value
        val weather = state.weatherMap[key] ?: return false

        val currentUsable = isDatasetUsable(
            hasData = weather.currentWeather != null,
            updatedAt = state.currentUpdateTimeMap[key] ?: 0L,
            now = now,
            maxAgeMs = staleServeWindowMs
        )
        val hourlyUsable = isDatasetUsable(
            hasData = weather.hourly != null,
            updatedAt = state.hourlyUpdateTimeMap[key] ?: 0L,
            now = now,
            maxAgeMs = staleServeWindowMs
        )
        val dailyUsable = isDatasetUsable(
            hasData = weather.daily != null,
            updatedAt = state.dailyUpdateTimeMap[key] ?: 0L,
            now = now,
            maxAgeMs = staleServeWindowMs
        )

        return currentUsable || hourlyUsable || dailyUsable
    }

    private fun isDatasetUsable(hasData: Boolean, updatedAt: Long, now: Long, maxAgeMs: Long): Boolean {
        return hasData && updatedAt > 0L && (now - updatedAt) <= maxAgeMs
    }

    private fun pruneExpiredWeatherEntries(now: Long = System.currentTimeMillis()) {
        val state = _uiState.value
        if (state.weatherMap.isEmpty()) return

        val keysToRemove = state.weatherMap.keys.filter { key ->
            !hasAnyUsableData(key, now)
        }.toSet()

        if (keysToRemove.isEmpty()) return

        _uiState.update { current ->
            current.copy(
                weatherMap = current.weatherMap - keysToRemove,
                updateTimeMap = current.updateTimeMap - keysToRemove,
                currentUpdateTimeMap = current.currentUpdateTimeMap - keysToRemove,
                hourlyUpdateTimeMap = current.hourlyUpdateTimeMap - keysToRemove,
                dailyUpdateTimeMap = current.dailyUpdateTimeMap - keysToRemove,
                cacheSignatureMap = current.cacheSignatureMap - keysToRemove,
                pageStatuses = current.pageStatuses - keysToRemove
            )
        }

        keysToRemove.forEach {
            locationBackoff.remove(it)
            locationRequestHistory.remove(it)
            val inFlightKeys = inFlightWeatherRequests.keys.filter { requestKey ->
                requestKey.startsWith("$it|")
            }
            inFlightKeys.forEach { requestKey ->
                inFlightWeatherRequests.remove(requestKey)
            }
        }

        persistCachesSoon()
    }

    private fun addError(error: String) {
        _uiState.update { state ->
            if (!state.errors.contains(error)) {
                state.copy(errors = state.errors + error)
            } else state
        }
    }

    private fun removeError(error: String) {
        _uiState.update { state ->
            state.copy(errors = state.errors.filter { it != error })
        }
    }

    private fun saveLastLocation(location: LocationItem) {
        viewModelScope.launch {
            applicationContext.dataStore.edit { settings ->
                settings[lastLocationKey] = json.encodeToString(location)
            }
        }
    }

    private fun saveSearchedLocation(location: LocationItem?) {
        viewModelScope.launch {
            applicationContext.dataStore.edit { settings ->
                if (location != null) {
                    settings[searchedLocationKey] = json.encodeToString(location)
                } else {
                    settings.remove(searchedLocationKey)
                }
            }
        }
    }

    private fun saveLiveLocation(location: LocationItem?) {
        viewModelScope.launch {
            applicationContext.dataStore.edit { settings ->
                if (location != null) {
                    settings[liveLocationKey] = json.encodeToString(location)
                } else {
                    settings.remove(liveLocationKey)
                }
            }
        }
    }

    private fun saveFollowMode(enabled: Boolean) {
        viewModelScope.launch {
            applicationContext.dataStore.edit { settings ->
                settings[followModeKey] = enabled
            }
        }
    }

    private fun saveFavorites(favorites: List<LocationItem>) {
        viewModelScope.launch {
            applicationContext.dataStore.edit { settings ->
                settings[favoritesKey] = json.encodeToString(favorites)
            }
        }
    }

    private fun persistCachesSoon() {
        persistCachesJob?.cancel()
        persistCachesJob = viewModelScope.launch {
            delay(120)
            persistCachesNow()
        }
    }

    private suspend fun persistCachesNow() {
        val state = _uiState.value
        val weatherEntries = state.weatherMap.map { (key, weather) ->
            PersistedWeatherEntry(
                key = key,
                weather = weather,
                currentUpdatedAt = state.currentUpdateTimeMap[key] ?: 0L,
                hourlyUpdatedAt = state.hourlyUpdateTimeMap[key] ?: 0L,
                dailyUpdatedAt = state.dailyUpdateTimeMap[key] ?: 0L,
                signature = state.cacheSignatureMap[key] ?: activeRequestSignature(state)
            )
        }

        val searchEntries = searchQueryCache.map { (query, entry) ->
            PersistedSearchEntry(
                query = query,
                normalizedQuery = entry.normalizedQuery,
                cachedAt = entry.cachedAt,
                suggestions = entry.suggestions
            )
        }

        val placeEntries = placeIdCache.map { (placeId, entry) ->
            PersistedPlaceEntry(
                placeId = placeId,
                displayName = entry.displayName,
                lat = entry.lat,
                lon = entry.lon,
                cachedAt = entry.cachedAt
            )
        }

        val payload = json.encodeToString(
            PersistedWeatherState(
                weatherEntries = weatherEntries,
                searchEntries = searchEntries,
                placeEntries = placeEntries
            )
        )

        applicationContext.dataStore.edit { prefs ->
            prefs[persistedCacheKey] = payload
        }
    }

    fun setTempUnit(unit: TempUnit) {
        _uiState.update { it.copy(tempUnit = unit) }
        viewModelScope.launch {
            applicationContext.dataStore.edit { settings ->
                settings[tempUnitKey] = unit.name
            }
        }
    }

    fun setPressureUnit(unit: PressureUnit) {
        _uiState.update { it.copy(pressureUnit = unit) }
        viewModelScope.launch {
            applicationContext.dataStore.edit { settings ->
                settings[pressureUnitKey] = unit.name
            }
        }
    }

    fun toggleCustomValues(enabled: Boolean) {
        val defaultZoom = 16f
        val defaultBlur = 6f
        val defaultTint = 0.1f
        val defaultTextAlpha = 0.8f
        val defaultSheetBlur = 16f
        val defaultSheetDistortion = 0.2f
        val defaultSearchBarTint = 0.15f

        _uiState.update {
            it.copy(
                customValuesEnabled = enabled,
                mapZoom = defaultZoom,
                blurStrength = defaultBlur,
                tintAlpha = defaultTint,
                textAlpha = defaultTextAlpha,
                sheetBlurStrength = defaultSheetBlur,
                sheetDistortion = defaultSheetDistortion,
                searchBarTintAlpha = defaultSearchBarTint
            )
        }
        viewModelScope.launch {
            applicationContext.dataStore.edit { prefs ->
                prefs[customValuesKey] = enabled
                prefs[mapZoomKey] = defaultZoom
                prefs[blurStrengthKey] = defaultBlur
                prefs[tintAlphaKey] = defaultTint
                prefs[textAlphaKey] = defaultTextAlpha
                prefs[sheetBlurKey] = defaultSheetBlur
                prefs[sheetDistortionKey] = defaultSheetDistortion
                prefs[searchBarTintKey] = defaultSearchBarTint
            }
        }
    }

    fun setMapZoom(zoom: Float) {
        _uiState.update { it.copy(mapZoom = zoom) }
        viewModelScope.launch {
            applicationContext.dataStore.edit { it[mapZoomKey] = zoom }
        }
    }

    fun setBlurStrength(strength: Float) {
        _uiState.update { it.copy(blurStrength = strength) }
        viewModelScope.launch {
            applicationContext.dataStore.edit { it[blurStrengthKey] = strength }
        }
    }

    fun setTintAlpha(alpha: Float) {
        _uiState.update { it.copy(tintAlpha = alpha) }
        viewModelScope.launch {
            applicationContext.dataStore.edit { it[tintAlphaKey] = alpha }
        }
    }

    fun setTextAlpha(alpha: Float) {
        _uiState.update { it.copy(textAlpha = alpha) }
        viewModelScope.launch {
            applicationContext.dataStore.edit { it[textAlphaKey] = alpha }
        }
    }

    fun setSheetBlurStrength(strength: Float) {
        _uiState.update { it.copy(sheetBlurStrength = strength) }
        viewModelScope.launch {
            applicationContext.dataStore.edit { it[sheetBlurKey] = strength }
        }
    }

    fun setSheetDistortion(distortion: Float) {
        _uiState.update { it.copy(sheetDistortion = distortion) }
        viewModelScope.launch {
            applicationContext.dataStore.edit { it[sheetDistortionKey] = distortion }
        }
    }

    fun setSearchBarTintAlpha(alpha: Float) {
        _uiState.update { it.copy(searchBarTintAlpha = alpha) }
        viewModelScope.launch {
            applicationContext.dataStore.edit { it[searchBarTintKey] = alpha }
        }
    }

    fun setObfuscationMode(mode: ObfuscationMode) {
        applyPrivacySettings(
            mode = mode,
            gridKm = _uiState.value.gridKm
        )
    }

    fun setGridKm(km: Float) {
        applyPrivacySettings(
            mode = _uiState.value.obfuscationMode,
            gridKm = km
        )
    }

    fun applyPrivacySettings(mode: ObfuscationMode, gridKm: Float) {
        val state = _uiState.value
        val modeChanged = state.obfuscationMode != mode
        val gridChanged = state.gridKm != gridKm
        if (!modeChanged && !gridChanged) {
            return
        }

        if (modeChanged) {
            addLog("Obfuscation mode set to: $mode")
        }
        if (gridChanged) {
            addLog("Grid size set to: $gridKm km")
        }

        _uiState.update {
            it.copy(
                obfuscationMode = mode,
                gridKm = gridKm
            )
        }
        refreshActiveRequestSignature()
        viewModelScope.launch {
            applicationContext.dataStore.edit {
                it[obfuscationModeKey] = mode.name
                it[gridKmKey] = gridKm
            }
        }
        _uiState.value.selectedLocation?.let {
            fetchWeather(
                location = it,
                force = true,
                bypassLocationGateIfDistanceOverKm = perLocationGateBypassDistanceKm
            )
        }
    }

    fun setExperimentalEnabled(enabled: Boolean) {
        addLog("Experimental features: $enabled")
        _uiState.update { it.copy(experimentalEnabled = enabled) }
        viewModelScope.launch {
            applicationContext.dataStore.edit { it[experimentalKey] = enabled }
        }
    }

    fun addLog(message: String) {
        Log.d(tag, message)
        _debugLogs.update { listOf("${System.currentTimeMillis() % 100000}: $message") + it }
    }

    fun toggleSettings(open: Boolean) {
        _uiState.update { it.copy(isSettingsOpen = open) }
    }

    fun toggleOrganizerMode(open: Boolean) {
        _uiState.update { it.copy(isOrganizerMode = open) }
    }

    fun updateFavorites(newFavorites: List<LocationItem>) {
        val filteredFavorites = newFavorites.filter { it.isFavorite }
        _uiState.update { state ->
            state.copy(favorites = filteredFavorites, pageStatuses = emptyMap())
        }
        saveFavorites(filteredFavorites)
        preCacheLocations(filteredFavorites)
    }

    fun reorderFavorites(fromIndex: Int, toIndex: Int) {
        _uiState.update { state ->
            val newList = state.favorites.toMutableList()
            if (fromIndex in newList.indices && toIndex in newList.indices) {
                val item = newList.removeAt(fromIndex)
                newList.add(toIndex, item)
                saveFavorites(newList)
                state.copy(favorites = newList, pageStatuses = emptyMap())
            } else state
        }
    }

    fun clearMapCache() {
        viewModelScope.launch(Dispatchers.IO) {
            val cacheDir = File(applicationContext.cacheDir, "osmdroid")
            if (cacheDir.exists()) {
                cacheDir.deleteRecursively()
                addLog("Map cache cleared")
            }
        }
    }

    fun clearFavorites() {
        _uiState.update { it.copy(favorites = emptyList()) }
        saveFavorites(emptyList())
        addLog("Favorites cleared")
    }

    fun clearStatuses() {
        _uiState.update { it.copy(pageStatuses = emptyMap()) }
    }

    fun setFollowMode(enabled: Boolean, context: Context) {
        if (_uiState.value.isFollowMode == enabled) return

        _uiState.update {
            it.copy(
                isFollowMode = enabled,
                pageStatuses = emptyMap(),
                selectedLocation = if (enabled) it.lastLiveLocation ?: it.selectedLocation else it.selectedLocation
            )
        }
        saveFollowMode(enabled)

        if (enabled) {
            addLog("Follow mode enabled")
            startFollowMode(context)
        } else {
            addLog("Follow mode disabled")
            locationJob?.cancel()
            followModeJob?.cancel()
        }
    }

    private fun startFollowMode(context: Context) {
        followModeJob?.cancel()
        followModeJob = viewModelScope.launch {
            while (true) {
                getCurrentLocation(context, isSilent = true)
                delay(30000)
            }
        }
    }

    fun testNetwork(context: Context) {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val nw = connectivityManager.activeNetwork
        if (nw == null) {
            addLog("Network: Not connected")
            return
        }
        val actNw = connectivityManager.getNetworkCapabilities(nw)
        val isConnected = actNw != null && (
            actNw.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                actNw.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)
            )
        addLog("Network: ${if (isConnected) "Connected" else "Disconnected"}")
    }

    fun checkPermissions(context: Context) {
        val internet = ContextCompat.checkSelfPermission(context, Manifest.permission.INTERNET) == PackageManager.PERMISSION_GRANTED
        val location = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        addLog("Perms: Internet=$internet, Location=$location")
    }

    fun testApiConnections() {
        viewModelScope.launch {
            try {
                nominatimService.search("London")
                addLog("API: Nominatim OK")
            } catch (e: Exception) {
                addLog("API: Nominatim FAILED (${e.message})")
            }
            try {
                openMeteoService.getWeather(
                    lat = 51.5,
                    lon = -0.1,
                    currentWeather = true,
                    hourly = hourlyFields,
                    daily = dailyFields
                )
                addLog("API: OpenMeteo OK")
            } catch (e: Exception) {
                addLog("API: OpenMeteo FAILED (${e.message})")
            }
        }
    }

    @SuppressLint("MissingPermission")
    fun getCurrentLocation(context: Context, isSilent: Boolean = false) {
        if (locationJob?.isActive == true) return

        if (!isSilent) {
            _uiState.update { it.copy(isLocating = true) }
        }
        addLog("Starting location request...")

        locationJob = viewModelScope.launch {
            try {
                val cts = CancellationTokenSource()
                val timeoutJob = launch {
                    delay(15000)
                    cts.cancel()
                    if (!isSilent) {
                        _uiState.update { it.copy(isLocating = false) }
                    }
                    addLog("Location request timed out")
                }

                val location = try {
                    fusedLocationClient.getCurrentLocation(
                        Priority.PRIORITY_HIGH_ACCURACY,
                        cts.token
                    ).await()
                } catch (_: Exception) {
                    null
                }

                timeoutJob.cancel()

                if (location != null) {
                    addLog("Location found: ${location.latitude}, ${location.longitude}")
                    val name = withContext(Dispatchers.IO) {
                        try {
                            val geocoder = Geocoder(applicationContext, Locale.getDefault())
                            @Suppress("DEPRECATION")
                            val addresses = geocoder.getFromLocation(location.latitude, location.longitude, 1)
                            addresses?.firstOrNull()?.let {
                                it.locality ?: it.subAdminArea ?: it.adminArea ?: "Current Location"
                            } ?: "Current Location"
                        } catch (_: Exception) {
                            "Current Location"
                        }
                    }

                    val liveLoc = LocationItem(name, location.latitude, location.longitude)
                    _uiState.update { it.copy(locationFound = true, lastLiveLocation = liveLoc) }
                    saveLiveLocation(liveLoc)
                    onLocationSelected(liveLoc, isManualSearch = false)
                } else {
                    addLog("Location returned null or cancelled")
                    if (_uiState.value.lastLiveLocation == null) {
                        _uiState.update { it.copy(locationFound = false) }
                    }
                }
            } catch (e: Exception) {
                addLog("Location error: ${e.message}")
                if (_uiState.value.lastLiveLocation == null) {
                    _uiState.update { it.copy(locationFound = false) }
                }
            } finally {
                _uiState.update { it.copy(isLocating = false) }
            }
        }
    }

    fun onSearchQueryChanged(query: String) {
        _uiState.update { it.copy(searchQuery = query) }

        if (query.isNotEmpty()) {
            if (_uiState.value.isLocating) {
                locationJob?.cancel()
                _uiState.update { it.copy(isLocating = false) }
                addLog("Location cancelled by search")
            }
            if (_uiState.value.isFollowMode) {
                _uiState.update { it.copy(isFollowMode = false) }
                saveFollowMode(false)
                followModeJob?.cancel()
                addLog("Follow mode disabled by search")
            }
        }

        searchJob?.cancel()
        val trimmed = query.trim()
        if (trimmed.isBlank()) {
            lastMeaningfulSearchQuery = ""
            _uiState.update { it.copy(suggestions = emptyList()) }
            return
        }

        searchJob = viewModelScope.launch {
            delay(searchDebounceMs)

            val latestQuery = _uiState.value.searchQuery.trim()
            if (latestQuery.isBlank()) {
                _uiState.update { it.copy(suggestions = emptyList()) }
                return@launch
            }

            val now = System.currentTimeMillis()
            pruneSearchCaches(now)

            val normalized = normalizeQuery(latestQuery)
            if (normalized.isBlank()) {
                _uiState.update { it.copy(suggestions = emptyList()) }
                return@launch
            }

            val cachedEntry = searchQueryCache[latestQuery]
            if (cachedEntry != null && now - cachedEntry.cachedAt <= queryCacheTtlMs) {
                publishSuggestions(cachedEntry.suggestions)
                lastMeaningfulSearchQuery = cachedEntry.normalizedQuery
                addLog("Search cache hit for '$latestQuery'")
                return@launch
            }

            if (normalized == lastMeaningfulSearchQuery) {
                addLog("Search skipped; query has no meaningful change")
                return@launch
            }

            try {
                val response = nominatimService.search(latestQuery)
                val suggestions = response.mapNotNull { buildCachedSuggestion(it, now) }

                searchQueryCache[latestQuery] = SearchCacheEntry(
                    normalizedQuery = normalized,
                    cachedAt = now,
                    suggestions = suggestions
                )
                publishSuggestions(suggestions)
                lastMeaningfulSearchQuery = normalized
                persistCachesSoon()
            } catch (e: Exception) {
                addLog("Search Error: ${e.message}")
            }
        }
    }

    private fun normalizeQuery(query: String): String {
        return query.trim().lowercase(Locale.US).replace(whitespaceRegex, " ")
    }

    private fun buildCachedSuggestion(response: GeocodingResponse, now: Long): CachedSuggestion? {
        val placeId = response.placeId
        val cachedPlace = placeIdCache[placeId]?.takeIf { now - it.cachedAt <= placeCacheTtlMs }

        val lat = cachedPlace?.lat ?: response.lat.toDoubleOrNull()
        val lon = cachedPlace?.lon ?: response.lon.toDoubleOrNull()
        if (lat == null || lon == null) {
            return null
        }

        val displayName = if (response.displayName.isNotBlank()) {
            response.displayName
        } else {
            cachedPlace?.displayName ?: return null
        }

        placeIdCache[placeId] = PlaceCacheEntry(
            displayName = displayName,
            lat = lat,
            lon = lon,
            cachedAt = now
        )

        return CachedSuggestion(
            placeId = placeId,
            displayName = displayName,
            lat = lat,
            lon = lon
        )
    }

    private fun publishSuggestions(suggestions: List<CachedSuggestion>) {
        _uiState.update { state ->
            val mapped = suggestions.map { suggestion ->
                val isFav = state.favorites.any { favorite -> favorite.name == suggestion.displayName }
                LocationItem(
                    name = suggestion.displayName,
                    lat = suggestion.lat,
                    lon = suggestion.lon,
                    isFavorite = isFav
                )
            }
            state.copy(suggestions = mapped)
        }
    }

    private fun pruneSearchCaches(now: Long = System.currentTimeMillis()) {
        var changed = false

        val queryIterator = searchQueryCache.entries.iterator()
        while (queryIterator.hasNext()) {
            val entry = queryIterator.next()
            if (now - entry.value.cachedAt > queryCacheTtlMs) {
                queryIterator.remove()
                changed = true
            }
        }

        val placeIterator = placeIdCache.entries.iterator()
        while (placeIterator.hasNext()) {
            val entry = placeIterator.next()
            if (now - entry.value.cachedAt > placeCacheTtlMs) {
                placeIterator.remove()
                changed = true
            }
        }

        if (changed) {
            persistCachesSoon()
        }
    }

    fun onLocationSelected(location: LocationItem, isManualSearch: Boolean = true) {
        addLog("Location selected: ${location.name} (${location.lat}, ${location.lon})")
        val updatedLocation = location.copy(lastViewed = System.currentTimeMillis())
        val isFavorite = _uiState.value.favorites.any { it.name == location.name }

        lastLocation = updatedLocation
        _uiState.update { state ->
            val updatedFavorites = state.favorites.map {
                if (it.name == location.name) updatedLocation else it
            }

            val newSearchedLocation = if (isManualSearch && !isFavorite) {
                updatedLocation
            } else if (isFavorite && state.searchedLocation?.name == location.name) {
                null
            } else {
                state.searchedLocation
            }

            state.copy(
                selectedLocation = updatedLocation,
                searchedLocation = newSearchedLocation,
                searchQuery = "",
                suggestions = emptyList(),
                favorites = updatedFavorites
            )
        }

        if (isManualSearch) {
            saveSearchedLocation(_uiState.value.searchedLocation)
            preCacheLocations(listOf(updatedLocation))
        }

        locationJob?.cancel()
        weatherJob?.cancel()

        saveLastLocation(updatedLocation)
        fetchWeather(updatedLocation)
    }

    fun toggleFavorite(location: LocationItem) {
        _uiState.update { state ->
            val isFav = state.favorites.any { it.name == location.name }
            val updated = location.copy(isFavorite = !isFav)

            addLog("Favorite toggled for ${location.name}: ${!isFav}")

            val newFavorites = if (!isFav) {
                (state.favorites + updated).distinctBy { it.name }
            } else {
                state.favorites.filter { it.name != location.name }
            }

            saveFavorites(newFavorites)
            preCacheLocations(newFavorites)

            val isNowFavorite = newFavorites.any { it.name == location.name }
            val newSearched = if (isNowFavorite && state.searchedLocation?.name == location.name) null else state.searchedLocation
            if (newSearched != state.searchedLocation) {
                saveSearchedLocation(newSearched)
            }

            val newSuggestions = state.suggestions.map {
                if (it.name == location.name) updated else it
            }
            val newSelected = if (state.selectedLocation?.name == location.name) updated else state.selectedLocation

            state.copy(
                favorites = newFavorites,
                suggestions = newSuggestions,
                selectedLocation = newSelected,
                searchedLocation = newSearched
            )
        }
    }

    private fun fetchWeather(
        location: LocationItem,
        force: Boolean = false,
        animateResponse: Boolean = true,
        trigger: RefreshTrigger = RefreshTrigger.USER_INTERACTION,
        bypassFollowModeGuard: Boolean = false,
        bypassLocationGateIfDistanceOverKm: Double? = null
    ) {
        weatherJob?.cancel()
        weatherJob = viewModelScope.launch {
            val key = locationKey(location)
            val activeWeather = weatherForActiveSignature(key)
            val hasAnyCachedWeather = _uiState.value.weatherMap[key] != null
            requestWeatherIfNeeded(
                location = location,
                force = force,
                trigger = trigger,
                animateResponse = animateResponse,
                bypassFollowModeGuard = bypassFollowModeGuard,
                showLoading = activeWeather == null && !hasAnyCachedWeather,
                bypassLocationGateIfDistanceOverKm = bypassLocationGateIfDistanceOverKm
            )
        }
    }

    private suspend fun requestWeatherIfNeeded(
        location: LocationItem,
        force: Boolean,
        trigger: RefreshTrigger,
        animateResponse: Boolean,
        bypassFollowModeGuard: Boolean,
        showLoading: Boolean,
        bypassLocationGateIfDistanceOverKm: Double? = null,
        logNoFetch: Boolean = true
    ): Boolean {
        refreshActiveRequestSignature()
        val key = locationKey(location)
        val now = System.currentTimeMillis()
        val state = _uiState.value
        val signature = activeRequestSignature(state)
        val existingWeather = weatherForActiveSignature(key, state)

        val datasetsToRefresh = resolveDatasetsToRefresh(
            key = key,
            weather = existingWeather,
            now = now,
            force = force
        )

        if (datasetsToRefresh.isEmpty()) {
            _uiState.update { it.copy(pageStatuses = it.pageStatuses + (key to true)) }
            if (logNoFetch) {
                addLog("No fetch needed for ${location.name}; cache is within thresholds")
            }
            return false
        }

        val bypassLocationGate = bypassLocationGateIfDistanceOverKm?.let { thresholdKm ->
            shouldBypassPerLocationGateForDistance(
                location = location,
                state = state,
                thresholdKm = thresholdKm
            )
        } ?: false

        val gateDecision = evaluateRequestGate(
            locationKey = key,
            now = now,
            bypassLocationGate = bypassLocationGate
        )
        if (!gateDecision.isAllowed) {
            val waitMs = (gateDecision.nextAllowedAtMs - now).coerceAtLeast(0L)
            val reasons = gateDecision.reasons.joinToString(" + ")
            addLog("Skipping fetch for ${location.name}; gate=$reasons, retry in ${waitMs / 1000}s")
            return false
        }

        val inFlightKey = buildInFlightKey(key, signature, datasetsToRefresh)
        return runCoalescedRequest(inFlightKey) {
            addLog(
                "Fetching ${datasetsToRefresh.joinToString()} for ${location.name} " +
                    "(${trigger.name.lowercase(Locale.US)})"
            )

            fetchAndStoreWeather(
                location = location,
                datasets = datasetsToRefresh,
                showLoading = showLoading,
                animateResponse = animateResponse,
                bypassFollowModeGuard = bypassFollowModeGuard
            )
        }
    }

    private fun resolveDatasetsToRefresh(
        key: String,
        weather: WeatherResponse?,
        now: Long,
        force: Boolean
    ): Set<WeatherDataset> {
        val state = _uiState.value
        val isSignatureMatch = isSignatureMatchForLocation(key, state)

        val refreshInput = DatasetRefreshInput(
            force = force,
            hasCurrent = weather?.currentWeather != null,
            hasHourly = weather?.hourly != null,
            hasDaily = weather?.daily != null,
            currentUpdatedAtMs = if (isSignatureMatch) state.currentUpdateTimeMap[key] ?: 0L else 0L,
            hourlyUpdatedAtMs = if (isSignatureMatch) state.hourlyUpdateTimeMap[key] ?: 0L else 0L,
            dailyUpdatedAtMs = if (isSignatureMatch) state.dailyUpdateTimeMap[key] ?: 0L else 0L,
            nowMs = now,
            thresholds = WeatherFreshnessThresholds(
                currentMs = currentFreshnessMs,
                hourlyMs = hourlyFreshnessMs,
                dailyMs = dailyFreshnessMs
            )
        )

        return WeatherRequestPolicy.resolveDatasetsToRefresh(refreshInput)
    }

    private fun evaluateRequestGate(
        locationKey: String,
        now: Long,
        bypassLocationGate: Boolean = false
    ): RequestGateDecision {
        pruneRequestHistories(now)

        val history = locationRequestHistory[locationKey]
        val snapshot = RequestGateSnapshot(
            backoffUntilMs = locationBackoff[locationKey]?.retryAfterMs ?: 0L,
            locationLastRequestAtMs = if (bypassLocationGate) null else history?.lastOrNull(),
            globalRequestCount = globalRequestHistory.size,
            globalOldestRequestAtMs = globalRequestHistory.firstOrNull()
        )
        val config = RequestGateConfig(
            locationMinRequestIntervalMs = locationMinRequestIntervalMs,
            globalRequestWindowMs = globalRequestWindowMs,
            globalRequestLimitPerWindow = globalRequestLimitPerWindow
        )
        return WeatherRequestPolicy.evaluateRequestGate(
            nowMs = now,
            snapshot = snapshot,
            config = config
        )
    }

    private fun buildInFlightKey(
        locationKey: String,
        signature: String,
        datasets: Set<WeatherDataset>
    ): String {
        val datasetSignature = datasets
            .map { it.name }
            .sorted()
            .joinToString(",")
        return "$locationKey|$signature|$datasetSignature"
    }

    private suspend fun runCoalescedRequest(
        requestKey: String,
        block: suspend () -> Boolean
    ): Boolean {
        val existing = inFlightWeatherRequests[requestKey]
        if (existing != null) {
            addLog("Joining in-flight weather request")
            return existing.await()
        }

        val deferred = viewModelScope.async(start = CoroutineStart.LAZY) {
            block()
        }
        inFlightWeatherRequests[requestKey] = deferred

        return try {
            deferred.start()
            deferred.await()
        } finally {
            val current = inFlightWeatherRequests[requestKey]
            if (current === deferred) {
                inFlightWeatherRequests.remove(requestKey)
            }
        }
    }

    private fun pruneRequestHistories(now: Long) {
        val prunedGlobal = WeatherRequestPolicy.pruneTimestampsWithinWindow(
            timestamps = globalRequestHistory,
            nowMs = now,
            windowMs = globalRequestWindowMs
        )
        globalRequestHistory.clear()
        globalRequestHistory.addAll(prunedGlobal)

        val iterator = locationRequestHistory.entries.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            val prunedLocation = WeatherRequestPolicy.pruneTimestampsWithinWindow(
                timestamps = entry.value,
                nowMs = now,
                windowMs = locationMinRequestIntervalMs
            )
            if (prunedLocation.isEmpty()) {
                iterator.remove()
            } else {
                entry.value.clear()
                entry.value.addAll(prunedLocation)
            }
        }
    }

    private fun recordRequest(locationKey: String, now: Long) {
        pruneRequestHistories(now)
        globalRequestHistory.addLast(now)
        val history = locationRequestHistory.getOrPut(locationKey) { ArrayDeque() }
        history.addLast(now)
    }

    private fun locationKey(location: LocationItem): String {
        return "${location.lat},${location.lon}"
    }

    private fun shouldBypassPerLocationGateForDistance(
        location: LocationItem,
        state: WeatherUiState,
        thresholdKm: Double
    ): Boolean {
        val key = locationKey(location)
        val previousWeather = state.weatherMap[key] ?: return false
        val previousLat = previousWeather.latitude
        val previousLon = previousWeather.longitude
        if (!previousLat.isFinite() || !previousLon.isFinite()) {
            return false
        }

        val nextRequestCoords = obfuscatedRequestLocation(location, state)
        val distanceKm = distanceBetweenKm(
            startLat = previousLat,
            startLon = previousLon,
            endLat = nextRequestCoords.latObf,
            endLon = nextRequestCoords.lonObf
        )
        if (distanceKm <= thresholdKm) {
            return false
        }

        addLog(
            "Bypassing per-location gate for ${location.name}; " +
                "coordinate shift=${"%.1f".format(Locale.US, distanceKm)}km"
        )
        return true
    }

    private fun distanceBetweenKm(
        startLat: Double,
        startLon: Double,
        endLat: Double,
        endLon: Double
    ): Double {
        val results = FloatArray(1)
        Location.distanceBetween(startLat, startLon, endLat, endLon, results)
        return results[0].toDouble() / 1000.0
    }

    private fun obfuscatedRequestLocation(
        location: LocationItem,
        state: WeatherUiState = _uiState.value
    ) = run {
        val calendar = Calendar.getInstance()
        val seedStr = "${calendar.get(Calendar.YEAR)}-${calendar.get(Calendar.DAY_OF_YEAR)}"
        val seed = seedStr.hashCode().toLong()
        obfuscateLocation(
            location.lat,
            location.lon,
            state.obfuscationMode,
            state.gridKm.toDouble(),
            seed = seed
        )
    }

    private suspend fun fetchAndStoreWeather(
        location: LocationItem,
        datasets: Set<WeatherDataset>,
        showLoading: Boolean,
        animateResponse: Boolean,
        bypassFollowModeGuard: Boolean
    ): Boolean {
        if (
            !bypassFollowModeGuard &&
            _uiState.value.isFollowMode &&
            !_uiState.value.locationFound &&
            location == _uiState.value.selectedLocation &&
            _uiState.value.lastLiveLocation == null
        ) {
            return false
        }

        if (datasets.isEmpty()) {
            return false
        }

        val key = locationKey(location)

        val requestState = _uiState.value
        val obfuscated = obfuscatedRequestLocation(location, requestState)

        if (showLoading) {
            _uiState.update { it.copy(isLoading = true) }
        }

        addLog(
            "Requesting weather: lat=${obfuscated.latObf}, lon=${obfuscated.lonObf} " +
                "(orig ${location.lat}, ${location.lon}; mode=${requestState.obfuscationMode})"
        )

        val requestTime = System.currentTimeMillis()
        recordRequest(key, requestTime)

        return try {
            val weather = openMeteoService.getWeather(
                lat = obfuscated.latObf,
                lon = obfuscated.lonObf,
                currentWeather = if (WeatherDataset.CURRENT in datasets) true else null,
                hourly = if (WeatherDataset.HOURLY in datasets) hourlyFields else null,
                daily = if (WeatherDataset.DAILY in datasets) dailyFields else null
            )

            val stateBeforeUpdate = _uiState.value
            val signature = activeRequestSignature(stateBeforeUpdate)
            val existing = weatherForActiveSignature(key, stateBeforeUpdate)
            val merged = mergeWeather(existing, weather, datasets)

            val now = System.currentTimeMillis()
            val newCurrentUpdatedAt = if (
                WeatherDataset.CURRENT in datasets && weather.currentWeather != null
            ) now else stateBeforeUpdate.currentUpdateTimeMap[key] ?: 0L

            val newHourlyUpdatedAt = if (
                WeatherDataset.HOURLY in datasets && weather.hourly != null
            ) now else stateBeforeUpdate.hourlyUpdateTimeMap[key] ?: 0L

            val newDailyUpdatedAt = if (
                WeatherDataset.DAILY in datasets && weather.daily != null
            ) now else stateBeforeUpdate.dailyUpdateTimeMap[key] ?: 0L

            val combinedUpdatedAt = max(newCurrentUpdatedAt, max(newHourlyUpdatedAt, newDailyUpdatedAt))

            _uiState.update { state ->
                state.copy(
                    weatherMap = state.weatherMap + (key to merged),
                    currentUpdateTimeMap = state.currentUpdateTimeMap + (key to newCurrentUpdatedAt),
                    hourlyUpdateTimeMap = state.hourlyUpdateTimeMap + (key to newHourlyUpdatedAt),
                    dailyUpdateTimeMap = state.dailyUpdateTimeMap + (key to newDailyUpdatedAt),
                    cacheSignatureMap = state.cacheSignatureMap + (key to signature),
                    updateTimeMap = state.updateTimeMap + (key to combinedUpdatedAt),
                    isLoading = if (showLoading) false else state.isLoading,
                    pageStatuses = state.pageStatuses + (key to true),
                    lastResponseCoords = if (animateResponse) obfuscated.latObf to obfuscated.lonObf else state.lastResponseCoords,
                    responseAnimTrigger = if (animateResponse) now else state.responseAnimTrigger,
                    errors = state.errors.filter { it != staleDataError }
                )
            }

            val missingDatasets = mutableListOf<String>()
            if (WeatherDataset.CURRENT in datasets && weather.currentWeather == null) {
                missingDatasets += "current"
            }
            if (WeatherDataset.HOURLY in datasets && weather.hourly == null) {
                missingDatasets += "hourly"
            }
            if (WeatherDataset.DAILY in datasets && weather.daily == null) {
                missingDatasets += "daily"
            }
            if (missingDatasets.isNotEmpty()) {
                addLog("Weather response missing requested data: ${missingDatasets.joinToString()}")
            }

            locationBackoff.remove(key)
            runCatching {
                persistCachesJob?.cancel()
                persistCachesNow()
            }.onFailure { persistError ->
                addLog("Cache persist failed after fetch: ${persistError.message}")
            }
            addLog("Weather updated for ${location.name}")
            true
        } catch (e: Exception) {
            val retryableReason = classifyRetryableFailure(e)
            val failureContext = buildFailureContext(
                error = e,
                datasets = datasets,
                obfuscatedLat = obfuscated.latObf,
                obfuscatedLon = obfuscated.lonObf
            )
            if (retryableReason != null) {
                val delayMs = registerBackoff(key)
                addLog(
                    "Weather fetch failed for ${location.name}: $retryableReason. " +
                        "Backoff ${delayMs / 1000}s. $failureContext"
                )
            } else {
                addLog("Weather fetch failed for ${location.name}: ${e.message}. $failureContext")
            }

            _uiState.update { state ->
                state.copy(
                    isLoading = if (showLoading) false else state.isLoading,
                    pageStatuses = state.pageStatuses + (key to false)
                )
            }
            false
        }
    }

    private fun mergeWeather(
        existing: WeatherResponse?,
        incoming: WeatherResponse,
        datasets: Set<WeatherDataset>
    ): WeatherResponse {
        val existingCurrent: CurrentWeather? = existing?.currentWeather
        val existingHourly: HourlyData? = existing?.hourly
        val existingDaily: DailyData? = existing?.daily

        return WeatherResponse(
            latitude = incoming.latitude,
            longitude = incoming.longitude,
            currentWeather = if (WeatherDataset.CURRENT in datasets) {
                incoming.currentWeather ?: existingCurrent
            } else {
                existingCurrent
            },
            hourly = if (WeatherDataset.HOURLY in datasets) {
                incoming.hourly ?: existingHourly
            } else {
                existingHourly
            },
            daily = if (WeatherDataset.DAILY in datasets) {
                incoming.daily ?: existingDaily
            } else {
                existingDaily
            }
        )
    }

    private fun classifyRetryableFailure(error: Throwable): String? {
        return when (error) {
            is SocketTimeoutException -> "timeout"
            is HttpException -> {
                val code = error.code()
                if (code == 429 || code in 500..599) {
                    "http $code"
                } else {
                    null
                }
            }
            is SerializationException -> "parse error"
            is IOException -> "network error"
            else -> {
                val cause = error.cause
                if (cause != null && cause !== error) {
                    classifyRetryableFailure(cause)
                } else {
                    null
                }
            }
        }
    }

    private fun buildFailureContext(
        error: Throwable,
        datasets: Set<WeatherDataset>,
        obfuscatedLat: Double,
        obfuscatedLon: Double
    ): String {
        val datasetTag = datasets.map { it.name.lowercase(Locale.US) }.sorted().joinToString(",")
        val params = "params={lat=$obfuscatedLat,lon=$obfuscatedLon,datasets=$datasetTag,tz=auto}"

        return when (error) {
            is HttpException -> {
                val status = error.code()
                val bodySnippet = runCatching {
                    error.response()
                        ?.errorBody()
                        ?.string()
                        ?.replace(whitespaceRegex, " ")
                        ?.take(240)
                }.getOrNull()
                if (bodySnippet.isNullOrBlank()) {
                    "http=$status $params"
                } else {
                    "http=$status $params body='$bodySnippet'"
                }
            }

            is SerializationException -> {
                "parse_error $params msg='${error.message.orEmpty().take(160)}'"
            }

            else -> params
        }
    }

    private fun registerBackoff(locationKey: String): Long {
        val state = locationBackoff.getOrPut(locationKey) { BackoffState() }
        state.failureCount += 1
        val backoffMs = WeatherRequestPolicy.backoffDelayForFailure(
            failureCount = state.failureCount,
            backoffStepsMs = backoffStepsMs
        )
        state.retryAfterMs = System.currentTimeMillis() + backoffMs
        return backoffMs
    }

    private fun startAutoUpdate() {
        autoUpdateJob?.cancel()
        autoUpdateJob = viewModelScope.launch {
            while (true) {
                delay(backgroundRefreshIntervalMs)
                val selected = _uiState.value.selectedLocation ?: continue
                requestWeatherIfNeeded(
                    location = selected,
                    force = false,
                    trigger = RefreshTrigger.BACKGROUND,
                    animateResponse = false,
                    bypassFollowModeGuard = false,
                    showLoading = false
                )
            }
        }
    }
}

class WeatherViewModelFactory(private val context: Context) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(WeatherViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return WeatherViewModel(context) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
