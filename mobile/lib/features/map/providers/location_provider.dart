import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:yantrago/core/network/api_client.dart';
import 'package:yantrago/features/auth/providers/auth_provider.dart';
import 'package:yantrago/models/location.dart';

/// Location provider — fetches latest locations for all machines.
///
/// Watches [currentUserProvider] so locations are re-fetched whenever the
/// authenticated user changes (login / logout / token refresh).
final locationProvider = FutureProvider<List<Location>>((ref) async {
  // Re-fetch when the authenticated user changes
  final user = ref.watch(currentUserProvider);
  if (user == null) return [];

  final dio = ref.watch(apiClientProvider);
  final response = await dio.get('/api/v1/locations/latest');
  final data = response.data;
  final List<dynamic> list = data is Map<String, dynamic>
      ? data['content'] as List
      : data as List;
  return list
      .map((l) => Location.fromJson(l as Map<String, dynamic>))
      .toList();
});
