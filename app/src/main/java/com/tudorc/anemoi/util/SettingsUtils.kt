package com.tudorc.anemoi.util

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import com.tudorc.anemoi.data.TempUnit

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

fun formatTemp(celsius: Double, unit: TempUnit): String {
    return when (unit) {
        TempUnit.CELSIUS -> "${celsius.toInt()}°"
        TempUnit.FAHRENHEIT -> "${(celsius * 9 / 5 + 32).toInt()}°"
        TempUnit.KELVIN -> "${(celsius + 273.15).toInt()}K"
    }
}
