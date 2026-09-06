import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:yantrago/core/network/api_client.dart';
import 'package:yantrago/models/command.dart';

/// History provider — fetches command/activity history.
final historyProvider =
    FutureProvider.family<List<Command>, String>((ref, machineId) async {
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
