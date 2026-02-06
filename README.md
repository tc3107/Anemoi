# Anemoi

Anemoi is an Android weather app built with Jetpack Compose. It combines live location, place search, and forecast visualization in a single mobile-first interface.

## Features

- Current, hourly, and daily weather data
- Place search with geocoding suggestions
- Live GPS location and follow mode
- Favorites and location organization
- Map-based visual background with local tile caching
- Adjustable display and privacy settings (including coordinate obfuscation modes)

## Tech Stack

- Kotlin + Jetpack Compose
- Android ViewModel + StateFlow
- Retrofit + Kotlinx Serialization + OkHttp
- Google Play Services Location
- AndroidX DataStore
- osmdroid

## Requirements

- JDK 17
- Android SDK 35
- Android Studio with current Android Gradle Plugin support

## Getting Started

1. Clone the repository.
2. Ensure `local.properties` points to your Android SDK path.
3. Build and run:

```bash
./gradlew :app:assembleDebug
```

To run unit tests:

```bash
./gradlew test
```

## Data Sources and Attribution

- Geocoding: OpenStreetMap Nominatim API
- Forecasts: Open-Meteo API
- Map tiles: OpenStreetMap via osmdroid

No private API key is required for the current implementation.

## Contributing

See `CONTRIBUTING.md` for development workflow and pull request expectations.

## Release Process

See `RELEASING.md` for versioning and release checklist details.

## Security

See `SECURITY.md` for responsible vulnerability disclosure.

## Notice

See `NOTICE.md` for third-party service attribution and licensing notes.

## License

This project is licensed under the MIT License. See `LICENSE` for details.
