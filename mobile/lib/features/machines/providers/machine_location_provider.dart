import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:yantrago/core/network/api_client.dart';
import 'package:yantrago/features/auth/providers/auth_provider.dart';
import 'package:yantrago/models/machine_location.dart';

/// Machine location provider — fetches the current GPS location for a
/// specific machine via GET /api/v1/locations/{machineId}.
///
/// Watches [currentUserProvider] so location is re-fetched on user change.
/// Returns null (not an error) when the machine has no location data yet.
final machineLocationProvider =
    FutureProvider.family<MachineLocation?, String>((ref, machineId) async {
  final user = ref.watch(currentUserProvider);
  if (user == null) return null;

  final dio = ref.watch(apiClientProvider);
  try {
    final response = await dio.get('/api/v1/locations/$machineId');
    if (response.statusCode == 200 && response.data != null) {
      return MachineLocation.fromJson(response.data as Map<String, dynamic>);
    }
    return null;
  } catch (e) {
    // 404 means no location data yet — return null instead of error
    return null;
  }
});
