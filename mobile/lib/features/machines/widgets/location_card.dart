import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import 'package:yantrago/features/machines/providers/machine_location_provider.dart';
import 'package:yantrago/features/machines/providers/machine_address_provider.dart';
import 'package:yantrago/models/machine_location.dart';

/// Location card — shows the machine's current GPS location with a location
/// icon and a reverse-geocoded address. Tapping the card opens an in-app
/// Google Map showing the machine's location.
///
/// Displays:
/// - Location icon (left)
/// - Multi-line detailed address (reverse geocoded from lat/lon)
/// - Tap to open in-app map
///
/// If no location data is available, shows "No location data" with a
/// disabled icon. If reverse geocoding fails, shows the raw coordinates.
class LocationCard extends ConsumerWidget {
  final String machineId;

  const LocationCard({super.key, required this.machineId});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final location = ref.watch(machineLocationProvider(machineId));
    final address = ref.watch(machineAddressProvider(machineId));

    return Card(
      child: location.when(
        loading: () => const Padding(
          padding: EdgeInsets.all(16),
          child: Row(
            children: [
              Icon(Icons.location_on, color: Colors.grey),
              SizedBox(width: 12),
              SizedBox(
                width: 24,
                height: 24,
                child: CircularProgressIndicator(strokeWidth: 2),
              ),
            ],
          ),
        ),
        error: (err, _) => _NoLocation(),
        data: (loc) {
          if (loc == null) return _NoLocation();

          return address.when(
            loading: () => _LocationContent(
              location: loc,
              addressLines: [
                '${loc.latitude.toStringAsFixed(6)}, ${loc.longitude.toStringAsFixed(6)}'
              ],
              machineId: machineId,
              isGeocoding: true,
            ),
            error: (_, __) => _LocationContent(
              location: loc,
              addressLines: [
                '${loc.latitude.toStringAsFixed(6)}, ${loc.longitude.toStringAsFixed(6)}'
              ],
              machineId: machineId,
            ),
            data: (addr) {
              final lines = addr != null
                  ? addr.toDetailLines()
                  : [
                      '${loc.latitude.toStringAsFixed(6)}, ${loc.longitude.toStringAsFixed(6)}'
                    ];
              return _LocationContent(
                location: loc,
                addressLines: lines,
                machineId: machineId,
              );
            },
          );
        },
      ),
    );
  }
}

/// The actual content of the location card — icon + address text, tappable.
class _LocationContent extends StatelessWidget {
  final MachineLocation location;
  final List<String> addressLines;
  final String machineId;
  final bool isGeocoding;

  const _LocationContent({
    required this.location,
    required this.addressLines,
    required this.machineId,
    this.isGeocoding = false,
  });

  void _openInAppMap(BuildContext context) {
    context.push('/app/machines/$machineId/location');
  }

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final hasAddress = addressLines.isNotEmpty &&
        addressLines.first != '${location.latitude.toStringAsFixed(6)}, ${location.longitude.toStringAsFixed(6)}';

    return InkWell(
      onTap: () => _openInAppMap(context),
      borderRadius: BorderRadius.circular(12),
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Row(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            // Location icon
            Padding(
              padding: const EdgeInsets.only(top: 2),
              child: Icon(
                Icons.location_on,
                color: theme.colorScheme.primary,
                size: 28,
              ),
            ),
            const SizedBox(width: 12),
            // Address text
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(
                    'Current Location',
                    style: theme.textTheme.labelMedium?.copyWith(
                      color: theme.colorScheme.onSurfaceVariant,
                    ),
                  ),
                  const SizedBox(height: 4),
                  ...addressLines.map((line) => Padding(
                        padding: const EdgeInsets.symmetric(vertical: 1),
                        child: Text(
                          line,
                          style: theme.textTheme.bodyMedium?.copyWith(
                            fontWeight: FontWeight.w500,
                          ),
                        ),
                      )),
                  if (isGeocoding) ...[
                    const SizedBox(height: 4),
                    Text(
                      'Resolving address...',
                      style: theme.textTheme.bodySmall?.copyWith(
                        color: theme.colorScheme.onSurfaceVariant,
                        fontStyle: FontStyle.italic,
                      ),
                    ),
                  ],
                  if (hasAddress && !isGeocoding) ...[
                    const SizedBox(height: 6),
                    Row(
                      children: [
                        Icon(
                          Icons.map_outlined,
                          size: 14,
                          color: theme.colorScheme.primary,
                        ),
                        const SizedBox(width: 4),
                        Text(
                          'Tap to view on map',
                          style: theme.textTheme.bodySmall?.copyWith(
                            color: theme.colorScheme.primary,
                            fontWeight: FontWeight.w500,
                          ),
                        ),
                      ],
                    ),
                  ],
                ],
              ),
            ),
            // Chevron
            const Padding(
              padding: EdgeInsets.only(top: 2),
              child: Icon(Icons.chevron_right, color: Colors.grey),
            ),
          ],
        ),
      ),
    );
  }
}

/// Shown when no location data is available for the machine.
class _NoLocation extends StatelessWidget {
  @override
  Widget build(BuildContext context) {
    return const Padding(
      padding: EdgeInsets.all(16),
      child: Row(
        children: [
          Icon(Icons.location_off, color: Colors.grey),
          SizedBox(width: 12),
          Expanded(
            child: Text(
              'No location data available',
              style: TextStyle(color: Colors.grey),
            ),
          ),
        ],
      ),
    );
  }
}
