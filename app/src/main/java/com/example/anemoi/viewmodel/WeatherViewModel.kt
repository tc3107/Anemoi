package com.example.anemoi.viewmodel

import android.annotation.SuppressLint
import android.content.Context
import android.location.Geocoder
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.content.pm.PackageManager
import android.Manifest
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.datastore.preferences.core.*
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.anemoi.data.*
import com.example.anemoi.util.*
import com.example.anemoi.util.dataStore
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import org.osmdroid.tileprovider.cachemanager.CacheManager
import org.osmdroid.tileprovider.MapTileProviderBasic
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.views.MapView
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.io.File
import java.util.Locale
import java.util.Calendar

data class WeatherUiState(
    val searchQuery: String = "",
    val suggestions: List<LocationItem> = emptyList(),
    val favorites: List<LocationItem> = emptyList(),
    val searchedLocation: LocationItem? = null,
    val selectedLocation: LocationItem? = null,
    val lastLiveLocation: LocationItem? = null,
    val weatherMap: Map<String, WeatherResponse> = emptyMap(), // Key: "original_lat,original_lon"
    val updateTimeMap: Map<String, Long> = emptyMap(), // Key: "original_lat,original_lon"
    val isLoading: Boolean = false,
    val isLocating: Boolean = false,
    val locationFound: Boolean = false,
    val pageStatuses: Map<String, Boolean> = emptyMap(), // Key: "original_lat,original_lon"
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
    val sheetBlurStrength: Float = 30f,
    val sheetDistortion: Float = 0.2f,
    val searchBarTintAlpha: Float = 0.15f,
    val obfuscationMode: ObfuscationMode = ObfuscationMode.PRECISE,
    val gridKm: Float = 5.0f,
    val lastResponseCoords: Pair<Double, Double>? = null, // lat, lon
    val responseAnimTrigger: Long = 0L,
    val experimentalEnabled: Boolean = false
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
    private var lastFetchTime = 0L
    private var lastLocation: LocationItem? = null

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
    private val defaultLocation = LocationItem(
        name = "New York",
        lat = 40.7128,
        lon = -74.0060
    )

    init {
        loadSettings()
        startAutoUpdate()
        startStalenessCheck()
    }

    private fun loadSettings() {
        viewModelScope.launch {
            val prefs = applicationContext.dataStore.data.firstOrNull()
            if (prefs == null) {
                addLog("Preferences unavailable, defaulting to New York")
                onLocationSelected(defaultLocation, isManualSearch = false)
                return@launch
            }

            val favs = prefs[favoritesKey]?.let {
                try { json.decodeFromString<List<LocationItem>>(it) } catch (e: Exception) { emptyList() }
            } ?: emptyList()
            
            val searched = prefs[searchedLocationKey]?.let {
                try { json.decodeFromString<LocationItem>(it) } catch (e: Exception) { null }
            }

            val live = prefs[liveLocationKey]?.let {
                try { json.decodeFromString<LocationItem>(it) } catch (e: Exception) { null }
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
                    sheetBlurStrength = prefs[sheetBlurKey] ?: 30f,
                    sheetDistortion = prefs[sheetDistortionKey] ?: 0.2f,
                    searchBarTintAlpha = prefs[searchBarTintKey] ?: 0.15f,
                    obfuscationMode = prefs[obfuscationModeKey]?.let { ObfuscationMode.valueOf(it) } ?: ObfuscationMode.PRECISE,
                    gridKm = prefs[gridKmKey] ?: 5.0f,
                    experimentalEnabled = prefs[experimentalKey] ?: false
                )
            }
            
            // Pre-cache tiles for favorites
            preCacheLocations(favs + listOfNotNull(searched, live))

            if (_uiState.value.isFollowMode) {
                startFollowMode(applicationContext)
            }

            prefs[lastLocationKey]?.let { jsonStr ->
                try {
                    val location = json.decodeFromString<LocationItem>(jsonStr)
                    lastLocation = location
                    _uiState.update { it.copy(selectedLocation = location) }
                    if (!_uiState.value.isFollowMode) {
                        fetchWeather(location)
                    }
                } catch (e: Exception) {
                    addLog("Load failed: ${e.message}")
                }
            }

            if (_uiState.value.selectedLocation == null) {
                addLog("No saved location found, defaulting to New York")
                onLocationSelected(defaultLocation, isManualSearch = false)
            }
            
        }
    }

    private fun preCacheLocations(locations: List<LocationItem>) {
        if (locations.isEmpty()) return
        viewModelScope.launch {
            try {
                // Initialize provider and MapView on Main
                val cacheManager = withContext(Dispatchers.Main) { 
                    val mapView = MapView(applicationContext)
                    CacheManager(mapView) 
                }
                
                withContext(Dispatchers.IO) {
                    locations.distinctBy { "${it.lat},${it.lon}" }.forEach { loc ->
                        addLog("Pre-caching tiles for: ${loc.name}")
                        val bb = BoundingBox(loc.lat + 0.08, loc.lon + 0.08, loc.lat - 0.08, loc.lon - 0.08)
                        // Download zoom levels 13 to 17 for a good range of detail
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
                delay(5000)
                val now = System.currentTimeMillis()
                val selectedLoc = _uiState.value.selectedLocation
                val staleThreshold = 15 * 60000L // 15 minutes
                val stalenessError = "Weather data is more than 15 minutes old"

                if (selectedLoc != null) {
                    val key = "${selectedLoc.lat},${selectedLoc.lon}"
                    val lastUpdate = _uiState.value.updateTimeMap[key] ?: 0L
                    val isStale = lastUpdate == 0L || (now - lastUpdate > staleThreshold)
                    
                    if (isStale) {
                        addError(stalenessError)
                    } else {
                        removeError(stalenessError)
                    }
                } else {
                    removeError(stalenessError)
                }
            }
        }
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

    fun setTempUnit(unit: TempUnit) {
        _uiState.update { it.copy(tempUnit = unit) }
        viewModelScope.launch {
            applicationContext.dataStore.edit { settings ->
                settings[tempUnitKey] = unit.name
            }
        }
        // Force refresh for unit change
        _uiState.value.selectedLocation?.let { fetchWeather(it, force = true) }
    }

    fun setPressureUnit(unit: PressureUnit) {
        _uiState.update { it.copy(pressureUnit = unit) }
        viewModelScope.launch {
            applicationContext.dataStore.edit { settings ->
                settings[pressureUnitKey] = unit.name
            }
        }
        // No network fetch needed for unit conversion, handled in UI
    }

    fun toggleCustomValues(enabled: Boolean) {
        val defaultZoom = 16f
        val defaultBlur = 6f
        val defaultTint = 0.1f
        val defaultTextAlpha = 0.8f
        val defaultSheetBlur = 30f
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
        addLog("Obfuscation mode set to: $mode")
        _uiState.update { it.copy(obfuscationMode = mode) }
        viewModelScope.launch {
            applicationContext.dataStore.edit { it[obfuscationModeKey] = mode.name }
        }
        _uiState.value.selectedLocation?.let { fetchWeather(it, force = true) }
    }

    fun setGridKm(km: Float) {
        addLog("Grid size set to: $km km")
        _uiState.update { it.copy(gridKm = km) }
        viewModelScope.launch {
            applicationContext.dataStore.edit { it[gridKmKey] = km }
        }
        _uiState.value.selectedLocation?.let { fetchWeather(it, force = true) }
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
        
        _uiState.update { it.copy(
            isFollowMode = enabled, 
            pageStatuses = emptyMap(),
            selectedLocation = if (enabled) it.lastLiveLocation ?: it.selectedLocation else it.selectedLocation
        ) }
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
        val isConnected = actNw != null && (actNw.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) || actNw.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR))
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
                openMeteoService.getWeather(51.5, -0.1)
                addLog("API: OpenMeteo OK")
            } catch (e: Exception) {
                addLog("API: OpenMeteo FAILED (${e.message})")
            }
        }
    }

    @SuppressLint("MissingPermission")
    fun getCurrentLocation(context: Context, isSilent: Boolean = false) {
        if (locationJob?.isActive == true) return
        
        if (!isSilent) _uiState.update { it.copy(isLocating = true) }
        addLog("Starting location request...")

        locationJob = viewModelScope.launch {
            try {
                val cts = CancellationTokenSource()
                val timeoutJob = launch {
                    delay(15000)
                    cts.cancel()
                    if (!isSilent) _uiState.update { it.copy(isLocating = false) }
                    addLog("Location request timed out")
                }

                val location = try {
                    fusedLocationClient.getCurrentLocation(
                        Priority.PRIORITY_HIGH_ACCURACY,
                        cts.token
                    ).await()
                } catch (e: Exception) {
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
                        } catch (e: Exception) {
                            "Current Location"
                        }
                    }
                    
                    val liveLoc = LocationItem(name, location.latitude, location.longitude)
                    _uiState.update { it.copy(locationFound = true, lastLiveLocation = liveLoc) }
                    saveLiveLocation(liveLoc)
                    onLocationSelected(liveLoc, isManualSearch = false)
                } else {
                    addLog("Location returned null or cancelled")
                    // Don't set locationFound to false if we have a lastLiveLocation
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
            // Search cancels follow mode
            if (_uiState.value.isFollowMode) {
                _uiState.update { it.copy(isFollowMode = false) }
                saveFollowMode(false)
                followModeJob?.cancel()
                addLog("Follow mode disabled by search")
            }
        }

        searchJob?.cancel()
        if (query.isNotBlank()) {
            searchJob = viewModelScope.launch {
                delay(500)
                try {
                    val response = nominatimService.search(query)
                    _uiState.update { state ->
                        state.copy(suggestions = response.map { res ->
                            val isFav = state.favorites.any { it.name == res.displayName }
                            LocationItem(res.displayName, res.lat.toDouble(), res.lon.toDouble(), isFavorite = isFav) 
                        })
                    }
                } catch (e: Exception) {
                    addLog("Search Error: ${e.message}")
                }
            }
        } else {
            _uiState.update { it.copy(suggestions = emptyList()) }
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
        
        // Dump pending requests when switching location
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
            if (newSearched != state.searchedLocation) saveSearchedLocation(newSearched)

            val newSuggestions = state.suggestions.map { if (it.name == location.name) updated else it }
            val newSelected = if (state.selectedLocation?.name == location.name) updated else state.selectedLocation
            
            state.copy(
                favorites = newFavorites, 
                suggestions = newSuggestions, 
                selectedLocation = newSelected,
                searchedLocation = newSearched
            )
        }
    }

    private fun fetchWeather(location: LocationItem, force: Boolean = false) {
        if (_uiState.value.isFollowMode && !_uiState.value.locationFound && location == _uiState.value.selectedLocation && _uiState.value.lastLiveLocation == null) {
            return 
        }
        
        val now = System.currentTimeMillis()
        val key = "${location.lat},${location.lon}"
        
        // Use obfuscated coordinates for the request
        val calendar = Calendar.getInstance()
        val seedStr = "${calendar.get(Calendar.YEAR)}-${calendar.get(Calendar.DAY_OF_YEAR)}"
        val seed = seedStr.hashCode().toLong()
        
        val obfuscated = obfuscateLocation(
            location.lat, 
            location.lon, 
            _uiState.value.obfuscationMode, 
            _uiState.value.gridKm.toDouble(),
            seed = seed
        )
        
        val isFetchTooRecent = now - lastFetchTime < 2000
        val lastUpdate = _uiState.value.updateTimeMap[key] ?: 0L
        val isDataFresh = _uiState.value.weatherMap[key] != null && (now - lastUpdate <= 15 * 60000L) // 15 minutes
        
        if (!force && isDataFresh && isFetchTooRecent) {
            addLog("Skipping fetch for ${location.name}, data is fresh or fetch too recent.")
            return
        }
        
        lastFetchTime = now
        _uiState.update { it.copy(isLoading = true) }
        
        weatherJob?.cancel()
        weatherJob = viewModelScope.launch {
            addLog("Requesting weather from OpenMeteo: Lat=${obfuscated.latObf}, Lon=${obfuscated.lonObf} (Original: ${location.lat}, ${location.lon}, Mode: ${_uiState.value.obfuscationMode})")
            try {
                val weather = openMeteoService.getWeather(obfuscated.latObf, obfuscated.lonObf)
                _uiState.update { it.copy(
                    weatherMap = it.weatherMap + (key to weather),
                    updateTimeMap = it.updateTimeMap + (key to System.currentTimeMillis()),
                    isLoading = false,
                    pageStatuses = it.pageStatuses + (key to true),
                    lastResponseCoords = obfuscated.latObf to obfuscated.lonObf,
                    responseAnimTrigger = System.currentTimeMillis()
                ) }
                _uiState.update { state ->
                    state.copy(errors = state.errors.filter { it != "Weather data is more than 15 minutes old" })
                }
                addLog("Weather successfully updated for ${location.name}. Wind Dir: ${weather.currentWeather?.windDirection}")
            } catch (e: Exception) {
                _uiState.update { it.copy(
                    isLoading = false,
                    pageStatuses = it.pageStatuses + (key to false)
                ) }
                addLog("Weather fetch FAILED for ${location.name}: ${e.message}")
            }
        }
    }

    private fun startAutoUpdate() {
        autoUpdateJob = viewModelScope.launch {
            while (true) {
                delay(10000)
                _uiState.value.selectedLocation?.let { fetchWeather(it) }
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
