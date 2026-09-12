import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:yantrago/core/config/app_config.dart';
import 'package:yantrago/core/network/api_client.dart';
import 'package:yantrago/features/auth/providers/auth_provider.dart';
import 'package:yantrago/models/alert.dart';

/// Alerts provider — fetches alerts for the current user.
///
/// Watches [currentUserProvider] so alerts are re-fetched whenever the
/// authenticated user changes (login / logout / token refresh).
final alertsProvider = FutureProvider<List<Alert>>((ref) async {
  // Re-fetch when the authenticated user changes
  final user = ref.watch(currentUserProvider);
  if (user == null) return [];

  final dio = ref.watch(apiClientProvider);
  final response = await dio.get(AppConfig.alertsEndpoint);
  final data = response.data;
  final List<dynamic> list = data is Map<String, dynamic>
      ? data['content'] as List
      : data as List;
  return list
      .map((a) => Alert.fromJson(a as Map<String, dynamic>))
      .toList();
});

/// Alert detail provider — fetches a single alert by ID.
///
/// Per AGENTS.md rule 22: no direct API calls from widgets — go through a provider.
final alertDetailProvider =
    FutureProvider.family<Alert?, String>((ref, id) async {
  final user = ref.watch(currentUserProvider);
  if (user == null) return null;

  final dio = ref.watch(apiClientProvider);
  final response = await dio.get('${AppConfig.alertsEndpoint}/$id');
  return Alert.fromJson(response.data as Map<String, dynamic>);
});

/// Machine alerts provider — fetches alerts filtered by machine ID.
///
/// Calls GET /api/v1/alerts?machineId={uuid} to show only the alerts
/// belonging to the machine shown on the detail page.
/// Per AGENTS.md rule 22: no direct API calls from widgets — go through a provider.
final machineAlertsProvider =
    FutureProvider.family<List<Alert>, String>((ref, machineId) async {
  final user = ref.watch(currentUserProvider);
  if (user == null) return [];

  final dio = ref.watch(apiClientProvider);
  final response =
      await dio.get('${AppConfig.alertsEndpoint}?machineId=$machineId');
  final data = response.data;
  final List<dynamic> list = data is Map<String, dynamic>
      ? data['content'] as List
      : data as List;
  return list
      .map((a) => Alert.fromJson(a as Map<String, dynamic>))
      .toList();
});
