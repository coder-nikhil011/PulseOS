import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:pulseos_mobile/main.dart';
import 'package:pulseos_mobile/models/telemetry.dart';

Future<void> pumpSettled(WidgetTester tester) async {
  await tester.pump();
  await tester.pump(const Duration(milliseconds: 300));
}

/// Scrolls the ListView in increments until [predicate] returns true or
/// [maxScrolls] attempts are exhausted.
Future<bool> scrollUntilFound(
  WidgetTester tester,
  Finder target, {
  int maxScrolls = 10,
  double scrollDelta = 300,
}) async {
  for (int i = 0; i < maxScrolls; i++) {
    if (tester.any(target)) return true;
    await tester.drag(find.byType(ListView), Offset(0, -scrollDelta));
    await tester.pump(const Duration(milliseconds: 100));
  }
  return tester.any(target);
}

void main() {
  testWidgets('PulseOS dashboard renders', (tester) async {
    await tester.pumpWidget(const PulseOSApp());
    expect(find.text('PulseOS'), findsOneWidget);
    expect(find.text('DEVICE HEALTH SCORE'), findsOneWidget);
    expect(find.text('Storage'), findsOneWidget);
  });

  testWidgets('All six destinations open', (tester) async {
    await tester.pumpWidget(const PulseOSApp());

    // Hardware page
    await tester.tap(find.descendant(
        of: find.byType(NavigationBar), matching: find.text('Hardware')));
    await pumpSettled(tester);
    expect(find.text('Phone components and available readings'), findsOneWidget);

    // Router page
    await tester.tap(find.text('Router'));
    await pumpSettled(tester);
    expect(find.text('Network connection and app activity'), findsOneWidget);

    // Storage page
    await tester.tap(find.text('Storage'));
    await pumpSettled(tester);
    expect(find.text('Storage Healer'), findsOneWidget);

    // Converter page
    await tester.tap(find.text('Convert'));
    await pumpSettled(tester);
    expect(find.text('Offline Converter'), findsOneWidget);

    // Settings page
    await tester.tap(find.text('Settings'));
    await pumpSettled(tester);
    expect(find.text('Local-first mode'), findsOneWidget);

    // Scroll until the Android shortcuts section is visible
    final foundDataUsage = await scrollUntilFound(tester, find.text('Data usage'));
    expect(foundDataUsage, isTrue, reason: '"Data usage" shortcut tile not found in Settings');

    final foundScreenTime = await scrollUntilFound(tester, find.text('App screen time'));
    expect(foundScreenTime, isTrue, reason: '"App screen time" shortcut tile not found in Settings');
  });

  test('Telemetry timeline uses current device metrics when no stored events exist', () {
    const snapshot = TelemetrySnapshot(
      battery: 17,
      batteryStatus: 'Discharging',
      batteryTemperature: 41.2,
      totalStorage: 1000,
      freeStorage: 250,
      totalMemory: 1024,
      availableMemory: 300,
    );

    final events = snapshot.buildRecentEventTimeline();
    expect(events, isNotEmpty);
    expect(events.any((event) => event['message']!.contains('Battery')), isTrue);
    expect(events.any((event) => event['message']!.contains('Storage')), isTrue);
  });
}
