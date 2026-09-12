import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:google_maps_flutter/google_maps_flutter.dart';
import 'package:yantrago/core/theme/app_semantic_colors.dart';
import 'package:yantrago/core/widgets/app_state_panel.dart';
import 'package:yantrago/core/widgets/app_status_badge.dart';
import 'package:yantrago/core/widgets/app_surface_card.dart';
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
  GoogleMapController? _mapController; // ignore: unused_field
  final Set<Marker> _markers = {};
  bool _mapReady = false;

  @override
  Widget build(BuildContext context) {
    final dashboard = ref.watch(dashboardProvider);
    final locations = ref.watch(locationProvider);

    return Scaffold(
      appBar: AppBar(title: const Text('Map')),
      body: dashboard.when(
        loading: () => AppStatePanel.loading(message: 'Loading map…'),
        error: (_, __) => AppStatePanel.error(
          message: 'Unable to load the map. Please try again.',
          onRetry: () => ref.refresh(dashboardProvider.future),
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
            children: <Widget>[
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
                  child: _MapInfoBanner(
                    machineCount: machines.length,
                    locationCount: locationList.length,
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

/// Info banner overlay on the map showing machine/location counts.
class _MapInfoBanner extends StatelessWidget {
  final int machineCount;
  final int locationCount;

  const _MapInfoBanner({
    required this.machineCount,
    required this.locationCount,
  });

  @override
  Widget build(BuildContext context) {
    final bool hasGps = locationCount > 0;
    final StatusTone tone = hasGps ? StatusTone.success : StatusTone.warning;
    final String message = hasGps
        ? '$locationCount machines with GPS on map'
        : '$machineCount machines (no GPS data yet)';

    return AppSurfaceCard(
      child: Row(
        children: <Widget>[
          AppStatusBadge(
            label: hasGps ? 'GPS' : 'No GPS',
            tone: tone,
            dot: true,
          ),
          const SizedBox(width: 12),
          Expanded(
            child: Text(
              message,
              style: Theme.of(context).textTheme.bodyMedium,
              overflow: TextOverflow.ellipsis,
            ),
          ),
        ],
      ),
    );
  }
}
