import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:yantrago/features/map/providers/location_provider.dart';
import 'package:yantrago/features/map/widgets/machine_marker.dart';

/// Machine map page — shows machine locations on Google Maps.
///
/// Scaffold with placeholder UI. Full implementation will use
/// google_maps_flutter with real-time WebSocket location updates.
class MachineMapPage extends ConsumerWidget {
  const MachineMapPage({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final locations = ref.watch(locationProvider);

    return Scaffold(
      appBar: AppBar(title: const Text('Map')),
      body: locations.when(
        loading: () => const Center(child: CircularProgressIndicator()),
        error: (err, _) => Center(child: Text('Error: $err')),
        data: (list) => Stack(
          children: [
            // Placeholder for Google Maps
            Container(
              color: Colors.grey[300],
              child: const Center(
                child: Text('Google Maps will be displayed here'),
              ),
            ),
            // Machine markers overlay (placeholder)
            Positioned(
              top: 16,
              left: 16,
              right: 16,
              child: Card(
                child: Padding(
                  padding: const EdgeInsets.all(12),
                  child: Text('${list.length} machines on map'),
                ),
              ),
            ),
          ],
        ),
      ),
    );
  }
}
