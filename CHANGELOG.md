# Changelog

All notable changes to this project will be documented in this file.

The format is based on Keep a Changelog, and this project follows Semantic Versioning once tagged releases begin.

## [Unreleased]

### Changed

- Enforced dark theme at startup and in Compose by removing light theme paths and setting a dark window background.
- Added a default fallback location (`New York`) when no saved location is available.
- Removed the floating `mm` value readout from the precipitation graph HUD while keeping percentage and time indicators.
- Updated location mode behavior to request location permission when the user enters location mode without access.
- Optimized map rendering performance by reducing transition blur cost, avoiding overlay rebuild churn, and limiting redraw invalidation to active animation states.
- Reduced default sheet blur strength to improve UI smoothness on lower-end devices.
- Added startup weather prefetch for all known locations and switched to cached-first location changes with background weather refresh updates.
- Capped UV index dial indicator movement so min/max positions stay fully within gauge bounds.
- Adjusted primary value vertical alignment on temperature and precipitation line graphs.
- Standardized hourly weather condition icon rendering to a light gray tint.
- Fixed startup paging so default or selected non-favorite locations (including New York fallback) open on a weather data page instead of the location-mode dash screen.
- Fixed pull-up sheet haptics to trigger consistently on actual swipe/tap commit transitions (expand/collapse), not only handle interactions.

## [0.1.0] - 2026-02-06

### Added

- Open-source project documentation and maintenance files
- GitHub issue templates and pull request template
- GitHub Actions Android CI workflow
- Dependabot configuration for Gradle and GitHub Actions

### Added

- Initial Android application codebase
