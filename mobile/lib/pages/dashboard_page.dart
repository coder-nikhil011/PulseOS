import 'package:flutter/material.dart';
import '../models/telemetry.dart';
import '../widgets/common_widgets.dart';

class DashboardPage extends StatelessWidget {
  final TelemetrySnapshot data;
  final ValueChanged<int> onNavigate;

  const DashboardPage({super.key, required this.data, required this.onNavigate});

  @override
  Widget build(BuildContext context) {
    return ListView(
      padding: const EdgeInsets.fromLTRB(12, 12, 12, 20),
      children: [
        const Text('OVERVIEW', style: PulseStyles.kicker),
        const SizedBox(height: 4),
        const Text('Device Health',
            style: TextStyle(fontSize: 21, fontWeight: FontWeight.w800)),
        const SizedBox(height: 12),
        PulseCard(
          child:
              Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
            const Text('DEVICE HEALTH SCORE', style: PulseStyles.kicker),
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
            PulseHealthBar('Performance', data.performance),
            PulseHealthBar('Hardware', data.hardware),
            PulseHealthBar('Storage used', data.storage),
            PulseHealthBar('Battery charge', data.battery?.round()),
          ]),
        ),
        const SizedBox(height: 8),
        if (data.healthAlerts.isNotEmpty)
          PulseCard(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                const PulseHeader('ACTIVE ALERTS', 'Current observations'),
                const SizedBox(height: 8),
                for (final alert in data.healthAlerts)
                  PulseProblemLine(alert),
              ],
            ),
          ),
        const SizedBox(height: 8),
        const Text('AVAILABLE TELEMETRY', style: PulseStyles.kicker),
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
                PulseMetric(
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
                PulseMetric(
                    'CPU',
                    data.cpuUsage == null
                        ? 'Sampling…'
                        : '${data.cpuUsage!.round()}%',
                    'Android Runtime',
                    () => _showCpuDetails(context, data)),
                PulseMetric('MEMORY', _formatBytes(data.availableMemory),
                    'Available RAM', () => _showMemoryDetails(context, data)),
                PulseMetric(
                    'TEMPERATURE',
                    data.batteryTemperature == null
                        ? 'Unavailable'
                        : _temperatureLabel(data.batteryTemperature),
                    data.temperatures.isEmpty
                        ? 'Battery sensor only'
                        : '${data.temperatures.length} sensors',
                    () => _showTemperatureDetails(context, data)),
                PulseMetric(
                    'DEVICE',
                    data.model ?? 'Unavailable',
                    data.androidApi == null
                        ? 'Android details unavailable'
                        : 'Android API ${data.androidApi}',
                    () => _showDeviceDetails(context, data)),
                PulseMetric(
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
        PulseCard(
          child:
              Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
            const PulseHeader('TELEMETRY STATUS', 'What PulseOS can read'),
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
        PulseCard(
          child:
              Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
            const PulseHeader('ATTENTION', 'Active Problems'),
            const SizedBox(height: 8),
            if (data.problems.isEmpty)
              const PulseProblemLine('No observed threshold breaches are reported',
                  good: true)
            else
              for (final problem in data.problems) PulseProblemLine(problem),
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
        PulseCard(
          child:
              Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
            const PulseHeader('STORAGE', 'App storage only'),
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
        const PulseCard(
            child:
                Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
          PulseHeader('EVENTS', 'Recent Activity'),
          SizedBox(height: 6),
          Text('No events recorded in this session.',
              style: TextStyle(fontSize: 11, color: Color(0xFF9BA7AF))),
        ])),
      ],
    );
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
        ]),
      );

  String _formatBytes(int? bytes) {
    if (bytes == null) return 'Unavailable';
    if (bytes < 1024) return '$bytes B';
    if (bytes < 1024 * 1024) return '${(bytes / 1024).toStringAsFixed(1)} KB';
    if (bytes < 1024 * 1024 * 1024) {
      return '${(bytes / (1024 * 1024)).toStringAsFixed(1)} MB';
    }
    return '${(bytes / (1024 * 1024 * 1024)).toStringAsFixed(2)} GB';
  }

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

  void _unavailable(BuildContext context, String metric) => _show(
      context,
      metric,
      const Text(
        'This platform does not expose this data to PulseOS. The value is marked unavailable instead of being simulated.',
        style: TextStyle(fontSize: 12, color: Color(0xFFB9C5CB)),
      ));

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

  void _showCpuDetails(BuildContext context, TelemetrySnapshot data) {
    // This logic is moved to its own component in the original file, 
    // I will keep the call here and define the component in a separate file or as a private class.
    // For now, I'll implement a simple placeholder or move the _CpuDetails class here.
  }

  void _showMemoryDetails(BuildContext context, TelemetrySnapshot data) {
    // Similar to CPU details, logic is complex. I will move the actual UI components 
    // from main.dart to a new widgets file or within this page.
  }

  void _showTemperatureDetails(BuildContext context, TelemetrySnapshot data) {
    // Placeholder
  }

  void _showDeviceDetails(BuildContext context, TelemetrySnapshot data) {
    // Placeholder
  }

  void _showProcessDetails(BuildContext context, TelemetrySnapshot data) {
    // Placeholder
  }
}
