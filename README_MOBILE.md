# PulseOS Mobile

# Complete Android Product & Backend Specification

> **PHONE MONITOR → UNDERSTAND → ALERT → ASSIST → VERIFY**

This README is the dedicated specification for the **Android mobile
application** of PulseOS. It defines the six mobile sections,
Android-native backend, real device telemetry, graphs, health
intelligence, notifications, widgets, settings integration, permissions,
storage handling, and Android platform limitations.

# 📱 PulseOS Mobile --- Complete Android Product & Backend Specification

> **Important:** This section defines the intended Android product
> direction in detail. The mobile application is not intended to be a
> small copy of the desktop UI. It should provide the same PulseOS
> philosophy while using Android-native APIs, permissions, storage
> rules, background-work restrictions, widgets, notifications, and
> device capabilities.

## Mobile Product Vision

PulseOS Mobile is intended to become an **all-in-one phone health,
performance, storage, network, app-management, conversion, and
system-settings center**.

The user should not have to search through multiple Android Settings
pages to understand or manage common device information. Wherever
Android permits a third-party application to read or control something
directly, PulseOS should provide that capability inside PulseOS. Where
Android deliberately restricts third-party access, PulseOS should open
the **exact relevant Android system screen**, rather than sending the
user to the generic Settings home page.

The mobile product follows this principle:

``` text
PHONE HARDWARE / ANDROID APIs
              ↓
       PulseOS Telemetry
              ↓
      Health & Intelligence
              ↓
   Graphs / Problems / Alerts
              ↓
      Safe User Actions
              ↓
          VERIFY
```

### Core Principles

-   Real phone data only; no fake telemetry.
-   Local-first monitoring for core health features.
-   Android-native APIs determine which values are available.
-   Unsupported capabilities must be shown as unavailable instead of
    inventing values.
-   User actions should happen inside PulseOS whenever Android allows
    them.
-   Restricted system actions should open the exact relevant Android
    screen.
-   Background monitoring must be battery-conscious.
-   Notifications must be useful and deduplicated, not spam users.
-   Graphs must be based on actual collected history.
-   Users can customize home-screen widgets and shortcuts.
-   Desktop and Android share the same PulseOS product philosophy while
    using platform-specific implementations.

------------------------------------------------------------------------

# 📱 Mobile Navigation --- Six Primary Sections

The Android application keeps the same six primary destinations as the
desktop product, but every destination is adapted to the phone platform.

``` text
1. Overview
2. Hardware
3. Smart Router
4. Storage Healer
5. Converter
6. Settings
```

  -----------------------------------------------------------------------
  Section                             Android Purpose
  ----------------------------------- -----------------------------------
  Overview                            Complete phone health and
                                      performance command center

  Hardware                            Processor, RAM, battery, thermal,
                                      sensors and device information

  Smart Router                        Wi-Fi, mobile network, latency,
                                      data usage and network-related app
                                      information

  Storage Healer                      Storage intelligence, large files,
                                      media, downloads, duplicate review
                                      and safe cleanup

  Converter                           Local file conversion and file
                                      utilities supported by the mobile
                                      build

  Settings                            Unified PulseOS settings,
                                      permissions, app controls and
                                      direct Android system shortcuts
  -----------------------------------------------------------------------

------------------------------------------------------------------------

# 1. 🏠 Overview --- Phone Command Center

Overview should be the main PulseOS command center. It should not simply
display six static cards. It should aggregate live telemetry, health
analysis, problems, trends, actions and recent activity.

## Overview Components

### Health Score

Display a calculated device health score from 0--100.

Example:

``` text
PULSOS HEALTH

87 / 100
GOOD

CPU Health        92
Memory Health     78
Battery Health    95
Thermal Health    90
Storage Health    72
Network Health    96
```

The score must be generated from real observations and health rules, not
a random or hardcoded value.

### Live Device Snapshot

-   CPU usage
-   RAM used / total / available
-   Battery percentage
-   Charging state
-   Battery temperature
-   Storage used / free / total
-   Network status
-   Thermal status
-   Device model
-   Android version

### Quick Health Status

Show immediate conditions such as:

-   Temperature normal / high
-   RAM normal / high
-   Storage normal / nearly full / critical
-   Battery normal / low / charging / thermal warning
-   Network connected / unstable / offline
-   App or data-usage warning where supported

### Quick Actions

The user should be able to reach common operations without going through
Settings:

-   Scan Device
-   Clean Storage
-   Check Network
-   Battery Health
-   Memory
-   Large Files
-   App Usage
-   Device Information
-   Converter

### Recent Activity

Examples:

``` text
✓ Storage scan completed
✓ Network checked
✓ Large files found
✓ Device health updated
✓ Conversion completed
```

------------------------------------------------------------------------

# 📊 Overview Graphs

