import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:url_launcher/url_launcher.dart';
import 'package:webview_flutter/webview_flutter.dart';
import 'package:yantrago/core/widgets/app_state_panel.dart';
import 'package:yantrago/features/machines/providers/machine_provider.dart';
import 'package:yantrago/features/machines/providers/machine_location_provider.dart';
import 'package:yantrago/features/machines/providers/machine_address_provider.dart';
import 'package:yantrago/features/machines/providers/theft_protection_provider.dart';

/// Machine location map page — shows a single machine's GPS location on an
/// in-app map using Leaflet.js + OpenStreetMap tiles with a default marker.
/// Opened from the LocationCard on the machine detail page.
///
/// Displays:
/// - Interactive map with marker at the machine's coordinates
/// - AppBar with the machine name and a back arrow
/// - Address text below the map (reverse-geocoded)
class MachineLocationMapPage extends ConsumerStatefulWidget {
  final String machineId;

  const MachineLocationMapPage({super.key, required this.machineId});

  @override
  ConsumerState<MachineLocationMapPage> createState() =>
      _MachineLocationMapPageState();
}

class _MachineLocationMapPageState
    extends ConsumerState<MachineLocationMapPage> {

  @override
  Widget build(BuildContext context) {
    final machine = ref.watch(machineDetailProvider(widget.machineId));
    final location = ref.watch(machineLocationProvider(widget.machineId));
    final address = ref.watch(machineAddressProvider(widget.machineId));
    final protection = ref.watch(theftProtectionNotifierProvider(widget.machineId));

    return Scaffold(
      appBar: AppBar(
        title: machine.maybeWhen(
          data: (m) => Text(m.name),
          orElse: () => const Text('Machine Location'),
        ),
      ),
      body: location.when(
        loading: () =>
            AppStatePanel.loading(message: 'Loading location…'),
        error: (_, __) => AppStatePanel.empty(
          title: 'No location data available',
          message: 'This machine has not reported its GPS coordinates yet.',
          icon: Icons.location_off,
        ),
        data: (loc) {
          if (loc == null) {
            return AppStatePanel.empty(
              title: 'No location data available',
              message: 'This machine has not reported its GPS coordinates yet.',
              icon: Icons.location_off,
            );
          }

          // Get geofence info for map circle (if protection active)
          final protectionStatus = protection.valueOrNull;
          final geofenceLat = protectionStatus?.geofenceLatitude;
          final geofenceLng = protectionStatus?.geofenceLongitude;
          final geofenceRadius = protectionStatus?.geofenceRadiusMeters;

          // Build Leaflet.js HTML with marker + optional geofence circle
          final html = _buildLeafletHtml(
            latitude: loc.latitude,
            longitude: loc.longitude,
            geofenceLatitude: geofenceLat,
            geofenceLongitude: geofenceLng,
            geofenceRadiusMeters: geofenceRadius,
          );

          final controller = WebViewController()
            ..setJavaScriptMode(JavaScriptMode.unrestricted)
            ..loadHtmlString(html);

          return Column(
            children: <Widget>[
              // Map — 75% of available height
              Expanded(
                flex: 3,
                child: WebViewWidget(controller: controller),
              ),
              // Bottom panel — 25% of available height
              Expanded(
                flex: 1,
                child: Column(
                  children: <Widget>[
                    // Address bar
                    _AddressBar(
                      latitude: loc.latitude,
                      longitude: loc.longitude,
                      address: address,
                    ),
                    // Theft protection bottom panel
                    _TheftProtectionPanel(machineId: widget.machineId),
                  ],
                ),
              ),
            ],
          );
        },
      ),
    );
  }

  /// Builds the Leaflet.js HTML with OpenStreetMap tiles, a marker, and
  /// an optional geofence circle.
  String _buildLeafletHtml({
    required double latitude,
    required double longitude,
    double? geofenceLatitude,
    double? geofenceLongitude,
    int? geofenceRadiusMeters,
  }) {
    final hasGeofence = geofenceLatitude != null &&
        geofenceLongitude != null &&
        geofenceRadiusMeters != null;
    final geofenceJs = hasGeofence
        ? '''
    L.circle([$geofenceLatitude, $geofenceLongitude], {
      radius: $geofenceRadiusMeters,
      color: '#1976D2',
      fillColor: '#1976D2',
      fillOpacity: 0.15,
      weight: 2
    }).addTo(map).bindPopup('Geofence ($geofenceRadiusMeters m)');
'''
        : '';
    return '''
<!DOCTYPE html>
<html>
<head>
  <meta name="viewport" content="width=device-width, initial-scale=1.0">
  <link rel="stylesheet" href="https://unpkg.com/leaflet@1.9.4/dist/leaflet.css" />
  <script src="https://unpkg.com/leaflet@1.9.4/dist/leaflet.js"></script>
  <style>
    html, body { margin: 0; padding: 0; width: 100%; height: 100%; overflow: hidden; }
    #map { width: 100%; height: 100%; }
  </style>
</head>
<body>
  <div id="map"></div>
  <script>
    var map = L.map('map').setView([$latitude, $longitude], 16);
    L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
      maxZoom: 19,
      attribution: '&copy; OpenStreetMap'
    }).addTo(map);
    L.marker([$latitude, $longitude]).addTo(map)
      .bindPopup('Machine Location');
    $geofenceJs
  </script>
</body>
</html>
''';
  }
}

/// Address bar below the map — shows reverse-geocoded address or coordinates.
class _AddressBar extends StatelessWidget {
  final double latitude;
  final double longitude;
  final AsyncValue<dynamic> address;

