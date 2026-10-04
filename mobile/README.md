# PulseOS Android

PulseOS Android is the Android-only mobile companion to the PulseOS desktop app. It keeps the same six primary destinations while adapting readings and actions to Android APIs and permissions.

## Destinations

1. Overview: phone health score, battery, processor, memory, storage, and observed warnings.
2. Hardware: battery and charging, processor, RAM, display resolution/refresh/touch, cameras/flash, microphone/audio routes, sensors, GPS, NFC, Bluetooth, biometrics, USB host, and vibration motor when Android reports them.
3. Router: current Wi-Fi/mobile connection plus optional app screen-time, visible memory, and CPU data through Android Usage Access.
4. Storage: phone storage capacity, PulseOS-owned temporary files, reviewable app-cache estimates, and Android storage settings.
5. Convert: supported on-device file conversions without uploading files.
6. Settings: PulseOS preferences plus Android shortcuts for data usage, app time, app permissions/notifications, Wi-Fi/mobile network, Bluetooth, battery, display, sound, storage, location, security, NFC, date/time, language, and accessibility.

This client does not target iOS, Windows, macOS, Linux, or web. It does not include desktop process controls, desktop cleanup workflows, or desktop-only hardware panels. Android decides which system settings and hardware capabilities are available on each phone; PulseOS does not simulate missing values.

## Build and run

Requirements: Flutter SDK, Android SDK, and an Android device or emulator.

```bash
flutter pub get
flutter test
flutter run -d <android-device-id>
```

Create a release APK with:

```bash
flutter build apk --release
```

Usage Access is optional and is opened only when requested for app-time details. App storage cleanup is restricted to Android-supported cache APIs and user-confirmed actions. System-setting shortcuts open Android Settings; they do not silently change phone settings.
