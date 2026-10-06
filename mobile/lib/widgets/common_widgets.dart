import 'package:flutter/material.dart';
import 'dart:math' as math;

class PulseCard extends StatelessWidget {
  final Widget child;
  final VoidCallback? onTap;

  const PulseCard({super.key, required this.child, this.onTap});

  @override
  Widget build(BuildContext context) => Container(
        margin: const EdgeInsets.only(bottom: 8),
        decoration: BoxDecoration(
            color: const Color(0xFF10161C),
            border: Border.all(color: const Color(0xFF242C33)),
            borderRadius: BorderRadius.circular(11)),
        child: Material(
            color: Colors.transparent,
            child: InkWell(
                borderRadius: BorderRadius.circular(11),
                onTap: onTap,
                child:
                    Padding(padding: const EdgeInsets.all(13), child: child))));
}

class PulseMetric extends StatelessWidget {
  final String title, value, sub;
  final VoidCallback tap;

  const PulseMetric(this.title, this.value, this.sub, this.tap, {super.key});

  @override
  Widget build(BuildContext context) => PulseCard(
        onTap: tap,
        child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
          Text(title, style: PulseStyles.kicker),
          const SizedBox(height: 5),
          Flexible(
              child: Text(value,
                  maxLines: 2,
                  overflow: TextOverflow.ellipsis,
                  style: const TextStyle(
                      fontSize: 20, fontWeight: FontWeight.w900))),
          Text(sub,
              maxLines: 2,
              overflow: TextOverflow.ellipsis,
              style: const TextStyle(fontSize: 10, color: Color(0xFF9BA7AF)))
        ]),
      );
}

class PulseHeader extends StatelessWidget {
  final String k, t;

  const PulseHeader(this.k, this.t, {super.key});

  @override
  Widget build(BuildContext context) =>
      Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
        Text(k, style: PulseStyles.kicker),
        const SizedBox(height: 4),
        Text(t,
            style: const TextStyle(fontSize: 14, fontWeight: FontWeight.w700))
      ]);
}

class PulseSectionTitle extends StatelessWidget {
  final String t, s;

  const PulseSectionTitle(this.t, this.s, {super.key});

  @override
  Widget build(BuildContext context) => Padding(
        padding: const EdgeInsets.only(bottom: 10),
        child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
          Text(t,
              style: const TextStyle(fontSize: 22, fontWeight: FontWeight.w800)),
          Text(s, style: const TextStyle(fontSize: 11, color: Color(0xFF9BA7AF)))
        ]),
      );
}

class PulseLivePill extends StatelessWidget {
  final bool available;

  const PulseLivePill({super.key, required this.available});

  @override
  Widget build(BuildContext context) => Container(
        margin: const EdgeInsets.only(right: 4),
        padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 5),
        decoration: BoxDecoration(
            border: Border.all(color: const Color(0xFF283129)),
            borderRadius: BorderRadius.circular(18)),
        child: Text(available ? '● LIVE' : '○ LIMITED',
            style: TextStyle(
                fontSize: 9,
                color:
                    available ? const Color(0xFFA8B9AC) : const Color(0xFFE0BE9E),
                fontWeight: FontWeight.w700)));
}

class PulseLogo extends StatelessWidget {
  const PulseLogo({super.key});

  @override
  Widget build(BuildContext context) => Container(
        width: 32,
        height: 32,
        clipBehavior: Clip.antiAlias,
        decoration: BoxDecoration(borderRadius: BorderRadius.circular(9)),
        child: Image.asset('web/icons/Icon-maskable-512.png', fit: BoxFit.cover));
}

class PulseHealthBar extends StatelessWidget {
  final String title;
  final int? value;

  const PulseHealthBar(this.title, this.value, {super.key});

  @override
  Widget build(BuildContext context) => Padding(
        padding: const EdgeInsets.only(bottom: 8),
        child: Column(children: [
          Row(children: [
            Expanded(
                child: Text(title,
                    style:
                        const TextStyle(fontSize: 11, color: Color(0xFF9BA7AF)))),
            Text(value == null ? 'Unavailable' : '$value%',
                style: const TextStyle(fontSize: 11, fontWeight: FontWeight.w700))
          ]),
          const SizedBox(height: 4),
          ClipRRect(
              borderRadius: BorderRadius.circular(9),
              child: LinearProgressIndicator(
                  value: value == null ? 0 : value! / 100,
                  minHeight: 5,
                  backgroundColor: const Color(0x00ff1c23),
                  valueColor: const AlwaysStoppedAnimation(Color(0xFFB9C3C9))))
        ]),
      );
}

class PulseProblemLine extends StatelessWidget {
  final String text;
  final bool good;

