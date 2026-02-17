package com.tudorc.anemoi.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.RemoteViews
import androidx.core.graphics.toColorInt
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.stringPreferencesKey
import com.tudorc.anemoi.MainActivity
import com.tudorc.anemoi.R
import com.tudorc.anemoi.data.LocationItem
import com.tudorc.anemoi.data.TempUnit
import com.tudorc.anemoi.data.WeatherResponse
import com.tudorc.anemoi.util.dataStore
import com.tudorc.anemoi.util.formatTemp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

class WeatherWidgetProvider : AppWidgetProvider() {
    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        WidgetRefreshScheduler.ensureScheduled(context.applicationContext)
    }

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        WidgetRefreshScheduler.ensureScheduled(context.applicationContext)
        scope.launch {
            updateWidgets(
                context = context.applicationContext,
                appWidgetManager = appWidgetManager,
                appWidgetIds = appWidgetIds
            )
        }
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle
    ) {
        super.onAppWidgetOptionsChanged(context, appWidgetManager, appWidgetId, newOptions)
        WidgetRefreshScheduler.ensureScheduled(context.applicationContext)
        scope.launch {
            updateWidgets(
                context = context.applicationContext,
                appWidgetManager = appWidgetManager,
                appWidgetIds = intArrayOf(appWidgetId)
            )
        }
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        super.onDeleted(context, appWidgetIds)
        appWidgetIds.forEach { appWidgetId ->
            WidgetLocationStore.removeLocation(context, appWidgetId)
        }
        WidgetRefreshScheduler.ensureScheduled(context.applicationContext)
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        WidgetRefreshScheduler.cancel(context.applicationContext)
    }

    companion object {
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        private val widgetIconTintColor = "#D6D9DE".toColorInt()
        private const val updateDebounceMs = 350L
        private val updateLock = Any()
        private var pendingUpdateJob: Job? = null

        fun requestUpdate(context: Context) {
            val appContext = context.applicationContext
            WidgetRefreshScheduler.ensureScheduled(appContext)
            synchronized(updateLock) {
                pendingUpdateJob?.cancel()
                pendingUpdateJob = scope.launch {
                    delay(updateDebounceMs)
                    updateAllWidgets(appContext)
                }
            }
        }

        private suspend fun updateAllWidgets(context: Context) {
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val component = ComponentName(context, WeatherWidgetProvider::class.java)
            val appWidgetIds = appWidgetManager.getAppWidgetIds(component)
            if (appWidgetIds.isEmpty()) {
                return
            }
            updateWidgets(context, appWidgetManager, appWidgetIds)
        }

        private suspend fun updateWidgets(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetIds: IntArray
        ) {
            if (appWidgetIds.isEmpty()) {
                return
            }

            val snapshotReader = WeatherWidgetSnapshotReader(context)
            appWidgetIds.forEach { appWidgetId ->
                val snapshot = snapshotReader.read(appWidgetId)
                val widthTier = resolveWidthTier(appWidgetManager, appWidgetId)
                appWidgetManager.updateAppWidget(
                    appWidgetId,
                    buildRemoteViews(context, snapshot, widthTier, appWidgetId)
                )
            }
        }

        private fun resolveWidthTier(
            appWidgetManager: AppWidgetManager,
            appWidgetId: Int
        ): WidgetWidthTier {
            val options = runCatching {
                appWidgetManager.getAppWidgetOptions(appWidgetId)
            }.getOrDefault(Bundle())

            val minWidthDp = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 110)
            val columns = ((minWidthDp + 30) / 70).coerceAtLeast(2)

            return when {
                columns <= 2 -> WidgetWidthTier.TWO_BY_ONE
                columns == 3 -> WidgetWidthTier.THREE_BY_ONE
                columns == 4 -> WidgetWidthTier.FOUR_BY_ONE
                else -> WidgetWidthTier.FIVE_PLUS_BY_ONE
            }
        }

        private fun buildRemoteViews(
            context: Context,
            snapshot: WeatherWidgetSnapshot,
            widthTier: WidgetWidthTier,
            appWidgetId: Int
        ): RemoteViews {
            val launchIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP
                putExtra(
                    MainActivity.EXTRA_WIDGET_LOCATION_IS_CURRENT,
                    snapshot.launchAsCurrentLocationPage
                )

                snapshot.launchLat?.let { lat ->
                    putExtra(MainActivity.EXTRA_WIDGET_LOCATION_LAT, lat)
                }
                snapshot.launchLon?.let { lon ->
                    putExtra(MainActivity.EXTRA_WIDGET_LOCATION_LON, lon)
                }
            }
            val launchPendingIntent = PendingIntent.getActivity(
                context,
                appWidgetId,
                launchIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            return RemoteViews(context.packageName, R.layout.widget_weather).apply {
                setTextViewText(R.id.widget_location, snapshot.locationName)
                setTextViewText(
                    R.id.widget_updated_time,
                    when (widthTier) {
                        WidgetWidthTier.TWO_BY_ONE,
                        WidgetWidthTier.THREE_BY_ONE -> snapshot.updatedShortText
                        WidgetWidthTier.FOUR_BY_ONE,
                        WidgetWidthTier.FIVE_PLUS_BY_ONE -> snapshot.updatedLongText
                    }
                )

                setImageViewResource(R.id.widget_two_current_icon, snapshot.currentIconRes)
                setTextViewText(R.id.widget_two_temperature, snapshot.currentTempText)
                setTextViewText(R.id.widget_two_feels_like, snapshot.feelsLikeText)
                setImageViewResource(R.id.widget_rich_current_icon, snapshot.currentIconRes)
                setTextViewText(R.id.widget_rich_temperature, snapshot.currentTempText)
                setTextViewText(R.id.widget_rich_feels_like, snapshot.feelsLikeText)
                applyIconTint(this, R.id.widget_two_current_icon, R.id.widget_rich_current_icon)

                setTextViewText(R.id.widget_high, snapshot.highText)
                setTextViewText(R.id.widget_low, snapshot.lowText)

                bindHourlyColumn(
                    this,
                    timeViewId = R.id.widget_hour_1_time,
                    iconViewId = R.id.widget_hour_1_icon,
                    tempViewId = R.id.widget_hour_1_temp,
                    slot = snapshot.hourlySlots[0]
                )
                bindHourlyColumn(
                    this,
                    timeViewId = R.id.widget_hour_2_time,
                    iconViewId = R.id.widget_hour_2_icon,
                    tempViewId = R.id.widget_hour_2_temp,
                    slot = snapshot.hourlySlots[1]
                )
                bindHourlyColumn(
                    this,
                    timeViewId = R.id.widget_hour_3_time,
                    iconViewId = R.id.widget_hour_3_icon,
                    tempViewId = R.id.widget_hour_3_temp,
                    slot = snapshot.hourlySlots[2]
                )

                when (widthTier) {
                    WidgetWidthTier.TWO_BY_ONE -> {
                        // Keep 2x1 visually identical to the current 3x1 layout.
                        setViewPadding(R.id.widget_top_row, 0, 0, 0, 0)
                        setViewVisibility(R.id.widget_row_two, View.GONE)
                        setViewVisibility(R.id.widget_row_rich, View.VISIBLE)
                        setViewVisibility(R.id.widget_hl_column, View.VISIBLE)
                        setViewVisibility(R.id.widget_rich_right_side, View.GONE)
                        setViewVisibility(R.id.widget_hourly_container, View.GONE)
                        setInt(R.id.widget_rich_left_side, "setGravity", Gravity.CENTER_VERTICAL or Gravity.START)

                        setTextViewTextSize(R.id.widget_rich_temperature, TypedValue.COMPLEX_UNIT_SP, 28f)
                    }

                    WidgetWidthTier.THREE_BY_ONE -> {
                        setViewPadding(R.id.widget_top_row, 0, 0, dpToPx(context, 35f), 0)
                        setViewVisibility(R.id.widget_row_two, View.GONE)
                        setViewVisibility(R.id.widget_row_rich, View.VISIBLE)
                        setViewVisibility(R.id.widget_hl_column, View.VISIBLE)
                        setViewVisibility(R.id.widget_rich_right_side, View.GONE)
                        setViewVisibility(R.id.widget_hourly_container, View.GONE)
                        setInt(R.id.widget_rich_left_side, "setGravity", Gravity.CENTER)

                        setTextViewTextSize(R.id.widget_rich_temperature, TypedValue.COMPLEX_UNIT_SP, 28f)
                    }

                    WidgetWidthTier.FOUR_BY_ONE -> {
                        setViewPadding(R.id.widget_top_row, 0, 0, 0, 0)
                        setViewVisibility(R.id.widget_row_two, View.GONE)
                        setViewVisibility(R.id.widget_row_rich, View.VISIBLE)
                        setViewVisibility(R.id.widget_hl_column, View.VISIBLE)
                        setViewVisibility(R.id.widget_rich_right_side, View.VISIBLE)
                        setViewVisibility(R.id.widget_hourly_container, View.VISIBLE)
                        setInt(R.id.widget_rich_left_side, "setGravity", Gravity.CENTER_VERTICAL or Gravity.START)

                        setTextViewTextSize(R.id.widget_rich_temperature, TypedValue.COMPLEX_UNIT_SP, 26f)
                    }

                    WidgetWidthTier.FIVE_PLUS_BY_ONE -> {
                        setViewPadding(R.id.widget_top_row, 0, 0, 0, 0)
                        setViewVisibility(R.id.widget_row_two, View.GONE)
                        setViewVisibility(R.id.widget_row_rich, View.VISIBLE)
                        setViewVisibility(R.id.widget_hl_column, View.VISIBLE)
                        setViewVisibility(R.id.widget_rich_right_side, View.VISIBLE)
                        setViewVisibility(R.id.widget_hourly_container, View.VISIBLE)
                        setInt(R.id.widget_rich_left_side, "setGravity", Gravity.CENTER_VERTICAL or Gravity.START)

                        setTextViewTextSize(R.id.widget_rich_temperature, TypedValue.COMPLEX_UNIT_SP, 26f)
                    }
                }

                setOnClickPendingIntent(R.id.widget_root, launchPendingIntent)
            }
        }

        private fun bindHourlyColumn(
            views: RemoteViews,
            timeViewId: Int,
            iconViewId: Int,
            tempViewId: Int,
            slot: WidgetHourlySlot
        ) {
            views.setTextViewText(timeViewId, slot.timeLabel)
            views.setTextViewText(tempViewId, slot.tempText)

            if (slot.iconRes != null) {
                views.setImageViewResource(iconViewId, slot.iconRes)
                views.setViewVisibility(iconViewId, View.VISIBLE)
            } else {
                views.setViewVisibility(iconViewId, View.INVISIBLE)
            }

            applyIconTint(views, iconViewId)
        }

        private fun applyIconTint(views: RemoteViews, vararg iconViewIds: Int) {
            iconViewIds.forEach { viewId ->
                views.setInt(viewId, "setColorFilter", widgetIconTintColor)
            }
        }

        private fun dpToPx(context: Context, dp: Float): Int {
            return (dp * context.resources.displayMetrics.density).roundToInt()
        }
    }
}

