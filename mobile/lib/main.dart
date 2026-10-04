import 'dart:async';
import 'dart:io';
import 'dart:isolate';
import 'dart:math' as math;

import 'package:archive/archive.dart';
import 'package:battery_plus/battery_plus.dart';
import 'package:file_picker/file_picker.dart';
import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:image/image.dart' as img;
import 'package:path_provider/path_provider.dart';
import 'package:pdf/widgets.dart' as pw;
import 'package:shared_preferences/shared_preferences.dart';
import 'package:video_player/video_player.dart';

void main() => runApp(const PulseOSLaunch());

class PulseOSLaunch extends StatefulWidget {
  const PulseOSLaunch({super.key});

  @override
  State<PulseOSLaunch> createState() => _PulseOSLaunchState();
}

class _PulseOSLaunchState extends State<PulseOSLaunch> {
  late final VideoPlayerController video;
  bool finished = false;

  @override
  void initState() {
    super.initState();
    SystemChrome.setEnabledSystemUIMode(SystemUiMode.immersiveSticky);
    video = VideoPlayerController.asset('assets/PulseOS_animation.mp4')
      ..initialize().then((_) {
        if (!mounted) return;
        video
          ..setLooping(false)
          ..play()
          ..addListener(_videoChanged);
        setState(() {});
      });
  }

  @override
  void dispose() {
    SystemChrome.setEnabledSystemUIMode(SystemUiMode.edgeToEdge);
    video
      ..removeListener(_videoChanged)
      ..dispose();
    super.dispose();
  }

  void _videoChanged() {
    if (!mounted || !video.value.isInitialized || finished) return;
    if (video.value.position >= video.value.duration) {
      setState(() => finished = true);
    }
  }

  @override
  Widget build(BuildContext context) {
    if (finished) return const PulseOSApp();
    return MaterialApp(
      debugShowCheckedModeBanner: false,
      title: 'PulseOS',
      theme: ThemeData(brightness: Brightness.light, fontFamily: 'Arial'),
      home: Scaffold(
        backgroundColor: Colors.black,
        body: video.value.isInitialized
            ? SizedBox.expand(
                child: FittedBox(
                  fit: BoxFit.cover,
                  clipBehavior: Clip.hardEdge,
                  child: SizedBox(
                    width: video.value.size.width,
                    height: video.value.size.height,
                    child: VideoPlayer(video),
                  ),
                ),
              )
            : const SizedBox.expand(),
      ),
    );
  }
}

class PulseOSApp extends StatelessWidget {
  const PulseOSApp({super.key});

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      debugShowCheckedModeBanner: false,
      title: 'PulseOS',
      theme: ThemeData(
        brightness: Brightness.dark,
        scaffoldBackgroundColor: const Color(0xFF090D11),
        colorScheme: const ColorScheme.dark(
          primary: Color(0xFFDDE4E9),
          surface: Color(0xFF10161C),
        ),
        useMaterial3: true,
        fontFamily: 'Arial',
      ),
      home: const PulseShell(),
    );
  }
}

class PulseShell extends StatefulWidget {
  const PulseShell({super.key});

  @override
  State<PulseShell> createState() => _PulseShellState();
}

class _PulseShellState extends State<PulseShell> {
  int index = 0;
  final telemetry = TelemetryController();
  Timer? timer;

  @override
  void initState() {
    super.initState();
    unawaited(_refreshTelemetry());
    timer = Timer.periodic(const Duration(seconds: 5), (_) {
      unawaited(_refreshTelemetry());
    });
  }

  Future<void> _refreshTelemetry() async {
    await telemetry.refresh();
    if (mounted) setState(() {});
  }

  @override
  void dispose() {
    timer?.cancel();
    telemetry.dispose();
    super.dispose();
  }

  void go(int next) => setState(() => index = next);

  @override
  Widget build(BuildContext context) {
    final pages = [
      DashboardPage(data: telemetry.snapshot, onNavigate: go),
      HardwareHealthPage(data: telemetry.snapshot),
      SmartRouterPage(data: telemetry.snapshot),
      const StorageHealerPage(),
      const ConverterPage(),
      const SettingsPage(),
    ];
    return Scaffold(
      appBar: AppBar(
        backgroundColor: const Color(0xFF0B0F13),
        surfaceTintColor: Colors.transparent,
        titleSpacing: 16,
        title: const Row(
          children: [
            _Logo(),
            SizedBox(width: 10),
            Flexible(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text('PulseOS',
                      style:
                          TextStyle(fontSize: 15, fontWeight: FontWeight.w800)),
                  Text('DEVICE HEALTH & HEALING',
                      overflow: TextOverflow.ellipsis,
                      style: TextStyle(
                          fontSize: 8,
                          letterSpacing: 1.15,
                          color: Color(0xFF9BA7AF))),
                ],
              ),
            ),
          ],
        ),
        actions: [
          _LivePill(available: telemetry.snapshot.hasLiveData),
          IconButton(
            tooltip: 'Refresh telemetry',
            onPressed: telemetry.refreshing ? null : _refreshTelemetry,
            icon: const Icon(Icons.refresh_rounded, size: 20),
          ),
        ],
      ),
      body: SafeArea(child: pages[index]),
      bottomNavigationBar: NavigationBar(
        backgroundColor: const Color(0xFF0B0F13),
        indicatorColor: const Color(0xFF1A2229),
        selectedIndex: index,
        onDestinationSelected: go,
        destinations: const [
          NavigationDestination(
              icon: Icon(Icons.dashboard_outlined),
              selectedIcon: Icon(Icons.dashboard),
              label: 'Overview'),
          NavigationDestination(
              icon: Icon(Icons.memory_outlined),
              selectedIcon: Icon(Icons.memory),
              label: 'Hardware'),
          NavigationDestination(
              icon: Icon(Icons.alt_route_outlined),
              selectedIcon: Icon(Icons.alt_route),
              label: 'Router'),
          NavigationDestination(
              icon: Icon(Icons.cleaning_services_outlined),
              selectedIcon: Icon(Icons.cleaning_services),
              label: 'Storage'),
          NavigationDestination(
              icon: Icon(Icons.swap_horiz_outlined),
              selectedIcon: Icon(Icons.swap_horiz),
              label: 'Convert'),
          NavigationDestination(
              icon: Icon(Icons.settings_outlined),
              selectedIcon: Icon(Icons.settings),
              label: 'Settings'),
        ],
      ),
    );
  }
}

/// Android exposes only the process rows and hardware inventory allowed by its
/// APIs. Unavailable per-app and vendor-specific readings remain null.
class TelemetrySnapshot {
  final double? battery;
  final DateTime? capturedAt;
  final String? error;
  final String? model;
  final String? manufacturer;
  final String? brand;
  final String? androidVersion;
  final String? securityPatch;
  final String? buildNumber;
  final String? architecture;
  final int? androidApi;
  final int? totalMemory;
  final int? availableMemory;
  final int? totalStorage;
  final int? freeStorage;
  final int? cpuCores;
  final double? cpuUsage;
  final int? processMemory;
  final List<Map<String, dynamic>> processes;
  final List<double> cpuHistory;
  final List<double> temperatureHistory;
  final List<double> memoryHistory;
  final String? batteryStatus;
  final double? batteryTemperature;
  final int? batteryVoltage;
  final int? batteryHealth;
  final List<Map<String, dynamic>> temperatures;
  final Map<String, dynamic> hardwareDetails;

  const TelemetrySnapshot({
    this.battery,
    this.capturedAt,
    this.error,
    this.model,
    this.manufacturer,
    this.brand,
    this.androidVersion,
    this.securityPatch,
    this.buildNumber,
    this.architecture,
    this.androidApi,
    this.totalMemory,
    this.availableMemory,
    this.totalStorage,
    this.freeStorage,
    this.cpuCores,
    this.cpuUsage,
    this.processMemory,
    this.processes = const [],
    this.cpuHistory = const [],
    this.temperatureHistory = const [],
    this.memoryHistory = const [],
    this.batteryStatus,
    this.batteryTemperature,
    this.batteryVoltage,
    this.batteryHealth,
    this.temperatures = const [],
    this.hardwareDetails = const {},
  });

  bool get hasLiveData => battery != null;
  String get batteryLabel =>
      battery == null ? 'Unavailable' : '${battery!.round()}%';
  String get capturedLabel =>
      capturedAt == null ? 'Not captured' : 'Updated just now';
  int? get storage => totalStorage == null || freeStorage == null
      ? null
      : ((totalStorage! - freeStorage!) * 100 / totalStorage!).round();
  int? get memoryAvailablePercent =>
      totalMemory == null || availableMemory == null
          ? null
          : (availableMemory! * 100 / totalMemory!).round();
  int? get performance => memoryAvailablePercent;
  int? get hardware {
    final signals = [
      cpuCores,
      androidApi,
      totalMemory,
      totalStorage,
    ].whereType<int>().length;
    return signals == 0 ? null : (signals * 25).clamp(0, 100);
  }