  const PulseProblemLine(this.text, {super.key, this.good = false});

  @override
  Widget build(BuildContext context) => Container(
        margin: const EdgeInsets.only(bottom: 6),
        padding: const EdgeInsets.all(9),
        decoration: BoxDecoration(
            color: const Color(0xFF0C1217),
            border: Border.all(color: const Color(0xFF20272E)),
            borderRadius: BorderRadius.circular(8)),
        child: Row(children: [
          Container(
              width: 4,
              height: 23,
              decoration: BoxDecoration(
                  color: good ? const Color(0xFF859C8D) : const Color(0xFF9A7F68),
                  borderRadius: BorderRadius.circular(3))),
          const SizedBox(width: 8),
          Expanded(
              child: Text(text,
                  style: const TextStyle(fontSize: 11, fontWeight: FontWeight.w600)))
        ]),
      );
}

class PulseLiveGraph extends StatelessWidget {
  final List<double> values;
  final double minY;
  final double maxY;
  final Color color;
  final String label;

  const PulseLiveGraph({
    super.key,
    required this.values,
    required this.minY,
    required this.maxY,
    required this.color,
    required this.label,
  });

  @override
  Widget build(BuildContext context) => Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(label, style: PulseStyles.kicker),
          const SizedBox(height: 5),
          SizedBox(
            height: 72,
            width: double.infinity,
            child: CustomPaint(
              painter: _LineGraphPainter(
                values: values,
                minY: minY,
                maxY: maxY,
                color: color,
              ),
            ),
          ),
        ],
      );
}

class _LineGraphPainter extends CustomPainter {
  final List<double> values;
  final double minY;
  final double maxY;
  final Color color;

  const _LineGraphPainter({
    required this.values,
    required this.minY,
    required this.maxY,
    required this.color,
  });

  @override
  void paint(Canvas canvas, Size size) {
    final gridPaint = Paint()
      ..color = const Color(0xFF263139)
      ..strokeWidth = 1;
    for (var index = 1; index < 4; index++) {
      final y = size.height * index / 4;
      canvas.drawLine(Offset(0, y), Offset(size.width, y), gridPaint);
    }
    if (values.length < 2) return;
    final line = Paint()
      ..color = color
      ..strokeWidth = 2
      ..style = PaintingStyle.stroke
      ..strokeCap = StrokeCap.round;
    final path = Path();
    for (var index = 0; index < values.length; index++) {
      final x = size.width * index / (values.length - 1);
      final normalized =
          ((values[index] - minY) / (maxY - minY)).clamp(0.0, 1.0);
      final point = Offset(x, size.height * (1 - normalized));
      if (index == 0) {
        path.moveTo(point.dx, point.dy);
      } else {
        path.lineTo(point.dx, point.dy);
      }
    }
    canvas.drawPath(path, line);
  }

  @override
  bool shouldRepaint(covariant _LineGraphPainter oldDelegate) =>
      oldDelegate.values != values || oldDelegate.color != color;
}

class PulseDonutPainter extends CustomPainter {
  final double fraction;

  const PulseDonutPainter({required this.fraction});

  @override
  void paint(Canvas canvas, Size size) {
    final center = Offset(size.width / 2, size.height / 2);
    final radius = size.width / 2 - 7;
    final background = Paint()
      ..color = const Color(0xFF4D5962)
      ..style = PaintingStyle.stroke
      ..strokeWidth = 16;
    final foreground = Paint()
      ..color = const Color(0xFFC2CBD1)
      ..style = PaintingStyle.stroke
      ..strokeWidth = 16;
    canvas.drawCircle(center, radius, background);
    if (fraction > 0) {
      canvas.drawArc(Rect.fromCircle(center: center, radius: radius),
          -math.pi / 2, math.pi * 2 * fraction, false, foreground);
    }
    final label = fraction == 0 ? '—' : '${(fraction * 100).round()}%';
    final painter = TextPainter(
        text: TextSpan(
            text: label,
            style: const TextStyle(
                color: Color(0xFFE0E5E8),
                fontSize: 17,
                fontWeight: FontWeight.w800)),
        textDirection: TextDirection.ltr)
      ..layout();
    painter.paint(
        canvas, center - Offset(painter.width / 2, painter.height / 2));
  }

  @override
  bool shouldRepaint(covariant PulseDonutPainter oldDelegate) =>
      oldDelegate.fraction != fraction;
}

class PulseStyles {
  static const TextStyle kicker = TextStyle(
      fontSize: 9,
      letterSpacing: 1.25,
      fontWeight: FontWeight.w800,
      color: Color(0xFF9BA7AF));
}
