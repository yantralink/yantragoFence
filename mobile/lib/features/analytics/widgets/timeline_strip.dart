import 'package:flutter/material.dart';
import 'package:intl/intl.dart';

/// One interval drawn on a [TimelineStrip] — a rounded bar positioned
/// proportionally inside [rangeStart]..[rangeEnd]. A null [end] is
/// clamped to the range end (ongoing intervals).
class TimelineInterval {
  final DateTime start;
  final DateTime? end;
  final Color color;

  const TimelineInterval({
    required this.start,
    required this.end,
    required this.color,
  });
}

/// A vertical tick overlaid on a [TimelineStrip] — e.g. a command
/// marker at its issued time.
class TimelineTick {
  final DateTime at;
  final Color color;
  final String tooltip;

  const TimelineTick({
    required this.at,
    required this.color,
    required this.tooltip,
  });
}

/// Compact Gantt-style strip: proportional interval bars on a shared
/// time axis with optional tick markers and start/end axis labels.
/// Intervals on one strip must not overlap (faults and sessions are
/// non-overlapping by construction on the backend).
class TimelineStrip extends StatelessWidget {
  final DateTime rangeStart;
  final DateTime rangeEnd;
  final List<TimelineInterval> intervals;
  final List<TimelineTick> ticks;
  final double height;

  const TimelineStrip({
    super.key,
    required this.rangeStart,
    required this.rangeEnd,
    this.intervals = const [],
    this.ticks = const [],
    this.height = 22,
  });

  @override
  Widget build(BuildContext context) {
    final colors = Theme.of(context).colorScheme;
    final text = Theme.of(context).textTheme;
    final span = rangeEnd.difference(rangeStart).inMilliseconds;
    if (span <= 0) return const SizedBox.shrink();

    double fraction(DateTime t) => (t
            .difference(rangeStart)
            .inMilliseconds /
        span)
        .clamp(0.0, 1.0)
        .toDouble();

    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        SizedBox(
          height: height + 14,
          child: LayoutBuilder(
            builder: (context, constraints) {
              final width = constraints.maxWidth;
              return Stack(
                clipBehavior: Clip.none,
                children: [
                  Positioned(
                    top: 0,
                    left: 0,
                    right: 0,
                    height: height,
                    child: Container(
                      decoration: BoxDecoration(
                        color: colors.surfaceContainerHighest,
                        borderRadius: BorderRadius.circular(6),
                      ),
                    ),
                  ),
                  for (final iv in intervals)
                    Positioned(
                      top: 0,
                      left: fraction(iv.start) * width,
                      height: height,
                      width: _barWidth(iv, width),
                      child: Container(
                        decoration: BoxDecoration(
                          color: iv.color,
                          borderRadius: BorderRadius.circular(6),
                        ),
                      ),
                    ),
                  for (final tick in ticks)
                    Positioned(
                      top: height + 1,
                      left: (fraction(tick.at) * width - 5)
                          .clamp(0.0, width - 10)
                          .toDouble(),
                      child: Tooltip(
                        message: tick.tooltip,
                        child: Icon(Icons.arrow_drop_down,
                            size: 12, color: tick.color),
                      ),
                    ),
                ],
              );
            },
          ),
        ),
        Row(
          mainAxisAlignment: MainAxisAlignment.spaceBetween,
          children: [
            Text(_axisLabel(rangeStart), style: text.labelSmall),
            Text(_axisLabel(rangeEnd), style: text.labelSmall),
          ],
        ),
      ],
    );
  }

  double _barWidth(TimelineInterval iv, double width) {
    final span = rangeEnd.difference(rangeStart).inMilliseconds;
    // Clamp both ends into the range so an interval starting before
    // the window doesn't overflow past the right edge.
    final l = (iv.start.difference(rangeStart).inMilliseconds / span)
        .clamp(0.0, 1.0);
    final r = ((iv.end ?? rangeEnd)
                .difference(rangeStart)
                .inMilliseconds /
            span)
        .clamp(0.0, 1.0);
    // Minimum sliver so a very short interval stays visible.
    return ((r - l) * width).clamp(4.0, width - l * width).toDouble();
  }

  String _axisLabel(DateTime t) {
    final sameDay = rangeStart.day == rangeEnd.day &&
        rangeStart.month == rangeEnd.month &&
        rangeStart.year == rangeEnd.year;
    return sameDay
        ? DateFormat('HH:mm').format(t)
        : DateFormat('dd/MM HH:mm').format(t);
  }
}
