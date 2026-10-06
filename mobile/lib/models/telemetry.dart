
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
  final int? healthScore;
  final List<String> healthAlerts;

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
    this.healthScore,
    this.healthAlerts = const [],
  });

  TelemetrySnapshot copyWith({
    double? battery,
    DateTime? capturedAt,
    String? error,
    String? model,
    String? manufacturer,
    String? brand,
    String? androidVersion,
    String? securityPatch,
    String? buildNumber,
    String? architecture,
    int? androidApi,
    int? totalMemory,
    int? availableMemory,
    int? totalStorage,
    int? freeStorage,
    int? cpuCores,
    double? cpuUsage,
    int? processMemory,
    List<Map<String, dynamic>>? processes,
    List<double>? cpuHistory,
    List<double>? temperatureHistory,
    List<double>? memoryHistory,
    String? batteryStatus,
    double? batteryTemperature,
    int? batteryVoltage,
    int? batteryHealth,
    List<Map<String, dynamic>>? temperatures,
    Map<String, dynamic>? hardwareDetails,
    int? healthScore,
    List<String>? healthAlerts,
  }) {
    return TelemetrySnapshot(
      battery: battery ?? this.battery,
      capturedAt: capturedAt ?? this.capturedAt,
      error: error ?? this.error,
      model: model ?? this.model,
      manufacturer: manufacturer ?? this.manufacturer,
      brand: brand ?? this.brand,
      androidVersion: androidVersion ?? this.androidVersion,
      securityPatch: securityPatch ?? this.securityPatch,
      buildNumber: buildNumber ?? this.buildNumber,
      architecture: architecture ?? this.architecture,
      androidApi: androidApi ?? this.androidApi,
      totalMemory: totalMemory ?? this.totalMemory,
      availableMemory: availableMemory ?? this.availableMemory,
      totalStorage: totalStorage ?? this.totalStorage,
      freeStorage: freeStorage ?? this.freeStorage,
      cpuCores: cpuCores ?? this.cpuCores,
      cpuUsage: cpuUsage ?? this.cpuUsage,
      processMemory: processMemory ?? this.processMemory,
      processes: processes ?? this.processes,
      cpuHistory: cpuHistory ?? this.cpuHistory,
      temperatureHistory: temperatureHistory ?? this.temperatureHistory,
      memoryHistory: memoryHistory ?? this.memoryHistory,
      batteryStatus: batteryStatus ?? this.batteryStatus,
      batteryTemperature: batteryTemperature ?? this.batteryTemperature,
      batteryVoltage: batteryVoltage ?? this.batteryVoltage,
      batteryHealth: batteryHealth ?? this.batteryHealth,
      temperatures: temperatures ?? this.temperatures,
      hardwareDetails: hardwareDetails ?? this.hardwareDetails,
      healthScore: healthScore ?? this.healthScore,
      healthAlerts: healthAlerts ?? this.healthAlerts,
    );
  }

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
    if (healthScore != null) {
      return healthScore;
    }
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
    if (healthAlerts.isNotEmpty) {
      return healthAlerts;
    }
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
