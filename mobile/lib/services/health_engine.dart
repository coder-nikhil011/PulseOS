import '../models/telemetry.dart';

class HealthProblem {
  final String type;
  final String severity; // INFO, WARNING, HIGH, CRITICAL
  final String title;
  final String message;
  final double? currentValue;
  final String recommendation;
  final String action;

  HealthProblem({
    required this.type,
    required this.severity,
    required this.title,
    required this.message,
    this.currentValue,
    required this.recommendation,
    required this.action,
  });
}

class HealthScoreEngine {
  // Configurable weights
  static const double cpuWeight = 0.15;
  static const double ramWeight = 0.20;
  static const double batteryWeight = 0.20;
  static const double thermalWeight = 0.25;
  static const double storageWeight = 0.20;

  static int? calculateScore(TelemetrySnapshot data) {
    double totalScore = 0;
    int contributingMetrics = 0;

    // 1. CPU Health (Inversely proportional to usage)
    if (data.cpuUsage != null) {
      totalScore += (100 - data.cpuUsage!) * cpuWeight;
      contributingMetrics++;
    }

    // 2. RAM Health (Available % )
    if (data.availableMemory != null && data.totalMemory != null) {
      final double ramHealth = (data.availableMemory! / data.totalMemory!) * 100;
      totalScore += ramHealth * ramWeight;
      contributingMetrics++;
    }

    // 3. Battery Health (Charge level)
    if (data.battery != null) {
      totalScore += data.battery! * batteryWeight;
      contributingMetrics++;
    }

    // 4. Thermal Health (Mapping temp to score)
    if (data.batteryTemperature != null) {
      final double temp = data.batteryTemperature!;
      final double thermalScore;
      if (temp < 30) {
        thermalScore = 100;
      } else if (temp < 35) {
        thermalScore = 90;
      } else if (temp < 40) {
        thermalScore = 70;
      } else if (temp < 45) {
        thermalScore = 40;
      } else {
        thermalScore = 10;
      }

      totalScore += thermalScore * thermalWeight;
      contributingMetrics++;
    }

    // 5. Storage Health (Free space %)
    if (data.totalStorage != null && data.freeStorage != null) {
      final double storageHealth = (data.freeStorage! / data.totalStorage!) * 100;
      totalScore += storageHealth * storageWeight;
      contributingMetrics++;
    }

    if (contributingMetrics == 0) return null;

    // Normalize score based on how many metrics were actually available
    double sumOfWeights = 0;
    if (data.cpuUsage != null) sumOfWeights += cpuWeight;
    if (data.availableMemory != null) sumOfWeights += ramWeight;
    if (data.battery != null) sumOfWeights += batteryWeight;
    if (data.batteryTemperature != null) sumOfWeights += thermalWeight;
    if (data.totalStorage != null) sumOfWeights += storageWeight;

    return ((totalScore / sumOfWeights).round()).clamp(0, 100);
  }
}

class ProblemDetector {
  static List<HealthProblem> detect(TelemetrySnapshot data) {
    final problems = <HealthProblem>[];

    // RAM Check
    if (data.availableMemory != null && data.totalMemory != null) {
      double usedPercent = ((data.totalMemory! - data.availableMemory!) / data.totalMemory!) * 100;
      if (usedPercent > 90) {
        problems.add(HealthProblem(
          type: 'MEMORY',
          severity: 'HIGH',
          title: 'High Memory Usage',
          message: 'RAM usage is currently ${usedPercent.toStringAsFixed(1)}%.',
          currentValue: usedPercent,
          recommendation: 'Close some background apps to free up memory.',
          action: 'CHECK_MEMORY',
        ));
      }
    }

    // Thermal Check
    if (data.batteryTemperature != null) {
      double temp = data.batteryTemperature!;
      if (temp > 45) {
        problems.add(HealthProblem(
          type: 'THERMAL',
          severity: 'CRITICAL',
          title: 'Device Overheating',
          message: 'Battery temperature is critical: ${temp.toStringAsFixed(1)}°C.',
          currentValue: temp,
          recommendation: 'Stop using heavy apps and let the device cool down.',
          action: 'VIEW_THERMALS',
        ));
      } else if (temp > 40) {
        problems.add(HealthProblem(
          type: 'THERMAL',
          severity: 'WARNING',
          title: 'Temperature Warning',
          message: 'Device is getting warm: ${temp.toStringAsFixed(1)}°C.',
          currentValue: temp,
          recommendation: 'Avoid charging while using heavy apps.',
          action: 'VIEW_THERMALS',
        ));
      }
    }

    // Storage Check
    if (data.totalStorage != null && data.freeStorage != null) {
      double usedPercent = ((data.totalStorage! - data.freeStorage!) / data.totalStorage!) * 100;
      if (usedPercent > 90) {
        problems.add(HealthProblem(
          type: 'STORAGE',
          severity: 'HIGH',
          title: 'Storage Almost Full',
          message: 'Storage is ${usedPercent.toStringAsFixed(1)}% full.',
          currentValue: usedPercent,
          recommendation: 'Run Storage Healer to remove large unnecessary files.',
          action: 'OPEN_STORAGE',
        ));
      }
    }

    // Battery Check
    if (data.battery != null && data.battery! < 20) {
      problems.add(HealthProblem(
        type: 'BATTERY',
        severity: 'WARNING',
        title: 'Low Battery',
        message: 'Battery is below 20% (${data.battery!.round()}%).',
        currentValue: data.battery,
        recommendation: 'Connect your charger soon to avoid shutdown.',
        action: 'BATTERY_SETTINGS',
      ));
    }

    return problems;
  }
}
