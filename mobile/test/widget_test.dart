import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:pulseos_mobile/main.dart';

void main() {
  testWidgets('PulseOS dashboard renders', (tester) async {
    await tester.pumpWidget(const PulseOSApp());
    expect(find.text('PulseOS'), findsOneWidget);
    expect(find.text('DEVICE HEALTH SCORE'), findsOneWidget);
    expect(find.text('Storage'), findsOneWidget);
  });

  testWidgets('All six destinations open', (tester) async {
    await tester.pumpWidget(const PulseOSApp());

    await tester.tap(find.descendant(
        of: find.byType(NavigationBar), matching: find.text('Hardware')));
    await tester.pumpAndSettle();
    expect(
        find.text('Phone components and available readings'), findsOneWidget);

    await tester.tap(find.text('Router'));
    await tester.pumpAndSettle();
    expect(find.text('Network connection and app activity'), findsOneWidget);

    await tester.tap(find.text('Storage'));
    await tester.pumpAndSettle();
    expect(find.text('Storage Healer'), findsOneWidget);

    await tester.tap(find.text('Convert'));
    await tester.pumpAndSettle();
    expect(find.text('Offline Converter'), findsOneWidget);

    await tester.tap(find.text('Settings'));
    await tester.pumpAndSettle();
    expect(find.text('Local-first mode'), findsOneWidget);
    expect(find.text('Data usage'), findsOneWidget);
    await tester.drag(find.byType(ListView), const Offset(0, -420));
    await tester.pumpAndSettle();
    expect(find.text('App screen time'), findsOneWidget);
  });
}
