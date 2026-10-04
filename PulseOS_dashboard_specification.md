# PulseOS Dashboard and Device Health Specification

## Scope

PulseOS will provide two focused experiences:

- Desktop: Windows and macOS device health, performance, storage, network and system diagnostics.
- Mobile: Android device health and hardware-problem monitoring.

The first stage detects and explains problems. Detailed repair workflows and guided hardware tests can be added later.

## Product Principle

```text
MONITOR -> UNDERSTAND -> PREDICT -> INFORM
```

PulseOS must distinguish between:

- Healthy: no problem signal observed.
- Observed: an unusual reading has appeared.
- Suspected: repeated readings or events support a possible problem.
- Active: the problem is currently continuing.
- Resolved: the signal returned to normal.
- Unsupported: the operating system does not expose enough data.

Physical damage should be reported as Possible or Suspected unless the operating system provides reliable confirmation.

---

# 1. Shared Dashboard Structure

Both platforms use a clean, card-based overview. Cards show a compact preview and open a detailed view when clicked or tapped.

## Overview order

1. Header with PulseOS identity, live/limited status and refresh.
2. Overall Device Health Score.
3. Active Problems card.
4. Component preview grid.
5. Recent events and trend summary.
6. Platform capability and unsupported-data notes.

## Preview card anatomy

Each card contains:

- Component icon and name.
- Current headline value.
- Short status: Healthy, Warning, Active, Limited or Unsupported.
- One supporting metric.
- Small trend indicator when history exists.
- Tap/click affordance for details.

Example:

```text
BATTERY & CHARGING        Healthy
87%                       Charging
Temperature 31 C          Updated now
```

## Detail view anatomy

The detail view is a modal sheet on mobile and a dialog or side panel on desktop. It contains:

- Current readings.
- Historical graph where available.
- Detected problems.
- Evidence and timestamps.
- Confidence level.
- Platform limitations.
- Recommended next observation or safe action.
- Future placeholder for Run Diagnosis.

## Active Problems card

The dashboard must show active hardware and system problems clearly:

```text
ACTIVE PROBLEMS

Battery temperature abnormal
Warning | 44 C while charging | First detected 10 minutes ago

Wi-Fi connection unstable
Warning | 8 disconnects in 30 minutes | Last seen now
```

When no issue is active:

```text
No active hardware problems detected
```

One isolated reading must not create a hardware problem. Repeated readings, a persistent threshold breach or repeated system events are required.

---

# 2. Desktop Dashboard: Windows and macOS

## Desktop overview cards

### Device Health

Preview: overall score, severity and number of active issues.

Details: score components, trend history, current warnings and evidence.

### CPU and Processor

Detect or display:

- CPU usage and per-core utilization where available.
- Clock speed and frequency scaling.
- Excessive sustained load.
- Low-frequency behavior.
- Thermal throttling and power throttling indicators.
- Thread count and top processes.
- CPU temperature where exposed.

Card preview:

```text
CPU / PROCESSOR           Normal
42% usage                 3.1 GHz
Temperature 58 C          8 cores
```

### RAM and Memory

Detect or display:

- Used and available memory.
- Memory pressure.
- Swap/pagefile usage.
- Abnormal growth suggesting a leak.
- Apps consuming excessive memory.
- Repeated app reloads or memory-related crashes.
- Suspected RAM instability from reliable OS events, never as an automatic physical confirmation.

### GPU and Graphics

Detect or display when supported:

- GPU utilization, temperature, clock and VRAM.
- Thermal or power throttling.
- Driver reset events.
- Rendering errors.
- Integrated/dedicated GPU switching.
- External-display graphics problems.

GPU support depends on the GPU vendor and operating-system permissions.

### SSD, NVMe and HDD

Detect or display:

- SMART health and remaining-life indicators where available.
- Read/write errors and bad-sector warnings.
- Disk temperature.
- Read/write latency and queue pressure.
- Performance degradation against the device baseline.
- Intermittent disconnects and PCIe/NVMe link errors.
- HDD retries and mechanical-degradation symptoms.

The wording must remain Possible or Suspected for controller, cable or mechanical faults.

### Battery and Power

Detect or display:

- Charge, charging state, capacity health and cycle count.
- Fast drain and abnormal idle drain.
- Voltage or temperature anomaly.
- Unexpected shutdown under load.
- Charger detection and insufficient power delivery where exposed.
- USB-C power-delivery negotiation on supported hardware.
- Sleep blockers and power-management issues.

Windows uses power APIs and event data. macOS uses power-management data such as pmset and I/O information.

### Cooling and Thermal

Detect or display:

- CPU/GPU/SSD/battery temperature.
- Fan RPM where exposed.
- Temperature spikes and abnormal idle temperature.
- Thermal and power throttling.
- Cooling correlation:

```text
Temperature rises + fan response is low + clock speed falls
= Cooling-system problem suspected
```

### Network

