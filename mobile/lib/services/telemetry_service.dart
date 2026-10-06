import 'dart:async';
import 'package:battery_plus/battery_plus.dart';
import 'package:flutter/services.dart';
import '../models/telemetry.dart';
import 'database_service.dart';
import 'health_service.dart';

class TelemetryController {
  final Battery _battery = Battery();
  final DatabaseService _db = DatabaseService();
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

      final healthScore = HealthService.calculateScore(nextSnapshot);
      final alerts = HealthService.detectAlerts(nextSnapshot);
      snapshot = nextSnapshot.copyWith(
        healthScore: healthScore,
        healthAlerts: alerts,
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

  double? _optionalTemperature(Object? value) {
    final temperature = (value as num?)?.toDouble();
    return temperature == null || temperature < 0 ? null : temperature;
  }
}
