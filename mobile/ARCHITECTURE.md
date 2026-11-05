# PulseOS Mobile Architecture

This app is organized around three layers so the project stays aligned with the full prompt without carrying dead or misleading features.

## 1. Flutter app layer
Purpose: user-facing screens, navigation, and actions.

Primary files:
- `lib/main.dart` — app shell and navigation
- `lib/pages/dashboard_page.dart` — overview and health snapshot
- `lib/pages/hardware_health_page.dart` — system inventory and hardware details
- `lib/pages/smart_router_page.dart` — network related UI
- `lib/pages/storage_healer_page.dart` — cleanup and storage review UI
- `lib/pages/converter_page.dart` — conversion tools UI
- `lib/pages/settings_page.dart` — Android settings shortcuts and preferences

Rules:
- Only keep screens that are connected to real data or supported Android actions.
- Remove placeholder screens or fake conversion actions that are not actually supported.
- Every screen must map to one real source of telemetry or one native Android action.

## 2. Android native layer
Purpose: real device access, OS actions, and system-level monitoring.

Primary file:
- `android/app/src/main/kotlin/com/example/pulseos_mobile/MainActivity.kt`

Supported responsibilities:
- battery status and health
- CPU sampling
- RAM and storage snapshot
- process list
- Android settings launchers
- app storage and app manager launchers

Rules:
- Only keep native methods that are actually called by Flutter.
- No fake OS features or “generic” settings links if no supported intent exists.
- Background monitoring, notification channels, widgets, and full MediaStore scanning belong to this layer and should be implemented here first in native phase 2.

## 3. Data + background intelligence layer
Purpose: health scoring, alert building, history, persistence, and background signal analysis.

Primary files:
- `lib/models/telemetry.dart` — single source model for device health state
- `lib/services/telemetry_service.dart` — polling and health refresh flow
- `lib/services/health_engine.dart` — scoring and problem detection rules
- `lib/services/health_service.dart` — public adapter to health logic
- `lib/services/alert_service.dart` — alert generation and prioritization
- `lib/services/database_service.dart` — SQLite persistence for telemetry and health history

Rules:
- Keep only logic that is currently tied to live or stored telemetry.
- History tables must be aligned with actual app use: telemetry samples, health events, notifications, storage insights, network telemetry.
- Background intelligence must be activated only after the native layer can reliably deliver real device events.

## What stays active now
These are the features currently aligned with the real app state and supported by the codebase:
- overview dashboard
- health score
- basic alert generation
- live battery + memory + storage readings
- settings shortcuts
- storage manager flow
- local telemetry persistence

## What is planned and should be implemented next
Phase 1: stabilize the base
- unify the app around the three layers above
- remove unused screens and fake actions
- keep a strict data-to-UI connection

Phase 2: native system features
- background monitoring service
- Android notification manager + channels
- app widget support
- real network diagnostics and latency capture
- MediaStore shared-storage scans

Phase 3: deep intelligence
- long-term history graphs
- notification history
- storage duplicate detection
- AI recommendation layer based on real telemetry

## Keep/Remove principle
Keep:
- all code used by the actual app flow
- all services with clear owners and real data sources
- all UI tied to supported capabilities

Remove:
- dead screens or noisy placeholders
- fake actions that claim feature support without Android permission or implementation
- duplicate logic across UI and service layers
- extra prompt-driven features that do not have data sources or native support

This structure is the correct foundation for building the full PulseOS prompt without creating a misleading or oversized application shell.