Detect or display:

- Internet reachability.
- DNS resolution time and failure.
- Ping, latency, jitter and packet loss.
- Wi-Fi signal and disconnect history.
- Ethernet link speed, negotiation and errors.
- Per-process bandwidth where available.
- Background bandwidth consumption.

### Startup and Boot

Detect or display:

- Boot duration and historical change.
- Startup applications and services.
- Boot-impact ranking.
- Slow service or driver evidence.
- Boot-loop, recovery and unexpected restart events where logs expose them.

Windows sources include startup folders, Registry, Task Scheduler, services and Event Viewer. macOS sources include Login Items, launchd and unified logs.

### Crash and Error Center

Detect or display:

- Application crash history.
- Repeated crashes and freezes.
- Not-responding events.
- Unexpected shutdowns and restarts.
- Windows Error Reporting, Event Viewer and BSOD/minidump patterns.
- macOS crash reports and kernel panic history.

### Devices and Peripherals

Preview cards or grouped detail sections:

- USB and USB-C.
- Bluetooth.
- Webcam.
- Microphone.
- Speakers and audio output.
- External display.
- Keyboard and touchpad.
- SD card reader and optical drive where present.

Monitor device connection events, initialization failures, repeated disconnects and driver communication errors.

### OS, Driver and Security

Detect or display:

- Failed services and system component errors.
- Update failures.
- Driver crash, timeout, conflict and restart events.
- Filesystem errors.
- Sleep/wake failures.
- Unknown startup programs.
- Unexpected scheduled tasks or network connections.
- Security protection status.
- Firmware, BIOS/UEFI and Secure Boot information where relevant.

Security results must say Suspicious behavior detected unless reliable evidence supports a stronger statement. PulseOS must not claim malware from a single heuristic.

## Desktop platform differences

### Windows

- Performance Counters and WMI/CIM for hardware and performance.
- Event Viewer, Reliability Monitor and Windows Error Reporting.
- Device Manager problem codes.
- Services, Registry startup items and Task Scheduler.
- Power plans, pagefile and sleep blockers.
- DXGI/NVML or vendor APIs for GPU data.
- Windows storage APIs for SMART where supported.

### macOS

- system_profiler for hardware inventory.
- pmset for power and battery information.
- ioreg and I/O information for devices and sensors.
- Unified logging and crash reports.
- APFS and disk health information where exposed.
- launchd and Login Items.
- Thermal pressure, power assertions and Metal GPU information.
- Wi-Fi, USB and Thunderbolt event data.

## Desktop features already present in the project

- Live CPU, RAM, temperature, threads and clock telemetry.
- Battery percentage, health and cycle data where exposed.
- Process ranking and process termination.
- Storage category scanning.
- Build-artifact detection.
- Quarantine-based cleanup.
- Downloads watcher and organization.
- Offline AI cleanup explanation, thermal explanation and natural-language file search.
- Offline file conversion.
- Health score, trend history and activity log.

The new dashboard should extend these existing flows rather than replace them.

---

# 3. Android Mobile Dashboard

## Mobile overview cards

### Battery and Charging

Show charge, status, temperature, voltage and Android battery-health state. Detect repeated fast drain, charging interruption, slow charging, abnormal temperature and percentage inconsistency where enough history exists.

### Thermal

Show exposed battery and thermal sensors, temperature history and Android thermal status. Detect overheating, repeated temperature spikes, charging slowdown and thermal throttling when the device exposes the signal.

### CPU and SoC

Show overall CPU usage, core count and available frequency/thermal information. Detect sustained high load, low frequency and performance drop only where Android exposes reliable readings.

### GPU and Graphics

Show supported graphics information. Mark GPU temperature, frequency, VRAM and driver data as Limited or Unsupported when public Android APIs do not expose them.

### RAM and Memory

Show total/available RAM, memory pressure, visible process memory and history. Detect low memory, abnormal usage, repeated app reloads and memory-related app crashes.

### Internal Storage

Show total/free storage and app-owned cache data. Detect storage pressure, failed writes, app install/update failures and camera-save failures when observable. Android generally does not expose PC-style SMART data, so storage health is indirect.

### Wi-Fi

Show connection state, signal, link speed, DNS reachability, latency and disconnect history where permissions allow. Detect authentication failure, weak signal, packet loss and instability.

### Mobile Network and SIM

Show SIM state, registration, network type and signal where Android permissions and carrier APIs allow. Detect no service, repeated drops, data failure and registration instability.

### Bluetooth

Show adapter state, paired/connected devices and repeated disconnect events. Detect pairing failure, audio dropouts and reconnect loops.

### GPS and Location

Show provider availability, time to first fix, accuracy and location continuity after permission. Detect unavailable location, slow lock and poor accuracy.

### Display and Touch

Preview current display settings and user-reported events. Future interactive tests can cover touch grid, dead zones, multi-touch, color, brightness and refresh rate.

