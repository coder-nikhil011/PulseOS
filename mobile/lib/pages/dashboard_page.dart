import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import '../models/telemetry.dart';
import '../services/database_service.dart';
import '../widgets/common_widgets.dart';

class DashboardPage extends StatefulWidget {
  final TelemetrySnapshot data;
  final ValueChanged<int> onNavigate;

  const DashboardPage({super.key, required this.data, required this.onNavigate});

  @override
  State<DashboardPage> createState() => _DashboardPageState();
}

class _DashboardPageState extends State<DashboardPage> {
  String _activeTimeFilter = 'Live';
  Map<String, List<double>>? _rangeData;
  bool _loadingRange = false;

  Future<void> _changeFilter(String filter) async {
    if (filter == 'Live') {
      setState(() {
        _activeTimeFilter = 'Live';
        _rangeData = null;
      });
      return;
    }

    setState(() {
      _activeTimeFilter = filter;
      _loadingRange = true;
    });

    int hours = 1;
    if (filter == '24h') hours = 24;
    if (filter == '7d') hours = 24 * 7;
    if (filter == '30d') hours = 24 * 30;

    try {
      final rows = await DatabaseService().getTelemetryRange(hours);
      final cpus = <double>[];
      final rams = <double>[];
      final batts = <double>[];
      final temps = <double>[];
      final storages = <double>[];
      final lats = <double>[];

      final step = rows.length > 50 ? (rows.length / 50).ceil() : 1;
      for (int i = 0; i < rows.length; i += step) {
        final row = rows[i];
        final c = (row['cpu'] as num?)?.toDouble();
        final b = (row['battery_level'] as num?)?.toDouble();
        final t = (row['battery_temp'] as num?)?.toDouble();
        final l = (row['latency'] as num?)?.toDouble();
        final totR = (row['ram_total'] as num?)?.toDouble();
        final avR = (row['ram_available'] as num?)?.toDouble();
        final totS = (row['storage_total'] as num?)?.toDouble();
        final frS = (row['storage_free'] as num?)?.toDouble();

        if (c != null) cpus.add(c);
        if (b != null) batts.add(b);
        if (t != null) temps.add(t);
        if (l != null) lats.add(l);
        if (totR != null && avR != null && totR > 0) {
          rams.add(((totR - avR) * 100 / totR).clamp(0, 100));
        }
        if (totS != null && frS != null && totS > 0) {
          storages.add(((totS - frS) * 100 / totS).clamp(0, 100));
        }
      }

      if (mounted) {
        setState(() {
          _rangeData = {
            'cpu': cpus,
            'ram': rams,
            'battery': batts,
            'temp': temps,
            'storage': storages,
            'latency': lats,
          };
          _loadingRange = false;
        });
      }
    } catch (_) {
      if (mounted) setState(() => _loadingRange = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    final data = widget.data;
    final cpuList = _rangeData?['cpu'] ?? (data.cpuHistory.isNotEmpty ? data.cpuHistory : (data.cpuUsage != null ? [data.cpuUsage!] : [0.0]));
    final ramList = _rangeData?['ram'] ?? (data.memoryHistory.isNotEmpty ? data.memoryHistory : (data.memoryAvailablePercent != null ? [100.0 - data.memoryAvailablePercent!] : [0.0]));
    final battList = _rangeData?['battery'] ?? (data.batteryHistory.isNotEmpty ? data.batteryHistory : (data.battery != null ? [data.battery!] : [0.0]));
    final tempList = _rangeData?['temp'] ?? (data.temperatureHistory.isNotEmpty ? data.temperatureHistory : (data.batteryTemperature != null ? [data.batteryTemperature!] : [25.0]));
    final storageList = _rangeData?['storage'] ?? (data.storageHistory.isNotEmpty ? data.storageHistory : (data.storage != null ? [data.storage!.toDouble()] : [0.0]));
    final latList = _rangeData?['latency'] ?? (data.latencyHistory.isNotEmpty ? data.latencyHistory : [28.0]);

    return ListView(
      padding: const EdgeInsets.fromLTRB(12, 12, 12, 24),
      children: [
        const Text('OVERVIEW', style: PulseStyles.kicker),
        const SizedBox(height: 4),
        const Text('Device Health',
            style: TextStyle(fontSize: 21, fontWeight: FontWeight.w800)),
        const SizedBox(height: 12),

        // Hero Health Score Card
        PulseCard(
          onTap: () => _showDoctor(context, data),
          child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
            Row(
              mainAxisAlignment: MainAxisAlignment.spaceBetween,
              children: [
                const Text('DEVICE HEALTH SCORE', style: PulseStyles.kicker),
                PulseStatusBadge(
                  status: (data.score ?? 85) >= 80 ? 'NORMAL' : ((data.score ?? 85) >= 60 ? 'ATTENTION' : 'CRITICAL'),
                ),
              ],
            ),
            const SizedBox(height: 4),
            Row(
              crossAxisAlignment: CrossAxisAlignment.baseline,
              textBaseline: TextBaseline.alphabetic,
              children: [
                Text(data.score?.toString() ?? '—',
                    style: const TextStyle(fontSize: 54, fontWeight: FontWeight.w900)),
                const SizedBox(width: 8),
                const Text('/ 100', style: TextStyle(fontSize: 16, color: Color(0xFF9BA7AF), fontWeight: FontWeight.w700)),
              ],
            ),
            Text(
              data.score == null
                  ? 'Calculating live metrics…'
                  : 'Score calculated from battery, thermal, CPU, RAM, and storage health.',
              style: const TextStyle(fontSize: 11, color: Color(0xFF9BA7AF)),
            ),
            const SizedBox(height: 12),
            PulseHealthBar('Performance (CPU & RAM)', data.performance),
            PulseHealthBar('Hardware & Thermal', data.hardware),
            PulseHealthBar('Storage Capacity Used', data.storage),
            PulseHealthBar('Battery Level', data.battery?.round()),
          ]),
        ),

        const SizedBox(height: 8),

        // Quick Actions Row
        PulseCard(
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              const PulseHeader('QUICK ACTIONS', 'One-tap Android system shortcuts'),
              const SizedBox(height: 10),
              SingleChildScrollView(
                scrollDirection: Axis.horizontal,
                child: Row(
                  children: [
                    _quickActionBtn(
                      icon: Icons.battery_saver_rounded,
                      label: 'Battery Saver',
                      onTap: () => _openSystemSetting(context, 'battery'),
                    ),
                    const SizedBox(width: 8),
                    _quickActionBtn(
                      icon: Icons.cleaning_services_rounded,
                      label: 'Storage Healer',
                      onTap: () => widget.onNavigate(3),
                    ),
                    const SizedBox(width: 8),
                    _quickActionBtn(
                      icon: Icons.alt_route_rounded,
                      label: 'Network Test',
                      onTap: () => widget.onNavigate(2),
                    ),
                    const SizedBox(width: 8),
                    _quickActionBtn(
                      icon: Icons.timer_rounded,
                      label: 'App Usage',
                      onTap: () => widget.onNavigate(2),
                    ),
                    const SizedBox(width: 8),
                    _quickActionBtn(
                      icon: Icons.security_rounded,
                      label: 'Permissions',
                      onTap: () => _openSystemSetting(context, 'permissions'),
                    ),
                  ],
                ),
              ),
            ],
          ),
        ),

        const SizedBox(height: 8),

        // Summary Metric Cards Grid (Tapping routes to respective screens)
        const Text('TELEMETRY SUMMARY', style: PulseStyles.kicker),
        const SizedBox(height: 6),
        GridView.count(
          shrinkWrap: true,
          physics: const NeverScrollableScrollPhysics(),
          crossAxisCount: 2,
          crossAxisSpacing: 8,
          mainAxisSpacing: 8,
          childAspectRatio: 1.35,
          children: [
            PulseMetric(
              'CPU LOAD',
              data.cpuUsage == null ? 'Sampling…' : '${data.cpuUsage!.round()}%',
              '${data.cpuCores ?? 8} cores · Tap for SoC details',
              () => widget.onNavigate(1),
            ),
            PulseMetric(
              'RAM / MEMORY',
              _formatBytes(data.availableMemory),
              'Available · Tap for memory inspect',
              () => widget.onNavigate(1),
            ),
            PulseMetric(
              'BATTERY',
              data.batteryLabel,
              '${data.batteryStatus ?? 'Active'} · ${_temperatureLabel(data.batteryTemperature)}',
              () => widget.onNavigate(1),
            ),
            PulseMetric(
              'STORAGE',
              data.storage != null ? '${data.storage}% used' : 'Check storage',
              '${_formatBytes(data.freeStorage)} free · Tap to heal',
              () => widget.onNavigate(3),
            ),
            PulseMetric(
              'NETWORK',
              'Online',
              'Wi-Fi / Mobile · Tap for router',
              () => widget.onNavigate(2),
            ),
            PulseMetric(
              'TEMPERATURE',
              _temperatureLabel(data.batteryTemperature),
              (data.batteryTemperature ?? 0) > 42 ? 'Warm thermal state' : 'Optimal thermal zone',
              () => widget.onNavigate(1),
            ),
          ],
        ),

        const SizedBox(height: 12),

        // Interactive Graphs Section with Time Range Filters
        Row(
          mainAxisAlignment: MainAxisAlignment.spaceBetween,
          children: [
            const Text('LIVE & HISTORICAL GRAPHS', style: PulseStyles.kicker),
            if (_loadingRange)
              const SizedBox(
                width: 12,
                height: 12,
                child: CircularProgressIndicator(strokeWidth: 2),
              ),
          ],
        ),
        const SizedBox(height: 6),

        PulseInteractiveGraph(
          title: 'CPU Usage Trend',
          unit: '%',
          values: cpuList,
          minY: 0,
          maxY: 100,
          color: const Color(0xFF6ED1CD),
          activeFilter: _activeTimeFilter,
          onFilterChanged: _changeFilter,
        ),

        const SizedBox(height: 8),

        PulseInteractiveGraph(
          title: 'Memory (RAM) Pressure',
          unit: '%',
          values: ramList,
          minY: 0,
          maxY: 100,
          color: const Color(0xFF7FE0B2),
          activeFilter: _activeTimeFilter,
          onFilterChanged: _changeFilter,
        ),

        const SizedBox(height: 8),

        PulseInteractiveGraph(
          title: 'Battery Level & Drain',
          unit: '%',
          values: battList,
          minY: 0,
          maxY: 100,
          color: const Color(0xFFF7D58C),
          activeFilter: _activeTimeFilter,
          onFilterChanged: _changeFilter,
        ),

        const SizedBox(height: 8),

        PulseInteractiveGraph(
          title: 'Device Temperature',
          unit: '°C',
          values: tempList,
          minY: 20,
          maxY: 70,
          color: const Color(0xFFE06D5F),
          activeFilter: _activeTimeFilter,
          onFilterChanged: _changeFilter,
        ),

        const SizedBox(height: 8),

        PulseInteractiveGraph(
          title: 'Storage Consumption Trend',
          unit: '%',
          values: storageList,
          minY: 0,
          maxY: 100,
          color: const Color(0xFF9F75E8),
          activeFilter: _activeTimeFilter,
          onFilterChanged: _changeFilter,
        ),

        const SizedBox(height: 8),

        PulseInteractiveGraph(
          title: 'Network Latency Probe',
          unit: 'ms',
          values: latList,
          minY: 0,
          maxY: 150,
          color: const Color(0xFF8EC5FF),
          activeFilter: _activeTimeFilter,
          onFilterChanged: _changeFilter,
        ),

        const SizedBox(height: 12),

        // Active Problems / Alerts Card
        PulseCard(
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Row(
                mainAxisAlignment: MainAxisAlignment.spaceBetween,
                children: [
                  const PulseHeader('ACTIVE ALERTS & DIAGNOSTICS', 'Health warnings'),
                  Badge(
                    backgroundColor: data.healthAlerts.isEmpty ? const Color(0xFF22C55E) : const Color(0xFFE06D5F),
                    label: Text('${data.healthAlerts.length}'),
                  ),
                ],
              ),
              const SizedBox(height: 8),
              if (data.healthAlerts.isEmpty)
                const PulseProblemLine('No critical threshold breaches detected. All systems operational.', good: true)
              else
                ...data.healthAlerts.map((alert) => InkWell(
                      onTap: () {
                        final lower = alert.toLowerCase();
                        if (lower.contains('storage')) {
                          widget.onNavigate(3);
                        } else if (lower.contains('battery') || lower.contains('thermal') || lower.contains('ram')) {
                          widget.onNavigate(1);
                        } else if (lower.contains('network')) {
                          widget.onNavigate(2);
                        }
                      },
                      child: Container(
                        margin: const EdgeInsets.only(bottom: 6),
                        padding: const EdgeInsets.all(10),
                        decoration: BoxDecoration(
                          color: const Color(0xFF161E26),
                          borderRadius: BorderRadius.circular(8),
                          border: Border.all(color: const Color(0xFF2A3945)),
                        ),
                        child: Row(
                          children: [
                            const Icon(Icons.warning_amber_rounded, size: 18, color: Color(0xFFF59E0B)),
                            const SizedBox(width: 8),
                            Expanded(
                              child: Text(alert, style: const TextStyle(fontSize: 12, fontWeight: FontWeight.w700)),
                            ),
                            const Icon(Icons.chevron_right_rounded, size: 16, color: Color(0xFF9BA7AF)),
                          ],
                        ),
                      ),
                    )),
            ],
          ),
        ),

        const SizedBox(height: 8),

        // Activity Feed (Timestamped records)
        PulseCard(
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              const PulseHeader('RECENT ACTIVITY FEED', 'Audited actions and device checks'),
              const SizedBox(height: 8),
              ..._buildHealthTimeline(data).take(6).map((event) => Padding(
                    padding: const EdgeInsets.symmetric(vertical: 4),
                    child: Row(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        SizedBox(
                          width: 50,
                          child: Text(
                            event['time'] ?? 'Now',
                            style: const TextStyle(fontSize: 10, color: Color(0xFF9BA7AF), fontWeight: FontWeight.w600),
                          ),
                        ),
                        const SizedBox(width: 6),
                        Text(event['icon'] ?? '⚡', style: const TextStyle(fontSize: 13)),
                        const SizedBox(width: 8),
                        Expanded(
                          child: Text(
                            event['message'] ?? 'Telemetry check',
                            style: const TextStyle(fontSize: 11, color: Color(0xFFDDE5EA)),
                          ),
                        ),
                      ],
                    ),
                  )),
            ],
          ),
        ),
      ],
    );
  }

  Widget _quickActionBtn({
    required IconData icon,
    required String label,
    required VoidCallback onTap,
  }) {
    return InkWell(
      onTap: onTap,
      borderRadius: BorderRadius.circular(10),
      child: Container(
        padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 8),
        decoration: BoxDecoration(
          color: const Color(0xFF161E26),
          borderRadius: BorderRadius.circular(10),
          border: Border.all(color: const Color(0xFF283642)),
        ),
        child: Row(
          mainAxisSize: MainAxisSize.min,
          children: [
            Icon(icon, size: 16, color: const Color(0xFF6ED1CD)),
            const SizedBox(width: 6),
            Text(label, style: const TextStyle(fontSize: 11, fontWeight: FontWeight.w700)),
          ],
        ),
      ),
    );
  }

  List<Map<String, String>> _buildHealthTimeline(TelemetrySnapshot data) {
    if (data.recentEvents.isNotEmpty) {
      return data.recentEvents;
    }
    return data.buildRecentEventTimeline();
  }

  String _formatBytes(int? bytes) {
    if (bytes == null) return 'Unavailable';
    if (bytes < 1024) return '$bytes B';
    if (bytes < 1024 * 1024) return '${(bytes / 1024).toStringAsFixed(1)} KB';
    if (bytes < 1024 * 1024 * 1024) {
      return '${(bytes / (1024 * 1024)).toStringAsFixed(1)} MB';
    }
    return '${(bytes / (1024 * 1024 * 1024)).toStringAsFixed(2)} GB';
  }

  String _temperatureLabel(double? value) =>
      value == null ? 'Unavailable' : '${value.toStringAsFixed(1)} °C';

  Future<void> _openSystemSetting(BuildContext context, String setting) async {
    try {
      await const MethodChannel('pulseos/device').invokeMethod('openPhoneSetting', {'setting': setting});
    } catch (_) {
      if (context.mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text('Android could not open the $setting screen directly.')),
        );
      }
    }
  }

  void _showDoctor(BuildContext context, TelemetrySnapshot data) {
    showDialog(
      context: context,
      builder: (_) => AlertDialog(
        backgroundColor: const Color(0xFF11171D),
        title: const Text('PulseOS Health Doctor'),
        content: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text('Health Score: ${data.score ?? 85} / 100', style: const TextStyle(fontWeight: FontWeight.w800)),
            const SizedBox(height: 8),
            Text('CPU: ${data.cpuUsage?.round() ?? 0}% · RAM: ${_formatBytes(data.availableMemory)} available · Temp: ${_temperatureLabel(data.batteryTemperature)}'),
            const SizedBox(height: 12),
            const Text('All diagnostics pass with standard Android power and memory policies.', style: TextStyle(fontSize: 12, color: Color(0xFF9BA7AF))),
          ],
        ),
        actions: [
          TextButton(onPressed: () => Navigator.pop(context), child: const Text('DISMISS')),
          FilledButton(
            onPressed: () {
              Navigator.pop(context);
              widget.onNavigate(1); // route to Hardware Health
            },
            child: const Text('HARDWARE HEALTH →'),
          ),
        ],
      ),
    );
  }
}
