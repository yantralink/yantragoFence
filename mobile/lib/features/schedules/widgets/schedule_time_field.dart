import 'package:flutter/material.dart';

/// Tappable time field for the schedule editor — label, clock icon, the
/// formatted time, and an optional "+1 day" chip when the schedule crosses
/// midnight.
class ScheduleTimeField extends StatelessWidget {
  final String label;
  final TimeOfDay time;
  final VoidCallback onTap;
  final bool showNextDayChip;
  final String? nextDayLabel;

  const ScheduleTimeField({
    super.key,
    required this.label,
    required this.time,
    required this.onTap,
    this.showNextDayChip = false,
    this.nextDayLabel,
  });

  @override
  Widget build(BuildContext context) {
    final text = Theme.of(context).textTheme;
    final colors = Theme.of(context).colorScheme;

    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: <Widget>[
        Text(label,
            style: text.labelLarge?.copyWith(color: colors.onSurfaceVariant)),
        const SizedBox(height: 4),
        InkWell(
          onTap: onTap,
          borderRadius: BorderRadius.circular(12),
          child: Container(
            width: double.infinity,
            padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 14),
            decoration: BoxDecoration(
              border: Border.all(color: colors.outlineVariant),
              borderRadius: BorderRadius.circular(12),
            ),
            child: Row(
              children: <Widget>[
                Icon(Icons.access_time, size: 20, color: colors.primary),
                const SizedBox(width: 10),
                Text(
                  MaterialLocalizations.of(context).formatTimeOfDay(time),
                  style: text.titleMedium,
                ),
                const Spacer(),
                if (showNextDayChip && nextDayLabel != null)
                  Chip(
                    label: Text(nextDayLabel!),
                    visualDensity: VisualDensity.compact,
                    padding: EdgeInsets.zero,
                  ),
              ],
            ),
          ),
        ),
      ],
    );
  }
}