  int? get score {
    final values = [
      battery?.round(),
      storage == null ? null : 100 - storage!,
      performance
    ].whereType<int>().toList();
    if (values.length < 2) return null;
    return (values.reduce((a, b) => a + b) / values.length)
        .round()
        .clamp(0, 100);
  }

  List<String> get problems {
    final issues = <String>[];
    if (battery != null && battery! < 20) {
      issues.add('Battery charge is below 20%');
    }
    if (storage != null && storage! > 90) {
      issues.add('Storage is more than 90% full');
    }
    if (memoryAvailablePercent != null && memoryAvailablePercent! < 10) {
      issues.add('Available memory is below 10%');
    }
    return issues;
  }
}

class TelemetryController {
  final Battery _battery = Battery();
  TelemetrySnapshot snapshot = const TelemetrySnapshot();
  bool refreshing = false;
  final List<double> batteryHistory = <double>[];
  final List<double> cpuHistory = <double>[];
  final List<double> temperatureHistory = <double>[];
  final List<double> memoryHistory = <double>[];

  Future<void> refresh() async {
    if (refreshing) return;
    refreshing = true;
    try {
      final level = await _battery.batteryLevel;
      final device = await _readDeviceSnapshot();
      snapshot = TelemetrySnapshot(
        battery: level.toDouble(),
        capturedAt: DateTime.now(),
        model: device['model'] as String?,
        manufacturer: device['manufacturer'] as String?,
        brand: device['brand'] as String?,
        androidVersion: device['androidVersion'] as String?,
        securityPatch: device['securityPatch'] as String?,
        buildNumber: device['buildNumber'] as String?,
        architecture: device['architecture'] as String?,
        androidApi: (device['androidApi'] as num?)?.toInt(),
        totalMemory: (device['totalMemory'] as num?)?.toInt(),
        availableMemory: (device['availableMemory'] as num?)?.toInt(),
        totalStorage: (device['totalStorage'] as num?)?.toInt(),
        freeStorage: (device['freeStorage'] as num?)?.toInt(),
        cpuCores: (device['cpuCores'] as num?)?.toInt(),
        cpuUsage: (device['cpuUsage'] as num?)?.toDouble(),
        processMemory: (device['processMemory'] as num?)?.toInt(),
        processes: ((device['processes'] as List?) ?? const [])
            .whereType<Map>()
            .map((item) =>
                item.map((key, value) => MapEntry(key.toString(), value)))
            .toList(),
        cpuHistory:
            _history(cpuHistory, (device['cpuUsage'] as num?)?.toDouble()),
        temperatureHistory: _history(temperatureHistory,
            _optionalTemperature(device['batteryTemperature'])),
        memoryHistory: _history(
            memoryHistory,
            _memoryUsedPercent(
                device['totalMemory'], device['availableMemory'])),
        batteryStatus: device['batteryStatus'] as String?,
        batteryTemperature: _optionalTemperature(device['batteryTemperature']),
        batteryVoltage: (device['batteryVoltage'] as num?)?.toInt(),
        batteryHealth: (device['batteryHealth'] as num?)?.toInt(),
        temperatures: ((device['temperatures'] as List?) ?? const [])
            .whereType<Map>()
            .map((item) =>
                item.map((key, value) => MapEntry(key.toString(), value)))
            .toList(),
        hardwareDetails: _stringKeyedMap(device['hardware']),
      );
      batteryHistory
        ..add(level.toDouble())
        ..removeRange(
            0, batteryHistory.length > 60 ? batteryHistory.length - 60 : 0);
    } catch (error) {
      snapshot = TelemetrySnapshot(
          capturedAt: DateTime.now(),
          error: 'Battery telemetry is unavailable on this platform.');
    } finally {
      refreshing = false;
    }
  }

  List<double> _history(List<double> values, double? next) {
    if (next != null) {
      values.add(next);
      if (values.length > 60) values.removeAt(0);
    }
    return List<double>.unmodifiable(values);
  }

  void dispose() {}

  Future<Map<String, dynamic>> _readDeviceSnapshot() async {
    try {
      final result = await const MethodChannel('pulseos/device')
          .invokeMethod<Map<Object?, Object?>>('snapshot');
      final values =
          result?.map((key, value) => MapEntry(key.toString(), value)) ?? {};
      if (values['cpuUsage'] == null) {
        try {
          values['cpuUsage'] = await const MethodChannel('pulseos/device')
              .invokeMethod<num>('cpuUsage');
        } on PlatformException {
          // Keep the rest of the telemetry when CPU sampling is unavailable.
        }
      }
      return values;
    } on PlatformException {
      return {};
    } on MissingPluginException {
      return {};
    }
  }
}

Map<String, dynamic> _stringKeyedMap(Object? value) {
  if (value is! Map) return const {};
  return value.map((key, item) => MapEntry(key.toString(), item));
}

double? _memoryUsedPercent(Object? totalValue, Object? availableValue) {
  final total = (totalValue as num?)?.toDouble();
  final available = (availableValue as num?)?.toDouble();
  if (total == null || available == null || total <= 0) return null;
  return ((total - available) * 100 / total).clamp(0, 100);
}

class DashboardPage extends StatelessWidget {
  final TelemetrySnapshot data;
  final ValueChanged<int> onNavigate;

  const DashboardPage(
      {super.key, required this.data, required this.onNavigate});

  @override
  Widget build(BuildContext context) {
    return ListView(
      padding: const EdgeInsets.fromLTRB(12, 12, 12, 20),
      children: [
        const Text('OVERVIEW', style: _kicker),
        const SizedBox(height: 4),
        const Text('Device Health',
            style: TextStyle(fontSize: 21, fontWeight: FontWeight.w800)),
        const SizedBox(height: 12),
        _Card(
          child:
              Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
            const Text('DEVICE HEALTH SCORE', style: _kicker),
            const SizedBox(height: 4),
            Text(data.score?.toString() ?? '—',
                style:
                    const TextStyle(fontSize: 54, fontWeight: FontWeight.w900)),
            Text(
              data.score == null
                  ? 'Waiting for supported device metrics'
                  : '${data.score}/100 from observed metrics',
              style: const TextStyle(fontSize: 11, color: Color(0xFF9BA7AF)),
            ),
            const SizedBox(height: 10),
            const Text(
              'Score uses observed battery, storage, and memory only. Unsupported Android metrics are excluded.',
              style: TextStyle(fontSize: 11, color: Color(0xFF9BA7AF)),
            ),
            const SizedBox(height: 12),
            _HealthBar('Performance', data.performance),
            _HealthBar('Hardware', data.hardware),
            _HealthBar('Storage used', data.storage),
            _HealthBar('Battery charge', data.battery?.round()),
          ]),
        ),
        const SizedBox(height: 8),
        const Text('AVAILABLE TELEMETRY', style: _kicker),
        const SizedBox(height: 6),
        LayoutBuilder(
          builder: (context, constraints) {
            final columns = constraints.maxWidth >= 600 ? 3 : 2;
            return GridView.count(
              shrinkWrap: true,
              physics: const NeverScrollableScrollPhysics(),
              crossAxisCount: columns,
              crossAxisSpacing: 7,
              mainAxisSpacing: 7,
              childAspectRatio: columns == 2 ? 1.1 : 1.45,
              children: [
                _Metric(
                    'BATTERY',
                    data.batteryLabel,
                    'Android BatteryManager',
                    () => _show(
                        context,
                        'Battery Details',
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
                          data.batteryVoltage == null ||
                                  data.batteryVoltage! < 0
                              ? 'Unavailable'
                              : '${data.batteryVoltage} mV',
                        ]))),
                _Metric(
                    'CPU',
                    data.cpuUsage == null
                        ? 'Sampling…'
                        : '${data.cpuUsage!.round()}%',
                    'Android Runtime',
                    () => _showCpuDetails(context, data)),
                _Metric('MEMORY', _formatBytes(data.availableMemory),
                    'Available RAM', () => _showMemoryDetails(context, data)),
                _Metric(
                    'TEMPERATURE',
                    data.batteryTemperature == null
                        ? 'Unavailable'
                        : _temperatureLabel(data.batteryTemperature),
                    data.temperatures.isEmpty
                        ? 'Battery sensor only'
                        : '${data.temperatures.length} sensors',
                    () => _showTemperatureDetails(context, data)),
                _Metric(
                    'DEVICE',
                    data.model ?? 'Unavailable',
                    data.androidApi == null
                        ? 'Android details unavailable'
                        : 'Android API ${data.androidApi}',
                    () => _showDeviceDetails(context, data)),
                _Metric(
                    'PROCESSES',
                    data.processes.isEmpty
                        ? _formatBytes(data.processMemory)
                        : '${data.processes.length} visible',
                    'Visible processor and memory status',
                    () => _showProcessDetails(context, data)),
              ],
            );
          },
        ),
        const SizedBox(height: 8),
        _Card(
          child:
              Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
            const _Header('TELEMETRY STATUS', 'What PulseOS can read'),
            const SizedBox(height: 8),
            _info('Battery charge', data.batteryLabel),
            _info(
                'System CPU / RAM',
                data.availableMemory == null
                    ? 'Unavailable on Android'
                    : '${_formatBytes(data.availableMemory)} available'),
            _info(
                'Thermal sensors', _temperatureLabel(data.batteryTemperature)),
            _info('Last capture', data.capturedLabel),
            if (data.error != null)
              Text(data.error!,
                  style:
                      const TextStyle(color: Color(0xFFE1B39A), fontSize: 11)),
          ]),
        ),
        _Card(
          child:
              Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
            const _Header('ATTENTION', 'Active Problems'),
            const SizedBox(height: 8),
            if (data.problems.isEmpty)
              const _ProblemLine('No observed threshold breaches are reported',
                  good: true)
            else
              for (final problem in data.problems) _ProblemLine(problem),
            const SizedBox(height: 8),
            Row(children: [
              Expanded(
                  child: OutlinedButton(
                      onPressed: () => _unavailable(context, 'System fixes'),
                      child: const Text('REVIEW LIMITS'))),
              const SizedBox(width: 8),
              Expanded(
                  child: FilledButton(
                      onPressed: () => _show(
                          context,
                          'Safe Action',
                          const Text(
                              'PulseOS does not terminate processes or claim cleanup without an Android-supported API.')),
                      child: const Text('SAFE ACTION'))),
            ]),
          ]),
        ),
        _Card(
          child:
              Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
            const _Header('STORAGE', 'App storage only'),
            const SizedBox(height: 8),
            const Text(
                'Android restricts access to other apps and shared storage. Open Storage to scan PulseOS-owned files; device-wide figures are not claimed.',
                style: TextStyle(fontSize: 11, color: Color(0xFF9BA7AF))),
            const SizedBox(height: 8),
            OutlinedButton(
                onPressed: () => onNavigate(3),
                child: const Text('OPEN STORAGE HEALER')),
          ]),
        ),
        const _Card(
            child:
                Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
          _Header('EVENTS', 'Recent Activity'),
          SizedBox(height: 6),
          Text('No events recorded in this session.',
              style: TextStyle(fontSize: 11, color: Color(0xFF9BA7AF))),
        ])),
      ],
    );
  }
}

