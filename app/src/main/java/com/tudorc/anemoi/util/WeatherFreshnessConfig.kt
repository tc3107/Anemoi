package com.tudorc.anemoi.util

enum class WeatherDatasetKind {
    CURRENT,
    HOURLY,
    DAILY
}

object WeatherFreshnessConfig {
    const val CURRENT_THRESHOLD_MS: Long = 5 * 60 * 1000L
    const val HOURLY_THRESHOLD_MS: Long = 20 * 60 * 1000L
    const val DAILY_THRESHOLD_MS: Long = 2 * 60 * 60 * 1000L
    const val AIR_QUALITY_THRESHOLD_MS: Long = 60 * 60 * 1000L
    const val STALE_SERVE_WINDOW_MS: Long = 12 * 60 * 60 * 1000L

    const val THRESHOLD_SUMMARY: String = "current 5m, hourly 20m, daily 2h, air quality 1h"
    const val STALE_WINDOW_SUMMARY: String = "up to 12h"

    fun thresholdMs(dataset: WeatherDatasetKind): Long {
        return when (dataset) {
            WeatherDatasetKind.CURRENT -> CURRENT_THRESHOLD_MS
            WeatherDatasetKind.HOURLY -> HOURLY_THRESHOLD_MS
            WeatherDatasetKind.DAILY -> DAILY_THRESHOLD_MS
        }
    }

    fun thresholdLabel(dataset: WeatherDatasetKind): String {
        return when (dataset) {
            WeatherDatasetKind.CURRENT -> "5m"
            WeatherDatasetKind.HOURLY -> "20m"
            WeatherDatasetKind.DAILY -> "2h"
        }
    }
}
