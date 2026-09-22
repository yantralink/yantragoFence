import 'dart:async';
import 'dart:convert';

import 'package:flutter/foundation.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:stomp_dart_client/stomp.dart';
import 'package:stomp_dart_client/stomp_config.dart';
import 'package:stomp_dart_client/stomp_frame.dart';
import 'package:yantrago/core/auth/auth_state.dart';
import 'package:yantrago/core/auth/token_manager.dart';
import 'package:yantrago/core/config/app_config.dart';
import 'package:yantrago/features/auth/providers/auth_provider.dart';
import 'package:yantrago/features/commands/providers/command_provider.dart';
import 'package:yantrago/models/command_status_update.dart';

/// Real-time command status socket controller.
///
/// Mirrors `NotificationSocketController` (notification plan Phase 4):
/// - Own `StompClient` connecting to `${AppConfig.wsBaseUrl}?token=$token`
/// - Authorization header fallback for non-SockJS clients
/// - 5s reconnect `Timer`
/// - `_disposed` guard
/// - Sealed state class
///
/// Subscribes to `/topic/command/{machineId}` and applies incoming
/// `CommandStatusUpdate`s to the shared `commandProvider`, then invalidates
/// `commandHistoryProvider` so the REST history re-fetches.
class CommandSocketController extends StateNotifier<CommandSocketState> {
  final Ref _ref;
  final String machineId;
  final bool _autoConnect;

  StompClient? _client;
  Timer? _reconnectTimer;
  bool _disposed = false;

  CommandSocketController(
    this._ref,
    this.machineId, {
    bool autoConnect = true,
  })  : _autoConnect = autoConnect,
        super(const CommandSocketState.disconnected()) {
    if (_autoConnect) {
      _init();
    }
  }

  void _init() {
    // Listen to auth state changes — connect on auth, disconnect on logout.
    // ref.listen auto-disposes when this provider is disposed.
    _ref.listen<AuthState>(authStateProvider, (prev, next) {
      if (next is Authenticated) {
        connect();
      } else if (next is Unauthenticated || next is AuthInitial) {
        disconnect();
      }
    });

    // Auto-connect if already authenticated at init time
    final currentState = _ref.read(authStateProvider);
    if (currentState is Authenticated) {
      connect();
    }
  }

  /// Connects to the WebSocket and subscribes to the command status topic.
  Future<void> connect() async {
    if (_disposed) return;
    if (_client?.isActive == true) return; // already connected

    state = const CommandSocketState.connecting();

    final token = await TokenManager.getAccessToken();
    if (token == null) {
      state = const CommandSocketState.disconnected();
      return;
    }

    // Pass token as query parameter (SockJS-safe); keep Authorization header
    // as fallback for non-SockJS clients.
    final wsUrl = '${AppConfig.wsBaseUrl}?token=$token';

    _client = StompClient(
      config: StompConfig(
        url: wsUrl,
        useSockJS: true,
        stompConnectHeaders: {'Authorization': 'Bearer $token'},
        webSocketConnectHeaders: {'Authorization': 'Bearer $token'},
        onConnect: (frame) {
          _onConnect();
        },
        onDisconnect: (frame) {
          state = const CommandSocketState.disconnected();
          _scheduleReconnect();
        },
        onStompError: (frame) {
          state = CommandSocketState.error('STOMP error: ${frame.body}');
          _scheduleReconnect();
        },
        onWebSocketError: (error) {
          state = CommandSocketState.error('WebSocket error: $error');
          _scheduleReconnect();
        },
      ),
    );
    _client?.activate();
  }

  void _onConnect() {
    state = const CommandSocketState.connected();

    // Subscribe to the machine-specific command status topic
    _client?.subscribe(
      destination: AppConfig.wsTopicCommand(machineId),
      callback: (frame) {
        _onCommandStatus(frame);
      },
    );

    // REST refresh on (re)connect — invalidate provider to re-fetch
    _ref.invalidate(commandHistoryProvider);
  }

  void _onCommandStatus(StompFrame frame) {
    try {
      if (frame.body == null) return;
      final json = jsonDecode(frame.body!) as Map<String, dynamic>;
      final update = CommandStatusUpdate.fromJson(json);
      if (update.machineId != machineId) return;
      _ref.read(commandProvider.notifier).applyStatusUpdate(update);
      _ref.invalidate(commandHistoryProvider);
    } catch (e) {
      // Never surface raw exceptions to the UI — log and swallow.
      debugPrint('CommandSocket: failed to handle status frame: $e');
    }
  }

  void _scheduleReconnect() {
    if (_disposed) return;
    _reconnectTimer?.cancel();
    _reconnectTimer = Timer(const Duration(seconds: 5), () {
      connect();
    });
  }

  /// Disconnects from the WebSocket.
  void disconnect() {
    _reconnectTimer?.cancel();
    _client?.deactivate();
    _client = null;
    state = const CommandSocketState.disconnected();
  }

  @override
  void dispose() {
    _disposed = true;
    _reconnectTimer?.cancel();
    _client?.deactivate();
    super.dispose();
  }
}

/// WebSocket connection state.
sealed class CommandSocketState {
  const CommandSocketState();

  const factory CommandSocketState.disconnected() = _Disconnected;
  const factory CommandSocketState.connecting() = _Connecting;
  const factory CommandSocketState.connected() = _Connected;
  const factory CommandSocketState.error(String message) = _Error;
}

class _Disconnected extends CommandSocketState {
  const _Disconnected();
}

class _Connecting extends CommandSocketState {
  const _Connecting();
}

class _Connected extends CommandSocketState {
  const _Connected();
}

class _Error extends CommandSocketState {
  final String message;
  const _Error(this.message);
}

/// Provider for the command status socket controller.
///
/// autoDispose + family: one socket per machine, scoped to the page that
/// watches it. Disposes (and disconnects) when the page is no longer mounted.
final commandSocketProvider = StateNotifierProvider.autoDispose
    .family<CommandSocketController, CommandSocketState, String>(
  (ref, machineId) => CommandSocketController(ref, machineId),
);
