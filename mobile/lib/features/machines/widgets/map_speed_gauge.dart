import 'package:flutter/material.dart';

/// Circular speed gauge shown bottom-left on the machine map — dark
/// disc with a green arc and the machine's current speed. Rendered
/// only when `MachineLocation.speed` is non-null.
class MapSpeedGauge extends StatelessWidget {
  final String valueLabel;
  final String unitLabel;

  const MapSpeedGauge({
    super.key,
    required this.valueLabel,
    required this.unitLabel,
  });

  static const double _size = 64;

  @override
  Widget build(BuildContext context) {
    return SizedBox(
      width: _size,
      height: _size,
      child: Stack(
        alignment: Alignment.center,
        children: <Widget>[
          const CustomPaint(
            size: Size.square(_size),
            painter: _GaugePainter(),
          ),
          Column(
            mainAxisSize: MainAxisSize.min,
            children: <Widget>[
              Text(
                valueLabel,
                style: const TextStyle(
                  color: Colors.white,
                  fontSize: 17,
                  fontWeight: FontWeight.w800,
                  height: 1,
                ),
              ),
              Text(
                unitLabel,
                style: const TextStyle(
                  color: Colors.white70,
                  fontSize: 10,
                  fontWeight: FontWeight.w500,
                ),
              ),
            ],
          ),
        ],
      ),
    );
  }
}

class _GaugePainter extends CustomPainter {
  const _GaugePainter();

  @override
  void paint(Canvas canvas, Size size) {
    final Offset center = size.center(Offset.zero);
    final double radius = size.width / 2;

    canvas.drawCircle(
      center,
      radius,
      Paint()..color = const Color(0xFF1F2937),
    );

    const double start = 2.356; // 135°
    const double sweep = 3.665; // 210°
    canvas.drawArc(
      Rect.fromCircle(center: center, radius: radius - 4),
      start,
      sweep,
      false,
      Paint()
        ..color = const Color(0xFF22C55E)
        ..style = PaintingStyle.stroke
        ..strokeWidth = 4
        ..strokeCap = StrokeCap.round,
    );
  }

  @override
  bool shouldRepaint(_GaugePainter oldDelegate) => false;
}