class HardwareHealthPage extends StatelessWidget {
  final TelemetrySnapshot data;

  const HardwareHealthPage({super.key, required this.data});

  @override
  Widget build(BuildContext context) {
    final usedMemory = data.totalMemory == null || data.availableMemory == null
        ? null
        : (data.totalMemory! - data.availableMemory!)
            .clamp(0, data.totalMemory!);
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
        const _SectionTitle(
            'Hardware', 'Phone components and available readings'),
        _Card(
          onTap: () => _showDeviceDetails(context, data),
          child:
              Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
            const _Header('DEVICE', 'Device identity'),
            _info('Model', data.model ?? 'Unavailable'),
            _info('Android', data.androidVersion ?? 'Unavailable'),
            _info(
                'Processor cores', data.cpuCores?.toString() ?? 'Unavailable'),
          ]),
        ),
        _Card(
          onTap: () => _showCpuDetails(context, data),
          child:
              Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
            const _Header('CPU / SOC', 'Observed'),
            _info(
                'Current load',
                data.cpuUsage == null
                    ? 'Unavailable'
                    : '${data.cpuUsage!.toStringAsFixed(1)}%'),
            _info('Core count', data.cpuCores?.toString() ?? 'Unavailable'),
            _info('Frequency / throttling', 'Not collected by Android adapter'),
          ]),
        ),
        _Card(
          onTap: () => _showMemoryDetails(context, data),
          child:
              Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
            const _Header('RAM / MEMORY', 'System memory'),
            _info('Used', _formatBytes(usedMemory)),
            _info('Available', _formatBytes(data.availableMemory)),
            _info('Total', _formatBytes(data.totalMemory)),
            const Text('Per-app memory visibility is limited by Android.',
                style: TextStyle(fontSize: 11, color: Color(0xFF9BA7AF))),
          ]),
        ),
        _Card(
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
          child:
              Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
            const _Header('BATTERY / POWER', 'Observed'),
            _info('Charge', data.batteryLabel),
            _info('Status', data.batteryStatus ?? 'Unavailable'),
            _info('Battery health', _batteryHealthLabel(data.batteryHealth)),
            _info('Temperature', _temperatureLabel(data.batteryTemperature)),
          ]),
        ),
        _Card(
          onTap: () => _showTemperatureDetails(context, data),
          child:
              Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
            const _Header('THERMAL', 'Exposed temperature sensors'),
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
        _Card(
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
          child:
              Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
            const _Header('DISPLAY & TOUCH', 'Screen hardware'),
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
        _Card(
          child:
              Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
            const _Header('CAMERA', 'Phone cameras'),
            _info('Camera count',
                camera['count'] == null ? 'Unavailable' : '${camera['count']}'),
            _info('Front camera', _yesNo(camera['front'])),
            _info('Flash', _yesNo(camera['flash'])),
          ]),
        ),
        _Card(
          child:
              Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
            const _Header('MICROPHONE & SPEAKER', 'Audio hardware'),
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
        _Card(
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
          child:
              Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
            const _Header('SENSORS', 'Phone sensor inventory'),
            _info('Available sensors', '${sensors.length}'),
            Text(sensorSummary,
                maxLines: 2,
                overflow: TextOverflow.ellipsis,
                style: const TextStyle(fontSize: 11, color: Color(0xFF9BA7AF))),
          ]),
        ),
        _Card(
          child:
              Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
            const _Header('PHONE FEATURES', 'Available hardware'),
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
        _Card(
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
          child:
              Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
            const _Header('INTERNAL STORAGE', 'Filesystem capacity'),
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
}

class SmartRouterPage extends StatefulWidget {
  final TelemetrySnapshot data;

  const SmartRouterPage({super.key, required this.data});

  @override
  State<SmartRouterPage> createState() => _SmartRouterPageState();
}

class _SmartRouterPageState extends State<SmartRouterPage>
    with WidgetsBindingObserver {
  bool loading = false;
  bool allowed = false;
  List<Map<String, dynamic>> leaders = const [];
  String? error;

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addObserver(this);
    unawaited(_scanUsage());
  }

  @override
  void dispose() {
    WidgetsBinding.instance.removeObserver(this);
    super.dispose();
  }

  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    if (state == AppLifecycleState.resumed) unawaited(_scanUsage());
  }

  Future<void> _scanUsage() async {
    setState(() {
      loading = true;
      error = null;
    });
    try {
      final result = await const MethodChannel('pulseos/device')
          .invokeMethod<Map<Object?, Object?>>('usageStats');
      final values =
          result?.map((key, value) => MapEntry(key.toString(), value));
      allowed = values?['allowed'] == true;
      if (!allowed) {
        if (mounted) {
          setState(() {
            loading = false;
            error = 'Allow Usage Access to view app screen time.';
          });
        }
        return;
      }
      leaders = ((values?['leaders'] as List?) ?? const [])
          .whereType<Map>()
          .map((item) =>
              item.map((key, value) => MapEntry(key.toString(), value)))
          .toList();
    } on PlatformException catch (exception) {
      error = exception.message ?? 'Usage data is unavailable.';
    } on MissingPluginException {
      error = 'Usage data is available only on Android.';
    }
    if (mounted) setState(() => loading = false);
  }

  Future<void> _openUsageSettings() async {
    try {
      await const MethodChannel('pulseos/device')
          .invokeMethod('openPhoneSetting', {'setting': 'usage'});
    } on PlatformException catch (exception) {
      if (mounted) setState(() => error = exception.message);
    } on MissingPluginException {
      if (mounted) setState(() => error = 'Android settings are unavailable.');
    }
  }

  @override
  Widget build(BuildContext context) => ListView(
        padding: const EdgeInsets.all(12),
        children: [
          const _SectionTitle('Router', 'Network connection and app activity'),
          _Card(
              child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                const _Header('PHONE CONNECTION', 'Current network'),
                _info('Connection',
                    '${_stringKeyedMap(widget.data.hardwareDetails['connectivity'])['transport'] ?? 'Unavailable'}'),
                _info(
                    'Internet',
                    _yesNo(_stringKeyedMap(widget
                        .data.hardwareDetails['connectivity'])['validated'])),
              ])),
          _Card(
              child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                const _Header('APPS', 'Usage Access'),
                if (!allowed)
                  const Padding(
                      padding: EdgeInsets.only(top: 8),
                      child: Text(
                          'Android protects app-usage history. Grant access only if you want screen-time details.',
                          style: TextStyle(
                              fontSize: 11, color: Color(0xFF9BA7AF)))),
                const SizedBox(height: 9),
                if (!allowed)
                  SizedBox(
                    width: double.infinity,
                    child: OutlinedButton(
                        onPressed: _openUsageSettings,
                        child: const Text('OPEN USAGE ACCESS SETTINGS')),
                  ),
                if (!allowed) const SizedBox(height: 8),
                SizedBox(
                    width: double.infinity,
                    child: FilledButton(
                        onPressed: loading ? null : _scanUsage,
                        child: Text(loading ? 'SCANNING…' : 'SCAN USAGE'))),
                if (leaders.isNotEmpty)
                  SingleChildScrollView(
                    scrollDirection: Axis.horizontal,
                    child: DataTable(
                      columns: const [
                        DataColumn(label: Text('App')),
                        DataColumn(label: Text('Screen time')),
                        DataColumn(label: Text('Memory')),
                        DataColumn(label: Text('CPU')),
                      ],
                      rows: [
                        for (final leader in leaders)
                          DataRow(cells: [
                            DataCell(Text('${leader['label']}')),
                            DataCell(
                                Text(_durationLabel(leader['minutes'] as num))),
                            DataCell(Text(_memoryMb(leader['memory'] as num?))),
                            DataCell(Text(leader['cpu'] == null
                                ? 'Unavailable'
                                : '${(leader['cpu'] as num).toStringAsFixed(1)}%')),
                          ]),
                      ],
                    ),
                  ),
                if (allowed && leaders.isEmpty)
                  const Padding(
                      padding: EdgeInsets.only(top: 8),
                      child: Text(
                          'No app usage history was returned by Android yet.',
                          style: TextStyle(
                              fontSize: 11, color: Color(0xFFE1B39A)))),
                if (error != null) ...[
                  const SizedBox(height: 8),
                  Text(error!,
                      style: const TextStyle(
                          fontSize: 11, color: Color(0xFFE1B39A))),
                ],
              ])),
        ],
      );
}

