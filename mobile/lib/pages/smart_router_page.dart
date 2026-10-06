import 'dart:async';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import '../models/telemetry.dart';
import '../widgets/common_widgets.dart';

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
          const PulseSectionTitle('Router', 'Network connection and app activity'),
          
          // NETWORK SECTION
          PulseCard(
              child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                const PulseHeader('PHONE CONNECTION', 'Current network'),
                const SizedBox(height: 8),
                _info('Connection',
                    '${_stringKeyedMap(widget.data.hardwareDetails['connectivity'])['transport'] ?? 'Unavailable'}'),
                _info(
                    'Internet',
                    _yesNo(_stringKeyedMap(widget
                        .data.hardwareDetails['connectivity'])['validated'])),
                const SizedBox(height: 8),
                const Text('Router analyzes your connectivity and suggests optimal paths for data transfer to minimize latency.',
                    style: TextStyle(fontSize: 10, color: Color(0xFF9BA7AF))),
              ])),
          
          const SizedBox(height: 8),
          
          // APP ACTIVITY SECTION
          PulseCard(
              child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                const PulseHeader('APPS', 'Usage Access'),
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
                if (leaders.isNotEmpty) ...[
                  const SizedBox(height: 15),
                  const Text('TOP RESOURCE CONSUMERS', style: PulseStyles.kicker),
                  const SizedBox(height: 8),
                  SingleChildScrollView(
                    scrollDirection: Axis.horizontal,
                    child: DataTable(
                      headingRowColor: WidgetStateProperty.all(const Color(0xFF1A2229)),
                      columns: const [
                        DataColumn(label: Text('App', style: TextStyle(fontSize: 11, fontWeight: FontWeight.w700))),
                        DataColumn(label: Text('Time', style: TextStyle(fontSize: 11, fontWeight: FontWeight.w700))),
                        DataColumn(label: Text('RAM', style: TextStyle(fontSize: 11, fontWeight: FontWeight.w700))),
                        DataColumn(label: Text('CPU', style: TextStyle(fontSize: 11, fontWeight: FontWeight.w700))),
                      ],
                      rows: [
                        for (final leader in leaders)
                          DataRow(cells: [
                            DataCell(Text('${leader['label']}', style: const TextStyle(fontSize: 11))),
                            DataCell(
                                Text(_durationLabel(leader['minutes'] as num), style: const TextStyle(fontSize: 11))),
                            DataCell(Text(_memoryMb(leader['memory'] as num?), style: const TextStyle(fontSize: 11))),
                            DataCell(Text(leader['cpu'] == null
                                ? '—'
                                : '${(leader['cpu'] as num).toStringAsFixed(1)}%', style: const TextStyle(fontSize: 11))),
                          ]),
                      ],
                    ),
                  ),
                ],
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

extension on _SmartRouterPageState {
  Map<String, dynamic> _stringKeyedMap(Object? value) {
    if (value is! Map) return const {};
    return value.map((key, item) => MapEntry(key.toString(), item));
  }

  String _yesNo(Object? value) => value == true
      ? 'Available'
      : value == false
          ? 'Not present'
          : 'Unavailable';

  String _durationLabel(num minutes) {
    final value = minutes.round();
    if (value < 60) return '$value min';
    return '${value ~/ 60}h ${value % 60}m';
  }

  String _memoryMb(num? bytes) => bytes == null
      ? '—'
      : '${(bytes / (1024 * 1024)).toStringAsFixed(1)} MB';

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
