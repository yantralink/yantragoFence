import 'package:dio/dio.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:yantrago/core/network/api_client.dart';
import 'package:yantrago/models/command.dart';

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

  /// Fetches command history for a machine.
  Future<void> fetchCommandHistory(String machineId) async {
    final response = await _dio.get('/api/v1/commands?machineId=$machineId');
    final commands = (response.data as List)
        .map((c) => Command.fromJson(c as Map<String, dynamic>))
        .toList();
    state = CommandState(history: commands);
  }
}

class CommandState {
  final Command? lastCommand;
  final List<Command> history;
  final bool pending;

  const CommandState({
    this.lastCommand,
    this.history = const [],
    this.pending = false,
  });
}

final commandProvider =
    StateNotifierProvider<CommandNotifier, CommandState>((ref) {
  return CommandNotifier(ref.watch(apiClientProvider));
});
