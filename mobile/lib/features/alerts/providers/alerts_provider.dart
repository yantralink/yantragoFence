import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:yantrago/core/network/api_client.dart';
import 'package:yantrago/models/alert.dart';

/// Alerts provider — fetches alerts for the organization.
final alertsProvider = FutureProvider<List<Alert>>((ref) async {
  final dio = ref.watch(apiClientProvider);
  final response = await dio.get('/api/alerts');
  return (response.data as List)
      .map((a) => Alert.fromJson(a as Map<String, dynamic>))
      .toList();
});
