import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:yantrago/core/theme/app_radii.dart';
import 'package:yantrago/core/theme/app_spacing.dart';
import 'package:yantrago/features/machines/providers/theft_protection_provider.dart';
import 'package:yantrago/l10n/l10n.dart';

/// Theft Protection card below the map — tinted surface with shield
/// icon, title, concise explanation, and toggle bound to
/// [theftProtectionNotifierProvider].
///
/// Per AGENTS.md rule 5 the toggle only reflects the confirmed backend
/// state — enable/disable responses replace the whole status, so the
/// switch never shows an unconfirmed optimistic value.
class TheftProtectionCard extends ConsumerWidget {
  final String machineId;

  const TheftProtectionCard({super.key, required this.machineId});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final statusAsync =
        ref.watch(theftProtectionNotifierProvider(machineId));
    final l10n = context.l10n;

    return statusAsync.when(
      loading: () => const Padding(
        padding: EdgeInsets.all(AppSpacing.lg),
        child: SizedBox(
          height: 24,
          child: Center(child: CircularProgressIndicator(strokeWidth: 2)),
        ),
      ),
      error: (_, __) => Padding(
        padding: const EdgeInsets.all(AppSpacing.lg),
        child: Row(
          children: <Widget>[
            Icon(Icons.error_outline,
                color: Theme.of(context).colorScheme.error, size: 20),
            const SizedBox(width: AppSpacing.sm),
            Expanded(
              child: Text(
                l10n.theftProtectionLoadFailed,
                style: Theme.of(context).textTheme.bodySmall,
              ),
            ),
            TextButton(
              onPressed: () => ref
                  .read(theftProtectionNotifierProvider(machineId).notifier)
                  .load(),
              child: Text(l10n.retry),
            ),
          ],
        ),
      ),
      data: (status) {
        final ColorScheme colors = Theme.of(context).colorScheme;
        final TextTheme text = Theme.of(context).textTheme;
        final bool enabled = status.protectionEnabled;

        return Container(
          padding: const EdgeInsets.symmetric(
              horizontal: AppSpacing.md, vertical: AppSpacing.sm),
          decoration: BoxDecoration(
            color: colors.primaryContainer,
            borderRadius: AppRadii.cardRadius,
          ),
          child: Row(
            children: <Widget>[
              Icon(
                enabled ? Icons.shield : Icons.shield_outlined,
                color:
                    enabled ? colors.primary : colors.onSurfaceVariant,
                size: 28,
              ),
              const SizedBox(width: AppSpacing.md),
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: <Widget>[
                    Text(
                      l10n.theftProtection,
                      style: text.titleSmall
                          ?.copyWith(fontWeight: FontWeight.w700),
                    ),
                    Text(
                      l10n.theftProtectionSubtitle,
                      style: text.bodySmall,
                    ),
                  ],
                ),
              ),
              Switch(
                value: enabled,
                onChanged: statusAsync.isLoading
                    ? null
                    : (bool value) async {
                        final notifier = ref.read(
                            theftProtectionNotifierProvider(machineId)
                                .notifier);
                        if (value) {
                          await notifier.enable();
                        } else {
                          await notifier.disable();
                        }
                      },
              ),
            ],
          ),
        );
      },
    );
  }
}