class StorageHealerPage extends StatefulWidget {
  const StorageHealerPage({super.key});

  @override
  State<StorageHealerPage> createState() => _StorageHealerPageState();
}

class _StorageHealerPageState extends State<StorageHealerPage> {
  StorageScan? scan;
  bool scanning = false;
  bool cacheScanning = false;
  int? cacheBytes;
  int? cacheFiles;
  List<Map<String, dynamic>> appCaches = const [];
  final Set<String> selectedPackages = <String>{};
  String? error;

  Future<void> _scan() async {
    setState(() {
      scanning = true;
      error = null;
    });
    try {
      final result = await StorageService.scanAppStorage();
      if (!mounted) return;
      setState(() => scan = result);
    } catch (exception) {
      if (!mounted) return;
      setState(
          () => error = exception.toString().replaceFirst('Exception: ', ''));
    }
    if (mounted) {
      setState(() => scanning = false);
    }
  }

  Future<void> _openStorageSettings() async {
    try {
      await const MethodChannel('pulseos/device')
          .invokeMethod('openStorageSettings');
    } on PlatformException catch (exception) {
      if (mounted) setState(() => error = exception.message);
    } on MissingPluginException {
      if (mounted) {
        setState(() => error = 'System storage settings are Android-only.');
      }
    }
  }

  Future<void> _cleanup() async {
    setState(() => cacheScanning = true);
    final cache = await StorageService.scanCache();
    Map<Object?, Object?>? device;
    try {
      device = await const MethodChannel('pulseos/device')
          .invokeMethod<Map<Object?, Object?>>('snapshot');
    } on PlatformException {
      // RAM details remain unavailable when the native bridge is unavailable.
    } on MissingPluginException {
      // RAM details remain unavailable outside Android.
    }
    if (!mounted) return;
    setState(() {
      cacheScanning = false;
      cacheBytes = cache.bytes;
      cacheFiles = cache.files;
    });
    await showDialog<void>(
        context: context,
        builder: (_) => AlertDialog(
              title: const Text('Cleanup'),
              content: Text(
                  'Available RAM: ${_formatBytes((device?['availableMemory'] as num?)?.toInt())} of ${_formatBytes((device?['totalMemory'] as num?)?.toInt())}.\n\nDetected PulseOS temporary files: ${_formatBytes(cache.bytes)} (${cache.files} files).\n\nOnly these app-owned temporary files can be safely removed.'),
              actions: [
                TextButton(
                    onPressed: _openStorageSettings,
                    child: const Text('SYSTEM STORAGE')),
                FilledButton(
                    onPressed: cache.bytes == 0
                        ? null
                        : () async {
                            Navigator.pop(context);
                            await _clearCache();
                          },
                    child: const Text('CLEAN SAFE CACHE')),
              ],
            ));
  }

  Future<void> _clearCache() async {
    final safe = await showDialog<bool>(
        context: context,
        builder: (_) => AlertDialog(
              title: const Text('Confirm cleanup'),
              content: const Text(
                  'Only PulseOS temporary files will be removed. Photos, videos, documents, downloads, databases and other apps are not touched.'),
              actions: [
                TextButton(
                    onPressed: () => Navigator.pop(context),
                    child: const Text('CANCEL')),
                FilledButton(
                    onPressed: () => Navigator.pop(context, true),
                    child: const Text('CONFIRM CLEANUP')),
              ],
            ));
    if (safe != true || !mounted) return;
    final deleted = await StorageService.clearCache();
    if (mounted) {
      setState(() {
        cacheBytes = 0;
        cacheFiles = 0;
      });
      _show(context, 'Cleanup complete',
          Text('Removed $deleted temporary entries.'));
    }
  }

  Future<void> _reviewCache() async {
    setState(() => cacheScanning = true);
    final cache = await StorageService.scanCache();
    List<Map<String, dynamic>> apps = const [];
    try {
      final result = await const MethodChannel('pulseos/device')
          .invokeMethod<List<Object?>>('appCaches');
      apps = (result ?? const [])
          .whereType<Map>()
          .map((item) =>
              item.map((key, value) => MapEntry(key.toString(), value)))
          .where((item) => (item['cacheBytes'] as num? ?? 0) > 0)
          .toList();
    } on PlatformException {
      // Keep the app-owned cache result when Android package stats are unavailable.
    } on MissingPluginException {
      // Package cache stats are Android-only.
    }
    if (!mounted) return;
    setState(() {
      cacheScanning = false;
      cacheBytes = cache.bytes;
      cacheFiles = cache.files;
      appCaches = apps;
    });
  }

  Future<void> _openAppStorage(String packageName) async {
    await const MethodChannel('pulseos/device').invokeMethod('openAppStorage', {
      'package': packageName,
    });
  }

  Future<void> _openAllAppStorage() async {
    await const MethodChannel('pulseos/device')
        .invokeMethod('openAllAppStorage');
  }

  @override
  Widget build(BuildContext context) => ListView(
        padding: const EdgeInsets.all(12),
        children: [
          const _SectionTitle('Storage Healer',
              'Analyze app-owned files → review → delete → verify'),
          _Card(
              child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                const _Header(
                    'STORAGE INTELLIGENCE', 'Internal device storage'),
                const SizedBox(height: 10),
                Center(
                    child: SizedBox(
                        width: 145,
                        height: 145,
                        child: CustomPaint(
                            painter:
                                _DonutPainter(fraction: scan?.fraction ?? 0)))),
                const SizedBox(height: 12),
                _info('Scan state',
                    scanning ? 'Scanning…' : scan?.state ?? 'Not scanned'),
                _info('App-owned files', scan == null ? '—' : '${scan!.files}'),
                _info('App-owned size',
                    scan == null ? '—' : _formatBytes(scan!.bytes)),
                _info(
                    'Internal storage',
                    scan?.totalStorage == null || scan?.freeStorage == null
                        ? 'Storage size unavailable'
                        : 'Used ${_formatBytes(scan!.totalStorage! - scan!.freeStorage!)} / ${_formatBytes(scan!.totalStorage)}'),
                _info(
                    'Free storage',
                    scan?.freeStorage == null
                        ? 'Storage size unavailable'
                        : _formatBytes(scan!.freeStorage)),
                const SizedBox(height: 9),
                Row(children: [
                  Expanded(
                      child: FilledButton(
                          onPressed: scanning ? null : _scan,
                          child:
                              Text(scanning ? 'SCANNING…' : 'SCAN STORAGE'))),
                  const SizedBox(width: 8),
                  Expanded(
                      child: OutlinedButton(
                          onPressed: scanning ? null : _cleanup,
                          child: const Text('CLEANUP'))),
                ]),
                const SizedBox(height: 7),
                const Text(
                    'Device totals come from Android. App-owned file details are limited by Android storage access rules.',
                    style: TextStyle(fontSize: 11, color: Color(0xFF9BA7AF))),
                if (error != null) ...[
                  const SizedBox(height: 7),
                  Text(error!,
                      style: const TextStyle(
                          fontSize: 11, color: Color(0xFFE1B39A))),
                ],
              ])),
          _Card(
              child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                const _Header('CACHE', 'Detectable temporary files'),
                const SizedBox(height: 8),
                const Text(
                    'Only PulseOS-owned temporary files can be measured or removed. Other apps caches are protected by Android.',
                    style: TextStyle(fontSize: 11, color: Color(0xFF9BA7AF))),
                const SizedBox(height: 8),
                _info(
                    'Detected cache',
                    cacheBytes == null
                        ? 'Not scanned'
                        : '${_formatBytes(cacheBytes)} ($cacheFiles files)'),
                const SizedBox(height: 8),
                if (appCaches.isNotEmpty) ...[
                  const Text('APP CACHE BY APPLICATION', style: _kicker),
                  for (final app in appCaches)
                    CheckboxListTile(
                      dense: true,
                      contentPadding: EdgeInsets.zero,
                      value: selectedPackages.contains(app['package']),
                      title: Text('${app['label']}'),
                      subtitle: Text(
                          _formatBytes((app['cacheBytes'] as num).toInt())),
                      onChanged: (checked) => setState(() {
                        final packageName = app['package'] as String;
                        if (checked == true) {
                          selectedPackages.add(packageName);
                        } else {
                          selectedPackages.remove(packageName);
                        }
                      }),
                    ),
                  OutlinedButton(
                      onPressed: selectedPackages.length == 1
                          ? () => _openAppStorage(selectedPackages.first)
                          : null,
                      child: const Text('OPEN SELECTED APP STORAGE')),
                ],
                OutlinedButton(
                    onPressed: cacheScanning ? null : _reviewCache,
                    child: Text(cacheScanning ? 'SCANNING…' : 'CLEAR CACHE')),
                TextButton.icon(
                  onPressed: _openAllAppStorage,
                  icon: const Icon(Icons.delete_sweep_outlined),
                  label: const Text('DELETE ALL APP CACHE'),
                ),
              ])),
        ],
      );
}

