package com.example.anemoi.widget

import android.Manifest
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.location.Geocoder
import androidx.core.content.ContextCompat
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.anemoi.data.LocationItem
import com.example.anemoi.data.OpenMeteoService
import com.example.anemoi.data.WeatherResponse
import com.example.anemoi.util.ObfuscationMode
import com.example.anemoi.util.dataStore
import com.example.anemoi.util.obfuscateLocation
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.Calendar
import java.util.Locale
import java.util.concurrent.TimeUnit

class WidgetWeatherRefreshWorker(
    appContext: Context,
    workerParameters: WorkerParameters
) : CoroutineWorker(appContext, workerParameters) {
    private val json = Json { ignoreUnknownKeys = true }
    private val okHttpClient = OkHttpClient.Builder().build()
    private val openMeteoService: OpenMeteoService by lazy {
        Retrofit.Builder()
            .baseUrl("https://api.open-meteo.com/")
            .client(okHttpClient)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(OpenMeteoService::class.java)
    }

    private val lastLocationKey = stringPreferencesKey("last_location")
    private val liveLocationKey = stringPreferencesKey("live_location")
    private val obfuscationModeKey = stringPreferencesKey("obfuscation_mode")
    private val gridKmKey = floatPreferencesKey("grid_km")
    private val persistedCacheKey = stringPreferencesKey("persisted_cache_v2")

    override suspend fun doWork(): Result {
        val context = applicationContext
        if (!WidgetRefreshScheduler.hasWidgets(context)) {
            WidgetRefreshScheduler.cancel(context)
            return Result.success()
        }

        val widgetIds = AppWidgetManager.getInstance(context)
            .getAppWidgetIds(ComponentName(context, WeatherWidgetProvider::class.java))
        val hasCurrentLocationWidget = widgetIds.any { appWidgetId ->
            WidgetLocationStore.loadSelection(context, appWidgetId) == WidgetLocationSelection.CurrentLocation
        }

        val prefs = context.dataStore.data.firstOrNull() ?: return Result.retry()
        val fallbackLastLocation = prefs[lastLocationKey]?.let(::decodeLocation)
        var liveLocation = prefs[liveLocationKey]?.let(::decodeLocation)
        if (hasCurrentLocationWidget) {
            val refreshedLiveLocation = resolveFreshCurrentLocation(context)
            if (refreshedLiveLocation != null && refreshedLiveLocation != liveLocation) {
                liveLocation = refreshedLiveLocation
                saveLiveLocation(context, refreshedLiveLocation)
            }
        }
        val obfuscationMode = prefs[obfuscationModeKey]
            ?.let { mode -> runCatching { ObfuscationMode.valueOf(mode) }.getOrNull() }
            ?: ObfuscationMode.PRECISE
        val gridKm = prefs[gridKmKey] ?: 5.0f
        val signature = buildRequestSignature(
            obfuscationMode = obfuscationMode,
            gridKm = gridKm
        )

        val targetLocations = widgetIds
            .toList()
            .mapNotNull { appWidgetId ->
                when (val selection = WidgetLocationStore.loadSelection(context, appWidgetId)) {
                    is WidgetLocationSelection.FixedLocation -> selection.location
                    WidgetLocationSelection.CurrentLocation -> liveLocation ?: fallbackLastLocation
                    null -> fallbackLastLocation
                }
            }
            .distinctBy(::locationKey)

        if (targetLocations.isEmpty()) {
            WeatherWidgetProvider.requestUpdate(context)
            return Result.success()
        }

        val persistedState = prefs[persistedCacheKey]
            ?.let { encoded ->
                runCatching {
                    json.decodeFromString<PersistedWeatherStateWorker>(encoded)
                }.getOrNull()
            }
            ?: PersistedWeatherStateWorker()
        val entriesByKey = persistedState.weatherEntries.associateBy { it.key }.toMutableMap()

        var successCount = 0
        for (location in targetLocations) {
            val key = locationKey(location)
            val existing = entriesByKey[key]
            val requestCoords = obfuscatedRequestLocation(
                location = location,
                mode = obfuscationMode,
                gridKm = gridKm.toDouble()
            )
            val weather = try {
                openMeteoService.getWeather(
                    lat = requestCoords.first,
                    lon = requestCoords.second,
                    currentWeather = true,
                    hourly = HOURLY_FIELDS,
                    daily = DAILY_FIELDS
                )
            } catch (_: Exception) {
                null
            } ?: continue

            val now = System.currentTimeMillis()
            entriesByKey[key] = PersistedWeatherEntryWorker(
                key = key,
                weather = weather,
                currentUpdatedAt = if (weather.currentWeather != null) now else existing?.currentUpdatedAt ?: 0L,
                hourlyUpdatedAt = if (weather.hourly != null) now else existing?.hourlyUpdatedAt ?: 0L,
                dailyUpdatedAt = if (weather.daily != null) now else existing?.dailyUpdatedAt ?: 0L,
                signature = signature
            )
            successCount += 1
        }

        if (successCount == 0) {
            return Result.retry()
        }

        val updatedState = persistedState.copy(weatherEntries = entriesByKey.values.toList())
        context.dataStore.edit { settings ->
            settings[persistedCacheKey] = json.encodeToString(updatedState)
        }

        WeatherWidgetProvider.requestUpdate(context)
        return Result.success()
    }

    private suspend fun resolveFreshCurrentLocation(context: Context): LocationItem? {
        val hasFinePermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val hasCoarsePermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        if (!hasFinePermission && !hasCoarsePermission) {
            return null
        }

        val fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)
        val priority = if (hasFinePermission) {
            Priority.PRIORITY_HIGH_ACCURACY
        } else {
            Priority.PRIORITY_BALANCED_POWER_ACCURACY
        }

        val location = try {
            val cts = CancellationTokenSource()
            val current = withTimeoutOrNull(12_000L) {
                fusedLocationClient.getCurrentLocation(priority, cts.token).await()
            }
            if (current == null) {
                cts.cancel()
                fusedLocationClient.lastLocation.await()
            } else {
                current
            }
        } catch (_: Exception) {
            null
        } ?: return null

        val displayName = resolveLocationName(
            context = context,
            lat = location.latitude,
            lon = location.longitude
        )

        return LocationItem(
            name = displayName,
            lat = location.latitude,
            lon = location.longitude
        )
    }

    private suspend fun saveLiveLocation(context: Context, location: LocationItem) {
        context.dataStore.edit { settings ->
            settings[liveLocationKey] = json.encodeToString(location)
        }
    }

    private suspend fun resolveLocationName(context: Context, lat: Double, lon: Double): String {
        return withContext(Dispatchers.IO) {
            try {
                val geocoder = Geocoder(context, Locale.getDefault())
                @Suppress("DEPRECATION")
                val addresses = geocoder.getFromLocation(lat, lon, 1)
                addresses?.firstOrNull()?.let {
                    it.locality ?: it.subAdminArea ?: it.adminArea ?: "Current Location"
                } ?: "Current Location"
            } catch (_: Exception) {
                "Current Location"
            }
        }
    }

    private fun decodeLocation(encoded: String): LocationItem? {
        return runCatching {
            json.decodeFromString<LocationItem>(encoded)
        }.getOrNull()
    }

    private fun locationKey(location: LocationItem): String {
        return "${location.lat},${location.lon}"
    }

    private fun buildRequestSignature(obfuscationMode: ObfuscationMode, gridKm: Float): String {
        val localeTag = Locale.getDefault().toLanguageTag()
        val gridString = String.format(Locale.US, "%.3f", gridKm)
        return listOf(
            "tz=auto",
            "locale=$localeTag",
            "hourly=$HOURLY_FIELDS",
            "daily=$DAILY_FIELDS",
            "obf=$obfuscationMode",
            "grid=$gridString"
        ).joinToString("|")
    }

    private fun obfuscatedRequestLocation(
        location: LocationItem,
        mode: ObfuscationMode,
        gridKm: Double
    ): Pair<Double, Double> {
        val calendar = Calendar.getInstance()
        val seed = "${calendar.get(Calendar.YEAR)}-${calendar.get(Calendar.DAY_OF_YEAR)}"
            .hashCode()
            .toLong()
        val obfuscated = obfuscateLocation(
            lat = location.lat,
            lon = location.lon,
            mode = mode,
            gridKm = gridKm,
            seed = seed
        )
        return obfuscated.latObf to obfuscated.lonObf
    }

    companion object {
        private const val HOURLY_FIELDS =
            "temperature_2m,weathercode,apparent_temperature,surface_pressure,precipitation_probability,precipitation,uv_index"
        private const val DAILY_FIELDS =
            "temperature_2m_max,temperature_2m_min,weather_code,precipitation_probability_max,sunrise,sunset,daylight_duration,uv_index_max"
    }
}

