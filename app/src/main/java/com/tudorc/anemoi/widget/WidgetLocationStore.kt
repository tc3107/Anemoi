package com.tudorc.anemoi.widget

import android.content.Context
import com.tudorc.anemoi.data.LocationItem
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

sealed interface WidgetLocationSelection {
    data object CurrentLocation : WidgetLocationSelection
    data class FixedLocation(val location: LocationItem) : WidgetLocationSelection
}

object WidgetLocationStore {
    private const val PREFS_NAME = "weather_widget_prefs"
    private const val LOCATION_PREFIX = "widget_location_"
    private const val CURRENT_LOCATION_SENTINEL = "__CURRENT_LOCATION__"
    private val json = Json { ignoreUnknownKeys = true }

    fun saveLocation(context: Context, appWidgetId: Int, location: LocationItem) {
        val encoded = runCatching { json.encodeToString(location) }.getOrNull() ?: return
        context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString("$LOCATION_PREFIX$appWidgetId", encoded)
            .apply()
    }

    fun saveCurrentLocationSelection(context: Context, appWidgetId: Int) {
        context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString("$LOCATION_PREFIX$appWidgetId", CURRENT_LOCATION_SENTINEL)
            .apply()
    }

    fun loadSelection(context: Context, appWidgetId: Int): WidgetLocationSelection? {
        val encoded = context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString("$LOCATION_PREFIX$appWidgetId", null)
            ?: return null

        if (encoded == CURRENT_LOCATION_SENTINEL) {
            return WidgetLocationSelection.CurrentLocation
        }

        return runCatching {
            WidgetLocationSelection.FixedLocation(json.decodeFromString<LocationItem>(encoded))
        }.getOrNull()
    }

    fun removeLocation(context: Context, appWidgetId: Int) {
        context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .remove("$LOCATION_PREFIX$appWidgetId")
            .apply()
    }

    fun updateCustomNameForLocation(
        context: Context,
        targetLocation: LocationItem,
        customName: String?
    ) {
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val updates = prefs.all
            .asSequence()
            .filter { (key, _) -> key.startsWith(LOCATION_PREFIX) }
            .mapNotNull { (key, value) ->
                val encoded = value as? String ?: return@mapNotNull null
                if (encoded == CURRENT_LOCATION_SENTINEL) return@mapNotNull null

                val location = runCatching {
                    json.decodeFromString<LocationItem>(encoded)
                }.getOrNull() ?: return@mapNotNull null

                if (!isSameLocation(location, targetLocation)) {
                    return@mapNotNull null
                }

                val updated = location.copy(customName = customName)
                key to runCatching { json.encodeToString(updated) }.getOrNull()
            }
            .filter { (_, encoded) -> encoded != null }
            .toList()

        if (updates.isEmpty()) return

        val editor = prefs.edit()
        updates.forEach { (key, encoded) ->
            editor.putString(key, encoded)
        }
        editor.apply()
    }

    private fun isSameLocation(a: LocationItem, b: LocationItem): Boolean {
        return a.lat == b.lat && a.lon == b.lon
    }
}
