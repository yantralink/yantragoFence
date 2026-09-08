import 'dart:convert';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart' show rootBundle;
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:webview_flutter/webview_flutter.dart';
import 'package:yantrago/features/machines/providers/machine_provider.dart';
import 'package:yantrago/features/machines/providers/machine_location_provider.dart';
import 'package:yantrago/features/machines/providers/machine_address_provider.dart';

/// Machine location map page — shows a single machine's GPS location on an
/// in-app map using Leaflet.js + OpenStreetMap tiles with a custom marker icon.
/// Opened from the LocationCard on the machine detail page.
///
/// Displays:
/// - Interactive map with custom marker at the machine's coordinates
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
  String? _markerIconBase64;

  @override
  void initState() {
    super.initState();
    _loadMarkerIcon();
  }

  Future<void> _loadMarkerIcon() async {
    try {
      final bytes = await rootBundle.load(
        'assets/icons/Location_icon.png',
      );
      final base64Str = base64Encode(bytes.buffer.asUint8List());
      if (mounted) {
        setState(() => _markerIconBase64 = base64Str);
      }
    } catch (e) {
      // If icon fails to load, map will use default marker
    }
  }

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
        loading: () => const Center(child: CircularProgressIndicator()),
        error: (err, _) => Center(
          child: Column(
            mainAxisAlignment: MainAxisAlignment.center,
            children: [
              const Icon(Icons.location_off, size: 48, color: Colors.grey),
              const SizedBox(height: 16),
              Text('No location data available',
                  style: Theme.of(context).textTheme.titleMedium),
            ],
          ),
        ),
        data: (loc) {
          if (loc == null) {
            return const Center(
              child: Column(
                mainAxisAlignment: MainAxisAlignment.center,
                children: [
                  Icon(Icons.location_off, size: 48, color: Colors.grey),
                  SizedBox(height: 16),
                  Text('No location data available',
                      style: TextStyle(color: Colors.grey)),
                ],
              ),
            );
          }

          // Build Leaflet.js HTML with custom marker icon
          final iconHtml = _markerIconBase64 != null
              ? 'data:image/png;base64,$_markerIconBase64'
              : null;

          final html = _buildLeafletHtml(
            latitude: loc.latitude,
            longitude: loc.longitude,
            iconUrl: iconHtml,
          );

          final controller = WebViewController()
            ..setJavaScriptMode(JavaScriptMode.unrestricted)
            ..loadHtmlString(html);

          return Column(
            children: [
              Expanded(
                child: WebViewWidget(controller: controller),
              ),
              // Address bar below the map
              Container(
                width: double.infinity,
                padding: const EdgeInsets.all(16),
                decoration: BoxDecoration(
                  color: Theme.of(context).colorScheme.surface,
                  boxShadow: [
                    BoxShadow(
                      color: Colors.black.withValues(alpha: 0.08),
                      blurRadius: 8,
                      offset: const Offset(0, -2),
                    ),
                  ],
                ),
                child: address.when(
                  loading: () => const Row(
                    children: [
                      SizedBox(
                        width: 20,
                        height: 20,
                        child: CircularProgressIndicator(strokeWidth: 2),
                      ),
                      SizedBox(width: 12),
                      Text('Resolving address...'),
                    ],
                  ),
                  error: (_, __) => Text(
                    '${loc.latitude.toStringAsFixed(6)}, '
                    '${loc.longitude.toStringAsFixed(6)}',
                  ),
                  data: (addr) {
                    final lines = addr != null
                        ? addr.toDetailLines()
                        : [
                            '${loc.latitude.toStringAsFixed(6)}, '
                                '${loc.longitude.toStringAsFixed(6)}'
                          ];
                    return Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Row(
                          children: [
                            Icon(Icons.location_on,
                                color: Theme.of(context).colorScheme.primary,
                                size: 20),
                            const SizedBox(width: 8),
                            Text('Current Location',
                                style:
                                    Theme.of(context).textTheme.labelMedium),
                          ],
                        ),
                        const SizedBox(height: 4),
                        ...lines.map((line) => Text(line,
                            style:
                                Theme.of(context).textTheme.bodyMedium?.copyWith(
                                      fontWeight: FontWeight.w500,
                                    ))),
                      ],
                    );
                  },
                ),
              ),
            ],
          );
        },
      ),
    );
  }

  /// Builds the Leaflet.js HTML with OpenStreetMap tiles and a custom marker.
  String _buildLeafletHtml({
    required double latitude,
    required double longitude,
    String? iconUrl,
  }) {
    final markerIcon = iconUrl != null
        ? '''
      var customIcon = L.icon({
        iconUrl: '$iconUrl',
        iconSize: [48, 48],
        iconAnchor: [24, 48],
        popupAnchor: [0, -48]
      });
      L.marker([$latitude, $longitude], {icon: customIcon}).addTo(map)
        .bindPopup('Machine Location');
    '''
        : '''
      L.marker([$latitude, $longitude]).addTo(map)
        .bindPopup('Machine Location');
    ''';

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
    $markerIcon
  </script>
</body>
</html>
''';
  }
}