### Camera

Use permission-based checks for camera open, preview, autofocus, flash, save and recording. Camera sensor temperature or hardware communication issues are shown only when observable.

### Microphone and Audio

Use permission-based recording/output checks for microphone level, speaker output, earpiece, distortion reports and audio routing. No recording is stored unless explicitly initiated by the user.

### USB-C and Charging Port

Monitor accessory connection, charging state changes, USB data/OTG and audio routes where Android exposes them. Port contamination or physical damage remains Suspected only.

### Sensors

Check availability and sanity of accelerometer, gyroscope, proximity, ambient light, magnetometer, barometer and Hall sensor where present.

### Biometrics, NFC and Haptics

Use Android result codes, not biometric data, to show fingerprint/face authentication problems. Show NFC adapter/tag results and vibration/haptic availability.

### Apps and Background Activity

Show app usage, cache, excessive memory/battery/network activity, repeated crashes and background restrictions. Keep app-level issues separate from hardware problems.

## Mobile data limitations

Android does not expose every hardware metric consistently. GPU details, SSD health, modem internals, charging-IC temperature, PMIC status and physical-button condition may be Limited or Unsupported. The UI must say so instead of guessing.

## Mobile features already present in the project

- Battery percentage, status, temperature, voltage and health state.
- CPU usage and core count.
- RAM and internal storage figures.
- Visible process memory.
- App usage statistics with Usage Access.
- App-owned cache scan and safe cleanup.
- File conversion.
- Clickable basic telemetry cards and detail sheets.
- Local-first settings and health-alert preference.

---

# 4. Problem Detection and Evidence Model

Every problem record should contain:

- Stable ID.
- Component: battery, thermal, storage, Wi-Fi, etc.
- Hardware or software classification.
- Title and user-readable explanation.
- State.
- Severity.
- Confidence.
- Evidence values.
- First detected timestamp.
- Last seen timestamp.
- Occurrence count.
- Platform capability reference.

## Example problem record

```text
Component: Battery
Title: Battery temperature abnormal
Type: Hardware signal
State: Active
Severity: Warning
Confidence: 86%
Evidence: 44 C during charging across 6 samples
First detected: 2026-09-11 16:20
Last seen: 2026-09-11 16:25
```

## Cross-component rules

Examples:

```text
Temperature up + frequency down + sustained load
= Thermal throttling suspected
```

```text
Storage latency up + write errors + repeated app install failures
= Storage degradation suspected
```

```text
Wi-Fi disconnects + packet loss + DNS failures
= Wi-Fi or network-path instability suspected
```

```text
Low memory + repeated app reloads + memory pressure
= Memory pressure active
```

---

# 5. UI and Interaction Rules

- Use a restrained dark utility interface consistent with the current PulseOS visual language.
- Keep preview cards compact and scannable.
- Use color for status, not decoration: green healthy, amber warning, red active/critical, muted gray unsupported.
- A card tap/click always opens relevant data; it must never be a dead decorative card.
- Use graphs for history and tables for multiple events or processes.
- Show the source and capture time for technical values.
- Explain unavailable data in the detail view.
- Separate Active Hardware Problems from Active Software Problems.
- Keep future Run Diagnosis actions disabled or marked as coming later until implemented.

## Suggested desktop layout

```text
Top bar: PulseOS | Device | Live status | Refresh

Health score        Active problems        Latest event

CPU     RAM     GPU     Battery     Thermal     Storage
Network Startup Crash   USB         Audio       Display

Recent events and long-term trends
```

## Suggested mobile layout

```text
Top bar: PulseOS | Live status | Refresh

Health score
Active hardware problems

Battery   Thermal   CPU       RAM
Storage   Wi-Fi     Cellular  Bluetooth
Sensors   Display   Camera    Audio
USB-C     Biometrics  NFC     Apps

Recent events
```

---

# 6. Later Diagnosis Phase

The first release only detects and informs. A later phase can add guided tests:

- Battery and charging test.
- Touchscreen grid test.
- Speaker and microphone test.
- Camera and flash test.
- GPS and sensor test.
- Wi-Fi and Bluetooth test.
- USB-C accessory test.
- Storage extended test.
- RAM reboot-based test guidance.

The diagnosis flow should be user initiated, permission based and explicit about any reboot, recording, network traffic or test limitations.

# 7. Recommended Implementation Order

1. Shared problem model and Active Problems card.
2. Expand current battery, thermal, RAM and storage signals.
3. Add desktop network, SMART, startup and crash adapters.
4. Add Android Wi-Fi, cellular, Bluetooth and sensor adapters.
5. Add peripheral preview cards with Available, Limited and Unsupported states.
6. Add event history and intermittent-problem correlation.
7. Add cross-component rules and confidence scoring.
8. Add guided diagnosis in a later release.

This structure keeps the interface clean while allowing PulseOS to grow from a telemetry dashboard into a trustworthy device-health platform.