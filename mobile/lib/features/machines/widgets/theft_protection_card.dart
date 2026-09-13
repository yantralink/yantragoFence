import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import 'package:yantrago/core/widgets/app_surface_card.dart';
import 'package:yantrago/features/machines/providers/theft_protection_provider.dart';

/// Theft Protection card — shows protection status and toggle button.
///
/// Per Phase 11 design: customer enables theft protection after installing
/// the machine on the farm. This card shows the current status and provides
/// a toggle to enable/disable.
class TheftProtectionCard extends ConsumerWidget {
  final String machineId;

  const TheftProtectionCard({super.key, required this.machineId});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final statusAsync = ref.watch(theftProtectionNotifierProvider(machineId));

    return statusAsync.when(
      loading: () => AppSurfaceCard(
        child: Padding(
          padding: const EdgeInsets.symmetric(vertical: 16),
          child: Row(
            children: <Widget>[
              Icon(Icons.shield_outlined,
                  color: Theme.of(context).colorScheme.onSurfaceVariant),
              const SizedBox(width: 12),
              const Text('Loading theft protection…'),
            ],
          ),
        ),
      ),
      error: (error, _) => AppSurfaceCard(
        child: Row(
          children: <Widget>[
            Icon(Icons.error_outline,
                color: Theme.of(context).colorScheme.error, size: 22),
            const SizedBox(width: 12),
            Expanded(
              child: Text(
                'Could not load theft protection status',
                style: Theme.of(context).textTheme.bodyMedium,
              ),
            ),
            TextButton(
              onPressed: () => ref
                  .read(theftProtectionNotifierProvider(machineId).notifier)
                  .load(),
              child: const Text('Retry'),
            ),
          ],
        ),
      ),
      data: (status) => _ProtectionContent(
        machineId: machineId,
        status: status,
      ),
    );
  }
}

class _ProtectionContent extends ConsumerWidget {
  final String machineId;
  final TheftProtectionStatus status;

  const _ProtectionContent({
    required this.machineId,
    required this.status,
  });

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final ColorScheme colors = Theme.of(context).colorScheme;
    final TextTheme text = Theme.of(context).textTheme;
    final bool enabled = status.protectionEnabled;

    return AppSurfaceCard(
      onTap: () => context.push('/app/machines/$machineId/location'),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: <Widget>[
          Row(
            children: <Widget>[
              Icon(
                enabled ? Icons.shield : Icons.shield_outlined,
                color: enabled ? colors.primary : colors.onSurfaceVariant,
                size: 24,
              ),
              const SizedBox(width: 12),
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: <Widget>[
                    Text(
                      'Theft Protection',
                      style: text.bodyLarge?.copyWith(
                        fontWeight: FontWeight.w600,
                      ),
                    ),
                    Text(
                      enabled ? 'Active' : 'Not Protected',
                      style: text.bodySmall?.copyWith(
                        color: enabled
                            ? colors.primary
                            : colors.onSurfaceVariant,
                      ),
                    ),
                  ],
                ),
              ),
              Switch(
                value: enabled,
                onChanged: ref.read(theftProtectionNotifierProvider(machineId)).isLoading ? null : (value) async {
                  final notifier = ref.read(
                      theftProtectionNotifierProvider(machineId).notifier);
                  if (value) {
                    await notifier.enable();
                  } else {
                    await notifier.disable();
                  }
                },
              ),
            ],
          ),
          if (enabled) ...<Widget>[
            const SizedBox(height: 12),
            Row(
              children: <Widget>[
                Icon(Icons.location_searching,
                    color: colors.onSurfaceVariant, size: 18),
                const SizedBox(width: 8),
                Text(
                  'Geofence: ${status.geofenceRadiusMeters ?? 200}m',
                  style: text.bodySmall,
                ),
                const SizedBox(width: 16),
                Icon(Icons.speed,
                    color: colors.onSurfaceVariant, size: 18),
                const SizedBox(width: 8),
                Expanded(
                  child: Text(
                    'Speed alert active',
                    style: text.bodySmall,
                  ),
                ),
              ],
            ),
            const SizedBox(height: 4),
            Text(
              'Tap to view on map',
              style: text.bodySmall?.copyWith(
                color: colors.primary,
                fontWeight: FontWeight.w500,
              ),
            ),
          ] else ...<Widget>[
            const SizedBox(height: 8),
            Text(
              'Enable after installing on your farm to prevent false alerts during transport.',
              style: text.bodySmall?.copyWith(
                color: colors.onSurfaceVariant,
              ),
            ),
          ],
        ],
      ),
    );
  }
}
