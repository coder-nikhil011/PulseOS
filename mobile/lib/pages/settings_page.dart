import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

class PulseStyles {
  static const TextStyle kicker = TextStyle(
    fontSize: 11,
    letterSpacing: 1.2,
    fontWeight: FontWeight.w700,
    color: Color(0xFF9BA7AF),
  );
}

class PulseCard extends StatelessWidget {
  const PulseCard({super.key, required this.child});

  final Widget child;

  @override
  Widget build(BuildContext context) => Container(
        width: double.infinity,
        margin: const EdgeInsets.only(bottom: 12),
        padding: const EdgeInsets.all(12),
        decoration: BoxDecoration(
          color: const Color(0xFF10161C),
          borderRadius: BorderRadius.circular(12),
          border: Border.all(color: const Color(0xFF1D2A34)),
        ),
        child: child,
      );
}

class PulseSectionTitle extends StatelessWidget {
  const PulseSectionTitle(this.title, this.subtitle, {super.key});

  final String title;
  final String subtitle;

  @override
  Widget build(BuildContext context) => Padding(
        padding: const EdgeInsets.only(bottom: 12),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text(title, style: const TextStyle(fontSize: 20, fontWeight: FontWeight.w800)),
            const SizedBox(height: 4),
            Text(
              subtitle,
              style: const TextStyle(fontSize: 11, color: Color(0xFF9BA7AF)),
            ),
          ],
        ),
      );
}

class PulseHeader extends StatelessWidget {
  const PulseHeader(this.label, this.detail, {super.key});

  final String label;
  final String detail;

  @override
  Widget build(BuildContext context) => Padding(
        padding: const EdgeInsets.only(bottom: 8),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text(label, style: PulseStyles.kicker),
            const SizedBox(height: 4),
            Text(detail, style: const TextStyle(fontSize: 11, color: Color(0xFF9BA7AF))),
          ],
        ),
      );
}

class SettingsPage extends StatefulWidget {
  const SettingsPage({super.key});

  @override
  State<SettingsPage> createState() => _SettingsPageState();
}

class _SettingsPageState extends State<SettingsPage> {
  bool localFirst = true;
  bool alerts = true;
  bool loading = true;

  @override
  void initState() {
    super.initState();
    _load();
  }

  Future<void> _load() async {
    // SharedPreferences implementation would go here
    setState(() => loading = false);
  }

  Future<void> _set(String key, bool value) async {
    setState(() {
      if (key == 'local_first') localFirst = value;
      if (key == 'alerts') alerts = value;
    });
  }

  Future<void> _openSystemSettings(String setting) async {
    try {
      await const MethodChannel('pulseos/device').invokeMethod('openPhoneSetting', {'setting': setting});
    } catch (e) {
      if (!mounted) return;
      _show(context, 'Android Settings', const Text('This settings screen is unavailable.'));
    }
  }

  Widget _settingsLink(String title, String detail, IconData icon, String target) =>
      ListTile(
        contentPadding: EdgeInsets.zero,
        leading: Icon(icon, size: 21),
        title: Text(title, style: const TextStyle(fontSize: 13)),
        subtitle: Text(detail,
            style: const TextStyle(fontSize: 11, color: Color(0xFF9BA7AF))),
        trailing: const Icon(Icons.open_in_new, size: 16),
        onTap: () => _openSystemSettings(target),
      );

  void _show(BuildContext context, String title, Widget body) =>
      showModalBottomSheet(
          context: context,
          backgroundColor: const Color(0xFF10161C),
          showDragHandle: true,
          builder: (_) => Padding(
              padding: const EdgeInsets.all(16),
              child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
                Text(title, style: const TextStyle(fontSize: 18, fontWeight: FontWeight.w800)),
                const SizedBox(height: 12),
                body,
              ])));

  @override
  Widget build(BuildContext context) => ListView(
        padding: const EdgeInsets.all(12),
        children: [
          const PulseSectionTitle('Settings', 'PulseOS mobile client preferences'),
          PulseCard(
              child: Column(children: [
            SwitchListTile(
                contentPadding: EdgeInsets.zero,
                title: const Text('Local-first mode',
                    style: TextStyle(fontSize: 13)),
                subtitle: const Text('Prefer on-device processing',
                    style: TextStyle(fontSize: 11, color: Color(0xFF9BA7AF))),
                value: localFirst,
                onChanged:
                    loading ? null : (value) => _set('local_first', value)),
            SwitchListTile(
                contentPadding: EdgeInsets.zero,
                title:
                    const Text('Health alerts', style: TextStyle(fontSize: 13)),
                subtitle: const Text('Alert on supported, observed signals',
                    style: TextStyle(fontSize: 11, color: Color(0xFF9BA7AF))),
                value: alerts,
                onChanged: loading ? null : (value) => _set('alerts', value)),
          ])),
          PulseCard(
              child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                const PulseHeader('NETWORK & DATA', 'Phone connection settings'),
                _settingsLink('Wi-Fi and mobile network', 'Manage connections', Icons.wifi, 'network'),
                _settingsLink('Data usage', 'View app usage', Icons.data_usage, 'data_usage'),
                _settingsLink('Bluetooth', 'Manage devices', Icons.bluetooth, 'bluetooth'),
              ])),
          PulseCard(
              child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                const PulseHeader('APPS & PRIVACY', 'App time and access'),
                _settingsLink('App screen time', 'Digital wellbeing', Icons.timelapse, 'usage'),
                _settingsLink('App permissions', 'Review permissions', Icons.admin_panel_settings, 'permissions'),
                _settingsLink('App notifications', 'Manage notifications', Icons.notifications_outlined, 'notifications'),
                _settingsLink('Installed apps', 'App management', Icons.apps, 'apps'),
              ])),
          PulseCard(
              child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                const PulseHeader('DEVICE SETTINGS', 'Phone controls'),
                _settingsLink('Battery', 'Power settings', Icons.battery_5_bar, 'battery'),
                _settingsLink('Display', 'Brightness & screen', Icons.brightness_6, 'display'),
                _settingsLink('Sound', 'Volume & vibration', Icons.volume_up, 'sound'),
                _settingsLink('Storage', 'Phone storage', Icons.storage, 'storage'),
                _settingsLink('Location', 'GPS settings', Icons.location_on_outlined, 'location'),
                _settingsLink('Security', 'Lock screen', Icons.lock_outline, 'security'),
                _settingsLink('NFC', 'Contactless', Icons.nfc, 'nfc'),
                _settingsLink('Date & Time', 'Clock settings', Icons.schedule, 'date_time'),
                _settingsLink('Language', 'Input preferences', Icons.keyboard, 'language'),
                _settingsLink('Accessibility', 'System access', Icons.accessibility_new, 'accessibility'),
              ])),
          const PulseCard(
              child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
            Text('ABOUT', style: PulseStyles.kicker),
            SizedBox(height: 5),
            Text('PulseOS Mobile v0.2',
                style: TextStyle(fontWeight: FontWeight.w700)),
            SizedBox(height: 5),
            Text('MONITOR → UNDERSTAND → PREDICT → HEAL',
                style: TextStyle(
                    fontSize: 10,
                    color: Color(0xFF9BA7AF),
                    letterSpacing: 1)),
          ])),
        ],
      );
}
