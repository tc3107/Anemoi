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
- Added consistent haptic feedback for UI press interactions across search, settings, debug, and sheet controls, including when focusing the search field to raise the keyboard.
- Made search and pull-up sheet interactions mutually exclusive: expanding the sheet clears active search input, and starting a search collapses the sheet.
- Smoothed search dropdown dismissal by preserving rendered suggestions until the close animation fully completes.
- Fixed an intermittent search bar focus/keyboard race where tapping the field could immediately close the keyboard.
- Updated search-field keyboard dismissal handling to clear the query text and remove focus when the IME is closed, ensuring search fully deselects when the keyboard is put away.
- Fixed a location navigation edge case where visuals could appear between two locations by synchronizing page-driven selection to `settledPage` (post-snap) instead of transient `currentPage` updates during swipe motion.
- Hardened map centering transitions by tracking the last center target and applying a recovery snap to exact coordinates when animation finishes but the map center is still offset, preventing persistent in-between map states.
- Refined settings segmented slider drag behavior by clamping manual swipe movement to the first/last option bounds and reducing haptic spam to one light tick per segment transition.

## [0.1.0] - 2026-02-06

### Added

- Open-source project documentation and maintenance files
- GitHub issue templates and pull request template
- GitHub Actions Android CI workflow
- Dependabot configuration for Gradle and GitHub Actions

### Added

- Initial Android application codebase