Graphs should be shown wherever historical data provides useful context.

## CPU Graph

``` text
CPU %
100 ┤
 80 ┤           ╭─╮
 60 ┤      ╭────╯ ╰──╮
 40 ┤──────╯          ╰──
 20 ┤
  0 ┼──────────────────────
       Time →
```

## RAM Graph

``` text
RAM %
100 ┤                 ╭──
 80 ┤          ╭──────╯
 60 ┤────╮─────╯
 40 ┤    ╰────────
 20 ┤
  0 ┼────────────────────
       Time →
```

## Battery Graph

``` text
Battery %
100 ┤╮
 90 ┤╰╮
 80 ┤ ╰────╮
 70 ┤      ╰──╮
 60 ┤         ╰──
    └────────────────
       Time →
```

## Temperature Graph

Show temperature history when the required sensor information is exposed
by Android.

``` text
Temperature
50°C ┤             ╭──
45°C ┤        ╭─────╯
40°C ┤────╮───╯
35°C ┤    ╰────────
30°C ┤
    └────────────────
       Time →
```

------------------------------------------------------------------------

# 2. 🖥 Hardware --- Complete Phone Hardware Center

The Hardware section is the mobile equivalent of desktop hardware
monitoring.

## Processor / SoC

Collect only information exposed by the Android device:

-   SoC / processor information
-   CPU architecture
-   CPU core count
-   CPU usage
-   CPU frequency where available
-   Core information where available
-   CPU/thermal information where exposed

## Memory

-   Total RAM
-   Used RAM
-   Available RAM
-   Memory percentage
-   Low-memory state
-   Memory trend
-   Top visible memory information where Android permits it

## Battery

-   Battery percentage
-   Charging / discharging state
-   Charging status
-   Battery temperature
-   Voltage where available
-   Current where available
-   Battery health where exposed
-   Battery technology where exposed
-   Cycle information where exposed

## Thermal

-   Battery temperature
-   Thermal status
-   Available thermal information
-   Thermal trend
-   Thermal warning state

Android thermal APIs should be used where supported. A device-specific
temperature value should never be invented when the platform does not
expose it.

## Sensors

Detect available sensors such as:

-   Accelerometer
-   Gyroscope
-   Magnetometer
-   Proximity
-   Ambient light
-   Barometer
-   Rotation / rotation vector
-   Other sensors reported by Android

Sensor capability should be separated from live sensor testing. PulseOS
does not need to continuously poll every sensor when the user is not
using a sensor diagnostic screen.

## Device Information

-   Manufacturer
-   Model
-   Android version
-   API level
-   Build information
-   Architecture
-   Security patch
-   Screen information
-   Touch capability
-   Camera / flash capability
-   Microphone / audio capability
-   GPS/location capability
-   NFC capability
-   Bluetooth capability
-   Biometrics capability
-   USB capability
-   Vibration capability

If a capability is not exposed on a particular device, display
`Unavailable on this device` instead of fake information.

------------------------------------------------------------------------

# 3. 🌐 Smart Router --- Complete Mobile Network Center

Smart Router on Android should be broader than a simple process list. It
becomes the PulseOS network and data-usage center.

## Network Status

-   Internet connectivity
-   Wi-Fi connected/disconnected
-   Mobile data state
-   Network transport
-   Network type
-   Signal information where available
-   IP information where available
-   DNS health
-   Internet reachability
-   Latency
-   Network health score

Example:

``` text
NETWORK HEALTH

GOOD · 94/100

Wi-Fi              Connected
Internet           Online
DNS                Healthy
Latency             24 ms
Signal              Excellent
```

## Wi-Fi

Show available information permitted by the Android version and
permissions:

-   Connected network
-   Connection state
-   Signal information where available
-   Link/network information where available
-   Internet validation
-   Latency

## Mobile Data

-   Network connection
-   Data usage
-   Usage period
-   Network type
-   SIM/network information where available

## App Network / Data Usage

Where Android usage APIs and user-granted access permit it:

``` text
APP DATA USAGE

YouTube       1.20 GB
Instagram     420 MB
Chrome        280 MB
WhatsApp      160 MB
```

Support views such as:

-   Today
-   Last 7 days
-   Last 30 days

## Network Graph

``` text
Latency (ms)
100 ┤             ╭╮
 80 ┤       ╭─────╯╰─╮
 60 ┤──╮────╯         ╰
 40 ┤  ╰────
 20 ┤
  0 ┼────────────────────
       Time →
```

The graph must use actual measured samples.

## Android Limitation

Desktop process termination cannot simply be copied to Android. A normal
Android application cannot arbitrarily kill other
applications/processes. Instead, PulseOS should provide supported
alternatives such as:

-   Open App Info
-   Open Battery Usage
-   Open Usage information
-   Open relevant app management screen
-   Open Android system control when available

