import 'package:flutter_riverpod/flutter_riverpod.dart';

import 'package:yantrago/core/network/api_client.dart';
import 'package:yantrago/features/auth/providers/auth_provider.dart';
import 'package:yantrago/models/telemetry.dart';

/// Machine telemetry provider — fetches the latest telemetry snapshot for
/// a machine from GET /api/v1/machines/{id}/telemetry/latest.
///
/// Watches [currentUserProvider] so telemetry is re-fetched on user change.
/// Per AGENTS.md rule 7: organization_id comes from JWT, never from request body.
/// Per AGENTS.md rule 22: never call API directly from widgets — go through
/// a provider.
final machineTelemetryProvider =
    FutureProvider.family<Telemetry, String>((ref, machineId) async {
  final user = ref.watch(currentUserProvider);
  if (user == null) {
    throw StateError('Not authenticated');
  }

  final dio = ref.watch(apiClientProvider);
  final response = await dio.get('/api/v1/machines/$machineId/telemetry/latest');
  return Telemetry.fromJson(response.data as Map<String, dynamic>);
});