object WidgetRefreshScheduler {
    private const val REFRESH_WORK_NAME = "widget_weather_refresh"
    private const val REFRESH_INTERVAL_MINUTES = 20L
    private const val REFRESH_FLEX_MINUTES = 5L

    fun ensureScheduled(context: Context) {
        val appContext = context.applicationContext
        if (!hasWidgets(appContext)) {
            cancel(appContext)
            return
        }

        val workManager = WorkManager.getInstance(appContext)
        val request = PeriodicWorkRequestBuilder<WidgetWeatherRefreshWorker>(
            REFRESH_INTERVAL_MINUTES,
            TimeUnit.MINUTES,
            REFRESH_FLEX_MINUTES,
            TimeUnit.MINUTES
        )
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .build()

        workManager.enqueueUniquePeriodicWork(
            REFRESH_WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            request
        )
    }

    fun cancel(context: Context) {
        WorkManager.getInstance(context.applicationContext)
            .cancelUniqueWork(REFRESH_WORK_NAME)
    }

    fun hasWidgets(context: Context): Boolean {
        val appWidgetIds = AppWidgetManager.getInstance(context.applicationContext)
            .getAppWidgetIds(ComponentName(context.applicationContext, WeatherWidgetProvider::class.java))
        return appWidgetIds.isNotEmpty()
    }
}

@Serializable
private data class PersistedWeatherEntryWorker(
    val key: String,
    val weather: WeatherResponse,
    val currentUpdatedAt: Long = 0L,
    val hourlyUpdatedAt: Long = 0L,
    val dailyUpdatedAt: Long = 0L,
    val signature: String = ""
)

@Serializable
private data class CachedSuggestionWorker(
    val placeId: Long,
    val displayName: String,
    val lat: Double,
    val lon: Double
)

@Serializable
private data class PersistedSearchEntryWorker(
    val query: String,
    val normalizedQuery: String,
    val cachedAt: Long,
    val suggestions: List<CachedSuggestionWorker>
)

@Serializable
private data class PersistedPlaceEntryWorker(
    val placeId: Long,
    val displayName: String,
    val lat: Double,
    val lon: Double,
    val cachedAt: Long
)

@Serializable
private data class PersistedWeatherStateWorker(
    val weatherEntries: List<PersistedWeatherEntryWorker> = emptyList(),
    val searchEntries: List<PersistedSearchEntryWorker> = emptyList(),
    val placeEntries: List<PersistedPlaceEntryWorker> = emptyList()
)