------------------------------------------------------------------------

# 4. 💾 Storage Healer --- Complete Mobile Storage Center

Storage Healer should answer the question:

> **What is using my phone storage, and what can I safely do about it?**

## Storage Overview

``` text
INTERNAL STORAGE

82% USED

████████████████░░░░

Used      209 GB
Free       47 GB
Total     256 GB
```

## Storage Categories

Where accessible through Android storage APIs:

-   Images
-   Videos
-   Audio
-   Documents
-   Downloads
-   APK files
-   ZIP / archives
-   Screenshots
-   Screen recordings
-   Large files
-   Temporary files
-   Other supported media categories

## Storage Category Graph

``` text
Videos        ███████████  82 GB
Images        ██████       45 GB
Apps          █████        38 GB
Documents    ██           12 GB
Downloads    ██           10 GB
Other        █████        ...
```

## Large File Scanner

Configurable thresholds:

-   100 MB

-   500 MB

-   1 GB

Example:

``` text
LARGE FILES

Video.mp4        1.8 GB
Movie.mp4        1.2 GB
Backup.zip       900 MB
ScreenRec.mp4    620 MB
```

User selects files before any destructive operation.

## Duplicate Detection

The duplicate engine should avoid hashing every file unnecessarily.

``` text
File size/name grouping
          ↓
Candidate groups
          ↓
Partial hash
          ↓
Exact hash when required
          ↓
Duplicate groups
```

Example:

``` text
3 duplicate groups
14 files
2.8 GB potentially recoverable
```

## Cleanup Workflow

``` text
Scan
 ↓
Analyse
 ↓
Recommend
 ↓
User Selects
 ↓
Confirm
 ↓
Safe Cleanup / Quarantine where supported
 ↓
Verify
 ↓
Update Storage Metrics
```

PulseOS must not claim that another application's private cache was
deleted when Android did not permit the operation.

## Android Storage Restrictions

Android storage is sandboxed and uses scoped-storage rules. PulseOS
should use MediaStore, Storage Access Framework, and other supported
Android storage APIs. Broad filesystem access must not be assumed.
Features must adapt to the Android version and permissions available on
the device.

------------------------------------------------------------------------

# 5. 🔄 Converter --- Mobile File Toolbox

The Converter section should provide local conversion features actually
supported by the mobile implementation.

## Image

Potential supported workflows:

-   JPG → PNG
-   PNG → JPG
-   Image → PDF
-   Resize
-   Compress

## PDF

Potential supported workflows:

-   Images → PDF
-   Merge
-   Split
-   Compress
-   Extract text where supported

## Documents

-   TXT → PDF
-   Other supported document conversions implemented by the mobile build

## Archive

-   Create ZIP
-   Extract ZIP
-   Archive selected files

## Media

-   Audio conversion
-   Video conversion
-   Compression where supported by available Android codecs/libraries

## Conversion History

``` text
TODAY

✓ Images → PDF
✓ ZIP created
✓ Video compressed
```

The application must never display a fake conversion progress state.
Unsupported conversions should be clearly marked as unsupported on the
current device/build.

------------------------------------------------------------------------

# 6. ⚙️ Settings --- Unified Phone Settings Center

Settings should make common device settings easy to discover without
forcing users to search through Android Settings.

## Connectivity

-   Wi-Fi
-   Mobile network
-   SIM/network information
-   Bluetooth
-   Hotspot where supported
-   VPN
-   Airplane mode
-   NFC

## Sound

-   Media volume
-   Ringtone volume
-   Alarm volume
-   Notification sound
-   Vibration
-   Do Not Disturb

## Display

-   Brightness
-   Auto-brightness where supported
-   Dark/light mode where supported
-   Screen timeout
-   Font size/system display controls where supported
-   Refresh rate where supported

## Battery

-   Battery state
-   Battery saver
-   Background usage
-   Charging information

## Security

-   Screen lock
-   Biometrics
-   Device security
-   Security information

## Privacy & Permissions

-   Location
-   Camera
-   Microphone
-   Contacts
-   Files/media
-   Notifications
-   Nearby devices

## Apps

Provide an installed-app center where Android package visibility and
permissions allow it.

Example:

``` text
Instagram

Storage       2.4 GB
Cache         180 MB
Data Usage    420 MB
Permissions   8
Notifications ON

[ Open App ]
[ App Info ]
[ Permissions ]
```

## Other System Categories

Where supported, provide direct navigation to:

-   Storage
-   Location
-   Accessibility
-   Date & time
-   Language
-   Notifications
-   Battery
-   Display
-   Sound
-   Security
-   App management

### System Settings Rule

PulseOS should not pretend to control a system setting that Android does
not permit a third-party app to change.

Instead:

``` text
User Action
    ↓
Can PulseOS change it directly?
    ↓
YES → Apply inside PulseOS
    │
    NO
    ↓
Open exact Android system screen
```