private enum class WidgetWidthTier {
    TWO_BY_ONE,
    THREE_BY_ONE,
    FOUR_BY_ONE,
    FIVE_PLUS_BY_ONE
}

private data class WidgetHourlySlot(
    val timeLabel: String,
    val tempText: String,
    val iconRes: Int?
)

private data class WeatherWidgetSnapshot(
    val locationName: String,
    val updatedShortText: String,
    val updatedLongText: String,
    val currentTempText: String,
    val currentIconRes: Int,
    val feelsLikeText: String,
    val highText: String,
    val lowText: String,
    val hourlySlots: List<WidgetHourlySlot>,
    val launchAsCurrentLocationPage: Boolean,
    val launchLat: Double?,
    val launchLon: Double?
)

private class WeatherWidgetSnapshotReader(private val context: Context) {
    private val json = Json { ignoreUnknownKeys = true }
    private var hasLoadedPrefs = false
    private var cachedPrefs: Preferences? = null
    private var cachedEncodedState: String? = null
    private var cachedWeatherByLocationKey: Map<String, PersistedWeatherEntrySnapshot> = emptyMap()

    private val lastLocationKey = stringPreferencesKey("last_location")
    private val liveLocationKey = stringPreferencesKey("live_location")
    private val tempUnitKey = stringPreferencesKey("temp_unit")
    private val persistedCacheKey = stringPreferencesKey("persisted_cache_v2")

