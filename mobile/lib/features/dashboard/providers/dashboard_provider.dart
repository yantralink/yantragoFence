import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:yantrago/core/network/api_client.dart';
import 'package:yantrago/models/machine.dart';
import 'package:yantrago/models/device_state.dart';

/// Dashboard provider — fetches summary data for the dashboard.
final dashboardProvider = FutureProvider<DashboardSummary>((ref) async {
  final dio = ref.watch(apiClientProvider);

  final machinesResponse = await dio.get('/api/machines');
  final machines = (machinesResponse.data as List)
      .map((m) => Machine.fromJson(m as Map<String, dynamic>))
      .toList();

  int online = 0;
  int fencingOn = 0;
  int fault = 0;
  int offline = 0;

  for (final m in machines) {
    switch (m.status) {
      case 'ONLINE':
        online++;
        break;
      case 'FENCING_ON':
        fencingOn++;
        break;
      case 'FAULT':
        fault++;
        break;
      default:
        offline++;
    }
  }

  return DashboardSummary(
    totalMachines: machines.length,
    onlineMachines: online + fencingOn,
    fencingOnMachines: fencingOn,
    faultMachines: fault,
    offlineMachines: offline,
    machines: machines,
  );
});

class DashboardSummary {
  final int totalMachines;
  final int onlineMachines;
  final int fencingOnMachines;
  final int faultMachines;
  final int offlineMachines;
  final List<Machine> machines;

  const DashboardSummary({
    required this.totalMachines,
    required this.onlineMachines,
    required this.fencingOnMachines,
    required this.faultMachines,
    required this.offlineMachines,
    required this.machines,
  });
}