The user should not be sent to the generic Settings home unless Android
gives no more specific route.

------------------------------------------------------------------------

# 🔎 Unified PulseOS Search

Because PulseOS is intended to centralize phone management, it should
provide a global search.

Examples:

``` text
Search: Wi-Fi
 → Wi-Fi status
 → Network health
 → Wi-Fi system settings

Search: Battery saver
 → Battery saver status
 → Battery settings

Search: Brightness
 → Display brightness
 → Display settings

Search: Storage
 → Storage health
 → Large files
 → Storage cleanup
```

The search system should return both PulseOS features and relevant
Android system destinations.

------------------------------------------------------------------------

# 🔔 PulseOS Health Notification Engine

Notifications are a core part of the mobile product. PulseOS should
notify users about meaningful device problems such as:

-   High temperature
-   High RAM usage
-   High storage usage
-   Low storage
-   Battery warning
-   Charging-related thermal warning
-   Network instability
-   High data usage
-   Important device-health problems

## Notification Architecture

``` text
Android Telemetry
       ↓
Health Problem Detector
       ↓
Problem Event
       ↓
Notification Policy
       ↓
Deduplication / Cooldown
       ↓
Android Notification
```

## Temperature Example

``` text
PULSOS

🌡️ Device temperature is high
Current: 44°C
Performance may be reduced.

[ View Health ]
```

## Memory Example

``` text
PULSOS

⚠️ High memory usage
RAM usage is currently 91%.

[ Check Memory ]
```

## Storage Example

``` text
PULSOS

⚠️ Storage almost full
Only 8.2 GB remains.

[ Clean Storage ]
```

## Network Example

``` text
PULSOS

⚠️ Network connection is unstable.

[ Check Network ]
```

------------------------------------------------------------------------

# 🔕 Notification Deduplication

PulseOS must not send repeated alerts for every telemetry sample.

Bad behaviour:

``` text
44.0°C → notification
44.2°C → notification
44.4°C → notification
44.1°C → notification
```

Correct behaviour:

``` text
Problem detected
      ↓
Notification sent
      ↓
Cooldown / problem state retained
      ↓
Problem resolved
      ↓
Problem happens again
      ↓
New notification
```

Severity increases may trigger an update even when a previous warning
exists.

------------------------------------------------------------------------

# 🚨 Notification Severity

### INFO

Examples:

-   Scan completed
-   Conversion completed
-   Health check completed

### WARNING

Examples:

-   Storage above configured warning level
-   RAM usage remains high
-   Network quality degraded

### HIGH

Examples:

-   High device temperature
-   Very low storage
-   Significant battery/thermal warning

### CRITICAL

Examples:

-   Critical thermal state
-   Severe device-health condition exposed by Android

The exact thresholds should be configurable and should account for
Android/device-specific signals where available rather than relying on
one universal temperature number.

------------------------------------------------------------------------

# 🔔 User Alert Preferences

Settings should allow users to control alert categories:

``` text
PulseOS Alerts

☑ Temperature
☑ Battery
☑ Storage
☑ Memory
☑ Network
☑ Data Usage
☑ App Activity
☑ Performance
☑ Security
```

Disabled categories should not generate their corresponding
notifications.

------------------------------------------------------------------------

# 📲 Android Home-Screen Widgets

PulseOS should support customizable Android home-screen widgets so users
can see important device information without opening the app.

## Available Widgets

-   Health
-   Battery
-   Storage
-   RAM
-   CPU
-   Temperature
-   Network
-   Alerts
-   Quick Actions

## Health Widget

``` text
┌──────────────────────┐
│ PULSOS HEALTH        │
│                      │
│       87 / 100       │
│                      │
│ CPU       24%        │
│ RAM       62%        │
│ Battery   78%        │
│ Temp      34°C       │
└──────────────────────┘
```

## Battery Widget

``` text
┌──────────────────┐
│ 🔋 Battery       │
│                  │
│       78%        │
│                  │
│ Normal           │
│ 34°C             │
└──────────────────┘
```

## Storage Widget

``` text
┌──────────────────┐
│ 💾 Storage       │
│                  │
│ 82% Used         │
│ ████████████░░   │
│                  │
│ 46 GB Free       │
└──────────────────┘
```

## Network Widget

``` text
┌──────────────────┐
│ 🌐 Network       │
│                  │
│ Wi-Fi     GOOD   │
│ 24 ms            │
└──────────────────┘
```

## Alerts Widget

``` text
┌────────────────────┐
│ PULSOS ALERTS      │
│                    │
│ 🔴 Storage High    │
│ 🟠 RAM High        │
│ 🟢 Battery Normal  │
└────────────────────┘
```

## User Customization

PulseOS should provide a widget-customization page:

``` text
Available Widgets

☑ Health
☑ Battery
☑ Storage
☑ RAM
☑ CPU
☑ Temperature
☑ Network
☑ Alerts
☑ Quick Actions
```

The user can choose which widgets they want on the Android home screen.
Android widget pinning and launcher support determine the exact
interaction available on a device.

------------------------------------------------------------------------

# ⚡ Quick Actions / App Shortcuts

PulseOS should expose Android app shortcuts for common tasks.

Example long-press menu:

``` text
PulseOS

▸ Health
▸ Storage Cleaner
▸ Network Check
▸ Battery
▸ Converter
▸ Scan Device
```

This allows the user to enter a specific PulseOS function without first
navigating through the full application.

------------------------------------------------------------------------

# 🧠 Mobile Health Intelligence Engine

The health engine should be a reusable backend service rather than UI
code.

``` text
Telemetry
   ↓
Health Rules
   ↓
Problem Detector
   ↓
Severity
   ↓
Recommendation
   ↓
Action
   ↓
Verification
```

## Health Problem Object

Conceptually:

``` text
Problem {
    id
    type
    severity
    title
    message
    currentValue
    status
    recommendation
    action
    createdAt
    resolvedAt
}
```

Example:

``` text
TYPE: STORAGE
SEVERITY: WARNING
CURRENT: 91%
TITLE: Storage almost full
RECOMMENDATION: Review large files
ACTION: OPEN_STORAGE
```

------------------------------------------------------------------------

# ⚙️ Android Telemetry Backend

The mobile implementation should separate Android-native data collection
from Flutter UI.

Recommended architecture:

``` text
Flutter UI
    ↓
Dart Repository Layer
    ↓
MethodChannel / Platform Interface
    ↓
Kotlin Android Services
    ↓
Android APIs
    ↓
Real Device Data
```

The current prototype already uses the concept of a Flutter
`MethodChannel('pulseos/device')`. The final architecture should expand
this into maintainable feature-specific services instead of keeping all
native logic in one activity.

------------------------------------------------------------------------

# 📡 CPU Monitoring Backend

Use Android-supported CPU information and system telemetry where
available. The implementation should calculate usage from actual samples
rather than a fixed value.

Conceptual calculation:

``` text
Sample 1
  ↓
CPU total / idle time

Sample 2
  ↓
CPU total / idle time

Difference
  ↓
CPU utilization
```

Store samples for graphing:

``` text
cpu_history
----------------
timestamp
cpu_usage
core_count
frequency_when_available
```

A foreground screen can update more frequently, while background
monitoring must use battery-conscious scheduling.

------------------------------------------------------------------------

# 🧠 RAM Monitoring Backend

Use Android `ActivityManager` memory information where available.

Conceptually:

``` text
used = total - available

usagePercent =
    used / total × 100
```

Store:

``` text
ram_history
----------------
timestamp
used_bytes
available_bytes
total_bytes
usage_percent
```

The RAM graph reads these actual samples.

------------------------------------------------------------------------

# 🔋 Battery Monitoring Backend

Use Android `BatteryManager` and battery broadcasts/APIs where
supported.

Collect:

-   Level
-   Charging state
-   Temperature
-   Voltage
-   Current where available
-   Health where exposed
-   Technology where exposed

Battery events:

``` text
Charging Started
Charging Stopped
Battery Low
Battery Critical
Temperature Warning
Normal State Restored
```

------------------------------------------------------------------------

# 🌡️ Thermal Monitoring Backend

Use Android thermal APIs and available battery/thermal readings.

Conceptual backend:

``` text
ThermalManager
     │
     ├── Battery temperature
     ├── Thermal status
     ├── Available thermal information
     └── Thermal trend
```

Thermal status should be treated as a platform signal. PulseOS must not
assume that one temperature value has the same meaning on every phone.

------------------------------------------------------------------------

# 🧭 Sensor Monitoring Backend

Use Android `SensorManager` to discover available sensors.

Separate:

``` text
Sensor Capability
       ≠
Live Sensor Test
```

Only start high-frequency sensor collection when the user opens a sensor
diagnostic screen or another feature genuinely requires it.

------------------------------------------------------------------------

# 🌐 Network Backend

Recommended components:

``` text
NetworkManager
    │
    ├── ConnectivityManager
    ├── NetworkCapabilities
    ├── Wi-Fi information
    ├── Cellular information
    ├── DNS check
    ├── Internet reachability
    └── Latency measurement
```

Unified model:

``` text
NetworkSnapshot {
    connected
    validated
    transport
    networkType
    signalWhenAvailable
    dnsHealthy
    internetReachable
    latency
    timestamp
}
```

------------------------------------------------------------------------

# 📶 Data Usage Backend

Where Android permissions and APIs allow it, collect application network
usage.

Conceptual model:

``` text
AppNetworkUsage
----------------
packageName
appName
wifiBytes
mobileBytes
totalBytes
timestamp
```

Provide daily, weekly and monthly summaries.

------------------------------------------------------------------------

# 📱 App Management Backend

Use Android package/app APIs within Android package-visibility rules.

Potential information:

-   App name
-   Package name
-   Version
-   Install/update information
-   Launchable state
-   App usage where permission is available
-   App information shortcut
-   Permission shortcut
-   Notification shortcut
-   Battery/storage information where Android exposes it

PulseOS must respect Android's package visibility and privacy
restrictions.

------------------------------------------------------------------------

# 💾 Storage Backend

Recommended mobile storage architecture:

``` text
StorageEngine
    │
    ├── Device Storage Monitor
    ├── MediaStore Scanner
    ├── Downloads Scanner
    ├── Images Scanner
    ├── Videos Scanner
    ├── Audio Scanner
    ├── Documents Scanner
    ├── Large File Scanner
    ├── Duplicate Scanner
    └── Cleanup Engine
```

Storage scanning must use only locations and APIs that the user has
authorized and Android permits.

------------------------------------------------------------------------

# 🧹 Duplicate File Engine

Efficient duplicate detection should use staged filtering:

``` text
All files
   ↓
Group by size
   ↓
Candidate groups
   ↓
Partial hash
   ↓
Exact hash
   ↓
Duplicate groups
```

Do not calculate full hashes for every file unnecessarily, especially on
large phones, because that can consume CPU, storage I/O and battery.

------------------------------------------------------------------------

# 🗃️ Local Database

A local database should retain short-term telemetry history, health
events, storage scan metadata and user preferences.

Recommended conceptual tables:

### `telemetry_samples`

``` text
id
timestamp
cpu
ram
battery
temperature
storage
network_latency
```

### `health_events`

``` text
id
timestamp
type
severity
title
message
resolved
```

### `app_usage`

``` text
package_name
timestamp
foreground_ms
network_bytes
```

### `storage_scans`

``` text
id
timestamp
total
used
free
```

### `storage_items`

``` text
id
path
size
type
modified
hash
```

### `notifications`

``` text
id
type
severity
created_at
read
```

### `widget_preferences`

``` text
widget_type
enabled
size
configuration
```

A local SQLite-based implementation such as Drift is suitable for the
Flutter layer, with Android-native services feeding the repository
layer.

------------------------------------------------------------------------

# 📈 Graph Architecture

Graphs should not calculate telemetry themselves.

``` text
Android Telemetry
       ↓
Repository
       ↓
Local Database
       ↓
Time-range Query
       ↓
Graph Data Model
       ↓
Flutter Chart
```

Recommended ranges:

-   Live / recent seconds
-   5 minutes
-   1 hour
-   24 hours
-   7 days
-   30 days where storage and sampling policy allow

The sampling frequency should be adaptive so that long-term history does
not consume unnecessary storage or battery.

------------------------------------------------------------------------

# 🔋 Background Monitoring Strategy

Continuous one-second monitoring of every metric would unnecessarily
drain a phone battery.

The intended architecture is adaptive:

``` text
App Open
   ↓
High-resolution foreground telemetry

App Minimized
   ↓
Battery-efficient periodic monitoring

Problem Detected
   ↓
Temporary higher-frequency monitoring where justified

Problem Resolved
   ↓
Return to normal schedule
```

Android background execution and foreground-service rules must be
followed. PulseOS should not keep an unrestricted background loop simply
to refresh UI numbers.

------------------------------------------------------------------------

# 🔔 Background Health Detection

The background engine should focus on meaningful state changes rather
than every raw sample.

Example:

``` text
Temperature samples
44.0
44.2
44.4
44.1
```

The system evaluates the trend and thermal state and creates one health
event instead of four notifications.

------------------------------------------------------------------------

# 🧩 Recommended Android Project Architecture

The current prototype can evolve toward the following structure:

``` text
mobile/
├── lib/
│   ├── main.dart
│   │
│   ├── core/
│   │   ├── theme/
│   │   ├── routing/
│   │   └── platform/
│   │
│   ├── models/
│   │   ├── telemetry.dart
│   │   ├── health.dart
│   │   ├── network.dart
│   │   ├── storage.dart
│   │   └── app_info.dart
│   │
│   ├── services/
│   │   ├── telemetry_repository.dart
│   │   ├── network_repository.dart
│   │   ├── storage_repository.dart
│   │   ├── health_repository.dart
│   │   └── settings_repository.dart
│   │
│   ├── pages/
│   │   ├── overview/
│   │   ├── hardware/
│   │   ├── router/
│   │   ├── storage/
│   │   ├── converter/
│   │   └── settings/
│   │
│   └── widgets/
│       ├── charts/
│       ├── health_cards/
│       ├── metric_cards/
│       └── quick_actions/
│
└── android/
    └── app/src/main/kotlin/
        └── .../pulseos/
            ├── telemetry/
            │   ├── CpuMonitor
            │   ├── MemoryMonitor
            │   ├── BatteryMonitor
            │   ├── ThermalMonitor
            │   ├── SensorMonitor
            │   └── DeviceMonitor
            │
            ├── network/
            │   ├── NetworkMonitor
            │   ├── WifiMonitor
            │   ├── MobileDataMonitor
            │   ├── NetworkUsageMonitor
            │   └── LatencyMonitor
            │
            ├── storage/
            │   ├── StorageMonitor
            │   ├── FileScanner
            │   ├── LargeFileScanner
            │   ├── DuplicateScanner
            │   └── CleanupEngine
            │
            ├── apps/
            │   ├── AppManager
            │   ├── AppUsageMonitor
            │   └── AppPermissionManager
            │
            ├── health/
            │   ├── HealthScoreEngine
            │   ├── ProblemDetector
            │   └── RecommendationEngine
            │
            ├── notifications/
            │   ├── NotificationEngine
            │   ├── AlertPolicy
            │   └── NotificationHistory
            │
            ├── widgets/
            │   ├── HealthWidget
            │   ├── BatteryWidget
            │   ├── StorageWidget
            │   ├── NetworkWidget
            │   └── AlertWidget
            │
            ├── settings/
            │   └── SystemSettingsRouter
            │
            └── database/
                ├── TelemetryDatabase
                ├── HealthEventDao
                ├── StorageDao
                └── WidgetDao
```

The exact package/file names can change during implementation; the
important requirement is separation of UI, repositories, Android
services, health logic, notifications, widgets and storage.

------------------------------------------------------------------------

# 🔗 Desktop ↔ Android Feature Mapping

The desktop already contains service concepts such as hardware
telemetry, network telemetry, storage healing, problem detection,
platform capability detection and download organization. Android should
implement equivalent concepts using Android-native APIs.

  -----------------------------------------------------------------------
  Desktop Concept                     Android Equivalent
  ----------------------------------- -----------------------------------
  `HardwareTelemetryService`          `AndroidTelemetryService` / native
                                      monitors

  `NetworkTelemetryService`           `AndroidNetworkService`

  `HardwareProblemDetector`           `MobileHealthProblemDetector`

  `StorageHealerService`              `AndroidStorageEngine`

  `PlatformCapabilityService`         `AndroidCapabilityService`

  `DownloadOrganizerService`          `AndroidFileOrganizer` where
                                      storage access permits

  Desktop converter engines           Mobile converter engines using
                                      supported Android libraries/codecs

  Desktop health score                Mobile health score using
                                      phone-specific signals

  Desktop activity feed               Mobile health/activity feed
  -----------------------------------------------------------------------

The goal is **feature parity by intent**, not identical APIs. Desktop
and Android should behave like the same product while respecting
platform capabilities.

------------------------------------------------------------------------

# 🔐 Android Capability & Permission Model

PulseOS should maintain a capability layer so the UI knows what the
current phone supports.

Conceptually:

``` text
Android Version
      +
Device Hardware
      +
Granted Permissions
      +
Available APIs
      ↓
Platform Capability Service
      ↓
Feature Availability
```

Examples:

``` text
Battery temperature       AVAILABLE
Battery health             PARTIAL
CPU frequency              AVAILABLE
CPU temperature            NOT EXPOSED
App data usage             PERMISSION REQUIRED
Broad filesystem scan      RESTRICTED
Exact process termination  NOT ALLOWED
```

The UI should react to capability results instead of assuming every
Android phone supports the same functionality.

------------------------------------------------------------------------

# 🚫 Features That Must Not Be Faked on Android

Some desktop operations cannot be directly reproduced on a normal
Android application.

Do not fake:

-   Arbitrary process termination
-   Private directories of other applications
-   Other apps' private cache deletion
-   CPU voltage if not exposed
-   CPU temperature if not exposed
-   Battery health if not exposed
-   Exact per-app RAM when Android does not provide it
-   Full filesystem access without permission
-   System settings that Android does not permit the application to
    change

Instead, use one of:

``` text
Unavailable on this device

Permission required

Supported through Android Settings

Not permitted by Android
```

This is an explicit product quality requirement.

------------------------------------------------------------------------

# 🧠 AI Assistant for Mobile

The AI layer should be optional and should not be required for basic
device monitoring.

Core monitoring remains local and deterministic.

AI should be invoked for tasks such as:

-   Explain a health problem
-   Explain why the phone may be heating
-   Explain high storage consumption
-   Summarize performance trends
-   Explain network problems
-   Suggest safe next steps

Example:

``` text
User:
Why is my phone getting hot?

PulseOS collects:
CPU trend
RAM trend
Battery state
Charging state
Thermal state
Recent relevant app activity

        ↓
AI explanation
```

AI should not be called on every telemetry sample.

------------------------------------------------------------------------

# 🧭 User Experience Rule: Do Not Hide Features in Settings

The following actions should be directly reachable from the relevant
section:

``` text
Storage problem
    → Storage Healer

Battery problem
    → Hardware / Battery

Network problem
    → Smart Router

RAM problem
    → Overview / Hardware

Large files
    → Storage Healer

App data usage
    → Smart Router

Device sensors
    → Hardware

File conversion
    → Converter
```

Settings is for configuration and system navigation, not a dumping
ground for primary product features.

------------------------------------------------------------------------

# 🏠 Home Screen + Notification + App Integration

The mobile PulseOS experience extends outside the application itself:

``` text
                    PHONE
                      │
       ┌──────────────┼──────────────┐
       │              │              │
    PulseOS App    Widgets      Notifications
       │              │              │
       │              │              │
       └──────────────┼──────────────┘
                      │
                Quick Actions
```

A user should be able to understand the device's important state without
opening PulseOS every time.

------------------------------------------------------------------------

# 📋 Mobile Feature Checklist

## Overview

-   [ ] Health score
-   [ ] CPU card
-   [ ] RAM card
-   [ ] Battery card
-   [ ] Temperature card
-   [ ] Storage card
-   [ ] Network card
-   [ ] Problems section
-   [ ] Recent activity
-   [ ] Quick actions
-   [ ] CPU graph
-   [ ] RAM graph
-   [ ] Battery graph
-   [ ] Temperature graph

## Hardware

-   [ ] CPU / SoC
-   [ ] RAM
-   [ ] Battery
-   [ ] Thermal
-   [ ] Sensors
-   [ ] Device information
-   [ ] Capability detection

## Smart Router

-   [ ] Wi-Fi
-   [ ] Mobile data
-   [ ] Internet health
-   [ ] DNS
-   [ ] Latency
-   [ ] Signal where available
-   [ ] Data usage
-   [ ] App network usage where permitted
-   [ ] Network graph

## Storage Healer

-   [ ] Storage overview
-   [ ] Category breakdown
-   [ ] Large files
-   [ ] Downloads
-   [ ] Images
-   [ ] Videos
-   [ ] Documents
-   [ ] Archives
-   [ ] Duplicate detection
-   [ ] Safe cleanup
-   [ ] Cleanup verification
-   [ ] Storage graph

## Converter

-   [ ] Image conversion
-   [ ] PDF workflows
-   [ ] Document workflows
-   [ ] ZIP/archive workflows
-   [ ] Media workflows where supported
-   [ ] Conversion history

## Settings

-   [ ] Connectivity
-   [ ] Sound
-   [ ] Display
-   [ ] Battery
-   [ ] Security
-   [ ] Privacy
-   [ ] Permissions
-   [ ] Apps
-   [ ] Notifications
-   [ ] Storage
-   [ ] Location
-   [ ] Accessibility
-   [ ] Date/time
-   [ ] Language
-   [ ] Direct Android system navigation
-   [ ] Global PulseOS search

## Notifications

-   [ ] High temperature
-   [ ] High RAM
-   [ ] Low storage
-   [ ] High storage
-   [ ] Battery warning
-   [ ] Charging thermal warning
-   [ ] Network warning
-   [ ] Data-usage warning
-   [ ] Performance warning
-   [ ] Deduplication
-   [ ] Cooldown
-   [ ] User-controlled categories

## Widgets

-   [ ] Health
-   [ ] Battery
-   [ ] Storage
-   [ ] RAM
-   [ ] CPU
-   [ ] Temperature
-   [ ] Network
-   [ ] Alerts
-   [ ] Quick Actions
-   [ ] Widget customization

------------------------------------------------------------------------

# 🎯 Final Mobile Product Goal

PulseOS Mobile should feel like a **complete phone control and health
platform**, not a reduced desktop dashboard.

The intended experience is:

``` text
USER INSTALLS PULSOS
          ↓
PULSOS UNDERSTANDS THE PHONE
          ↓
PULSOS MONITORS REAL TELEMETRY
          ↓
PULSOS EXPLAINS DEVICE HEALTH
          ↓
PULSOS DETECTS PROBLEMS
          ↓
PULSOS NOTIFIES THE USER
          ↓
PULSOS OFFERS SAFE ACTIONS
          ↓
PULSOS VERIFIES THE RESULT
          ↓
USER CAN PIN IMPORTANT INFORMATION
TO THE HOME SCREEN
```

The final product should make the user feel that **the important parts
of the phone are available from one platform**, while still respecting
Android's security, permission, privacy, storage and
background-execution rules.
