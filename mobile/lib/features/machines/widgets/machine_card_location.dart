import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import 'package:yantrago/core/theme/app_spacing.dart';
import 'package:yantrago/core/utils/date_utils.dart';
import 'package:yantrago/features/machines/providers/machine_address_provider.dart';
import 'package:yantrago/features/machines/providers/machine_location_provider.dart';
import 'package:yantrago/l10n/l10n.dart';
import 'package:yantrago/models/machine.dart';
import 'package:yantrago/models/machine_location.dart';

/// Always reserves a location row, including while offline or without GPS.
/// Requests and consent checks live in providers, not in the card.
class MachineCardLocation extends ConsumerWidget {
  final Machine machine;
  const MachineCardLocation({super.key, required this.machine});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    return ref
        .watch(machineLocationProvider(machine.id))
        .when(
          loading: () => _row(context, context.l10n.loadingLocation),
          error: (_, __) => _row(context, context.l10n.noLocationData),
          data: (location) => location == null
              ? _row(context, context.l10n.noLocationData)
              : _locatedRow(context, ref, location),
        );
  }

  Widget _locatedRow(
    BuildContext context,
    WidgetRef ref,
    MachineLocation location,
  ) {
    final address = ref.watch(machineAddressProvider(machine.id));
    // Riverpod retains the previous value during refresh and after errors.
    // Show the current coordinates until a fresh address is available.
    final resolved = address.isLoading || address.hasError
        ? null
        : address.valueOrNull;
    final lines = resolved?.toShortLines() ?? const <String>[];
    // With no consent or no geocoding result, coordinates stay on-device.
    final value = lines.isNotEmpty
        ? lines.join(', ')
        : '${location.latitude.toStringAsFixed(6)}, '
              '${location.longitude.toStringAsFixed(6)}'; // always-en: coordinates
    final label = machine.isOnlineStatus
        ? value
        : context.l10n.lastKnownLocation(value);
    final row = _row(context, label);
    return location.recordedAt == null
        ? row
        : Tooltip(
            message: context.l10n.updatedAt(
              relativeTime(context.l10n, location.recordedAt),
            ),
            child: row,
          );
  }

  Widget _row(BuildContext context, String label) {
    final theme = Theme.of(context);
    return Row(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Icon(
          Icons.location_on_outlined,
          color: theme.colorScheme.onSurfaceVariant,
        ),
        const SizedBox(width: AppSpacing.sm),
        Expanded(child: Text(label, style: theme.textTheme.bodyMedium)),
      ],
    );
  }
}
