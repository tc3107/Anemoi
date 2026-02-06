# Changelog

All notable changes to this project will be documented in this file.

The format is based on Keep a Changelog, and this project follows Semantic Versioning once tagged releases begin.

## [Unreleased]

### Changed

- Enforced dark theme at startup and in Compose by removing light theme paths and setting a dark window background.
- Added a default fallback location (`New York`) when no saved location is available.
- Removed the floating `mm` value readout from the precipitation graph HUD while keeping percentage and time indicators.
- Updated location mode behavior to request location permission when the user enters location mode without access.

## [0.1.0] - 2026-02-06

### Added

- Open-source project documentation and maintenance files
- GitHub issue templates and pull request template
- GitHub Actions Android CI workflow
- Dependabot configuration for Gradle and GitHub Actions

### Added

- Initial Android application codebase
