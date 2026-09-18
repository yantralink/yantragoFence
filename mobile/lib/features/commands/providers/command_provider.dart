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

/// Command history state — accumulated pages of commands.
class CommandHistoryState {
  final List<Command> commands;
  final bool hasMore;
  final bool loadingMore;

  const CommandHistoryState({
    this.commands = const [],
    this.hasMore = false,
    this.loadingMore = false,
  });
}

/// Command history notifier — fetches all commands for the current
/// tenant (every machine), newest first, one page at a time.
///
/// Calls GET /api/v1/commands (no machineId) which the backend scopes to
/// the caller's organization from the JWT.
/// Per AGENTS.md rule 22: no direct API calls from widgets — go through a provider.
class CommandHistoryNotifier
    extends StateNotifier<AsyncValue<CommandHistoryState>> {
  CommandHistoryNotifier(this._dio) : super(const AsyncValue.loading());

  final Dio _dio;
  static const int _pageSize = 20;
  int _page = 0;

  Future<void> load() async {
    state = const AsyncValue.loading();
    try {
      final result = await _fetchPage(0);
      _page = 0;
      state = AsyncValue.data(
        CommandHistoryState(
          commands: result.commands,
          hasMore: result.hasMore,
        ),
      );
    } catch (e, st) {
      state = AsyncValue.error(e, st);
    }
  }

  /// Appends the next page. Rethrows on failure so the page can show a
  /// snackbar; already-loaded commands are kept.
  Future<void> loadMore() async {
    final current = state.valueOrNull;
    if (current == null || !current.hasMore || current.loadingMore) return;
    state = AsyncValue.data(
      CommandHistoryState(
        commands: current.commands,
        hasMore: current.hasMore,
        loadingMore: true,
      ),
    );
    try {
      final result = await _fetchPage(_page + 1);
      _page++;
      state = AsyncValue.data(
        CommandHistoryState(
          commands: <Command>[...current.commands, ...result.commands],
          hasMore: result.hasMore,
        ),
      );
    } catch (_) {
      state = AsyncValue.data(
        CommandHistoryState(
          commands: current.commands,
          hasMore: current.hasMore,
        ),
      );
      rethrow;
    }
  }

  Future<({List<Command> commands, bool hasMore})> _fetchPage(
    int page,
  ) async {
    final response = await _dio.get(
      '${AppConfig.commandsEndpoint}?page=$page&size=$_pageSize&sort=createdAt,desc',
    );
    final data = response.data;
    if (data is Map<String, dynamic>) {
      final List<dynamic> list = data['content'] as List? ?? const [];
      final int number = (data['number'] as num?)?.toInt() ?? page;
      final int totalPages = (data['totalPages'] as num?)?.toInt() ?? 1;
      return (
        commands: list
            .map((c) => Command.fromJson(c as Map<String, dynamic>))
            .toList(),
        hasMore: number + 1 < totalPages,
      );
    }
    final List<dynamic> list = data as List;
    return (
      commands: list
          .map((c) => Command.fromJson(c as Map<String, dynamic>))
          .toList(),
      hasMore: false,
    );
  }
}

final commandHistoryProvider = StateNotifierProvider.autoDispose<
    CommandHistoryNotifier, AsyncValue<CommandHistoryState>>((ref) {
  final notifier = CommandHistoryNotifier(ref.watch(apiClientProvider));
  if (ref.watch(currentUserProvider) != null) {
    notifier.load();
  }
  return notifier;
});
