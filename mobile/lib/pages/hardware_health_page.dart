import 'package:flutter/material.dart';
import '../services/telemetry_service.dart';
import '../widgets/common_widgets.dart';

class HardwareHealthPage extends StatefulWidget {
  final TelemetryController telemetry;

  const HardwareHealthPage({super.key, required this.telemetry});

  @override
  State<HardwareHealthPage> createState() => _HardwareHealthPageState();
}

class _HardwareHealthPageState extends State<HardwareHealthPage> {
  @override
  Widget build(BuildContext context) {
    final data = widget.telemetry.snapshot;
    final usedMemory = data.totalMemory == null || data.availableMemory == null
        ? null
        : (data.totalMemory! - data.availableMemory!).clamp(0, data.totalMemory!);
    final usedStorage = data.totalStorage == null || data.freeStorage == null
        ? null
        : data.totalStorage! - data.freeStorage!;
    final display = _stringKeyedMap(data.hardwareDetails['display']);
    final camera = _stringKeyedMap(data.hardwareDetails['camera']);
    final audio = _stringKeyedMap(data.hardwareDetails['audio']);
    final features = _stringKeyedMap(data.hardwareDetails['features']);
    final sensors = ((data.hardwareDetails['sensors'] as List?) ?? const [])
        .whereType<Map>()
        .map(_stringKeyedMap)
        .toList();
    final sensorSummary = sensors.isEmpty
        ? 'No sensors reported'
        : sensors.take(4).map((sensor) => '${sensor['name']}').join(', ');

    return ListView(
      padding: const EdgeInsets.fromLTRB(12, 12, 12, 20),
      children: [
        const PulseSectionTitle(
            'Hardware', 'Phone components and available readings'),
        PulseCard(
          onTap: () => _show(context, 'Device Details', _detail([
            'Model', data.model ?? 'Unavailable',
            'Android', data.androidVersion ?? 'Unavailable',
            'Processor cores', data.cpuCores?.toString() ?? 'Unavailable',
          ])),
          child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
            const PulseHeader('DEVICE', 'Device identity'),
            _info('Model', data.model ?? 'Unavailable'),
            _info('Android', data.androidVersion ?? 'Unavailable'),
            _info(
                'Processor cores', data.cpuCores?.toString() ?? 'Unavailable'),
          ]),
        ),
        PulseCard(
          onTap: () => _show(context, 'CPU Details', const Text('CPU monitoring active.')),
          child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
            const PulseHeader('CPU / SOC', 'Observed'),
            _info(
                'Current load',
                data.cpuUsage == null
                    ? 'Unavailable'
                    : '${data.cpuUsage!.toStringAsFixed(1)}%'),
            _info('Core count', data.cpuCores?.toString() ?? 'Unavailable'),
            _info('Frequency / throttling', 'Not collected by Android adapter'),
          ]),
        ),
        PulseCard(
          onTap: () => _show(context, 'Memory Details', const Text('RAM monitoring active.')),
          child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
            const PulseHeader('RAM / MEMORY', 'System memory'),
            _info('Used', _formatBytes(usedMemory)),
            _info('Available', _formatBytes(data.availableMemory)),
            _info('Total', _formatBytes(data.totalMemory)),
            const Text('Per-app memory visibility is limited by Android.',
                style: TextStyle(fontSize: 11, color: Color(0xFF9BA7AF))),
          ]),
        ),
        PulseCard(
          onTap: () => _show(
              context,
              'Battery and Charging',
              _detail([
                'Charge',
                data.batteryLabel,
                'Status',
                data.batteryStatus ?? 'Unavailable',
                'Health',
                _batteryHealthLabel(data.batteryHealth),
                'Temperature',
                _temperatureLabel(data.batteryTemperature),
                'Voltage',
                data.batteryVoltage == null || data.batteryVoltage! < 0
                    ? 'Unavailable'
                    : '${data.batteryVoltage} mV',
              ])),
          child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
            const PulseHeader('BATTERY / POWER', 'Observed'),
            _info('Charge', data.batteryLabel),
            _info('Status', data.batteryStatus ?? 'Unavailable'),
            _info('Battery health', _batteryHealthLabel(data.batteryHealth)),
            _info('Temperature', _temperatureLabel(data.batteryTemperature)),
          ]),
        ),
        PulseCard(
          onTap: () => _show(context, 'Thermal Details', const Text('Thermal monitoring active.')),
          child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
            const PulseHeader('THERMAL', 'Exposed temperature sensors'),
            _info('Battery sensor', _temperatureLabel(data.batteryTemperature)),
            _info(
                'Other sensors',
                data.temperatures.isEmpty
                    ? 'Not exposed by this Android adapter'
                    : '${data.temperatures.length} sensors'),
            const Text(
                'Temperature is shown only for sensors reported by the phone.',
                style: TextStyle(fontSize: 11, color: Color(0xFF9BA7AF))),
          ]),
        ),
        PulseCard(
          onTap: () => _show(
              context,
              'Display and Touch',
              _detail([
                'Resolution',
                '${display['widthPixels'] ?? 'Unavailable'} × ${display['heightPixels'] ?? 'Unavailable'}',
                'Pixel density',
                display['densityDpi'] == null
                    ? 'Unavailable'
                    : '${display['densityDpi']} dpi',
                'Refresh rate',
                display['refreshRate'] == null
                    ? 'Unavailable'
                    : '${(display['refreshRate'] as num).toStringAsFixed(0)} Hz',
                'Touchscreen',
                _yesNo(display['touchscreen']),
                'Multi-touch',
                _yesNo(display['multiTouch']),
                'Brightness',
                _brightnessLabel(display['brightness']),
              ])),
          child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
            const PulseHeader('DISPLAY & TOUCH', 'Screen hardware'),
            _info('Resolution',
                '${display['widthPixels'] ?? 'Unavailable'} × ${display['heightPixels'] ?? 'Unavailable'}'),
            _info(
                'Refresh rate',
                display['refreshRate'] == null
                    ? 'Unavailable'
                    : '${(display['refreshRate'] as num).toStringAsFixed(0)} Hz'),
            _info('Touchscreen', _yesNo(display['touchscreen'])),
            _info('Multi-touch', _yesNo(display['multiTouch'])),
          ]),
        ),
        PulseCard(
          child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
            const PulseHeader('CAMERA', 'Phone cameras'),
            _info('Camera count',
                camera['count'] == null ? 'Unavailable' : '${camera['count']}'),
            _info('Front camera', _yesNo(camera['front'])),
            _info('Flash', _yesNo(camera['flash'])),
          ]),
        ),
        PulseCard(
          child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
            const PulseHeader('MICROPHONE & SPEAKER', 'Audio hardware'),
            _info('Microphone', _yesNo(audio['microphone'])),
            _info(
                'Audio inputs',
                audio['inputCount'] == null
                    ? 'Unavailable'
                    : '${audio['inputCount']}'),
            _info(
                'Audio outputs',
                audio['outputCount'] == null
                    ? 'Unavailable'
                    : '${audio['outputCount']}'),
          ]),
        ),
        PulseCard(
          onTap: () => _show(
              context,
              'Phone Sensors',
              Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
                if (sensors.isEmpty)
                  const Text('The phone did not report sensor hardware.')
                else
                  for (final sensor in sensors)
                    _info('${sensor['name']}',
                        '${sensor['type']} · ${sensor['vendor']}'),
              ])),
          child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
            const PulseHeader('SENSORS', 'Phone sensor inventory'),
            _info('Available sensors', '${sensors.length}'),
            Text(sensorSummary,
                maxLines: 2,
                overflow: TextOverflow.ellipsis,
                style: const TextStyle(fontSize: 11, color: Color(0xFF9BA7AF))),
          ]),
        ),
        PulseCard(
          child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
            const PulseHeader('PHONE FEATURES', 'Available hardware'),
            _info('GPS',
                _featureLabel(features, 'gps', enabledKey: 'gpsEnabled')),
            _info('NFC',
                _featureLabel(features, 'nfc', enabledKey: 'nfcEnabled')),
            _info('Bluetooth', _yesNo(features['bluetooth'])),
            _info('Fingerprint', _yesNo(features['fingerprint'])),
            _info('Face biometrics', _yesNo(features['faceBiometric'])),
            _info('USB host', _yesNo(features['usbHost'])),
            _info('Vibration motor', _yesNo(features['vibrator'])),
          ]),
        ),
        PulseCard(
          onTap: () => _show(
              context,
              'Internal Storage',
              _detail([
                'Used',
                _formatBytes(usedStorage),
                'Free',
                _formatBytes(data.freeStorage),
                'Total',
                _formatBytes(data.totalStorage),
                'Storage type',
                'Internal phone storage',
              ])),
          child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
            const PulseHeader('INTERNAL STORAGE', 'Filesystem capacity'),
            _info('Used', _formatBytes(usedStorage)),
            _info('Free', _formatBytes(data.freeStorage)),
            _info('Total', _formatBytes(data.totalStorage)),
            const Text(
                'Only phone storage capacity is shown; personal files are not opened.',
                style: TextStyle(fontSize: 11, color: Color(0xFF9BA7AF))),
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

  String _yesNo(Object? value) => value == true
      ? 'Available'
      : value == false
          ? 'Not present'
          : 'Unavailable';

  String _brightnessLabel(Object? value) {
    if (value is! num) return 'Unavailable';
    return '${(value * 100 / 255).round()}%';
  }

  String _featureLabel(Map<String, dynamic> features, String key,
      {String? enabledKey}) {
    if (features[key] != true) return _yesNo(features[key]);
    if (enabledKey != null && features[enabledKey] == false) {
      return 'Available · off';
    }
    return 'Available';
  }

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

  String _batteryHealthLabel(int? value) {
    switch (value) {
      case 2: return 'Good';
      case 3: return 'Overheat';
      case 4: return 'Dead';
      default: return 'Unavailable';
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
}
