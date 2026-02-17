<p align="center">
  <img src="assets/icon/icon.svg" width="240" alt="Anemoi logo" />
</p>

# Anemoi

Anemoi is a modern Android weather app focused on fast glanceability, a sleek UI, and practical location privacy controls.

In Greek mythology, the Anemoi are the wind gods, each associated with a cardinal direction and seasonal weather.

## Key Features

- Sleek, modern UI with weather-reactive animated backgrounds
- Fast weather at a glance: current, hourly, and daily forecasts
- Powerful location controls: search, live GPS follow mode, and favorites
- Privacy-first location grid obfuscation (1 km to 50 km)
- Home screen widget support with adaptive layouts
- Free to use, with no telemetry

## Tech Stack

- Kotlin + Jetpack Compose
- Android ViewModel + StateFlow
- Retrofit + Kotlinx Serialization + OkHttp
- Google Play Services Location
- AndroidX DataStore

## Package ID

- `com.tudorc.anemoi`

## Releases

Releases are published on this GitHub repository's Releases page.

## Data Sources

- Geocoding: OpenStreetMap Nominatim API
- Forecasts: Open-Meteo API

No private API key is required for the current implementation.

## Notice

See `NOTICE.md` for third-party service attribution and licensing notes.

## License

This project is licensed under the MIT License. See `LICENSE` for details.
