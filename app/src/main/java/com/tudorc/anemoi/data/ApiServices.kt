package com.tudorc.anemoi.data

import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Query

interface NominatimService {
    @GET("search")
    suspend fun search(
        @Query("q") query: String,
        @Query("format") format: String = "json",
        @Query("addressdetails") addressDetails: Int = 1,
        @Query("limit") limit: Int = 5,
        @Query("accept-language") language: String = "en",
        @Header("User-Agent") userAgent: String = "Anemoi",
        @Header("Accept-Language") acceptLanguage: String = "en"
    ): List<GeocodingResponse>
}

interface OpenMeteoService {
    @GET("v1/forecast")
    suspend fun getWeather(
        @Query("latitude") lat: Double,
        @Query("longitude") lon: Double,
        @Query("current_weather") currentWeather: Boolean? = true,
        @Query("hourly") hourly: String? = "temperature_2m,weathercode,apparent_temperature,surface_pressure,precipitation_probability,precipitation,uv_index,wind_speed_10m,wind_direction_10m,wind_gusts_10m",
        @Query("daily") daily: String? = "temperature_2m_max,temperature_2m_min,weather_code,precipitation_probability_max,sunrise,sunset,daylight_duration,uv_index_max",
        @Query("forecast_days") forecastDays: Int = 10,
        @Query("timezone") timezone: String = "auto"
    ): WeatherResponse
}

interface OpenMeteoAirQualityService {
    @GET("v1/air-quality")
    suspend fun getAirQuality(
        @Query("latitude") lat: Double,
        @Query("longitude") lon: Double,
        @Query("hourly") hourly: String,
        @Query("start_date") startDate: String,
        @Query("end_date") endDate: String,
        @Query("timezone") timezone: String = "auto"
    ): AirQualityResponse
}
