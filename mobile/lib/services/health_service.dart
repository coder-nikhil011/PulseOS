import '../models/telemetry.dart';
import 'health_engine.dart';

class HealthService {
  static int? calculateScore(TelemetrySnapshot snapshot) {
    return HealthScoreEngine.calculateScore(snapshot);
  }

  static List<String> detectAlerts(TelemetrySnapshot snapshot) {
    return ProblemDetector.detect(snapshot)
        .map((problem) => problem.title)
        .toList();
  }
}
