# PulseOS Mobile Prompt Completion Plan

This plan follows the three-layer architecture so the app stays aligned with the prompt without carrying dead or misleading features.

## Layer 1 — Flutter app layer
Owner: Flutter UI shell and pages

Files:
- `lib/main.dart`
- `lib/pages/dashboard_page.dart`
- `lib/pages/hardware_health_page.dart`
- `lib/pages/smart_router_page.dart`
- `lib/pages/storage_healer_page.dart`
- `lib/pages/converter_page.dart`
- `lib/pages/settings_page.dart`

Responsibilities:
- Build the main navigation and app shell
- Show live health and telemetry cards
- Route features by supported device actions
- Show alert summaries and recent status
- Keep the UI honest: only expose actions that work through native or supported logic

Must do next:
- Remove any unconnected fake actions
- Keep only pages backed by actual data or valid Android intent routes
- Add a status strip for feature coverage vs native support

## Layer 2 — Android native layer
Owner: Kotlin native bridge

File:
- `android/app/src/main/kotlin/com/example/pulseos_mobile/MainActivity.kt`

Responsibilities:
- Battery & thermal state
- CPU/RAM/storage snapshot
- Process list and app-level statistics
- Android settings and app management routes
- Background monitoring service
- Local notification channels and manager
- Widget support
- Network and usage stats access
- MediaStore scanning for shared storage and downloads

Must do next:
- Add a real background monitor service for health checks
- Add notification channel + manager integration
- Add app widget provider support
- Add MediaStore queries for large files, downloads, and duplicate patterns
- Add permission and feature checks before using system APIs

## Layer 3 — Data + background intelligence layer
Owner: local telemetry and health engine

Files:
- `lib/models/telemetry.dart`
- `lib/services/telemetry_service.dart`
- `lib/services/health_engine.dart`
- `lib/services/health_service.dart`
- `lib/services/alert_service.dart`
- `lib/services/database_service.dart`

Responsibilities:
- Compute health score
- Detect warning and critical conditions
- Persist telemetry history and health events
- Build notification-worthy alerts
- Track latency, network, storage, and usage trends
- Support graphing and history review

Must do next:
- Add network latency sampling and history
- Add storage scan results and duplicate detection data model
- Add daily/hourly health history records
- Add notification event persistence and resolved state tracking
- Add user preference storage for alerts and widgets

## Phase-by-phase completion flow

### Phase 1 — Foundation cleanup
Goal: remove dead code and align the app to the three layers.

Completed:
- created architecture notes
- cleaned messaging around real vs fake features
- kept the app aligned with the working mobile telemetry path

Next:
- remove unused screens/actions that are not connected to data sources
- keep only supported settings routes and real device data

### Phase 2 — Native system completion
Goal: give Android the system-level features the prompt needs.

Tasks:
- background monitoring
- notification channels
- overrides for device alerts
- widget provider
- real network and storage access

### Phase 3 — Intelligence and history
Goal: close the gap between device stats and predictive health insights.

Tasks:
- trend charts
- historical alerts
- storage recommendations
- duplicate detection
- usage analysis
- search-to-action mapping

### Phase 4 — Full prompt parity
Goal: match the prompt behavior at a real product level.

Done only when:
- background health checks work
- notifications appear under Android conditions
- app scans actual shared storage and downloads
- latency and usage data are truly captured
- widgets and unified search are live on device
- end-to-end device testing is verified on Android hardware or emulator

## Zero-fake rule
Any feature that does not have:
1. real device data,
2. real Android support,
3. a known owner layer,

must be removed or moved to future phase.

This is the exact rule that keeps the project honest and prompt-aligned.
