# Changelog

All notable changes to this project will be documented in this file.

The format is based on Keep a Changelog, and this project follows Semantic Versioning once tagged releases begin.

## [Unreleased]

### Changed

- Replaced the inline weather-page `Last updated` age label with a settings-level warning shown when displayed weather data is older than one hour.
- Disabled map touch/pan input while Settings or Organizer overlays are open to prevent background map interaction through uncovered areas.
- Scoped global pull-up-sheet drag handling to the fully-collapsed state so it no longer intercepts gestures while the sheet is settling or already expanded.
- Smoothed pull-up-sheet drag input with deadzone filtering, direction-change damping, and per-frame delta clamping to reduce jitter and abrupt motion.
- Disabled details-sheet list scrolling while collapsed while preserving lazy-list state for consistent scroll position after expansion.
- Smoothed map transitions by replacing heavy snapshot/tile-wait flow with a lightweight transition mask during location switches.
- Coalesced rapid location-switch updates so only the latest swipe target is applied after a short debounce, reducing repeated map recenter churn.
- Reduced map transition render pressure by removing extra transition blur and limiting forced redraws to active transition/response animation windows.

## [0.3.0] - 2026-02-08

### Added

- Added a full weather request policy module with pure logic for dataset freshness selection, request gating, backoff progression, and timestamp window pruning.
- Added focused unit test coverage for request-policy behavior including threshold boundaries, rate limits, deterministic multi-gate blocking, and backoff resets/progression.
- Added persisted cache signatures and active request signature tracking so cached weather is only reused when request semantics match.
- Added in-flight request coalescing per location/signature/dataset set to prevent duplicate concurrent weather calls.
- Added stale-age UX messaging with a `Last updated Xh Ym ago` indicator when displayed data is older than one hour.

### Changed

- Reworked weather fetching to be cache-first with per-dataset freshness thresholds (current 5m, hourly 20m, daily 2h) and selective dataset requests.
- Replaced direct rate-limit checks with deterministic gate evaluation that combines backoff, per-location throttling (1/min), and global cap (30/min) and chooses the longest wait.
- Updated automatic refresh cadence to 60 minutes in the background while preserving on-demand refresh from user interactions.
- Updated weather API request wiring to allow nullable `current_weather`, `hourly`, and `daily` params so only required data blocks are requested.
- Made weather cache usage signature-aware in both main weather surface and details sheet rendering.

### Fixed

- Fixed semantic cache reuse issues by invalidating stale signature-mismatched cache entries when request-relevant settings change.
- Fixed potential duplicate network fetches from rapid repeated triggers by joining existing in-flight requests.
- Fixed post-fetch cache durability risk by flushing cache persistence immediately after successful weather updates.
- Improved failure diagnostics by logging retry context (requested datasets, request params, HTTP status, capped error-body snippet, and parse error details).

## [0.2.0]

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
- Renamed the app label from "Anemoi Forecast" to "Anemoi" for launcher and app title consistency.

## [0.1.0] - 2026-02-06

### Added

- Open-source project documentation and maintenance files
- GitHub issue templates and pull request template
- GitHub Actions Android CI workflow
- Dependabot configuration for Gradle and GitHub Actions

### Added

- Initial Android application codebase