  const _AddressBar({
    required this.latitude,
    required this.longitude,
    required this.address,
  });

  @override
  Widget build(BuildContext context) {
    final ColorScheme colors = Theme.of(context).colorScheme;
    final TextTheme text = Theme.of(context).textTheme;

    return Container(
      width: double.infinity,
      padding: const EdgeInsets.all(16),
      decoration: BoxDecoration(
        color: colors.surface,
        boxShadow: <BoxShadow>[
          BoxShadow(
            color: colors.shadow.withValues(alpha: 0.08),
            blurRadius: 8,
            offset: const Offset(0, -2),
          ),
        ],
      ),
      child: address.when(
        loading: () => Row(
          children: <Widget>[
            const SizedBox(
              width: 20,
              height: 20,
              child: CircularProgressIndicator(strokeWidth: 2),
            ),
            const SizedBox(width: 12),
            Text('Resolving address…',
                style: text.bodyMedium?.copyWith(color: colors.onSurfaceVariant)),
          ],
        ),
        error: (_, __) => _AddressContent(
          lines: <String>[
            '${latitude.toStringAsFixed(6)}, ${longitude.toStringAsFixed(6)}'
          ],
          latitude: latitude,
          longitude: longitude,
        ),
        data: (addr) {
          final lines = addr != null
              ? addr.toShortLines()
              : <String>[
                  '${latitude.toStringAsFixed(6)}, ${longitude.toStringAsFixed(6)}'
                ];
          return _AddressContent(
            lines: lines,
            latitude: latitude,
            longitude: longitude,
          );
        },
      ),
    );
  }
}

class _AddressContent extends StatelessWidget {
  final List<String> lines;
  final double latitude;
  final double longitude;

  const _AddressContent({
    required this.lines,
    required this.latitude,
    required this.longitude,
  });

  Future<void> _openInGoogleMaps() async {
    final uri = Uri.parse(
      'https://www.google.com/maps?q=$latitude,$longitude',
    );
    await launchUrl(uri, mode: LaunchMode.externalApplication);
  }

  @override
  Widget build(BuildContext context) {
    final ColorScheme colors = Theme.of(context).colorScheme;
    final TextTheme text = Theme.of(context).textTheme;

    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: <Widget>[
        Row(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: <Widget>[
            Icon(Icons.location_on, color: colors.primary, size: 20),
            const SizedBox(width: 8),
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: lines
                    .map((line) => Text(
                          line,
                          style: text.bodyMedium?.copyWith(
                            fontWeight: FontWeight.w500,
                          ),
                          maxLines: 1,
                          overflow: TextOverflow.ellipsis,
                        ))
                    .toList(),
              ),
            ),
          ],
        ),
        const SizedBox(height: 8),
        Align(
          alignment: Alignment.centerRight,
          child: GestureDetector(
            onTap: _openInGoogleMaps,
            child: Row(
              mainAxisSize: MainAxisSize.min,
              children: <Widget>[
                Icon(Icons.map_outlined, color: colors.primary, size: 16),
                const SizedBox(width: 4),
                Text(
                  'Open in Google Maps',
                  style: text.bodySmall?.copyWith(
                    color: colors.primary,
                    fontWeight: FontWeight.w600,
                  ),
                ),
                const SizedBox(width: 2),
                Icon(Icons.arrow_forward, color: colors.primary, size: 14),
              ],
            ),
          ),
        ),
      ],
    );
  }
}

/// Theft protection bottom panel — shows a toggle to enable/disable protection.
class _TheftProtectionPanel extends ConsumerWidget {
  final String machineId;

  const _TheftProtectionPanel({required this.machineId});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final statusAsync =
        ref.watch(theftProtectionNotifierProvider(machineId));

    return statusAsync.when(
      loading: () => const Padding(
        padding: EdgeInsets.all(16),
        child: SizedBox(
          height: 24,
          child: Center(child: CircularProgressIndicator(strokeWidth: 2)),
        ),
      ),
      error: (_, __) => Padding(
        padding: const EdgeInsets.all(16),
        child: Row(
          children: <Widget>[
            Icon(Icons.error_outline,
                color: Theme.of(context).colorScheme.error, size: 20),
            const SizedBox(width: 8),
            Expanded(
              child: Text(
                'Could not load theft protection status',
                style: Theme.of(context).textTheme.bodySmall,
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
      data: (status) {
        final ColorScheme colors = Theme.of(context).colorScheme;
        final TextTheme text = Theme.of(context).textTheme;
        final bool enabled = status.protectionEnabled;

        return Container(
          width: double.infinity,
          padding: const EdgeInsets.all(16),
          decoration: BoxDecoration(
            color: colors.surface,
            boxShadow: <BoxShadow>[
              BoxShadow(
                color: colors.shadow.withValues(alpha: 0.08),
                blurRadius: 8,
                offset: const Offset(0, -2),
              ),
            ],
          ),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: <Widget>[
              // Header row with toggle
              Row(
                children: <Widget>[
                  Icon(
                    enabled ? Icons.shield : Icons.shield_outlined,
                    color: enabled ? colors.primary : colors.onSurfaceVariant,
                    size: 22,
                  ),
                  const SizedBox(width: 8),
                  Expanded(
                    child: Text(
                      'Theft Protection',
                      style: text.titleSmall?.copyWith(
                        fontWeight: FontWeight.w600,
                      ),
                    ),
                  ),
                  Switch(
                    value: enabled,
                    onChanged: statusAsync.isLoading ? null : (value) async {
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
            ],
          ),
        );
      },
    );
  }
}
