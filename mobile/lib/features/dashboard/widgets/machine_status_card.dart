import 'package:flutter/material.dart';

import 'package:yantrago/core/theme/app_semantic_colors.dart';
import 'package:yantrago/core/widgets/app_surface_card.dart';
import 'package:yantrago/l10n/l10n.dart';

/// Machine status card — summary of machine counts by status.
///
/// Composes [AppSurfaceCard] and centralized semantic colors. Each count
/// is shown with a colored dot and label; color is never the sole
/// indicator (the label always accompanies it).
class MachineStatusCard extends StatelessWidget {
  final int total;
  final int online;
  final int fencingOn;
  final int fault;
  final int offline;

  const MachineStatusCard({
    super.key,
    required this.total,
    required this.online,
    required this.fencingOn,
    required this.fault,
    required this.offline,
  });

  @override
  Widget build(BuildContext context) {
    final l10n = context.l10n;
    final AppSemanticColors? semantic =
        Theme.of(context).extension<AppSemanticColors>();
    final TextTheme text = Theme.of(context).textTheme;
    final ColorScheme colors = Theme.of(context).colorScheme;

    return AppSurfaceCard(
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: <Widget>[
          Text(l10n.machineStatusTitle, style: text.titleMedium),
          const SizedBox(height: 16),
          Row(
            children: <Widget>[
              _Count(
                label: l10n.countTotal,
                count: total,
                tone: StatusTone.info,
                semantic: semantic,
                text: text,
                colors: colors,
              ),
              const SizedBox(width: 8),
              _Count(
                label: l10n.countOnline,
                count: online,
                tone: StatusTone.success,
                semantic: semantic,
                text: text,
                colors: colors,
              ),
              const SizedBox(width: 8),
              _Count(
                label: l10n.countFencing,
                count: fencingOn,
                tone: StatusTone.warning,
                semantic: semantic,
                text: text,
                colors: colors,
              ),
            ],
          ),
          const SizedBox(height: 8),
          Row(
            children: <Widget>[
              _Count(
                label: l10n.countFault,
                count: fault,
                tone: StatusTone.danger,
                semantic: semantic,
                text: text,
                colors: colors,
              ),
              const SizedBox(width: 8),
              _Count(
                label: l10n.countOffline,
                count: offline,
                tone: StatusTone.neutral,
                semantic: semantic,
                text: text,
                colors: colors,
              ),
            ],
          ),
        ],
      ),
    );
  }
}

class _Count extends StatelessWidget {
  final String label;
  final int count;
  final StatusTone tone;
  final AppSemanticColors? semantic;
  final TextTheme text;
  final ColorScheme colors;

  const _Count({
    required this.label,
    required this.count,
    required this.tone,
    required this.semantic,
    required this.text,
    required this.colors,
  });

  @override
  Widget build(BuildContext context) {
    final Color foreground = semantic?.tone(tone) ?? colors.primary;
    final Color background = semantic?.toneSoft(tone) ?? foreground.withValues(alpha: 0.12);
    return Expanded(
      child: Container(
        padding: const EdgeInsets.symmetric(vertical: 12, horizontal: 8),
        decoration: BoxDecoration(
          color: background,
          borderRadius: BorderRadius.circular(10),
        ),
        child: Column(
          children: <Widget>[
            Text(
              '$count', // always-en: numeric value, Latin digits
              style: text.headlineSmall?.copyWith(
                fontWeight: FontWeight.w700,
                color: foreground,
              ),
            ),
            const SizedBox(height: 2),
            Text(
              label,
              style: text.bodySmall?.copyWith(color: foreground),
              overflow: TextOverflow.ellipsis,
            ),
          ],
        ),
      ),
    );
  }
}
