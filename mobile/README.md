# PulseOS Mobile

Mobile client boundary for Android/iOS.

The desktop JavaFX client is intentionally preserved in `../desktop`. Mobile should share the PulseOS intelligence/data contracts but use native platform UI and permissions. The website contains a responsive mobile experience preview.

Planned mobile capabilities:
- Device health score
- Battery and storage intelligence
- Health trends
- Safe recommendations
- Cross-device PulseOS account/sync (optional future service)
- Platform-specific actions only where Android/iOS permissions allow

Do not port JavaFX/OSHI directly to mobile.
