package com.example.anemoi.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class GeocodingResponse(
    @SerialName("place_id") val placeId: Long,
    @SerialName("display_name") val displayName: String,
    val lat: String,
    val lon: String,
    val address: Address? = null
)

@Serializable
data class Address(
    val city: String? = null,
    val town: String? = null,
    val village: String? = null,
    val country: String? = null
)

@Serializable
data class WeatherResponse(
    val latitude: Double,
    val longitude: Double,
    @SerialName("current_weather") val currentWeather: CurrentWeather? = null,
    val hourly: HourlyData? = null,
    val daily: DailyData? = null
)

@Serializable
data class CurrentWeather(
    val temperature: Double,
    @SerialName("windspeed") val windSpeed: Double,
    @SerialName("winddirection") val windDirection: Double = 0.0,
    @SerialName("weathercode") val weatherCode: Int,
    @SerialName("surface_pressure") val pressure: Double? = null,
    val time: String
)

@Serializable
data class HourlyData(
    val time: List<String>,
    @SerialName("temperature_2m") val temperatures: List<Double>,
    @SerialName("weathercode") val weatherCodes: List<Int>? = null,
    @SerialName("apparent_temperature") val apparentTemperatures: List<Double>? = null,
    @SerialName("surface_pressure") val pressures: List<Double>? = null,
    @SerialName("precipitation_probability") val precipitationProbability: List<Int>? = null,
    val precipitation: List<Double>? = null,
    @SerialName("uv_index") val uvIndex: List<Double>? = null
)

@Serializable
data class DailyData(
    val time: List<String>,
    @SerialName("temperature_2m_max") val maxTemp: List<Double>,
    @SerialName("temperature_2m_min") val minTemp: List<Double>,
    @SerialName("weather_code") val weatherCodes: List<Int>? = null,
    @SerialName("precipitation_probability_max") val precipitationProbabilityMax: List<Double>? = null,
    val sunrise: List<String>? = null,
    val sunset: List<String>? = null,
    @SerialName("daylight_duration") val daylightDuration: List<Double>? = null,
    @SerialName("uv_index_max") val uvIndexMax: List<Double>? = null
)

@Serializable
data class LocationItem(
    val name: String,
    val lat: Double,
    val lon: Double,
    val isFavorite: Boolean = false,
    val lastViewed: Long = 0L,
    val customName: String? = null
) {
    val displayName: String
        get() = customName?.trim()?.takeIf { it.isNotEmpty() } ?: name
}

enum class TempUnit {
    CELSIUS, FAHRENHEIT, KELVIN
}

enum class PressureUnit(val label: String) {
    HPA("hPa"), MMHG("mmHg"), INHG("inHg"), MBAR("mbar")
}
