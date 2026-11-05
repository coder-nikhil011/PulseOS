# PulseOS Mobile - Fixes Applied

This build is based on `mobile(4)` and contains code-level fixes without introducing fake telemetry.

## Fixed
- Deduplicated active health events in SQLite.
- Automatically resolves an active health event when its condition is no longer present.
- Added telemetry/history DB indexes.
- Restores up to the latest 60 telemetry points from SQLite so graphs can survive app restarts.
- Stores measured network latency in telemetry when available.
- Added a real TCP connectivity latency measurement to 1.1.1.1:53; unavailable is reported when it cannot be measured.
- Router page displays measured latency.
- Battery charge is treated as charge/availability, not battery-capacity health.
- Background monitoring state now reflects the actual PulseMonitoringService process state.
- Foreground monitoring service uses Android `specialUse` type with an explicit device-health-monitoring subtype.
- Added INTERNET and FOREGROUND_SERVICE_SPECIAL_USE permissions required by the above implementation.

## Validation
- AndroidManifest.xml parses successfully.
- Kotlin source brace/parenthesis structure was checked.
- Full Gradle/Flutter build could not be completed in this environment because Gradle attempted to download its distribution and external network access is unavailable.

## Still intentionally not faked
- CPU/GPU temperature when Android does not expose it.
- Complete device-wide storage when Android scoped-storage/MediaStore access is not available.
- Cellular signal details when not exposed by the device/API.
- Network speed, packet loss, DNS latency, duplicate-file detection, widgets, AI, and other features that require additional implementation remain marked as future work rather than simulated.
