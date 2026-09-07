import 'package:flutter_test/flutter_test.dart';
import 'package:pulseos_mobile/main.dart';

void main() {
  testWidgets('PulseOS dashboard renders', (tester) async {
    await tester.pumpWidget(const PulseOSApp());
    expect(find.text('PulseOS'), findsOneWidget);
    expect(find.text('DEVICE HEALTH SCORE'), findsOneWidget);
    expect(find.text('Storage Intelligence'), findsOneWidget);
  });
}
