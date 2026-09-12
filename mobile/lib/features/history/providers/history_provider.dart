import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:yantrago/core/network/api_client.dart';
import 'package:yantrago/features/auth/providers/auth_provider.dart';
import 'package:yantrago/models/command.dart';

/// History provider — fetches command/activity history.
/// Watches [currentUserProvider] so history is re-fetched on user change.
final historyProvider =
    FutureProvider.family<List<Command>, String>((ref, machineId) async {
  final user = ref.watch(currentUserProvider);
  if (user == null) return [];

  final dio = ref.watch(apiClientProvider);
  final response = await dio.get('/api/v1/commands?machineId=$machineId');
  final data = response.data;
  final List<dynamic> list = data is Map<String, dynamic>
      ? data['content'] as List
      : data as List;
  return list
      .map((c) => Command.fromJson(c as Map<String, dynamic>))
      .toList();
});
