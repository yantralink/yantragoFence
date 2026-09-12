import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:yantrago/core/network/api_client.dart';
import 'package:yantrago/features/auth/providers/auth_provider.dart';
import 'package:yantrago/models/machine.dart';

/// Dashboard provider — fetches summary data for the dashboard.
///
/// Watches [currentUserProvider] so the summary is re-fetched whenever the
/// authenticated user changes (login / logout / token refresh).
final dashboardProvider = FutureProvider<DashboardSummary>((ref) async {
  // Re-fetch when the authenticated user changes
  final user = ref.watch(currentUserProvider);
  if (user == null) {
    return const DashboardSummary(
      totalMachines: 0,
      onlineMachines: 0,
      activeMachines: 0,
      inStockMachines: 0,
      faultMachines: 0,
      offlineMachines: 0,
      machines: [],
    );
  }

  final dio = ref.watch(apiClientProvider);

  final machinesResponse = await dio.get('/api/v1/machines');
  final data = machinesResponse.data;
  // Handle paginated response { "content": [...] } or plain list
  final List<dynamic> list = data is Map<String, dynamic>
      ? data['content'] as List
      : data as List;
  final machines = list
      .map((m) => Machine.fromJson(m as Map<String, dynamic>))
      .toList();

  int online = 0;
  int active = 0;
  int inStock = 0;
  int fault = 0;
  int offline = 0;

  for (final m in machines) {
    switch (m.status) {
      case 'ACTIVE':
        active++;
        if (m.isOnline) online++;
        break;
      case 'IN_STOCK':
        inStock++;
        break;
      case 'FAULT':
        fault++;
        break;
      case 'OFFLINE':
        offline++;
        break;
      default:
        if (m.isOnline) online++;
        else offline++;
    }
  }

  return DashboardSummary(
    totalMachines: machines.length,
    onlineMachines: online,
    activeMachines: active,
    inStockMachines: inStock,
    faultMachines: fault,
    offlineMachines: offline,
    machines: machines,
  );
});

class DashboardSummary {
  final int totalMachines;
  final int onlineMachines;
  final int activeMachines;
  final int inStockMachines;
  final int faultMachines;
  final int offlineMachines;
  final List<Machine> machines;

  const DashboardSummary({
    required this.totalMachines,
    required this.onlineMachines,
    required this.activeMachines,
    required this.inStockMachines,
    required this.faultMachines,
    required this.offlineMachines,
    required this.machines,
  });
}