class StorageScan {
  final String state;
  final int files;
  final int bytes;
  final int? totalStorage;
  final int? freeStorage;

  const StorageScan(this.state, this.files, this.bytes,
      {this.totalStorage, this.freeStorage});

  double get fraction {
    if (totalStorage == null || freeStorage == null || totalStorage == 0) {
      return 0;
    }
    return ((totalStorage! - freeStorage!) / totalStorage!).clamp(0.0, 1.0);
  }
}

class StorageService {
  static Future<({int files, int bytes})> scanCache() async {
    try {
      final root = await getTemporaryDirectory();
      var files = 0;
      var bytes = 0;
      if (await root.exists()) {
        await for (final entity
            in root.list(recursive: true, followLinks: false)) {
          if (entity is File) {
            files++;
            try {
              bytes += await entity.length();
            } on FileSystemException {
              // A temporary file can disappear while it is being scanned.
            }
          }
        }
      }
      return (files: files, bytes: bytes);
    } on FileSystemException {
      return (files: 0, bytes: 0);
    }
  }

  static Future<StorageScan> scanAppStorage() async {
    if (kIsWeb) {
      return const StorageScan('Android app required', 0, 0);
    }
    try {
      final roots = <Directory>[
        await getApplicationDocumentsDirectory(),
        await getTemporaryDirectory(),
      ];
      var files = 0;
      var bytes = 0;
      int? totalStorage;
      int? freeStorage;
      try {
        final snapshot = await const MethodChannel('pulseos/device')
            .invokeMethod<Map<Object?, Object?>>('snapshot');
        totalStorage = (snapshot?['totalStorage'] as num?)?.toInt();
        freeStorage = (snapshot?['freeStorage'] as num?)?.toInt();
      } on PlatformException {
        // The app-owned scan remains useful when the native overview is absent.
      } on MissingPluginException {
        // The app-owned scan remains useful on non-Android platforms.
      }
      for (final root in roots) {
        if (!await root.exists()) continue;
        await for (final entity
            in root.list(recursive: true, followLinks: false)) {
          if (entity is File) {
            files++;
            try {
              bytes += await entity.length();
            } on FileSystemException {
              // A file can disappear while a scan is in progress.
            }
          }
        }
      }
      return StorageScan('Complete', files, bytes,
          totalStorage: totalStorage, freeStorage: freeStorage);
    } catch (_) {
      return const StorageScan('Unavailable', 0, 0);
    }
  }

  static Future<int> clearCache() async {
    try {
      final root = await getTemporaryDirectory();
      var deleted = 0;
      if (await root.exists()) {
        await for (final entity in root.list(followLinks: false)) {
          try {
            await entity.delete(recursive: true);
            deleted++;
          } on FileSystemException {
            // Ignore files the OS removes concurrently.
          }
        }
      }
      return deleted;
    } catch (_) {
      return 0;
    }
  }
}

class ConverterPage extends StatefulWidget {
  const ConverterPage({super.key});

  @override
  State<ConverterPage> createState() => _ConverterPageState();
}

class _ConverterPageState extends State<ConverterPage> {
  String selected = 'Document';
  String format = 'TXT';
  String? selectedPath;
  bool running = false;
  String? status;

  @override
  Widget build(BuildContext context) => ListView(
        padding: const EdgeInsets.all(12),
        children: [
          const _SectionTitle('Offline Converter',
              'Real local conversions with an explicit compatibility matrix'),
          _Card(
              child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                const _Header('CONVERT', 'Choose a workflow'),
                const SizedBox(height: 10),
                DropdownButtonFormField<String>(
                  initialValue: selected,
                  items: ConverterMatrix.types
                      .map((x) => DropdownMenuItem(value: x, child: Text(x)))
                      .toList(),
                  onChanged: running
                      ? null
                      : (v) => setState(() {
                            selected = v!;
                            final supported =
                                ConverterMatrix.supportedOutputs(v);
                            format = supported.isEmpty ? '' : supported.first;
                            status = null;
                          }),
                  decoration: const InputDecoration(labelText: 'Input type'),
                ),
                const SizedBox(height: 8),
                DropdownButtonFormField<String>(
                  initialValue: format.isEmpty ? null : format,
                  items: ConverterMatrix.supportedOutputs(selected)
                      .map((x) => DropdownMenuItem(
                            value: x,
                            enabled: ConverterMatrix.canConvert(selected, x),
                            child: Text(ConverterMatrix.canConvert(selected, x)
                                ? x
                                : '$x (unavailable)'),
                          ))
                      .toList(),
                  onChanged: running || format.isEmpty
                      ? null
                      : (v) => setState(() => format = v!),
                  decoration: const InputDecoration(labelText: 'Output format'),
                ),
                const SizedBox(height: 8),
                Text(
                    ConverterMatrix.canConvert(selected, format)
                        ? 'Supported: local ${format == 'ZIP' ? 'archive creation' : 'text export'}'
                        : 'Unavailable: no bundled codec for this conversion',
                    style: const TextStyle(
                        fontSize: 11, color: Color(0xFF9BA7AF))),
                const SizedBox(height: 12),
                if (selectedPath != null)
                  Text('Selected: ${_basename(selectedPath!)}',
                      maxLines: 1,
                      overflow: TextOverflow.ellipsis,
                      style: const TextStyle(fontSize: 11)),
                const SizedBox(height: 5),
                LayoutBuilder(builder: (context, constraints) {
                  final vertical = constraints.maxWidth < 360;
                  final buttons = [
                    OutlinedButton(
                        onPressed: running ? null : _chooseFile,
                        child: const Text('CHOOSE FILE')),
                    FilledButton(
                        onPressed:
                            running || selectedPath == null ? null : _convert,
                        child: Text(running ? 'CONVERTING…' : 'CONVERT')),
                  ];
                  return vertical
                      ? Column(
                          crossAxisAlignment: CrossAxisAlignment.stretch,
                          children: [
                            buttons[0],
                            buttons[1],
                          ],
                        )
                      : Row(
                          children: [
                            Expanded(child: buttons[0]),
                            const SizedBox(width: 8),
                            Expanded(child: buttons[1]),
                          ],
                        );
                }),
                if (running) ...[
                  const SizedBox(height: 10),
                  const LinearProgressIndicator(),
                  const SizedBox(height: 6),
                  const Text('Processing off the UI isolate…',
                      style: TextStyle(fontSize: 11, color: Color(0xFF9BA7AF)))
                ],
                if (status != null) ...[
                  const SizedBox(height: 8),
                  Text(status!,
                      style: const TextStyle(
                          fontSize: 11, color: Color(0xFFBFD0C4))),
                ],
              ])),
          _Card(
              child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                const _Header('COMPATIBILITY', 'Bundled conversions'),
                const SizedBox(height: 6),
                for (final entry in ConverterMatrix.outputs.entries)
                  Padding(
                      padding: const EdgeInsets.symmetric(vertical: 3),
                      child: Text(
                          '${entry.key}: ${ConverterMatrix.supportedOutputs(entry.key).isEmpty ? 'No supported conversions' : ConverterMatrix.supportedOutputs(entry.key).join(', ')}',
                          style: const TextStyle(fontSize: 11))),
                const SizedBox(height: 5),
                const Text(
                    'Files remain on-device. Only conversions listed above are implemented and selectable.',
                    style: TextStyle(fontSize: 11, color: Color(0xFF9BA7AF))),
              ])),
        ],
      );

  Future<void> _chooseFile() async {
    if (kIsWeb) {
      setState(() => status = 'Install the Android app to choose phone files.');
      return;
    }
    final result = await FilePicker.platform
        .pickFiles(withData: false, allowMultiple: false);
    if (!mounted || result == null || result.files.single.path == null) return;
    final path = result.files.single.path!;
    final detected = ConverterMatrix.detectType(path);
    setState(() {
      selectedPath = path;
      if (detected != null) {
        selected = detected;
        final supported = ConverterMatrix.supportedOutputs(detected);
        format = supported.isEmpty ? '' : supported.first;
      }
      status = null;
    });
  }

  Future<void> _convert() async {
    final path = selectedPath;
    if (path == null) return;
    setState(() {
      running = true;
      status = null;
    });
    try {
      final output = await ConversionService.convert(
          path: path, inputType: selected, outputFormat: format);
      if (mounted) setState(() => status = 'Saved ${_basename(output.path)}');
    } catch (error) {
      if (mounted) {
        setState(
            () => status = error.toString().replaceFirst('Exception: ', ''));
      }
    } finally {
      if (mounted) setState(() => running = false);
    }
  }
}

