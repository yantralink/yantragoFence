import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:yantrago/core/network/api_client.dart';
import 'package:yantrago/features/auth/providers/auth_provider.dart';
import 'package:yantrago/models/machine.dart';

/// Machine list provider — fetches machines for the current user.
///
/// Watches [currentUserProvider] so the list is re-fetched whenever the
/// authenticated user changes (login / logout / token refresh).
/// Per AGENTS.md rule 7: organization_id comes from JWT, never from request body.
/// Backend returns a paginated response: { "content": [...], ... }
final machineListProvider = FutureProvider<List<Machine>>((ref) async {
  // Re-fetch when the authenticated user changes
  final user = ref.watch(currentUserProvider);
  if (user == null) return [];

  final dio = ref.watch(apiClientProvider);
  final response = await dio.get('/api/v1/machines');
  final data = response.data;
  // Handle paginated response { "content": [...] } or plain list
  final List<dynamic> list = data is Map<String, dynamic>
      ? data['content'] as List
      : data as List;
  return list
      .map((m) => Machine.fromJson(m as Map<String, dynamic>))
      .toList();
});

/// Machine detail provider — fetches a single machine by ID.
/// Watches [currentUserProvider] so detail is re-fetched on user change.
final machineDetailProvider =
    FutureProvider.family<Machine, String>((ref, id) async {
  final user = ref.watch(currentUserProvider);
  if (user == null) {
    throw StateError('Not authenticated');
  }

  final dio = ref.watch(apiClientProvider);
  final response = await dio.get('/api/v1/machines/$id');
  return Machine.fromJson(response.data as Map<String, dynamic>);
});