    suspend fun read(appWidgetId: Int): WeatherWidgetSnapshot {
        val prefs = loadPrefs() ?: return placeholderSnapshot()

        val selection = WidgetLocationStore.loadSelection(context, appWidgetId)
        val selectedLocation = when (selection) {
            is WidgetLocationSelection.FixedLocation -> selection.location
            WidgetLocationSelection.CurrentLocation -> {
                prefs[liveLocationKey]?.let(::decodeLocation)
                    ?: prefs[lastLocationKey]?.let(::decodeLocation)
            }

            null -> prefs[lastLocationKey]?.let(::decodeLocation)
        }

        val locationName = selectedLocation
            ?.displayName
            ?.takeIf { it.isNotBlank() }
            ?: when (selection) {
                WidgetLocationSelection.CurrentLocation -> context.getString(R.string.widget_current_location_option)
                else -> context.getString(R.string.widget_location_placeholder)
            }

        val unit = prefs[tempUnitKey].toTempUnitOrDefault()
        val weatherEntry = selectedLocation?.let { location ->
            resolveWeatherEntryForLocation(prefs, location)
        }
        val weather = weatherEntry?.weather
        val updatedShortText = buildUpdatedShortText(weatherEntry?.latestUpdatedAtMs() ?: 0L)
        val updatedLongText = buildUpdatedLongText(updatedShortText)

        val currentTempText = weather
            ?.currentWeather
            ?.temperature
            ?.let { formatTemp(it, unit) }
            ?: context.getString(R.string.widget_temp_placeholder)

        val currentIconRes = weatherIconRes(weather?.currentWeather?.weatherCode) ?: R.drawable.wmo_0

        val feelsLikeText = buildFeelsLikeText(weather, unit)

        val highText = weather
            ?.daily
            ?.maxTemp
            ?.firstOrNull()
            ?.let { "H: ${formatTemp(it, unit)}" }
            ?: "H: --°"

        val lowText = weather
            ?.daily
            ?.minTemp
            ?.firstOrNull()
            ?.let { "L: ${formatTemp(it, unit)}" }
            ?: "L: --°"

        val hourlySlots = buildHourlySlots(weather, unit)

        return WeatherWidgetSnapshot(
            locationName = locationName,
            updatedShortText = updatedShortText,
            updatedLongText = updatedLongText,
            currentTempText = currentTempText,
            currentIconRes = currentIconRes,
            feelsLikeText = feelsLikeText,
            highText = highText,
            lowText = lowText,
            hourlySlots = hourlySlots,
            launchAsCurrentLocationPage = selection == WidgetLocationSelection.CurrentLocation,
            launchLat = selectedLocation?.lat,
            launchLon = selectedLocation?.lon
        )
    }

