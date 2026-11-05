import '../models/alert.dart';

class AlertService {
  static List<PulseAlert> buildAlerts({
    required double? battery,
    required double? batteryTemperature,
    required int? storageUsedPercent,
    required int? ramUsedPercent,
    required int? latencyMs,
  }) {
    final alerts = <PulseAlert>[];

    if (battery != null && battery < 20) {
      alerts.add(PulseAlert(
        id: 'battery_low',
        type: 'battery',
        severity: 'warning',
        title: 'Battery low',
        message:
            'Battery is at ${battery.round()}%. Charge soon to avoid an unexpected shutdown.',
        actionLabel: 'Battery',
        actionTarget: 'settings',
        createdAt: DateTime.now(),
      ));
    }

    if (storageUsedPercent != null && storageUsedPercent > 90) {
      alerts.add(PulseAlert(
        id: 'storage_full',
        type: 'storage',
        severity: 'high',
        title: 'Storage almost full',
        message:
            'Storage is $storageUsedPercent% full. Review large files and old downloads.',
        actionLabel: 'Open storage',
        actionTarget: 'storage',
        createdAt: DateTime.now(),
      ));
    }

    if (ramUsedPercent != null && ramUsedPercent > 85) {
      alerts.add(PulseAlert(
        id: 'ram_high',
        type: 'memory',
        severity: 'warning',
        title: 'RAM pressure detected',
        message:
            'Memory usage is high. Close heavy apps to improve responsiveness.',
        actionLabel: 'Check memory',
        actionTarget: 'hardware',
        createdAt: DateTime.now(),
      ));
    }

    if (batteryTemperature != null && batteryTemperature > 45) {
      alerts.add(PulseAlert(
        id: 'thermal_critical',
        type: 'thermal',
        severity: 'critical',
        title: 'Device overheating',
        message:
            'Temperature is ${batteryTemperature.toStringAsFixed(1)}°C. Reduce heavy use and let the device cool down.',
        actionLabel: 'Thermals',
        actionTarget: 'hardware',
        createdAt: DateTime.now(),
      ));
    } else if (batteryTemperature != null && batteryTemperature > 40) {
      alerts.add(PulseAlert(
        id: 'thermal_warning',
        type: 'thermal',
        severity: 'high',
        title: 'Temperature warning',
        message:
            'Device is running warm. Charging and gaming may be increasing heat.',
        actionLabel: 'Hardware',
        actionTarget: 'hardware',
        createdAt: DateTime.now(),
      ));
    }

    if (latencyMs != null && latencyMs > 150) {
      alerts.add(PulseAlert(
        id: 'network_unstable',
        type: 'network',
        severity: 'warning',
        title: 'Network unstable',
        message:
            'Latency is ${latencyMs}ms. Connection may be weak or unstable right now.',
        actionLabel: 'Router',
        actionTarget: 'router',
        createdAt: DateTime.now(),
      ));
    }

    return deduplicate(alerts);
  }

  static List<PulseAlert> deduplicate(List<PulseAlert> alerts) {
    final map = <String, PulseAlert>{};
    for (final alert in alerts) {
      map[alert.id] = alert;
    }
    final ordered = map.values.toList()
      ..sort((a, b) => b.createdAt.compareTo(a.createdAt));
    return ordered;
  }

  static PulseAlert? topPriority(List<PulseAlert> alerts) {
    if (alerts.isEmpty) return null;

    final order = {'critical': 4, 'high': 3, 'warning': 2, 'info': 1};

    alerts.sort((a, b) {
      final diff = (order[b.severity] ?? 0) - (order[a.severity] ?? 0);
      if (diff != 0) return diff;
      return b.createdAt.compareTo(a.createdAt);
    });

    return alerts.first;
  }
}
