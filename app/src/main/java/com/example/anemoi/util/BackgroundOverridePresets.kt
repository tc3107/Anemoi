package com.example.anemoi.util

import java.util.Locale

data class BackgroundOverridePreset(
    val label: String,
    val weatherCode: Int,
    val hourOfDay: Int
)

val backgroundOverridePresets: List<BackgroundOverridePreset> = listOf(
    BackgroundOverridePreset(label = "Sunny Day", weatherCode = 0, hourOfDay = 12),
    BackgroundOverridePreset(label = "Cloudy Day", weatherCode = 3, hourOfDay = 12),
    BackgroundOverridePreset(label = "Rain Day", weatherCode = 63, hourOfDay = 12),
    BackgroundOverridePreset(label = "Snow Day", weatherCode = 75, hourOfDay = 12),
    BackgroundOverridePreset(label = "Storm Day", weatherCode = 95, hourOfDay = 12),
    BackgroundOverridePreset(label = "Clear Night", weatherCode = 0, hourOfDay = 23),
    BackgroundOverridePreset(label = "Rain Night", weatherCode = 63, hourOfDay = 23),
    BackgroundOverridePreset(label = "Snow Night", weatherCode = 75, hourOfDay = 23),
    BackgroundOverridePreset(label = "Storm Night", weatherCode = 95, hourOfDay = 23)
)

fun backgroundOverridePresetAt(index: Int): BackgroundOverridePreset {
    if (backgroundOverridePresets.isEmpty()) {
        return BackgroundOverridePreset(label = "Fallback", weatherCode = 0, hourOfDay = 12)
    }
    return backgroundOverridePresets[index.coerceIn(0, backgroundOverridePresets.lastIndex)]
}

fun backgroundOverrideTimeIso(hourOfDay: Int): String {
    val clampedHour = hourOfDay.coerceIn(0, 23)
    return String.format(Locale.US, "2026-01-01T%02d:00", clampedHour)
}
