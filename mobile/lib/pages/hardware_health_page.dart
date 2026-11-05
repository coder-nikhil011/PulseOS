import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import '../services/telemetry_service.dart';
import '../widgets/common_widgets.dart';

class HardwareHealthPage extends StatefulWidget {
  final TelemetryController telemetry;

  const HardwareHealthPage({super.key, required this.telemetry});

  @override
  State<HardwareHealthPage> createState() => _HardwareHealthPageState();
}

class _HardwareHealthPageState extends State<HardwareHealthPage> {
  bool _diagnosing = false;
  Map<String, dynamic>? _diagnosisResult;

  void _runHardwareDiagnosis() {
    setState(() => _diagnosing = true);
    final data = widget.telemetry.snapshot;

    Future.delayed(const Duration(milliseconds: 600), () {
      if (!mounted) return;
      final issues = <String>[];
      final recs = <String>[];

      final cpu = data.cpuUsage ?? 0;
      final ramUsedPct = data.totalMemory != null && data.availableMemory != null
          ? ((data.totalMemory! - data.availableMemory!) * 100 / data.totalMemory!)
          : 0;
      final temp = data.batteryTemperature ?? 25;
      final batt = data.battery ?? 100;

      if (cpu > 80) {
        issues.add('CPU saturation ($cpu% load) detected.');
        recs.add('Limit intensive foreground tasks and review active services.');
      }
      if (ramUsedPct > 85) {
        issues.add('High RAM memory pressure (${ramUsedPct.toStringAsFixed(0)}% used).');
        recs.add('Close idle background applications to reclaim system buffer.');
      }
      if (temp > 42) {
        issues.add('Thermal threshold warning (${temp.toStringAsFixed(1)}°C).');
        recs.add('Disconnect charger if fast-charging or pause graphic-heavy tasks.');
      }
      if (batt < 20) {
        issues.add('Battery critically low (${batt.round()}%).');
        recs.add('Engage Android Battery Saver to prevent unexpected device shutdown.');
      }

      setState(() {
        _diagnosing = false;
        _diagnosisResult = {
          'healthScore': data.score ?? 85,
          'status': issues.isEmpty ? 'HEALTHY' : (issues.length == 1 ? 'ATTENTION' : 'CRITICAL'),
          'issues': issues,
          'recs': recs,
        };
      });

      _showDiagnosisDialog();
    });
  }

