import 'package:flutter/material.dart';
import 'dart:math' as math;

class PulseCard extends StatelessWidget {
  final Widget child;
  final VoidCallback? onTap;

  const PulseCard({super.key, required this.child, this.onTap});

  @override
  Widget build(BuildContext context) {
    final isDark = Theme.of(context).brightness == Brightness.dark;
    return Container(
      margin: const EdgeInsets.only(bottom: 8),
      decoration: BoxDecoration(
        color: isDark ? const Color(0xFF10161C) : Colors.white,
        border: Border.all(color: isDark ? const Color(0xFF242C33) : const Color(0xFFE2E8F0)),
        borderRadius: BorderRadius.circular(12),
        boxShadow: isDark
            ? null
            : [
                BoxShadow(
                  color: Colors.black.withValues(alpha: 0.04),
                  blurRadius: 6,
                  offset: const Offset(0, 2),
                )
              ],
      ),
      child: Material(
        color: Colors.transparent,
        child: InkWell(
          borderRadius: BorderRadius.circular(12),
          onTap: onTap,
          child: Padding(padding: const EdgeInsets.all(13), child: child),
        ),
      ),
    );
  }
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

class PulseStatusBadge extends StatelessWidget {
  final String status; // NORMAL, ATTENTION, CRITICAL

  const PulseStatusBadge({super.key, required this.status});

  @override
  Widget build(BuildContext context) {
    Color bg;
    Color fg;
    String label = status.toUpperCase();

    if (label == 'CRITICAL' || label == 'HIGH') {
      bg = const Color(0x33E06D5F);
      fg = const Color(0xFFE06D5F);
    } else if (label == 'ATTENTION' || label == 'WARNING' || label == 'WATCH') {
      bg = const Color(0x33F59E0B);
      fg = const Color(0xFFF59E0B);
    } else {
      bg = const Color(0x3322C55E);
      fg = const Color(0xFF22C55E);
      label = 'NORMAL';
    }

    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 3),
      decoration: BoxDecoration(
        color: bg,
        borderRadius: BorderRadius.circular(6),
        border: Border.all(color: fg.withValues(alpha: 0.35)),
      ),
      child: Text(
        label,
        style: TextStyle(
          color: fg,
          fontSize: 9,
          fontWeight: FontWeight.w800,
          letterSpacing: 0.8,
        ),
      ),
    );
  }
}

class PulseInteractiveGraph extends StatefulWidget {
  final String title;
  final String unit;
  final List<double> values;
  final double minY;
  final double maxY;
  final Color color;
  final bool showFilters;
  final String activeFilter;
  final ValueChanged<String>? onFilterChanged;

  const PulseInteractiveGraph({
    super.key,
    required this.title,
    required this.unit,
    required this.values,
    required this.minY,
    required this.maxY,
    required this.color,
    this.showFilters = true,
    this.activeFilter = 'Live',
    this.onFilterChanged,
  });

  @override
  State<PulseInteractiveGraph> createState() => _PulseInteractiveGraphState();
}

class _PulseInteractiveGraphState extends State<PulseInteractiveGraph> {
  int? touchedIndex;

  @override
  Widget build(BuildContext context) {
    final isDark = Theme.of(context).brightness == Brightness.dark;
    final vals = widget.values;

    double current = vals.isNotEmpty ? vals.last : 0.0;
    double minVal = vals.isNotEmpty ? vals.reduce(math.min) : 0.0;
    double maxVal = vals.isNotEmpty ? vals.reduce(math.max) : 0.0;
    double avgVal = vals.isNotEmpty ? vals.reduce((a, b) => a + b) / vals.length : 0.0;

    double displayVal = (touchedIndex != null && touchedIndex! < vals.length)
        ? vals[touchedIndex!]
        : current;

    return PulseCard(
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            mainAxisAlignment: MainAxisAlignment.spaceBetween,
            children: [
              Text(widget.title.toUpperCase(), style: PulseStyles.kicker),
              Text(
                '${displayVal.toStringAsFixed(1)} ${widget.unit}',
                style: TextStyle(
                  fontSize: 15,
                  fontWeight: FontWeight.w900,
                  color: widget.color,
                ),
              ),
            ],
          ),
          const SizedBox(height: 4),
          Row(
            children: [
              Text(
                'Min: ${minVal.toStringAsFixed(1)} · Max: ${maxVal.toStringAsFixed(1)} · Avg: ${avgVal.toStringAsFixed(1)} ${widget.unit}',
                style: TextStyle(
                  fontSize: 10,
                  color: isDark ? const Color(0xFF9BA7AF) : const Color(0xFF64748B),
                ),
              ),
              if (touchedIndex != null) ...[
                const Spacer(),
                Text(
                  'Point: #${touchedIndex! + 1}',
                  style: TextStyle(fontSize: 10, fontWeight: FontWeight.bold, color: widget.color),
                ),
              ],
            ],
          ),
          if (widget.showFilters) ...[
            const SizedBox(height: 8),
            SingleChildScrollView(
              scrollDirection: Axis.horizontal,
              child: Row(
                children: ['Live', '1h', '24h', '7d', '30d'].map((f) {
                  final active = widget.activeFilter == f;
                  return Padding(
                    padding: const EdgeInsets.only(right: 6),
                    child: InkWell(
                      onTap: () => widget.onFilterChanged?.call(f),
                      borderRadius: BorderRadius.circular(12),
                      child: Container(
                        padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 3),
                        decoration: BoxDecoration(
                          color: active
                              ? widget.color.withValues(alpha: 0.2)
                              : (isDark ? const Color(0xFF161E26) : const Color(0xFFF1F5F9)),
                          borderRadius: BorderRadius.circular(12),
                          border: Border.all(
                            color: active ? widget.color : Colors.transparent,
                          ),
                        ),
                        child: Text(
                          f,
                          style: TextStyle(
                            fontSize: 10,
                            fontWeight: active ? FontWeight.w800 : FontWeight.w500,
                            color: active
                                ? widget.color
                                : (isDark ? const Color(0xFF9BA7AF) : const Color(0xFF475569)),
                          ),
                        ),
                      ),
                    ),
                  );
                }).toList(),
              ),
            ),
          ],
          const SizedBox(height: 10),
          GestureDetector(
            onPanDown: (d) => _handleTouch(d.localPosition.dx),
            onPanUpdate: (d) => _handleTouch(d.localPosition.dx),
            onPanEnd: (_) => setState(() => touchedIndex = null),
            onPanCancel: () => setState(() => touchedIndex = null),
            child: SizedBox(
              height: 85,
              width: double.infinity,
              child: CustomPaint(
                painter: _AdvancedLineGraphPainter(
                  values: vals,
                  minY: widget.minY,
                  maxY: widget.maxY,
                  color: widget.color,
                  touchedIndex: touchedIndex,
                  isDark: isDark,
                ),
              ),
            ),
          ),
        ],
      ),
    );
  }

  void _handleTouch(double dx) {
    if (widget.values.isEmpty) return;
    final RenderBox? box = context.findRenderObject() as RenderBox?;
    if (box == null) return;
    final width = box.size.width - 26; // minus padding
    if (width <= 0) return;
    final count = widget.values.length;
    final idx = ((dx / width) * (count - 1)).round().clamp(0, count - 1);
    setState(() => touchedIndex = idx);
  }
}