class ConverterMatrix {
  static const types = ['Document', 'Image', 'Archive', 'Media', 'Ebook'];
  static const outputs = <String, List<String>>{
    'Document': ['TXT', 'PDF', 'DOCX'],
    'Image': ['PNG', 'JPG', 'PDF'],
    'Archive': ['ZIP'],
    'Media': ['MP3', 'MP4'],
    'Ebook': ['PDF', 'EPUB'],
  };

  static bool canConvert(String input, String output) =>
      (input == 'Document' && output == 'TXT') ||
      (input == 'Image' && ['PNG', 'JPG', 'PDF'].contains(output)) ||
      (input == 'Archive' && output == 'ZIP');

  static List<String> supportedOutputs(String input) =>
      outputs[input]!.where((output) => canConvert(input, output)).toList();

  static String? detectType(String path) {
    final extension = _basename(path).toLowerCase().split('.').last;
    if (['txt', 'md', 'csv', 'json', 'xml', 'html'].contains(extension)) {
      return 'Document';
    }
    if (['zip'].contains(extension)) return 'Archive';
    if (['png', 'jpg', 'jpeg', 'webp'].contains(extension)) return 'Image';
    if (['mp3', 'wav', 'mp4', 'm4a'].contains(extension)) return 'Media';
    if (['epub', 'mobi'].contains(extension)) return 'Ebook';
    return null;
  }
}

class ConversionResult {
  final String path;

  const ConversionResult(this.path);
}

class ConversionService {
  static Future<ConversionResult> convert(
      {required String path,
      required String inputType,
      required String outputFormat}) async {
    if (!ConverterMatrix.canConvert(inputType, outputFormat)) {
      throw Exception(
          'Conversion is unavailable: no bundled codec supports $inputType → $outputFormat.');
    }
    final input = File(path);
    if (!await input.exists()) {
      throw Exception('The selected file no longer exists.');
    }
    final bytes = await input.readAsBytes();
    if (bytes.length > 100 * 1024 * 1024) {
      throw Exception('Files larger than 100 MB are not processed in memory.');
    }
    final name = _basename(path);
    final outputBytes = await _convertBytes(
        name: name,
        bytes: bytes,
        inputType: inputType,
        outputFormat: outputFormat);
    final extension = outputFormat.toLowerCase();
    final outputDirectory = await getApplicationDocumentsDirectory();
    final outputPath =
        '${outputDirectory.path}${Platform.pathSeparator}${_withoutExtension(name)}_converted.$extension';
    final output = File(outputPath);
    await output.writeAsBytes(outputBytes, flush: true);
    return ConversionResult(output.path);
  }
}

Future<List<int>> _convertBytes({
  required String name,
  required List<int> bytes,
  required String inputType,
  required String outputFormat,
}) async {
  if (outputFormat == 'ZIP') {
    return Isolate.run(() => _zipBytes(name, bytes));
  }
  if (inputType == 'Image') {
    final decoded = img.decodeImage(Uint8List.fromList(bytes));
    if (decoded == null) {
      throw Exception('The selected image format could not be decoded.');
    }
    if (outputFormat == 'PNG') return img.encodePng(decoded);
    if (outputFormat == 'JPG') return img.encodeJpg(decoded, quality: 95);
    if (outputFormat == 'PDF') {
      final pngBytes = img.encodePng(decoded);
      final document = pw.Document();
      document.addPage(pw.Page(
          build: (_) => pw.Center(
              child:
                  pw.Image(pw.MemoryImage(pngBytes), fit: pw.BoxFit.contain))));
      return document.save();
    }
  }
  return bytes;
}

List<int> _zipBytes(String name, List<int> bytes) {
  final archive = Archive()..addFile(ArchiveFile(name, bytes.length, bytes));
  return ZipEncoder().encode(archive);
}

String _basename(String path) {
  final normalized = path.replaceAll('\\', '/');
  return normalized.substring(normalized.lastIndexOf('/') + 1);
}

String _withoutExtension(String name) {
  final dot = name.lastIndexOf('.');
  return dot > 0 ? name.substring(0, dot) : name;
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
    unawaited(_load());
  }

  Future<void> _load() async {
    final preferences = await SharedPreferences.getInstance();
    if (!mounted) return;
    setState(() {
      localFirst = preferences.getBool('local_first') ?? true;
      alerts = preferences.getBool('alerts') ?? true;
      loading = false;
    });
  }

  Future<void> _set(String key, bool value) async {
    setState(() {
      if (key == 'local_first') localFirst = value;
      if (key == 'alerts') alerts = value;
    });
    final preferences = await SharedPreferences.getInstance();
    await preferences.setBool(key, value);
  }

  Future<void> _openSystemSettings(String setting) async {
    try {
      await const MethodChannel('pulseos/device')
          .invokeMethod('openPhoneSetting', {'setting': setting});
    } on PlatformException catch (exception) {
      if (mounted) {
        _show(context, 'Android Settings',
            Text(exception.message ?? 'This settings screen is unavailable.'));
      }
    } on MissingPluginException {
      if (mounted) {
        _show(context, 'Android Settings',
            const Text('Android settings are unavailable on this device.'));
      }
    }
  }

  Widget _settingsLink(
          String title, String detail, IconData icon, String target) =>
      ListTile(
        contentPadding: EdgeInsets.zero,
        leading: Icon(icon, size: 21),
        title: Text(title, style: const TextStyle(fontSize: 13)),
        subtitle: Text(detail,
            style: const TextStyle(fontSize: 11, color: Color(0xFF9BA7AF))),
        trailing: const Icon(Icons.open_in_new, size: 16),
        onTap: () => _openSystemSettings(target),
      );

  @override
  Widget build(BuildContext context) => ListView(
        padding: const EdgeInsets.all(12),
        children: [
          const _SectionTitle('Settings', 'PulseOS mobile client preferences'),
          _Card(
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
          _Card(
              child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                const _Header('NETWORK & DATA', 'Phone connection settings'),
                _settingsLink(
                    'Wi-Fi and mobile network',
                    'Manage connections and SIM settings',
                    Icons.wifi,
                    'network'),
                _settingsLink(
                    'Data usage',
                    'View mobile data and Wi-Fi usage by app',
                    Icons.data_usage,
                    'data_usage'),
                _settingsLink(
                    'Bluetooth',
                    'Manage paired devices and connections',
                    Icons.bluetooth,
                    'bluetooth'),
              ])),
          _Card(
              child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                const _Header('APPS & PRIVACY', 'App time and access'),
                _settingsLink(
                    'App screen time',
                    'Usage Access and digital wellbeing',
                    Icons.timelapse,
                    'usage'),
                _settingsLink('App permissions', 'Review PulseOS permissions',
                    Icons.admin_panel_settings, 'permissions'),
                _settingsLink(
                    'App notifications',
                    'Manage PulseOS notifications',
                    Icons.notifications_outlined,
                    'notifications'),
                _settingsLink('Installed apps', 'Open Android app management',
                    Icons.apps, 'apps'),
              ])),
          _Card(
              child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                const _Header('DEVICE SETTINGS', 'Phone controls'),
                _settingsLink('Battery', 'Battery use and power settings',
                    Icons.battery_5_bar, 'battery'),
                _settingsLink(
                    'Display and brightness',
                    'Screen appearance and brightness',
                    Icons.brightness_6,
                    'display'),
                _settingsLink('Sound and vibration',
                    'Volume, audio, and vibration', Icons.volume_up, 'sound'),
                _settingsLink('Storage', 'Phone storage and cleanup',
                    Icons.storage, 'storage'),
                _settingsLink('Location', 'Location services and GPS',
                    Icons.location_on_outlined, 'location'),
                _settingsLink(
                    'Security and lock screen',
                    'Device security and biometrics',
                    Icons.lock_outline,
                    'security'),
                _settingsLink(
                    'NFC', 'Contactless and NFC settings', Icons.nfc, 'nfc'),
                _settingsLink('Date and time', 'Clock and time-zone settings',
                    Icons.schedule, 'date_time'),
                _settingsLink(
                    'Language and keyboard',
                    'Input and language preferences',
                    Icons.keyboard,
                    'language'),
                _settingsLink('Accessibility', 'Accessibility settings',
                    Icons.accessibility_new, 'accessibility'),
              ])),
          const _Card(
              child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                Text('ABOUT', style: _kicker),
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
        ])));

double? _optionalTemperature(Object? value) {
  final temperature = (value as num?)?.toDouble();
  return temperature == null || temperature < 0 ? null : temperature;
}

