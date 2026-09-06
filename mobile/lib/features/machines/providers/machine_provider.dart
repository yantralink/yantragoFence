import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:yantrago/core/network/api_client.dart';
import 'package:yantrago/models/machine.dart';

/// Machine list provider — fetches all machines for the organization.
///
/// Per AGENTS.md rule 7: organization_id comes from JWT, never from request body.
final machineListProvider = FutureProvider<List<Machine>>((ref) async {
  final dio = ref.watch(apiClientProvider);
  final response = await dio.get('/api/machines');
  return (response.data as List)
      .map((m) => Machine.fromJson(m as Map<String, dynamic>))
      .toList();
});

/// Machine detail provider — fetches a single machine by ID.
final machineDetailProvider =
    FutureProvider.family<Machine, String>((ref, id) async {
  final dio = ref.watch(apiClientProvider);
  final response = await dio.get('/api/machines/$id');
  return Machine.fromJson(response.data as Map<String, dynamic>);
});
