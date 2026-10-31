import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:yantrago/core/services/map_consent_service.dart';
import 'package:yantrago/features/machines/providers/machine_location_provider.dart';
import 'package:yantrago/l10n/l10n.dart';

/// Consent gate shown before any third-party map/geocoding transfer.
///
/// SEC-15 / Play user-data policy: machine coordinates are farmer-linked
/// precise location, so rendering the map requires prior affirmative
/// consent. The disclosure names the recipients (OpenStreetMap tiles;
/// the backend geocoding proxy) and states what is shared.
class MapConsentGate extends ConsumerWidget {
  const MapConsentGate({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final ColorScheme colors = Theme.of(context).colorScheme;
    final TextTheme text = Theme.of(context).textTheme;
    final l10n = context.l10n;

    return Center(
      child: SingleChildScrollView(
        padding: const EdgeInsets.all(24),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: <Widget>[
            Icon(Icons.privacy_tip_outlined,
                color: colors.primary, size: 48),
            const SizedBox(height: 16),
            Text(
              l10n.mapConsentTitle,
              style: text.titleMedium
                  ?.copyWith(fontWeight: FontWeight.w600),
              textAlign: TextAlign.center,
            ),
            const SizedBox(height: 12),
            Text(
              l10n.mapConsentMessage,
              style: text.bodyMedium
                  ?.copyWith(color: colors.onSurfaceVariant),
              textAlign: TextAlign.center,
            ),
            const SizedBox(height: 24),
            SizedBox(
              width: double.infinity,
              child: FilledButton(
                onPressed: () =>
                    ref.read(mapConsentProvider.notifier).grant(),
                child: Text(l10n.mapConsentAllow),
              ),
            ),
            const SizedBox(height: 8),
            SizedBox(
              width: double.infinity,
              child: TextButton(
                onPressed: () =>
                    ref.read(mapConsentProvider.notifier).decline(),
                child: Text(l10n.mapConsentNotNow),
              ),
            ),
          ],
        ),
      ),
    );
  }
}

/// Shown when the user declined map services — raw coordinates only, no
/// third-party requests. Offers a path to grant consent later.
class MapDeclinedView extends ConsumerWidget {
  final String machineId;

  const MapDeclinedView({super.key, required this.machineId});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final ColorScheme colors = Theme.of(context).colorScheme;
    final TextTheme text = Theme.of(context).textTheme;
    final l10n = context.l10n;
    final location = ref.watch(machineLocationProvider(machineId));

    return Center(
      child: SingleChildScrollView(
        padding: const EdgeInsets.all(24),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: <Widget>[
            Icon(Icons.map_outlined,
                color: colors.onSurfaceVariant, size: 48),
            const SizedBox(height: 16),
            Text(
              l10n.mapDisabledTitle,
              style: text.titleMedium
                  ?.copyWith(fontWeight: FontWeight.w600),
              textAlign: TextAlign.center,
            ),
            const SizedBox(height: 12),
            Text(
              l10n.mapDisabledMessage,
              style: text.bodyMedium
                  ?.copyWith(color: colors.onSurfaceVariant),
              textAlign: TextAlign.center,
            ),
            const SizedBox(height: 16),
            location.maybeWhen(
              data: (loc) => loc == null
                  ? const SizedBox.shrink()
                  : Text(
                      '${loc.latitude.toStringAsFixed(6)}, '
                      '${loc.longitude.toStringAsFixed(6)}',
                      style: text.bodyMedium
                          ?.copyWith(fontWeight: FontWeight.w500),
                    ),
              orElse: () => const SizedBox.shrink(),
            ),
            const SizedBox(height: 24),
            SizedBox(
              width: double.infinity,
              child: OutlinedButton(
                onPressed: () =>
                    ref.read(mapConsentProvider.notifier).grant(),
                child: Text(l10n.mapEnableServices),
              ),
            ),
          ],
        ),
      ),
    );
  }
}
