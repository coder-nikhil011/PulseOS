import 'dart:async';
import 'package:battery_plus/battery_plus.dart';
import 'package:flutter/services.dart';
import '../models/telemetry.dart';
import 'alert_service.dart';
import 'database_service.dart';
import 'health_service.dart';

class TelemetryController {
  final Battery _battery = Battery();
  final DatabaseService _db = DatabaseService();
  TelemetrySnapshot snapshot = const TelemetrySnapshot();
  bool refreshing = false;
  bool _historyLoaded = false;
  final List<double> batteryHistory = <double>[];
  final List<double> cpuHistory = <double>[];
  final List<double> temperatureHistory = <double>[];
  final List<double> memoryHistory = <double>[];
  final List<double> storageHistory = <double>[];
  final List<double> latencyHistory = <double>[];

  Future<void> refresh() async {
    if (refreshing) return;
    await _loadPersistentHistory();
    refreshing = true;
    try {
      final level = await _battery.batteryLevel;
      final device = await _readDeviceSnapshot();
      final nextSnapshot = TelemetrySnapshot(
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
        batteryHistory: _history(batteryHistory, level.toDouble()),
        storageHistory: _history(
            storageHistory,
            _storageUsedPercent(
                device['totalStorage'], device['freeStorage'])),
        latencyHistory: _history(
            latencyHistory,
            (device['networkLatencyMs'] as num?)?.toDouble()),
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
        appHealth: _stringKeyedMap(device['appHealth']),
      );

      final healthScore = HealthService.calculateScore(nextSnapshot);
      final alerts = AlertService.buildAlerts(
        battery: nextSnapshot.battery,
        batteryTemperature: nextSnapshot.batteryTemperature,
        storageUsedPercent:
            nextSnapshot.totalStorage == null || nextSnapshot.freeStorage == null
                ? null
                : (((nextSnapshot.totalStorage! - nextSnapshot.freeStorage!) /
                            nextSnapshot.totalStorage!) * 100)
                        .round(),
        ramUsedPercent:
            nextSnapshot.totalMemory == null || nextSnapshot.availableMemory == null
                ? null
                : (((nextSnapshot.totalMemory! - nextSnapshot.availableMemory!) /
                            nextSnapshot.totalMemory!) * 100)
                        .round(),
        latencyMs: (device['networkLatencyMs'] as num?)?.toInt(),
      );

      await _persistHealthEvents(alerts);
      final recentDatabaseEvents = await _db.getRecentEvents(4);
      final recentEvents = recentDatabaseEvents.isNotEmpty
          ? recentDatabaseEvents.map((row) => {
              'time': _formatEventTime(row['timestamp'] as int?),
              'icon': _eventIcon(row['type'] as String?),
              'message': (row['message'] as String?) ??
                  (row['title'] as String?) ??
                  'System health updated',
            }).toList()
          : nextSnapshot.buildRecentEventTimeline();

      snapshot = nextSnapshot.copyWith(
        healthScore: healthScore,
        healthAlerts: alerts.map((alert) => alert.title).toList(),
        recentEvents: recentEvents,
      );

      // Save to database for persistent history
      await _db.insertTelemetry({
        'timestamp': DateTime.now().millisecondsSinceEpoch,
        'cpu': snapshot.cpuUsage,
        'ram_available': snapshot.availableMemory,
        'ram_total': snapshot.totalMemory,
        'battery_level': snapshot.battery,
        'battery_temp': snapshot.batteryTemperature,
        'storage_free': snapshot.freeStorage,
        'storage_total': snapshot.totalStorage,
        'latency': (device['networkLatencyMs'] as num?)?.toInt(),
      });

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

  Future<void> _persistHealthEvents(List<dynamic> alerts) async {
    final now = DateTime.now().millisecondsSinceEpoch;
    final activeKeys = <String>{};
    for (final alert in alerts) {
      final title = alert.title as String? ?? 'Health alert';
      final type = alert.type as String? ?? 'system';
      activeKeys.add('$type::$title');
      await _db.insertOrRefreshEvent({
        'timestamp': now,
        'type': type,
        'severity': alert.severity as String? ?? 'info',
        'title': title,
        'message': alert.message as String? ?? 'System health updated',
        'resolved': 0,
      });
    }
    await _db.resolveActiveEventsExcept(activeKeys);
  }

  Future<void> _loadPersistentHistory() async {
    if (_historyLoaded) return;
    _historyLoaded = true;
    try {
      final rows = await _db.getTelemetryRange(24);
      for (final row in rows.take(60)) {
        final battery = (row['battery_level'] as num?)?.toDouble();
        final cpu = (row['cpu'] as num?)?.toDouble();
        final temp = (row['battery_temp'] as num?)?.toDouble();
        final totalRam = (row['ram_total'] as num?)?.toDouble();
        final availableRam = (row['ram_available'] as num?)?.toDouble();
        if (battery != null) batteryHistory.add(battery);
        if (cpu != null) cpuHistory.add(cpu);
        if (temp != null) temperatureHistory.add(temp);
        if (totalRam != null && availableRam != null && totalRam > 0) {
          memoryHistory.add(((totalRam - availableRam) * 100 / totalRam).clamp(0, 100));
        }
      }
      if (batteryHistory.length > 60) batteryHistory.removeRange(0, batteryHistory.length - 60);
      if (cpuHistory.length > 60) cpuHistory.removeRange(0, cpuHistory.length - 60);
      if (temperatureHistory.length > 60) temperatureHistory.removeRange(0, temperatureHistory.length - 60);
      if (memoryHistory.length > 60) memoryHistory.removeRange(0, memoryHistory.length - 60);
    } catch (_) {
      // History is optional; live telemetry must continue if the DB is unavailable.
    }
  }

  String _formatEventTime(int? timestamp) {
    if (timestamp == null) return 'Now';
    final date = DateTime.fromMillisecondsSinceEpoch(timestamp);
    final diffMinutes = DateTime.now().difference(date).inMinutes;
    if (diffMinutes <= 1) return 'Now';
    if (diffMinutes < 60) return '${diffMinutes}m';
    final hours = diffMinutes ~/ 60;
    if (hours < 24) return '${hours}h';
    return '${(hours / 24).floor()}d';
  }

  String _eventIcon(String? type) {
    switch (type?.toLowerCase()) {
      case 'battery':
        return '🔋';
      case 'storage':
        return '💾';
      case 'memory':
        return '🧠';
      case 'thermal':
        return '🌡️';
      case 'network':
        return '📶';
      default:
        return '✅';
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

  double? _storageUsedPercent(Object? totalValue, Object? freeValue) {
    final total = (totalValue as num?)?.toDouble();
    final free = (freeValue as num?)?.toDouble();
    if (total == null || free == null || total <= 0) return null;
    return (((total - free) * 100) / total).clamp(0, 100);
  }

  Future<Map<String, List<double>>> fetchHistoryForRange(String filter) async {
    int hours;
    switch (filter) {
      case '1h':
        hours = 1;
        break;
      case '24h':
        hours = 24;
        break;
      case '7d':
        hours = 24 * 7;
        break;
      case '30d':
        hours = 24 * 30;
        break;
      default:
        // Live
        return {
          'cpu': cpuHistory,
          'ram': memoryHistory,
          'battery': batteryHistory,
          'temp': temperatureHistory,
          'storage': storageHistory,
          'latency': latencyHistory,
        };
    }

    try {
      final rows = await _db.getTelemetryRange(hours);
      final cpus = <double>[];
      final rams = <double>[];
      final batts = <double>[];
      final temps = <double>[];
      final storages = <double>[];
      final lats = <double>[];

      // Downsample to max 50 points so rendering stays instant and smooth
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

      return {
        'cpu': cpus.isNotEmpty ? cpus : cpuHistory,
        'ram': rams.isNotEmpty ? rams : memoryHistory,
        'battery': batts.isNotEmpty ? batts : batteryHistory,
        'temp': temps.isNotEmpty ? temps : temperatureHistory,
        'storage': storages.isNotEmpty ? storages : storageHistory,
        'latency': lats.isNotEmpty ? lats : latencyHistory,
      };
    } catch (_) {
      return {
        'cpu': cpuHistory,
        'ram': memoryHistory,
        'battery': batteryHistory,
        'temp': temperatureHistory,
        'storage': storageHistory,
        'latency': latencyHistory,
      };
    }
  }

  double? _optionalTemperature(Object? value) {
    final temperature = (value as num?)?.toDouble();
    return temperature == null || temperature < 0 ? null : temperature;
  }
}