String _temperatureLabel(double? value) =>
    value == null ? 'Unavailable' : '${value.toStringAsFixed(1)} °C';

String _batteryHealthLabel(int? value) {
  switch (value) {
    case 2:
      return 'Good';
    case 3:
      return 'Overheat';
    case 4:
      return 'Dead';
    case 5:
      return 'Over voltage';
    case 6:
      return 'Failure';
    default:
      return 'Unavailable';
  }
}

String _durationLabel(num minutes) {
  final value = minutes.round();
  if (value < 60) return '$value min';
  return '${value ~/ 60}h ${value % 60}m';
}

void _showTemperatureDetails(BuildContext context, TelemetrySnapshot data) {
  final rows = <String>[
    'Battery: ${_temperatureLabel(data.batteryTemperature)}',
    if (data.temperatures.isEmpty) 'Other exposed sensors: None',
    for (final sensor in data.temperatures)
      '${sensor['name']}: ${sensor['value']} ${sensor['unit']}',
  ];
  _show(
      context,
      'Thermal Information',
      Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
        _LiveGraph(
            values: data.temperatureHistory,
            minY: 15,
            maxY: 50,
            color: const Color(0xFFE1B39A),
            label: 'Temperature history'),
        const SizedBox(height: 12),
        Text(rows.join('\n')),
        const SizedBox(height: 12),
        const Text(
          'Temperature readings depend on which sensors the phone exposes to Android.',
            style: TextStyle(fontSize: 12, color: Color(0xFFB9C5CB))),
      ]));
}

void _showCpuDetails(BuildContext context, TelemetrySnapshot data) {
  showModalBottomSheet<void>(
      context: context,
      backgroundColor: const Color(0xFF10161C),
      showDragHandle: true,
      isScrollControlled: true,
      builder: (_) => _CpuDetails(initial: data));
}

void _showMemoryDetails(BuildContext context, TelemetrySnapshot data) {
  final total = data.totalMemory;
  final available = data.availableMemory;
  final used = total == null || available == null
      ? null
      : (total - available).clamp(0, total);
  final fraction =
      total == null || used == null || total == 0 ? 0.0 : used / total;
  _show(
      context,
      'Memory Details',
      Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
        Row(crossAxisAlignment: CrossAxisAlignment.start, children: [
          SizedBox(
              width: 130,
              height: 130,
              child: CustomPaint(painter: _DonutPainter(fraction: fraction))),
          const SizedBox(width: 14),
          Expanded(
              child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                _info('Used', _formatBytes(used)),
                _info('Free', _formatBytes(available)),
                _info('Total', _formatBytes(total)),
              ])),
        ]),
        const SizedBox(height: 12),
        _LiveGraph(
            values: data.memoryHistory,
            minY: 0,
            maxY: 100,
            color: const Color(0xFF9BB7D3),
            label: 'Memory used history'),
        const SizedBox(height: 12),
        const Text('VISIBLE MEMORY USERS', style: _kicker),
        const SizedBox(height: 5),
        if (data.processes.isEmpty)
          const Text('Android did not expose process memory rows.',
              style: TextStyle(fontSize: 11, color: Color(0xFFB9C5CB)))
        else
          for (final process in data.processes)
            _info('${process['name']}', _memoryMb(process['memory'] as num?)),
        const SizedBox(height: 8),
        const Text(
            'Android restricts complete per-app RAM attribution. These are only the visible process rows returned by the system.',
            style: TextStyle(fontSize: 11, color: Color(0xFFB9C5CB))),
      ]));
}

void _showDeviceDetails(BuildContext context, TelemetrySnapshot data) {
  _show(
      context,
      'Device Information',
      _detail([
        'Model',
        data.model ?? 'Unavailable',
        'Manufacturer',
        data.manufacturer ?? 'Unavailable',
        'Brand',
        data.brand ?? 'Unavailable',
        'Android',
        data.androidVersion ?? 'Unavailable',
        'SDK',
        data.androidApi?.toString() ?? 'Unavailable',
        'Security patch',
        data.securityPatch ?? 'Unavailable',
        'Build',
        data.buildNumber ?? 'Unavailable',
        'Architecture',
        data.architecture ?? 'Unavailable',
        'CPU cores',
        data.cpuCores?.toString() ?? 'Unavailable',
        'Total RAM',
        _formatBytes(data.totalMemory),
        'Free RAM',
        _formatBytes(data.availableMemory),
        'Storage',
        data.totalStorage == null || data.freeStorage == null
            ? 'Unavailable'
            : '${_formatBytes(data.totalStorage! - data.freeStorage!)} used',
      ]));
}

void _showProcessDetails(BuildContext context, TelemetrySnapshot data) {
  _show(
      context,
      'Processes',
      Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
        _info('PulseOS status', 'Running'),
        _info('PulseOS memory', _formatBytes(data.processMemory)),
        _info('Active processes', '${data.processes.length}'),
        const SizedBox(height: 10),
        if (data.processes.isEmpty)
          const Text('No process rows were returned by Android.',
              style: TextStyle(fontSize: 12, color: Color(0xFFB9C5CB)))
        else
          for (final process in data.processes)
            _info(
                '${process['name']}',
                '${_memoryMb(process['memory'] as num?)} · '
                    '${process['cpu'] == null ? 'Unavailable' : '${(process['cpu'] as num).toStringAsFixed(1)}% CPU'}'),
        const SizedBox(height: 8),
        const Text(
            'Android provides memory and importance for visible running processes. Per-process CPU percentages are restricted.',
            style: TextStyle(fontSize: 12, color: Color(0xFFB9C5CB))),
      ]));
}

class _CpuDetails extends StatefulWidget {
  final TelemetrySnapshot initial;

  const _CpuDetails({required this.initial});

  @override
  State<_CpuDetails> createState() => _CpuDetailsState();
}

class _CpuDetailsState extends State<_CpuDetails> {
  Timer? timer;
  double? usage;
  int? cores;

  @override
  void initState() {
    super.initState();
    usage = widget.initial.cpuUsage;
    cores = widget.initial.cpuCores;
    unawaited(_refresh());
    timer = Timer.periodic(const Duration(seconds: 2), (_) {
      unawaited(_refresh());
    });
  }

  @override
  void dispose() {
    timer?.cancel();
    super.dispose();
  }

  Future<void> _refresh() async {
    try {
      final result = await const MethodChannel('pulseos/device')
          .invokeMethod<Map<Object?, Object?>>('snapshot');
      if (!mounted) return;
      setState(() {
        usage = (result?['cpuUsage'] as num?)?.toDouble();
        cores = (result?['cpuCores'] as num?)?.toInt();
      });
    } on PlatformException {
      // Keep the last observed value and show unavailable when none exists.
    } on MissingPluginException {
      // CPU sampling is Android-only.
    }
  }

  @override
  Widget build(BuildContext context) => Padding(
      padding: const EdgeInsets.fromLTRB(16, 2, 16, 24),
      child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
        const Text('CPU Monitor',
            style: TextStyle(fontSize: 18, fontWeight: FontWeight.w800)),
        const SizedBox(height: 12),
        _info('Overall usage',
            usage == null ? 'Unavailable' : '${usage!.toStringAsFixed(1)}%'),
        _info('CPU cores', cores?.toString() ?? 'Unavailable'),
        _LiveGraph(
            values: widget.initial.cpuHistory,
            minY: 0,
            maxY: 100,
            color: const Color(0xFF9CC7B8),
            label: 'CPU usage history'),
        const SizedBox(height: 8),
        LinearProgressIndicator(value: usage == null ? null : usage! / 100),
        const SizedBox(height: 12),
        const Text('CPU Consumers', style: _kicker),
        const SizedBox(height: 5),
        const Text(
            'Detailed per-app CPU usage is restricted by Android on this device. PulseOS does not display guessed consumers.',
            style: TextStyle(fontSize: 12, color: Color(0xFFB9C5CB))),
      ]));
}

void _show(BuildContext context, String title, Widget body) =>
    showModalBottomSheet(
        context: context,
        backgroundColor: const Color(0xFF10161C),
        showDragHandle: true,
        isScrollControlled: true,
        builder: (_) => Padding(
            padding: const EdgeInsets.fromLTRB(16, 2, 16, 24),
            child: SingleChildScrollView(
                child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                  Text(title,
                      style: const TextStyle(
                          fontSize: 18, fontWeight: FontWeight.w800)),
                  const SizedBox(height: 12),
                  body,
                  const SizedBox(height: 8),
                ]))));

void _unavailable(BuildContext context, String metric) => _show(
    context,
    metric,
    const Text(
      'This platform does not expose this data to PulseOS. The value is marked unavailable instead of being simulated.',
      style: TextStyle(fontSize: 12, color: Color(0xFFB9C5CB)),
    ));

class _Card extends StatelessWidget {
  final Widget child;
  final VoidCallback? onTap;

  const _Card({required this.child, this.onTap});