    private fun placeholderSnapshot(): WeatherWidgetSnapshot {
        return WeatherWidgetSnapshot(
            locationName = context.getString(R.string.widget_location_placeholder),
            updatedShortText = context.getString(R.string.widget_updated_placeholder),
            updatedLongText = context.getString(R.string.widget_updated_placeholder),
            currentTempText = context.getString(R.string.widget_temp_placeholder),
            currentIconRes = R.drawable.wmo_0,
            feelsLikeText = context.getString(R.string.widget_feels_like_placeholder),
            highText = "H: --°",
            lowText = "L: --°",
            hourlySlots = listOf(
                WidgetHourlySlot("--:--", "--°", null),
                WidgetHourlySlot("--:--", "--°", null),
                WidgetHourlySlot("--:--", "--°", null)
            ),
            launchAsCurrentLocationPage = false,
            launchLat = null,
            launchLon = null
        )
    }

    private fun decodeLocation(encodedLocation: String): LocationItem? {
        return runCatching {
            json.decodeFromString<LocationItem>(encodedLocation)
        }.getOrNull()
    }

    private fun resolveWeatherEntryForLocation(
        prefs: Preferences,
        location: LocationItem
    ): PersistedWeatherEntrySnapshot? {
        val encodedState = prefs[persistedCacheKey] ?: return null
        ensurePersistedStateLoaded(encodedState)

        val key = "${location.lat},${location.lon}"
        return cachedWeatherByLocationKey[key]
    }

    private suspend fun loadPrefs(): Preferences? {
        if (!hasLoadedPrefs) {
            cachedPrefs = runCatching {
                context.dataStore.data.firstOrNull()
            }.getOrNull()
            hasLoadedPrefs = true
        }
        return cachedPrefs
    }

    private fun ensurePersistedStateLoaded(encodedState: String) {
        if (cachedEncodedState == encodedState) {
            return
        }

        cachedEncodedState = encodedState
        val persistedState = runCatching {
            json.decodeFromString<PersistedWeatherStateSnapshot>(encodedState)
        }.getOrNull()

        cachedWeatherByLocationKey = persistedState
            ?.weatherEntries
            ?.associateBy { it.key }
            .orEmpty()
    }

