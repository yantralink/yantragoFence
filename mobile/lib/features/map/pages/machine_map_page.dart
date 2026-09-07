import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:google_maps_flutter/google_maps_flutter.dart';
import 'package:yantrago/features/dashboard/providers/dashboard_provider.dart';
import 'package:yantrago/features/map/providers/location_provider.dart';
import 'package:yantrago/models/location.dart';
import 'package:yantrago/models/machine.dart';

/// Machine map page — shows machine locations on Google Maps.
///
/// Per AGENTS.md rule 12: production features require validation,
/// error handling, logging.
///
/// This page fetches both machines and their latest locations.
/// If the locations endpoint is not yet available (returns 500),
/// it falls back to showing machine count only.
class MachineMapPage extends ConsumerStatefulWidget {
  const MachineMapPage({super.key});

  @override
  ConsumerState<MachineMapPage> createState() => _MachineMapPageState();
}

class _MachineMapPageState extends ConsumerState<MachineMapPage> {
  GoogleMapController? _mapController;
  final Set<Marker> _markers = {};
  bool _mapReady = false;

  @override
  Widget build(BuildContext context) {
    final dashboard = ref.watch(dashboardProvider);
    final locations = ref.watch(locationProvider);

    return Scaffold(
      appBar: AppBar(title: const Text('Map')),
      body: dashboard.when(
        loading: () => const Center(child: CircularProgressIndicator()),
        error: (err, _) => Center(
          child: Column(
            mainAxisAlignment: MainAxisAlignment.center,
            children: [
              const Icon(Icons.error_outline, size: 48, color: Colors.red),
              const SizedBox(height: 16),
              Text('Error: $err', textAlign: TextAlign.center),
              const SizedBox(height: 16),
              FilledButton(
                onPressed: () => ref.refresh(dashboardProvider),
                child: const Text('Retry'),
              ),
            ],
          ),
        ),
        data: (summary) {
          final machines = summary.machines;
          // Try to get locations — may fail if endpoint not implemented
          final locationList = locations.maybeWhen(
            data: (list) => list,
            orElse: () => <Location>[],
          );

          _updateMarkers(machines, locationList);

          return Stack(
            children: [
              GoogleMap(
                initialCameraPosition: _getInitialCameraPosition(locationList),
                markers: _markers,
                myLocationEnabled: true,
                myLocationButtonEnabled: true,
                zoomControlsEnabled: true,
                mapToolbarEnabled: true,
                compassEnabled: true,
                onMapCreated: (controller) {
                  _mapController = controller;
                  setState(() => _mapReady = true);
                },
              ),
              // Machine count badge
              if (_mapReady)
                Positioned(
                  top: 16,
                  left: 16,
                  right: 16,
                  child: Card(
                    child: Padding(
                      padding: const EdgeInsets.all(12),
                      child: Row(
                        children: [
                          Icon(
                            machines.isEmpty
                                ? Icons.info_outline
                                : Icons.devices,
                            color: machines.isEmpty
                                ? Colors.orange
                                : Colors.green,
                          ),
                          const SizedBox(width: 8),
                          Expanded(
                            child: Text(
                              locationList.isEmpty
                                  ? '${machines.length} machines (no GPS data yet)'
                                  : '${locationList.length} machines with GPS on map',
                              style: Theme.of(context).textTheme.bodyMedium,
                            ),
                          ),
                        ],
                      ),
                    ),
                  ),
                ),
            ],
          );
        },
      ),
    );
  }

  CameraPosition _getInitialCameraPosition(List<Location> locations) {
    if (locations.isEmpty) {
      // Default to India center
      return const CameraPosition(
        target: LatLng(20.5937, 78.9629),
        zoom: 5,
      );
    }
    // Center on first machine with location
    final first = locations.first;
    return CameraPosition(
      target: LatLng(first.latitude, first.longitude),
      zoom: 12,
    );
  }

  void _updateMarkers(List<Machine> machines, List<Location> locations) {
    _markers.clear();

    // If we have location data, show markers with GPS coordinates
    if (locations.isNotEmpty) {
      for (int i = 0; i < locations.length; i++) {
        final loc = locations[i];
        _markers.add(
          Marker(
            markerId: MarkerId('machine_${loc.deviceId}'),
            position: LatLng(loc.latitude, loc.longitude),
            infoWindow: InfoWindow(
              title: 'Machine ${loc.imei}',
              snippet:
                  'Speed: ${loc.speed ?? 0} km/h | ${_formatTime(loc.timestamp)}',
            ),
            icon: BitmapDescriptor.defaultMarkerWithHue(
                BitmapDescriptor.hueGreen),
          ),
        );
      }
    }
    // If no location data, we can't place markers without coordinates
    // Just show the map with info banner
  }

  String _formatTime(DateTime dt) {
    return '${dt.day}/${dt.month} ${dt.hour.toString().padLeft(2, '0')}:'
        '${dt.minute.toString().padLeft(2, '0')}';
  }
}
