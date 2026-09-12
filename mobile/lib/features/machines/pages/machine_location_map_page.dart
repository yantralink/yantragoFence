import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:webview_flutter/webview_flutter.dart';
import 'package:yantrago/core/widgets/app_state_panel.dart';
import 'package:yantrago/features/machines/providers/machine_provider.dart';
import 'package:yantrago/features/machines/providers/machine_location_provider.dart';
import 'package:yantrago/features/machines/providers/machine_address_provider.dart';

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

          // Build Leaflet.js HTML with default marker
          final html = _buildLeafletHtml(
            latitude: loc.latitude,
            longitude: loc.longitude,
          );

          final controller = WebViewController()
            ..setJavaScriptMode(JavaScriptMode.unrestricted)
            ..loadHtmlString(html);

          return Column(
            children: <Widget>[
              Expanded(
                child: WebViewWidget(controller: controller),
              ),
              // Address bar below the map
              _AddressBar(
                latitude: loc.latitude,
                longitude: loc.longitude,
                address: address,
              ),
            ],
          );
        },
      ),
    );
  }

  /// Builds the Leaflet.js HTML with OpenStreetMap tiles and a default marker.
  String _buildLeafletHtml({
    required double latitude,
    required double longitude,
  }) {
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
          label: 'Current Location',
          lines: <String>[
            '${latitude.toStringAsFixed(6)}, ${longitude.toStringAsFixed(6)}'
          ],
        ),
        data: (addr) {
          final lines = addr != null
              ? addr.toDetailLines()
              : <String>[
                  '${latitude.toStringAsFixed(6)}, ${longitude.toStringAsFixed(6)}'
                ];
          return _AddressContent(label: 'Current Location', lines: lines);
        },
      ),
    );
  }
}

class _AddressContent extends StatelessWidget {
  final String label;
  final List<String> lines;

  const _AddressContent({required this.label, required this.lines});

  @override
  Widget build(BuildContext context) {
    final ColorScheme colors = Theme.of(context).colorScheme;
    final TextTheme text = Theme.of(context).textTheme;

    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: <Widget>[
        Row(
          children: <Widget>[
            Icon(Icons.location_on, color: colors.primary, size: 20),
            const SizedBox(width: 8),
            Text(label, style: text.labelMedium),
          ],
        ),
        const SizedBox(height: 4),
        ...lines.map((line) => Text(
              line,
              style: text.bodyMedium?.copyWith(fontWeight: FontWeight.w500),
            )),
      ],
    );
  }
}