class _AdvancedLineGraphPainter extends CustomPainter {
  final List<double> values;
  final double minY;
  final double maxY;
  final Color color;
  final int? touchedIndex;
  final bool isDark;

  const _AdvancedLineGraphPainter({
    required this.values,
    required this.minY,
    required this.maxY,
    required this.color,
    this.touchedIndex,
    required this.isDark,
  });

  @override
  void paint(Canvas canvas, Size size) {
    final gridPaint = Paint()
      ..color = isDark ? const Color(0xFF1E2831) : const Color(0xFFE2E8F0)
      ..strokeWidth = 1;

    for (var index = 1; index < 4; index++) {
      final y = size.height * index / 4;
      canvas.drawLine(Offset(0, y), Offset(size.width, y), gridPaint);
    }

    if (values.length < 2) {
      if (values.length == 1) {
        final normalized = ((values[0] - minY) / (maxY - minY)).clamp(0.0, 1.0);
        final y = size.height * (1 - normalized);
        canvas.drawCircle(Offset(size.width / 2, y), 4, Paint()..color = color);
      }
      return;
    }

    final path = Path();
    final areaPath = Path();
    areaPath.moveTo(0, size.height);

    for (var index = 0; index < values.length; index++) {
      final x = size.width * index / (values.length - 1);
      final normalized = ((values[index] - minY) / (maxY - minY)).clamp(0.0, 1.0);
      final y = size.height * (1 - normalized);

      if (index == 0) {
        path.moveTo(x, y);
        areaPath.lineTo(x, y);
      } else {
        path.lineTo(x, y);
        areaPath.lineTo(x, y);
      }
    }

    areaPath.lineTo(size.width, size.height);
    areaPath.close();

    // Area fill with gradient
    final fillPaint = Paint()
      ..shader = LinearGradient(
        begin: Alignment.topCenter,
        end: Alignment.bottomCenter,
        colors: [
          color.withValues(alpha: 0.28),
          color.withValues(alpha: 0.02),
        ],
      ).createShader(Rect.fromLTWH(0, 0, size.width, size.height));
    canvas.drawPath(areaPath, fillPaint);

    // Line stroke
    final linePaint = Paint()
      ..color = color
      ..strokeWidth = 2.2
      ..style = PaintingStyle.stroke
      ..strokeCap = StrokeCap.round
      ..strokeJoin = StrokeJoin.round;
    canvas.drawPath(path, linePaint);

    // Touched scrubber
    if (touchedIndex != null && touchedIndex! < values.length) {
      final x = size.width * touchedIndex! / (values.length - 1);
      final normalized = ((values[touchedIndex!] - minY) / (maxY - minY)).clamp(0.0, 1.0);
      final y = size.height * (1 - normalized);

      final indicatorPaint = Paint()
        ..color = isDark ? Colors.white70 : Colors.black87
        ..strokeWidth = 1.2
        ..style = PaintingStyle.stroke;
      canvas.drawLine(Offset(x, 0), Offset(x, size.height), indicatorPaint);

      // Outer glow & center dot
      canvas.drawCircle(Offset(x, y), 5.5, Paint()..color = color.withValues(alpha: 0.5));
      canvas.drawCircle(Offset(x, y), 3.5, Paint()..color = Colors.white);
    }
  }

  @override
  bool shouldRepaint(covariant _AdvancedLineGraphPainter oldDelegate) =>
      oldDelegate.values != values ||
      oldDelegate.touchedIndex != touchedIndex ||
      oldDelegate.color != color ||
      oldDelegate.isDark != isDark;
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
  Widget build(BuildContext context) {
    return PulseInteractiveGraph(
      title: label,
      unit: '%',
      values: values,
      minY: minY,
      maxY: maxY,
      color: color,
      showFilters: false,
    );
  }
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
