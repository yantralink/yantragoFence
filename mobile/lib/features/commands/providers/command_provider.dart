import 'package:dio/dio.dart';
import 'package:flutter/foundation.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:yantrago/core/config/app_config.dart';
import 'package:yantrago/core/network/api_client.dart';
import 'package:yantrago/features/auth/providers/auth_provider.dart';
import 'package:yantrago/models/command.dart';
import 'package:yantrago/models/command_status_update.dart';

/// Command provider — sends ON/OFF commands and tracks command status.
///
/// Per AGENTS.md rule 5: never assume a command succeeded until ACK is received.
/// Per AGENTS.md rule 6: all commands must be auditable.
class CommandNotifier extends StateNotifier<CommandState> {
  final Dio _dio;

  CommandNotifier(this._dio) : super(const CommandState());

  /// Sends a command (FENCING_ON / FENCING_OFF) to a machine.
  Future<void> sendCommand({
    required String machineId,
    required String imei,
    required String commandType,
  }) async {
    final response = await _dio.post('/api/v1/commands', data: {
      'machineId': machineId,
      'imei': imei,
      'commandType': commandType,
    });
    final command = Command.fromJson(response.data as Map<String, dynamic>);
    state = CommandState(lastCommand: command, pending: true);
  }

  /// Applies a real-time status update from the command WebSocket.
  /// Only updates if it matches the last sent command.
  void applyStatusUpdate(CommandStatusUpdate update) {
    final last = state.lastCommand;
    if (last == null || last.id != update.commandId) return;
    final updated = last.copyWith(
      status: update.status,
      attemptCount: update.attemptCount,
      lastError: update.error,
      completedAt: (update.status == 'DONE' ||
              update.status == 'FAILED' ||
              update.status == 'TIMEOUT')
          ? DateTime.now()
          : null,
    );
    state = CommandState(
      lastCommand: updated,
      pending: !updated.isTerminal,
    );
  }

  /// Seeds the last command for testing — no network call.
  @visibleForTesting
  void seedLastCommand(Command c) {
    state = CommandState(lastCommand: c, pending: !c.isTerminal);
  }
}

class CommandState {
  final Command? lastCommand;
  final bool pending;

  const CommandState({
    this.lastCommand,
    this.pending = false,
  });
}

final commandProvider =
    StateNotifierProvider<CommandNotifier, CommandState>((ref) {
  return CommandNotifier(ref.watch(apiClientProvider));
});

/// Machine commands provider — fetches command history filtered by machine ID.
///
/// Calls GET /api/v1/commands?machineId={uuid} to show only the commands
/// belonging to the machine shown on the detail page.
/// Per AGENTS.md rule 22: no direct API calls from widgets — go through a provider.
final machineCommandsProvider =
    FutureProvider.family<List<Command>, String>((ref, machineId) async {
  final user = ref.watch(currentUserProvider);
  if (user == null) return [];

  final dio = ref.watch(apiClientProvider);
  final response =
      await dio.get('${AppConfig.commandsEndpoint}?machineId=$machineId&size=10&sort=createdAt,desc');
  final data = response.data;
  final List<dynamic> list = data is Map<String, dynamic>
      ? data['content'] as List
      : data as List;
  return list
      .map((c) => Command.fromJson(c as Map<String, dynamic>))
      .toList();
});
