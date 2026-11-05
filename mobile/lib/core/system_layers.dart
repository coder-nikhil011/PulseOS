enum PulseLayer { ui, native, intelligence }

class PulseFeatureStatus {
  final String name;
  final PulseLayer layer;
  final String state;

  const PulseFeatureStatus({
    required this.name,
    required this.layer,
    required this.state,
  });
}

class PulseLayerRegistry {
  static const List<PulseFeatureStatus> features = [
    PulseFeatureStatus(
      name: 'Health dashboard',
      layer: PulseLayer.ui,
      state: 'LIVE',
    ),
    PulseFeatureStatus(
      name: 'Telemetry refresh',
      layer: PulseLayer.intelligence,
      state: 'LIVE',
    ),
    PulseFeatureStatus(
      name: 'Android settings routes',
      layer: PulseLayer.native,
      state: 'LIVE',
    ),
    PulseFeatureStatus(
      name: 'Background monitoring',
      layer: PulseLayer.native,
      state: 'PENDING',
    ),
    PulseFeatureStatus(
      name: 'Notification manager',
      layer: PulseLayer.native,
      state: 'PENDING',
    ),
    PulseFeatureStatus(
      name: 'Network latency analysis',
      layer: PulseLayer.intelligence,
      state: 'PENDING',
    ),
    PulseFeatureStatus(
      name: 'MediaStore scanning',
      layer: PulseLayer.native,
      state: 'PENDING',
    ),
    PulseFeatureStatus(
      name: 'Home widgets',
      layer: PulseLayer.native,
      state: 'PENDING',
    ),
  ];

  static Map<PulseLayer, int> summary() {
    final counts = {PulseLayer.ui: 0, PulseLayer.native: 0, PulseLayer.intelligence: 0};
    for (final feature in features) {
      counts[feature.layer] = (counts[feature.layer] ?? 0) + 1;
    }
    return counts;
  }

  static List<PulseFeatureStatus> byLayer(PulseLayer layer) =>
      features.where((feature) => feature.layer == layer).toList();
}
