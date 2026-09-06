import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:yantrago/core/network/api_client.dart';
import 'package:yantrago/models/alert.dart';

/// Alerts provider — fetches alerts for the organization.
final alertsProvider = FutureProvider<List<Alert>>((ref) async {
  final dio = ref.watch(apiClientProvider);
  final response = await dio.get('/api/v1/alerts');
  final data = response.data;
  final List<dynamic> list = data is Map<String, dynamic>
      ? data['content'] as List
      : data as List;
  return list
      .map((a) => Alert.fromJson(a as Map<String, dynamic>))
      .toList();
});
