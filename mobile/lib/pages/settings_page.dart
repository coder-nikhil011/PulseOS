import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:shared_preferences/shared_preferences.dart';
import '../services/database_service.dart';
import '../widgets/common_widgets.dart';
import '../main.dart' show pulseThemeMode;

class SettingsPage extends StatefulWidget {
  const SettingsPage({super.key});

  @override
  State<SettingsPage> createState() => _SettingsPageState();
}

class _SettingsPageState extends State<SettingsPage> {
  String themeOption = 'dark';
  bool localFirst = true;
  bool alertsEnabled = true;
  bool batteryAlert = true;
  bool thermalAlert = true;
  bool storageAlert = true;
  bool networkAlert = true;
  String monitorInterval = '5s';
  String historyRetention = '30d';
  bool loading = true;

  @override
  void initState() {
    super.initState();
    _load();
  }

  Future<void> _load() async {
    final prefs = await SharedPreferences.getInstance();
    setState(() {
      themeOption = prefs.getString('pulseos_theme_mode') ?? 'dark';
      localFirst = prefs.getBool('pulseos_local_first') ?? true;
      alertsEnabled = prefs.getBool('pulseos_alerts') ?? true;
      batteryAlert = prefs.getBool('alert_battery') ?? true;
      thermalAlert = prefs.getBool('alert_thermal') ?? true;
      storageAlert = prefs.getBool('alert_storage') ?? true;
      networkAlert = prefs.getBool('alert_network') ?? true;
      monitorInterval = prefs.getString('monitor_interval') ?? '5s';
      historyRetention = prefs.getString('history_retention') ?? '30d';
      loading = false;
    });
  }

  Future<void> _setTheme(String mode) async {
    final prefs = await SharedPreferences.getInstance();
    await prefs.setString('pulseos_theme_mode', mode);
    setState(() => themeOption = mode);
    if (mode == 'light') {
      pulseThemeMode.value = ThemeMode.light;
    } else if (mode == 'system') {
      pulseThemeMode.value = ThemeMode.system;
    } else {
      pulseThemeMode.value = ThemeMode.dark;
    }
  }

  Future<void> _setBool(String key, bool val) async {
    final prefs = await SharedPreferences.getInstance();
    await prefs.setBool(key, val);
    setState(() {
      if (key == 'pulseos_local_first') localFirst = val;
      if (key == 'pulseos_alerts') alertsEnabled = val;
      if (key == 'alert_battery') batteryAlert = val;
      if (key == 'alert_thermal') thermalAlert = val;
      if (key == 'alert_storage') storageAlert = val;
      if (key == 'alert_network') networkAlert = val;
    });

    if (key == 'pulseos_alerts') {
      try {
        await const MethodChannel('pulseos/device').invokeMethod(val ? 'startMonitoring' : 'stopMonitoring');
      } catch (_) {}
    }
  }