    private fun buildUpdatedShortText(updatedAtMs: Long): String {
        if (updatedAtMs <= 0L) {
            return context.getString(R.string.widget_updated_placeholder)
        }

        val elapsedMs = (System.currentTimeMillis() - updatedAtMs).coerceAtLeast(0L)
        if (elapsedMs < 60_000L) {
            return context.getString(R.string.widget_updated_now_short)
        }

        val elapsedMinutes = elapsedMs / 60_000L
        return when {
            elapsedMinutes < 60L -> "${elapsedMinutes}m"
            elapsedMinutes < 1_440L -> "${elapsedMinutes / 60L}h"
            else -> "${elapsedMinutes / 1_440L}d"
        }
    }

    private fun buildUpdatedLongText(updatedShortText: String): String {
        return when (updatedShortText) {
            context.getString(R.string.widget_updated_placeholder) -> {
                context.getString(R.string.widget_updated_placeholder)
            }

            context.getString(R.string.widget_updated_now_short) -> {
                context.getString(R.string.widget_updated_now_long)
            }

            else -> context.getString(R.string.widget_updated_long_format, updatedShortText)
        }
    }

    private fun buildFeelsLikeText(weather: WeatherResponse?, unit: TempUnit): String {
        val feelsLike = weather
            ?.hourly
            ?.apparentTemperatures
            ?.firstOrNull()
            ?: weather?.currentWeather?.temperature

        return feelsLike
            ?.let { context.getString(R.string.widget_feels_like_format, formatTemp(it, unit)) }
            ?: context.getString(R.string.widget_feels_like_placeholder)
    }

    private fun buildHourlySlots(weather: WeatherResponse?, unit: TempUnit): List<WidgetHourlySlot> {
        val hourly = weather?.hourly

        val baseTime = weather
            ?.currentWeather
            ?.time
            ?.let(::parseWeatherTime)
            ?: Date()

        val hourPrefixFormatter = SimpleDateFormat("yyyy-MM-dd'T'HH", Locale.US)
        val hourLabelFormatter = SimpleDateFormat("HH", Locale.US)

        val cursor = Calendar.getInstance().apply {
            time = baseTime
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        return buildList {
            repeat(3) {
                cursor.add(Calendar.HOUR_OF_DAY, 1)
                val targetPrefix = hourPrefixFormatter.format(cursor.time)
                val hourLabel = "${hourLabelFormatter.format(cursor.time)}:00"

                val index = hourly?.time?.indexOfFirst { time ->
                    time.startsWith(targetPrefix)
                } ?: -1

                val temp = if (index >= 0) hourly?.temperatures?.getOrNull(index) else null
                val weatherCode = if (index >= 0) hourly?.weatherCodes?.getOrNull(index) else null

                add(
                    WidgetHourlySlot(
                        timeLabel = hourLabel,
                        tempText = temp?.let { formatTemp(it, unit) } ?: "--°",
                        iconRes = weatherIconRes(weatherCode)
                    )
                )
            }
        }
    }

    private fun parseWeatherTime(value: String): Date? {
        val parser = SimpleDateFormat("yyyy-MM-dd'T'HH:mm", Locale.US).apply {
            isLenient = false
        }
        return runCatching { parser.parse(value) }.getOrNull()
    }

    private fun weatherIconRes(code: Int?): Int? {
        if (code == null) return null
        @Suppress("DiscouragedApi")
        val resourceId = context.resources.getIdentifier("wmo_$code", "drawable", context.packageName)
        return resourceId.takeIf { it != 0 }
    }

    private fun String?.toTempUnitOrDefault(): TempUnit {
        if (this == null) {
            return TempUnit.CELSIUS
        }
        return runCatching {
            TempUnit.valueOf(this)
        }.getOrDefault(TempUnit.CELSIUS)
    }
}

@Serializable
private data class PersistedWeatherStateSnapshot(
    val weatherEntries: List<PersistedWeatherEntrySnapshot> = emptyList()
)

@Serializable
private data class PersistedWeatherEntrySnapshot(
    val key: String,
    val weather: WeatherResponse,
    val currentUpdatedAt: Long = 0L,
    val hourlyUpdatedAt: Long = 0L,
    val dailyUpdatedAt: Long = 0L,
    val signature: String = ""
)

private fun PersistedWeatherEntrySnapshot.latestUpdatedAtMs(): Long {
    return listOf(currentUpdatedAt, hourlyUpdatedAt, dailyUpdatedAt).maxOrNull() ?: 0L
}