  @override
  Widget build(BuildContext context) => Container(
      margin: const EdgeInsets.only(bottom: 8),
      decoration: BoxDecoration(
          color: const Color(0xFF10161C),
          border: Border.all(color: const Color(0xFF242C33)),
          borderRadius: BorderRadius.circular(11)),
      child: Material(
          color: Colors.transparent,
          child: InkWell(
              borderRadius: BorderRadius.circular(11),
              onTap: onTap,
              child:
                  Padding(padding: const EdgeInsets.all(13), child: child))));
}

class _Metric extends StatelessWidget {
  final String title, value, sub;
  final VoidCallback tap;

  const _Metric(this.title, this.value, this.sub, this.tap);

  @override
  Widget build(BuildContext context) => _Card(
      onTap: tap,
      child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
        Text(title, style: _kicker),
        const SizedBox(height: 5),
        Flexible(
            child: Text(value,
                maxLines: 2,
                overflow: TextOverflow.ellipsis,
                style: const TextStyle(
                    fontSize: 20, fontWeight: FontWeight.w900))),
        Text(sub,
            maxLines: 2,
            overflow: TextOverflow.ellipsis,
            style: const TextStyle(fontSize: 10, color: Color(0xFF9BA7AF)))
      ]));
}

class _Header extends StatelessWidget {
  final String k, t;

  const _Header(this.k, this.t);

  @override
  Widget build(BuildContext context) =>
      Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
        Text(k, style: _kicker),
        const SizedBox(height: 4),
        Text(t,
            style: const TextStyle(fontSize: 14, fontWeight: FontWeight.w700))
      ]);
}

class _SectionTitle extends StatelessWidget {
  final String t, s;

  const _SectionTitle(this.t, this.s);

  @override
  Widget build(BuildContext context) => Padding(
      padding: const EdgeInsets.only(bottom: 10),
      child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
        Text(t,
            style: const TextStyle(fontSize: 22, fontWeight: FontWeight.w800)),
        Text(s, style: const TextStyle(fontSize: 11, color: Color(0xFF9BA7AF)))
      ]));
}

class _LivePill extends StatelessWidget {
  final bool available;

  const _LivePill({required this.available});

  @override
  Widget build(BuildContext context) => Container(
      margin: const EdgeInsets.only(right: 4),
      padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 5),
      decoration: BoxDecoration(
          border: Border.all(color: const Color(0xFF283129)),
          borderRadius: BorderRadius.circular(18)),
      child: Text(available ? '● LIVE' : '○ LIMITED',
          style: TextStyle(
              fontSize: 9,
              color:
                  available ? const Color(0xFFA8B9AC) : const Color(0xFFE0BE9E),
              fontWeight: FontWeight.w700)));
}

class _Logo extends StatelessWidget {
  const _Logo();

  @override
  Widget build(BuildContext context) => Container(
      width: 32,
      height: 32,
      clipBehavior: Clip.antiAlias,
      decoration: BoxDecoration(borderRadius: BorderRadius.circular(9)),
      child: Image.asset('web/icons/Icon-maskable-512.png', fit: BoxFit.cover));
}

class _HealthBar extends StatelessWidget {
  final String title;
  final int? value;

  const _HealthBar(this.title, this.value);

  @override
  Widget build(BuildContext context) => Padding(
      padding: const EdgeInsets.only(bottom: 8),
      child: Column(children: [
        Row(children: [
          Expanded(
              child: Text(title,
                  style:
                      const TextStyle(fontSize: 11, color: Color(0xFF9BA7AF)))),
          Text(value == null ? 'Unavailable' : '$value%',
              style: const TextStyle(fontSize: 11, fontWeight: FontWeight.w700))
        ]),
        const SizedBox(height: 4),
        ClipRRect(
            borderRadius: BorderRadius.circular(9),
            child: LinearProgressIndicator(
                value: value == null ? 0 : value! / 100,
                minHeight: 5,
                backgroundColor: const Color(0xFF1C2329),
                valueColor: const AlwaysStoppedAnimation(Color(0xFFB9C3C9))))
      ]));
}

class _ProblemLine extends StatelessWidget {
  final String text;
  final bool good;

  const _ProblemLine(this.text, {this.good = false});

  @override
  Widget build(BuildContext context) => Container(
      margin: const EdgeInsets.only(bottom: 6),
      padding: const EdgeInsets.all(9),
      decoration: BoxDecoration(
          color: const Color(0xFF0C1217),
          border: Border.all(color: const Color(0xFF20272E)),
          borderRadius: BorderRadius.circular(8)),
      child: Row(children: [
        Container(
            width: 4,
            height: 23,
            decoration: BoxDecoration(
                color: good ? const Color(0xFF859C8D) : const Color(0xFF9A7F68),
                borderRadius: BorderRadius.circular(3))),
        const SizedBox(width: 8),
        Expanded(
            child: Text(text,
                style:
                    const TextStyle(fontSize: 11, fontWeight: FontWeight.w600)))
      ]));
}

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
    ]));

String _formatBytes(int? bytes) {
  if (bytes == null) return 'Unavailable';
  if (bytes < 1024) return '$bytes B';
  if (bytes < 1024 * 1024) return '${(bytes / 1024).toStringAsFixed(1)} KB';
  if (bytes < 1024 * 1024 * 1024) {
    return '${(bytes / (1024 * 1024)).toStringAsFixed(1)} MB';
  }
  return '${(bytes / (1024 * 1024 * 1024)).toStringAsFixed(2)} GB';
}

String _memoryMb(num? bytes) => bytes == null
    ? 'Unavailable'
    : '${(bytes / (1024 * 1024)).toStringAsFixed(1)} MB';

const _kicker = TextStyle(
    fontSize: 9,
    letterSpacing: 1.25,
    fontWeight: FontWeight.w800,
    color: Color(0xFF9BA7AF));

class _LiveGraph extends StatelessWidget {
  final List<double> values;
  final double minY;
  final double maxY;
  final Color color;
  final String label;

  const _LiveGraph({
    required this.values,
    required this.minY,
    required this.maxY,
    required this.color,
    required this.label,
  });

  @override
  Widget build(BuildContext context) => Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(label, style: _kicker),
          const SizedBox(height: 5),
          SizedBox(
            height: 72,
            width: double.infinity,
            child: CustomPaint(
              painter: _LineGraphPainter(
                values: values,
                minY: minY,
                maxY: maxY,
                color: color,
              ),
            ),
          ),
        ],
      );
}

class _LineGraphPainter extends CustomPainter {
  final List<double> values;
  final double minY;
  final double maxY;
  final Color color;

  const _LineGraphPainter({
    required this.values,
    required this.minY,
    required this.maxY,
    required this.color,
  });

  @override
  void paint(Canvas canvas, Size size) {
    final gridPaint = Paint()
      ..color = const Color(0xFF263139)
      ..strokeWidth = 1;
    for (var index = 1; index < 4; index++) {
      final y = size.height * index / 4;
      canvas.drawLine(Offset(0, y), Offset(size.width, y), gridPaint);
    }
    if (values.length < 2) return;
    final line = Paint()
      ..color = color
      ..strokeWidth = 2
      ..style = PaintingStyle.stroke
      ..strokeCap = StrokeCap.round;
    final path = Path();
    for (var index = 0; index < values.length; index++) {
      final x = size.width * index / (values.length - 1);
      final normalized =
          ((values[index] - minY) / (maxY - minY)).clamp(0.0, 1.0);
      final point = Offset(x, size.height * (1 - normalized));
      if (index == 0) {
        path.moveTo(point.dx, point.dy);
      } else {
        path.lineTo(point.dx, point.dy);
      }
    }
    canvas.drawPath(path, line);
  }

  @override
  bool shouldRepaint(covariant _LineGraphPainter oldDelegate) =>
      oldDelegate.values != values || oldDelegate.color != color;
}

class _DonutPainter extends CustomPainter {
  final double fraction;

  const _DonutPainter({required this.fraction});

  @override
  void paint(Canvas canvas, Size size) {
    final center = Offset(size.width / 2, size.height / 2);
    final radius = size.width / 2 - 7;
    final background = Paint()
      ..color = const Color(0xFF4D5962)
      ..style = PaintingStyle.stroke
      ..strokeWidth = 16;
    final foreground = Paint()
      ..color = const Color(0xFFC2CBD1)
      ..style = PaintingStyle.stroke
      ..strokeWidth = 16;
    canvas.drawCircle(center, radius, background);
    if (fraction > 0) {
      canvas.drawArc(Rect.fromCircle(center: center, radius: radius),
          -math.pi / 2, math.pi * 2 * fraction, false, foreground);
    }
    final label = fraction == 0 ? '—' : '${(fraction * 100).round()}%';
    final painter = TextPainter(
        text: TextSpan(
            text: label,
            style: const TextStyle(
                color: Color(0xFFE0E5E8),
                fontSize: 17,
                fontWeight: FontWeight.w800)),
        textDirection: TextDirection.ltr)
      ..layout();
    painter.paint(
        canvas, center - Offset(painter.width / 2, painter.height / 2));
  }

  @override
  bool shouldRepaint(covariant _DonutPainter oldDelegate) =>
      oldDelegate.fraction != fraction;
}
