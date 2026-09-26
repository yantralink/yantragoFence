import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import 'package:yantrago/features/analytics/providers/analytics_provider.dart';
import 'package:yantrago/features/machines/providers/machine_provider.dart';
import 'package:yantrago/models/machine.dart';

/// Machine dropdown for the Analytics page. Shows the selected machine;
/// defaults to the first machine when nothing is picked.
class AnalyticsMachineSelector extends ConsumerWidget {
  const AnalyticsMachineSelector({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final machines = ref.watch(machineListProvider).valueOrNull ?? const <Machine>[];
    final selectedId = ref.watch(effectiveAnalyticsMachineProvider);
    final colors = Theme.of(context).colorScheme;

    if (machines.length <= 1) {
      final name = machines.isEmpty ? '—' : machines.first.name;
      return _shell(
        colors,
        Text(name, style: Theme.of(context).textTheme.titleMedium),
      );
    }

    return _shell(
      colors,
      DropdownButtonHideUnderline(
        child: DropdownButton<String>(
          value: selectedId,
          isExpanded: true,
          icon: const Icon(Icons.expand_more),
          items: [
            for (final m in machines)
              DropdownMenuItem(value: m.id, child: Text(m.name)),
          ],
          onChanged: (id) =>
              ref.read(analyticsMachineIdProvider.notifier).state = id,
        ),
      ),
    );
  }

  Widget _shell(ColorScheme colors, Widget child) {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 4),
      decoration: BoxDecoration(
        color: colors.surfaceContainerHighest.withValues(alpha: 0.5),
        borderRadius: BorderRadius.circular(12),
        border: Border.all(color: colors.outlineVariant),
      ),
      child: child,
    );
  }
}