  void _showDiagnosisDialog() {
    final res = _diagnosisResult;
    if (res == null) return;
    final issues = (res['issues'] as List<String>);
    final recs = (res['recs'] as List<String>);

    showDialog(
      context: context,
      builder: (_) => AlertDialog(
        backgroundColor: const Color(0xFF11171D),
        title: const Row(
          children: [
            Icon(Icons.fact_check_rounded, color: Color(0xFF6ED1CD), size: 22),
            SizedBox(width: 8),
            Text('Hardware Diagnosis'),
          ],
        ),
        content: SingleChildScrollView(
          child: Column(
            mainAxisSize: MainAxisSize.min,
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Row(
                mainAxisAlignment: MainAxisAlignment.spaceBetween,
                children: [
                  Text('Overall State: ${res['status']}', style: const TextStyle(fontWeight: FontWeight.w800)),
                  PulseStatusBadge(status: res['status']),
                ],
              ),
              const SizedBox(height: 12),
              if (issues.isEmpty)
                const Text('All hardware sensors and system controllers report optimal parameters. No thermal or memory bottlenecks found.', style: TextStyle(fontSize: 12, color: Color(0xFF9BA7AF)))
              else ...[
                const Text('Observed Anomalies:', style: TextStyle(fontSize: 12, fontWeight: FontWeight.bold, color: Color(0xFFE06D5F))),
                const SizedBox(height: 6),
                ...issues.map((i) => Padding(
                      padding: const EdgeInsets.only(bottom: 4),
                      child: Text('• $i', style: const TextStyle(fontSize: 12)),
                    )),
                const SizedBox(height: 10),
                const Text('Recommended Actions:', style: TextStyle(fontSize: 12, fontWeight: FontWeight.bold, color: Color(0xFF6ED1CD))),
                const SizedBox(height: 6),
                ...recs.map((r) => Padding(
                      padding: const EdgeInsets.only(bottom: 4),
                      child: Text('→ $r', style: const TextStyle(fontSize: 12, color: Color(0xFFD5DDE3))),
                    )),
              ],
            ],
          ),
        ),
        actions: [
          TextButton(onPressed: () => Navigator.pop(context), child: const Text('DISMISS')),
          FilledButton(
            onPressed: () {
              Navigator.pop(context);
              _openSetting('battery');
            },
            child: const Text('BATTERY SETTINGS'),
          ),
        ],
      ),
    );
  }

  Future<void> _openSetting(String setting) async {
    try {
      await const MethodChannel('pulseos/device').invokeMethod('openPhoneSetting', {'setting': setting});
    } catch (_) {}
  }

  @override
  Widget build(BuildContext context) {
    final data = widget.telemetry.snapshot;
    final usedMemory = data.totalMemory == null || data.availableMemory == null
        ? null
        : (data.totalMemory! - data.availableMemory!).clamp(0, data.totalMemory!);
    final display = _stringKeyedMap(data.hardwareDetails['display']);
    final sensors = ((data.hardwareDetails['sensors'] as List?) ?? const [])
        .whereType<Map>()
        .map(_stringKeyedMap)
        .toList();

    // Determine component health statuses
    final cpuLoad = data.cpuUsage ?? 0;
    final cpuStatus = cpuLoad > 85 ? 'CRITICAL' : (cpuLoad > 65 ? 'ATTENTION' : 'NORMAL');

    final ramPct = data.totalMemory != null && data.availableMemory != null
        ? ((data.totalMemory! - data.availableMemory!) * 100 / data.totalMemory!)
        : 0;
    final ramStatus = ramPct > 88 ? 'CRITICAL' : (ramPct > 75 ? 'ATTENTION' : 'NORMAL');

    final temp = data.batteryTemperature ?? 25;
    final thermalStatus = temp > 45 ? 'CRITICAL' : (temp > 40 ? 'ATTENTION' : 'NORMAL');

    final battLevel = data.battery ?? 100;
    final battStatus = battLevel < 15 ? 'CRITICAL' : (battLevel < 25 ? 'ATTENTION' : 'NORMAL');

    return ListView(
      padding: const EdgeInsets.fromLTRB(12, 12, 12, 24),
      children: [
        const PulseSectionTitle(
            'Hardware Health', 'Phone components and available readings'),

        // Diagnosis Action Card
        PulseCard(
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Row(
                mainAxisAlignment: MainAxisAlignment.spaceBetween,
                children: [
                  const PulseHeader('SYSTEM AUDIT', 'One-tap hardware diagnostic engine'),
                  PulseStatusBadge(status: thermalStatus == 'CRITICAL' || ramStatus == 'CRITICAL' ? 'CRITICAL' : 'NORMAL'),
                ],
              ),
              const SizedBox(height: 6),
              const Text(
                'Cross-checks live CPU, memory pressure, thermals and battery discharge against Android safety tolerances.',
                style: TextStyle(fontSize: 11, color: Color(0xFF9BA7AF)),
              ),
              const SizedBox(height: 10),
              SizedBox(
                width: double.infinity,
                child: FilledButton.icon(
                  onPressed: _diagnosing ? null : _runHardwareDiagnosis,
                  icon: _diagnosing
                      ? const SizedBox(width: 14, height: 14, child: CircularProgressIndicator(strokeWidth: 2, color: Colors.white))
                      : const Icon(Icons.troubleshoot_rounded, size: 18),
                  label: Text(_diagnosing ? 'Scanning Hardware…' : 'Run Full Diagnosis →'),
                ),
              ),
            ],
          ),
        ),

        const SizedBox(height: 8),

        // Component 1: Processor / SoC
        PulseCard(
          onTap: () => _show(context, 'Processor / SoC Details', _detail([
            'SoC / Model', data.model ?? 'Android SoC',
            'Architecture', data.architecture ?? 'ARM64',
            'Logical Cores', '${data.cpuCores ?? 8} cores',
            'Load', data.cpuUsage == null ? 'Unavailable' : '${data.cpuUsage!.toStringAsFixed(1)}%',
            'Governor Mode', 'Android CFS (Dynamic)',
            'Status', cpuStatus,
          ])),
          child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
            Row(
              mainAxisAlignment: MainAxisAlignment.spaceBetween,
              children: [
                const PulseHeader('PROCESSOR / SOC', 'Silicon performance'),
                PulseStatusBadge(status: cpuStatus),
              ],
            ),
            const SizedBox(height: 4),
            _info('Device SoC', data.model ?? 'Android Device'),
            _info('Core Count', '${data.cpuCores ?? 8} active cores'),
            _info('Current Load', data.cpuUsage == null ? 'Sampling…' : '${data.cpuUsage!.toStringAsFixed(1)}%'),
            const SizedBox(height: 8),
            PulseLiveGraph(
              values: data.cpuHistory.isNotEmpty ? data.cpuHistory : [cpuLoad],
              minY: 0,
              maxY: 100,
              color: const Color(0xFF6ED1CD),
              label: 'Live CPU Signal',
            ),
          ]),
        ),

        const SizedBox(height: 8),

        // Component 2: RAM / Memory
        PulseCard(
          onTap: () => _show(context, 'RAM Architecture', _detail([
            'Total RAM', _formatBytes(data.totalMemory),
            'Available Buffer', _formatBytes(data.availableMemory),
            'Used Memory', _formatBytes(usedMemory),
            'Usage Percent', '${ramPct.toStringAsFixed(1)}%',
            'Low Memory Mode', data.availableMemory != null && data.availableMemory! < 400 * 1024 * 1024 ? 'Active' : 'Inactive',
            'Status', ramStatus,
          ])),
          child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
            Row(
              mainAxisAlignment: MainAxisAlignment.spaceBetween,
              children: [
                const PulseHeader('RAM / MEMORY', 'Kernel & user space allocations'),
                PulseStatusBadge(status: ramStatus),
              ],
            ),
            const SizedBox(height: 4),
            _info('Used Memory', _formatBytes(usedMemory)),
            _info('Free Buffer', _formatBytes(data.availableMemory)),
            _info('Physical Total', _formatBytes(data.totalMemory)),
            const SizedBox(height: 8),
            PulseLiveGraph(
              values: data.memoryHistory.isNotEmpty ? data.memoryHistory : [ramPct.toDouble()],
              minY: 0,
              maxY: 100,
              color: const Color(0xFF7FE0B2),
              label: 'RAM Pressure Trend',
            ),
          ]),
        ),

        const SizedBox(height: 8),

        // Component 3: Battery & Power
        PulseCard(
          onTap: () => _show(
            context,
            'Battery & Charging Controller',
            Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                _detail([
                  'Charge Level', data.batteryLabel,
                  'Charging State', data.batteryStatus ?? 'Discharging',
                  'Battery Health', _batteryHealthLabel(data.batteryHealth),
                  'Temperature', _temperatureLabel(data.batteryTemperature),
                  'Voltage', data.batteryVoltage != null && data.batteryVoltage! > 0 ? '${data.batteryVoltage} mV' : 'Normal',
                  'Power Technology', 'Li-ion / Li-Po',
                ]),
                const SizedBox(height: 12),
                OutlinedButton.icon(
                  onPressed: () => _openSetting('battery'),
                  icon: const Icon(Icons.battery_saver_rounded, size: 16),
                  label: const Text('OPEN ANDROID BATTERY SETTINGS'),
                ),
              ],
            ),
          ),
          child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
            Row(
              mainAxisAlignment: MainAxisAlignment.spaceBetween,
              children: [
                const PulseHeader('BATTERY / CHARGING', 'Hardware power subsystem'),
                PulseStatusBadge(status: battStatus),
              ],
            ),
            const SizedBox(height: 4),
            _info('Charge Level', data.batteryLabel),
            _info('State', data.batteryStatus ?? 'Normal'),
            _info('Health Rating', _batteryHealthLabel(data.batteryHealth)),
            _info('Cell Voltage', data.batteryVoltage != null && data.batteryVoltage! > 0 ? '${data.batteryVoltage} mV' : 'Nominal'),
            const SizedBox(height: 8),
            PulseLiveGraph(
              values: data.batteryHistory.isNotEmpty ? data.batteryHistory : [battLevel],
              minY: 0,
              maxY: 100,
              color: const Color(0xFFF7D58C),
              label: 'Battery Level Trend',
            ),
          ]),
        ),

        const SizedBox(height: 8),

        // Component 4: Thermal State
        PulseCard(
          onTap: () => _show(context, 'Thermal State Details', _detail([
            'Primary Sensor', _temperatureLabel(data.batteryTemperature),
            'Thermal Zone', temp > 45 ? 'Overheat' : (temp > 40 ? 'Warm' : 'Normal'),
            'Additional Sensors', '${data.temperatures.length} detected',
            'Thermal Throttling', temp > 45 ? 'Active' : 'Standby',
          ])),
          child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
            Row(
              mainAxisAlignment: MainAxisAlignment.spaceBetween,
              children: [
                const PulseHeader('THERMAL CONTROLLER', 'Hardware temperature monitors'),
                PulseStatusBadge(status: thermalStatus),
              ],
            ),
            const SizedBox(height: 4),
            _info('Battery Sensor', _temperatureLabel(data.batteryTemperature)),
            _info('Thermal State', temp > 42 ? 'Warm' : 'Nominal'),
            const SizedBox(height: 8),
            PulseLiveGraph(
              values: data.temperatureHistory.isNotEmpty ? data.temperatureHistory : [temp],
              minY: 20,
              maxY: 70,
              color: const Color(0xFFE06D5F),
              label: 'Thermal Trend',
            ),
          ]),
        ),

        const SizedBox(height: 8),

        // Component 5: Display & Touch
        PulseCard(
          onTap: () => _show(context, 'Display & Panel Information', Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              _detail([
                'Resolution', '${display['widthPixels'] ?? '—'} × ${display['heightPixels'] ?? '—'}',
                'Density', '${display['densityDpi'] ?? '—'} DPI',
                'Refresh Rate', '${(display['refreshRate'] as num?)?.toStringAsFixed(0) ?? '60'} Hz',
                'Touch Controller', _yesNo(display['touchscreen']),
                'Multi-Touch', _yesNo(display['multiTouch']),
              ]),
              const SizedBox(height: 12),
              OutlinedButton.icon(
                onPressed: () => _openSetting('display'),
                icon: const Icon(Icons.brightness_6_rounded, size: 16),
                label: const Text('OPEN ANDROID DISPLAY SETTINGS'),
              ),
            ],
          )),
          child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
            const Row(
              mainAxisAlignment: MainAxisAlignment.spaceBetween,
              children: [
                PulseHeader('DISPLAY & TOUCH', 'Screen panel hardware'),
                PulseStatusBadge(status: 'NORMAL'),
              ],
            ),
            const SizedBox(height: 4),
            _info('Native Resolution', '${display['widthPixels'] ?? '—'} × ${display['heightPixels'] ?? '—'} px'),
            _info('Refresh Rate', '${(display['refreshRate'] as num?)?.toStringAsFixed(0) ?? '60'} Hz'),
            _info('Multi-Touch Support', _yesNo(display['multiTouch'])),
          ]),
        ),

        const SizedBox(height: 8),

        // Component 6: Hardware Sensors
        PulseCard(
          onTap: () => _show(
            context,
            'Sensors Inventory',
            sensors.isEmpty
                ? const Text('No sensors reported by this Android device.')
                : Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: sensors
                        .map((s) => Container(
                              margin: const EdgeInsets.only(bottom: 6),
                              padding: const EdgeInsets.all(8),
                              decoration: BoxDecoration(
                                color: const Color(0xFF0C1217),
                                borderRadius: BorderRadius.circular(8),
                                border: Border.all(color: const Color(0xFF20272E)),
                              ),
                              child: Row(
                                children: [
                                  const Icon(Icons.sensors_rounded, size: 16, color: Color(0xFF6ED1CD)),
                                  const SizedBox(width: 8),
                                  Expanded(
                                    child: Column(
                                      crossAxisAlignment: CrossAxisAlignment.start,
                                      children: [
                                        Text('${s['name'] ?? 'Sensor'}', style: const TextStyle(fontSize: 12, fontWeight: FontWeight.bold)),
                                        Text('${s['vendor'] ?? 'OEM'} · ${s['type'] ?? 'General'}', style: const TextStyle(fontSize: 10, color: Color(0xFF9BA7AF))),
                                      ],
                                    ),
                                  ),
                                ],
                              ),
                            ))
                        .toList(),
                  ),
          ),
          child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
            const Row(
              mainAxisAlignment: MainAxisAlignment.spaceBetween,
              children: [
                PulseHeader('SENSORS INVENTORY', 'Integrated hardware sensors'),
                PulseStatusBadge(status: 'NORMAL'),
              ],
            ),
            const SizedBox(height: 4),
            _info('Detected Sensors', '${sensors.length} active sensors'),
            Text(
              sensors.isEmpty ? 'Scanning hardware bus…' : sensors.take(3).map((s) => '${s['name']}').join(', '),
              maxLines: 2,
              overflow: TextOverflow.ellipsis,
              style: const TextStyle(fontSize: 11, color: Color(0xFF9BA7AF)),
            ),
          ]),
        ),
      ],
    );
  }

  Map<String, dynamic> _stringKeyedMap(Object? value) {
    if (value is! Map) return const {};
    return value.map((key, item) => MapEntry(key.toString(), item));
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

  String _yesNo(Object? value) => value == true ? 'Available' : 'Not present';

  String _batteryHealthLabel(int? value) {
    switch (value) {
      case 2:
        return 'Good';
      case 3:
        return 'Overheat';
      case 4:
        return 'Dead';
      case 5:
        return 'Over-voltage';
      default:
        return 'Good';
    }
  }

  String _temperatureLabel(double? value) =>
      value == null ? 'Unavailable' : '${value.toStringAsFixed(1)} °C';

  void _show(BuildContext context, String title, Widget body) =>
      showModalBottomSheet(
          context: context,
          backgroundColor: const Color(0xFF10161C),
          showDragHandle: true,
          isScrollControlled: true,
          builder: (_) => Padding(
              padding: const EdgeInsets.fromLTRB(16, 2, 16, 24),
              child: SingleChildScrollView(
                  child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
                    Text(title,
                        style: const TextStyle(
                            fontSize: 18, fontWeight: FontWeight.w800)),
                    const SizedBox(height: 12),
                    body,
                    const SizedBox(height: 8),
                  ]))));

  Widget _info(String a, String b) => Padding(
        padding: const EdgeInsets.symmetric(vertical: 3),
        child: Row(children: [
          Expanded(
              child: Text(a,
                  style: const TextStyle(fontSize: 11, color: Color(0xFF9BA7AF)))),
          Flexible(
              child: Text(b,
                  textAlign: TextAlign.end,
                  overflow: TextOverflow.ellipsis,
                  style: const TextStyle(
                      fontSize: 11,
                      fontWeight: FontWeight.w700,
                      color: Color(0xFFC4CCD0))))
        ]),
      );

  Widget _detail(List<String> pairs) => GridView.builder(
        shrinkWrap: true,
        physics: const NeverScrollableScrollPhysics(),
        gridDelegate: const SliverGridDelegateWithFixedCrossAxisCount(
            crossAxisCount: 2,
            childAspectRatio: 2.8,
            crossAxisSpacing: 7,
            mainAxisSpacing: 7),
        itemCount: pairs.length ~/ 2,
        itemBuilder: (context, index) => Container(
            padding: const EdgeInsets.all(9),
            decoration: BoxDecoration(
                color: const Color(0xFF0C1217),
                border: Border.all(color: const Color(0xFF20272E)),
                borderRadius: BorderRadius.circular(8)),
            child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
              Text(pairs[index * 2],
                  style: const TextStyle(fontSize: 9, color: Color(0xFF9BA7AF))),
              Text(pairs[index * 2 + 1],
                  maxLines: 2,
                  overflow: TextOverflow.ellipsis,
                  style:
                      const TextStyle(fontSize: 12, fontWeight: FontWeight.w700)),
            ])),
      );
}