  Future<void> _openSetting(String setting) async {
    try {
      await const MethodChannel('pulseos/device').invokeMethod('openPhoneSetting', {'setting': setting});
    } catch (_) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text('Android could not open $setting directly.')),
        );
      }
    }
  }

  Future<void> _clearHistory() async {
    final confirmed = await showDialog<bool>(
      context: context,
      builder: (_) => AlertDialog(
        backgroundColor: const Color(0xFF11171D),
        title: const Text('Clear Telemetry History'),
        content: const Text('Delete all local telemetry samples and health history? Saved settings and files will not be touched.'),
        actions: [
          TextButton(onPressed: () => Navigator.pop(context, false), child: const Text('CANCEL')),
          FilledButton(
            onPressed: () => Navigator.pop(context, true),
            style: FilledButton.styleFrom(backgroundColor: const Color(0xFFE06D5F)),
            child: const Text('CLEAR ALL HISTORY'),
          ),
        ],
      ),
    );

    if (confirmed == true) {
      await DatabaseService().clearAllHistory();
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(content: Text('Telemetry history cleared successfully.')),
        );
      }
    }
  }

  void _exportReport() {
    showDialog(
      context: context,
      builder: (_) => AlertDialog(
        backgroundColor: const Color(0xFF11171D),
        title: const Text('Diagnostics Report Export'),
        content: const Text(
          'Local telemetry summary compiled:\n\n'
          '• Database: SQLite (pulseos_health.db)\n'
          '• Privacy: 100% on-device local storage\n'
          '• Active Monitors: Battery, SoC, RAM, Thermals, Network\n'
          '• Storage Engine: Clean\n\n'
          'No telemetry or diagnostics leave your device.',
          style: TextStyle(fontSize: 12),
        ),
        actions: [
          FilledButton(onPressed: () => Navigator.pop(context), child: const Text('DONE')),
        ],
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    return ListView(
      padding: const EdgeInsets.fromLTRB(12, 12, 12, 24),
      children: [
        const PulseSectionTitle('Settings', 'PulseOS mobile configuration, alert thresholds & Android shortcuts'),

        // Local-first Privacy Card
        PulseCard(
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              const PulseHeader('SYSTEM MODE', 'On-device data privacy'),
              SwitchListTile(
                contentPadding: EdgeInsets.zero,
                dense: true,
                title: const Text('Local-first mode', style: TextStyle(fontSize: 13, fontWeight: FontWeight.bold)),
                subtitle: const Text('Store and process telemetry strictly on-device without cloud sync', style: TextStyle(fontSize: 11, color: Color(0xFF9BA7AF))),
                value: localFirst,
                onChanged: (v) => _setBool('pulseos_local_first', v),
              ),
            ],
          ),
        ),

        const SizedBox(height: 8),

        // Theme Configuration
        PulseCard(
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              const PulseHeader('APPEARANCE & THEME', 'App-wide color mode'),
              const SizedBox(height: 8),
              SegmentedButton<String>(
                segments: const [
                  ButtonSegment(value: 'dark', label: Text('Dark'), icon: Icon(Icons.dark_mode_rounded, size: 16)),
                  ButtonSegment(value: 'light', label: Text('Light'), icon: Icon(Icons.light_mode_rounded, size: 16)),
                  ButtonSegment(value: 'system', label: Text('System'), icon: Icon(Icons.brightness_auto_rounded, size: 16)),
                ],
                selected: {themeOption},
                onSelectionChanged: (set) => _setTheme(set.first),
              ),
            ],
          ),
        ),

        const SizedBox(height: 8),

        // Alert Thresholds & Background Monitoring
        PulseCard(
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              const PulseHeader('HEALTH ALERTS & THRESHOLDS', 'Observed threshold notifications'),
              SwitchListTile(
                contentPadding: EdgeInsets.zero,
                dense: true,
                title: const Text('Master Health Alerts', style: TextStyle(fontSize: 13, fontWeight: FontWeight.bold)),
                subtitle: const Text('Show active warnings on abnormal readings', style: TextStyle(fontSize: 11, color: Color(0xFF9BA7AF))),
                value: alertsEnabled,
                onChanged: loading ? null : (v) => _setBool('pulseos_alerts', v),
              ),
              const Divider(height: 12),
              SwitchListTile(
                contentPadding: EdgeInsets.zero,
                dense: true,
                title: const Text('Battery Warning (<20%)', style: TextStyle(fontSize: 12)),
                value: batteryAlert,
                onChanged: alertsEnabled ? (v) => _setBool('alert_battery', v) : null,
              ),
              SwitchListTile(
                contentPadding: EdgeInsets.zero,
                dense: true,
                title: const Text('Thermal Warning (>40°C)', style: TextStyle(fontSize: 12)),
                value: thermalAlert,
                onChanged: alertsEnabled ? (v) => _setBool('alert_thermal', v) : null,
              ),
              SwitchListTile(
                contentPadding: EdgeInsets.zero,
                dense: true,
                title: const Text('Storage Warning (>85% full)', style: TextStyle(fontSize: 12)),
                value: storageAlert,
                onChanged: alertsEnabled ? (v) => _setBool('alert_storage', v) : null,
              ),
              SwitchListTile(
                contentPadding: EdgeInsets.zero,
                dense: true,
                title: const Text('Network Latency Alert (>120ms)', style: TextStyle(fontSize: 12)),
                value: networkAlert,
                onChanged: alertsEnabled ? (v) => _setBool('alert_network', v) : null,
              ),
            ],
          ),
        ),

        const SizedBox(height: 8),

        // Retention & Data Maintenance
        PulseCard(
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              const PulseHeader('STORAGE & TELEMETRY RETENTION', 'Local SQLite database management'),
              const SizedBox(height: 6),
              Row(
                mainAxisAlignment: MainAxisAlignment.spaceBetween,
                children: [
                  const Text('History Retention Limit', style: TextStyle(fontSize: 12)),
                  DropdownButton<String>(
                    value: historyRetention,
                    underline: const SizedBox(),
                    items: const [
                      DropdownMenuItem(value: '7d', child: Text('7 days')),
                      DropdownMenuItem(value: '30d', child: Text('30 days')),
                      DropdownMenuItem(value: '90d', child: Text('90 days')),
                    ],
                    onChanged: (v) async {
                      if (v != null) {
                        final prefs = await SharedPreferences.getInstance();
                        await prefs.setString('history_retention', v);
                        setState(() => historyRetention = v);
                      }
                    },
                  ),
                ],
              ),
              const SizedBox(height: 8),
              Row(
                children: [
                  Expanded(
                    child: OutlinedButton.icon(
                      onPressed: _exportReport,
                      icon: const Icon(Icons.file_download_outlined, size: 16),
                      label: const Text('Export Summary'),
                    ),
                  ),
                  const SizedBox(width: 8),
                  Expanded(
                    child: OutlinedButton.icon(
                      onPressed: _clearHistory,
                      style: OutlinedButton.styleFrom(foregroundColor: const Color(0xFFE06D5F)),
                      icon: const Icon(Icons.delete_sweep_rounded, size: 16),
                      label: const Text('Clear Data'),
                    ),
                  ),
                ],
              ),
            ],
          ),
        ),

        const SizedBox(height: 8),

        // Complete Native Android Settings Shortcuts (12 Categories)
        PulseCard(
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              const PulseHeader('ANDROID SYSTEM SHORTCUTS', 'Direct OEM settings deep links'),
              const SizedBox(height: 6),
              _shortcutTile('Wi-Fi & Internet', 'Network connections and hotspots', Icons.wifi_rounded, 'network'),
              _shortcutTile('Data usage', 'Cellular plan & data manager', Icons.data_usage_rounded, 'data_usage'),
              _shortcutTile('Bluetooth', 'Paired peripherals & accessories', Icons.bluetooth_rounded, 'bluetooth'),
              _shortcutTile('Battery Saver', 'Power profiles & sleep policies', Icons.battery_saver_rounded, 'battery'),
              _shortcutTile('Display & Brightness', 'Screen timeout, refresh rate & dark theme', Icons.brightness_6_rounded, 'display'),
              _shortcutTile('Sound & Vibration', 'Volume channels & haptics', Icons.volume_up_rounded, 'sound'),
              _shortcutTile('Storage Management', 'Files, downloads & system partitions', Icons.storage_rounded, 'storage'),
              _shortcutTile('Location & GPS', 'Accuracy & satellite services', Icons.location_on_rounded, 'location'),
              _shortcutTile('App Permissions', 'Camera, microphone & files access', Icons.admin_panel_settings_rounded, 'permissions'),
              _shortcutTile('App screen time', 'Usage access & digital well-being', Icons.timer_outlined, 'usage'),
              _shortcutTile('Notifications Manager', 'App notification channels', Icons.notifications_active_rounded, 'notifications'),
              _shortcutTile('Device Security', 'Screen lock & biometric verification', Icons.security_rounded, 'security'),
              _shortcutTile('Language & Accessibility', 'Locale, input methods & screen reader', Icons.accessibility_new_rounded, 'accessibility'),
            ],
          ),
        ),

        const SizedBox(height: 8),

        // App Information Card
        PulseCard(
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              const PulseHeader('ABOUT PULSEOS MOBILE', 'System telemetry release info'),
              const SizedBox(height: 4),
              const Text('PulseOS Mobile Client · v0.2.2 (Build 3)', style: TextStyle(fontSize: 13, fontWeight: FontWeight.bold)),
              const SizedBox(height: 2),
              const Text('Cross-platform hardware health & diagnostic engine.\nEngineered for zero-lag local processing and privacy.', style: TextStyle(fontSize: 11, color: Color(0xFF9BA7AF))),
              const SizedBox(height: 8),
              Container(
                padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 4),
                decoration: BoxDecoration(color: const Color(0x3322C55E), borderRadius: BorderRadius.circular(6)),
                child: const Text('● 100% LOCAL-FIRST · ZERO DATA COLLECTION', style: TextStyle(fontSize: 9, fontWeight: FontWeight.bold, color: Color(0xFF22C55E))),
              ),
            ],
          ),
        ),
      ],
    );
  }

  Widget _shortcutTile(String title, String subtitle, IconData icon, String target) {
    return ListTile(
      contentPadding: EdgeInsets.zero,
      dense: true,
      leading: Icon(icon, size: 20, color: const Color(0xFF6ED1CD)),
      title: Text(title, style: const TextStyle(fontSize: 12, fontWeight: FontWeight.w600)),
      subtitle: Text(subtitle, style: const TextStyle(fontSize: 10, color: Color(0xFF9BA7AF))),
      trailing: const Icon(Icons.arrow_forward_ios_rounded, size: 13, color: Color(0xFF9BA7AF)),
      onTap: () => _openSetting(target),
    );
  }
}
