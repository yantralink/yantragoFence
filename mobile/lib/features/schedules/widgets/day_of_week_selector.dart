import 'package:flutter/material.dart';
import 'package:intl/intl.dart';

import 'package:yantrago/features/schedules/models/machine_schedule.dart';

/// Monday–Sunday day-of-week chips for the schedule editor.
///
/// Displays localized short weekday names (intl) and emits the bitmask the
/// backend expects (Mon=1, Tue=2, … Sun=64; 127=daily).
class DayOfWeekSelector extends StatelessWidget {
  final int daysMask;
  final ValueChanged<int> onChanged;

  const DayOfWeekSelector({
    super.key,
    required this.daysMask,
    required this.onChanged,
  });

  /// 2024-01-01 is a Monday — fixed reference dates give localized short
  /// weekday names for each index (Mon=1 … Sun=7). Created per build so a
  /// language switch re-localizes the chip labels.
  String _dayLabel(int weekday) =>
      DateFormat.E().format(DateTime(2024, 1, weekday));

  @override
  Widget build(BuildContext context) {
    return Wrap(
      spacing: 6,
      runSpacing: 6,
      children: List.generate(7, (i) {
        final weekday = i + 1;
        final selected = daysMask & (1 << i) != 0;
        return FilterChip(
          label: Text(_dayLabel(weekday)),
          selected: selected,
          onSelected: (v) =>
              onChanged(MachineSchedule.toggleDay(daysMask, weekday, v)),
        );
      }),
    );
  }
}
