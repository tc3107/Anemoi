# Changelog

All notable changes to this project will be documented in this file.

The format is based on Keep a Changelog, and this project follows Semantic Versioning once tagged releases begin.

## [Unreleased]

### Changed

- Refined the 10-day forecast rows with taller item height, larger min/max temperature text, and thicker daily range tracks for better readability.
- Reworked 10-day forecast row layout to reserve a precipitation lane before the weather icon, shifting the temperature track start farther right.
- Added per-day precipitation probability labels (non-zero only) to the left of each 10-day forecast icon, with blue intensity mapped from low to high probability.
- Updated temperature graph readout behavior to show the real `current_weather` value when idle, using interpolated values only while dragging.
- Aligned fixed temperature graph markers (`H`, `L`, and current) to the plotted line path so points sit directly on the curve.
- Matched temperature graph value rounding to the main temperature display by using truncation instead of nearest-integer rounding.
- Replaced the inline weather-page `Last updated` age label with a settings-level warning shown when displayed weather data is older than one hour.
- Disabled map touch/pan input while Settings or Organizer overlays are open to prevent background map interaction through uncovered areas.
- Scoped global pull-up-sheet drag handling to the fully-collapsed state so it no longer intercepts gestures while the sheet is settling or already expanded.
- Smoothed pull-up-sheet drag input with deadzone filtering, direction-change damping, and per-frame delta clamping to reduce jitter and abrupt motion.
- Disabled details-sheet list scrolling while collapsed while preserving lazy-list state for consistent scroll position after expansion.
- Smoothed map transitions by replacing heavy snapshot/tile-wait flow with a lightweight transition mask during location switches.
- Coalesced rapid location-switch updates so only the latest swipe target is applied after a short debounce, reducing repeated map recenter churn.
- Reduced map transition render pressure by removing extra transition blur and limiting forced redraws to active transition/response animation windows.
- Changed mode-switch weather handling to keep existing values visible (greyed) while fetching new-signature data, instead of clearing immediately.
- Added conditional mode-switch fetch acceleration: bypass per-location 60s gate only when prior-vs-new request coordinates differ by more than 15 km (global cap/backoff unchanged).
- Moved weather warnings to a regular Settings block at the bottom, added auto-scroll-to-warnings on open, and added a concise explanation of greyed weather text.
- Updated settings cog status tint to amber when weather warnings are present (error red still takes priority).
- Deferred privacy setting application so obfuscation mode and grid distance are staged during editing and applied only when Settings is closed.

## [0.5.0] - 2026-02-09

### Added

- Added a home-screen weather widget with a configuration flow that lets each widget instance target Current Location or a saved location.
- Added responsive widget layouts for compact and wide widths, including current conditions, feels-like, high/low, and a 3-hour outlook.
- Added widget provider/configuration registration and resources (`appwidget-provider`, widget layouts, strings, and outline drawable).
- Added custom location display names (`customName`) with organizer rename/revert controls.

### Changed

- Updated organizer and search surfaces to display custom location names while preserving the original location name for reference.
- Updated location identity handling in organizer interactions to key by coordinates so rename operations do not break drag/favorite actions.
- Updated app launch handling so widget taps can deep-link to a specific location and open that location in weather mode.
- Updated weather view-model flows to persist renamed display names across favorites/selected/live/search state and sync widget selections.
- Updated widget refresh triggers so widget content is refreshed after relevant app-state changes (location selection, live location updates, temperature unit changes, rename events, and successful weather fetches).
- Updated request gating to use a longer per-location minimum interval for background refresh triggers (`15m`) while keeping interaction/startup behavior unchanged.
- Increased stale-warning evaluation cadence from 60s to 15s.

## [0.4.0] - 2026-02-09

### Added

- Added a new full-width `10-DAY FORECAST` pull-up widget with 10 rows (`Today` + next 9 days), weekday labels, daily icons, min/max temperatures, and per-day range tracks normalized against the displayed 10-day min/max envelope.
- Added daily forecast widget integration in the pull-up details flow and extended daily API/model support for `forecast_days=10`, `daily.weather_code`, and `daily.precipitation_probability_max`.

### Changed

- Reordered pull-up widgets so hourly forecast is second-to-last and 10-day forecast is last.
- Refined daily forecast row layout: dynamic day-label width based on longest label, centered minimum temperature column, and centered icon lane between labels and minimums.
- Updated daily range-track styling to match temperature-graph blues (`#81D4FA` -> `#0288D1`) with a highlighted segment that reveals the same gradient and keeps rounded ends.
- Improved temperature and precipitation graph edge fading with monotonic masks to prevent dashed/line segments from visually reappearing past faded tips.
- Updated graph HUD readouts:
  - Added explicit temperature units in graph values/axis labels (`°C`, `°F`, `K`).
  - Aligned temperature axis labels to the same right edge as precipitation `mm` labels.
  - Reworked value/clock HUD positioning relative to widget header and top graph line.
  - Moved value+clock to a single horizontal row (value left, clock right), with shared centerline.
  - Added adaptive HUD text scaling and reduced clock text size for reliable fit.
- Lowered UV and pressure gauges slightly within their cards by increasing content top gap.
- Adjusted pressure dial geometry so its visual footprint matches UV dial size more closely.
- Increased pressure dial visual weight (tick/indicator lengths and stroke widths) to better match UV dial thickness.

### Fixed

- Fixed weather warning persistence so warning state clears when not actively stale/expired (missing or inactive datasets no longer keep warning indicators latched).

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
