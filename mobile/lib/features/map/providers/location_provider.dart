import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:yantrago/core/network/api_client.dart';
import 'package:yantrago/models/location.dart';

/// Location provider — fetches latest locations for all machines.
final locationProvider = FutureProvider<List<Location>>((ref) async {
  final dio = ref.watch(apiClientProvider);
  final response = await dio.get('/api/locations/latest');
  return (response.data as List)
      .map((l) => Location.fromJson(l as Map<String, dynamic>))
      .toList();
});
