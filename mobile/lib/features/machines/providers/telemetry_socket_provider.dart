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
import 'package:yantrago/models/telemetry.dart';

/// Real-time telemetry socket controller.
///
/// Mirrors `CommandSocketController` (notification plan Phase 4 pattern):
/// - Own `StompClient` connecting to `${AppConfig.wsBaseUrl}?token=$token`
/// - Authorization header fallback for non-SockJS clients
/// - 5s reconnect `Timer`
/// - `_disposed` guard
///
/// Subscribes to `/topic/telemetry/{machineId}` and holds the latest
/// [Telemetry] pushed by the backend as its state, so the machine details
/// screen shows ACC (ignition), battery, GSM, and voltage changes live —
/// no pull-to-refresh needed. State is null until the first frame arrives;
/// widgets fall back to the REST snapshot until then.
class TelemetrySocketController extends StateNotifier<Telemetry?> {
  final Ref _ref;
  final String machineId;
  final bool _autoConnect;

  StompClient? _client;
  Timer? _reconnectTimer;
  bool _disposed = false;

  TelemetrySocketController(
    this._ref,
    this.machineId, {
    bool autoConnect = true,
  })  : _autoConnect = autoConnect,
        super(null) {
    if (_autoConnect) {
      _init();
    }
  }

  void _init() {
    // Listen to auth state changes — connect on auth, disconnect on logout.
    _ref.listen<AuthState>(authStateProvider, (prev, next) {
      if (next is Authenticated) {
        connect();
      } else if (next is Unauthenticated || next is AuthInitial) {
        disconnect();
      }
    });

    final currentState = _ref.read(authStateProvider);
    if (currentState is Authenticated) {
      connect();
    }
  }

  Future<void> connect() async {
    if (_disposed) return;
    if (_client?.isActive == true) return; // already connected

    final token = await TokenManager.getAccessToken();
    if (token == null) {
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
          _client?.subscribe(
            destination: AppConfig.wsTopicTelemetryMachine(machineId),
            callback: _onTelemetryFrame,
          );
        },
        onDisconnect: (frame) {
          _scheduleReconnect();
        },
        onStompError: (frame) {
          debugPrint('TelemetrySocket: STOMP error: ${frame.body}');
          _scheduleReconnect();
        },
        onWebSocketError: (error) {
          debugPrint('TelemetrySocket: WebSocket error: $error');
          _scheduleReconnect();
        },
      ),
    );
    _client?.activate();
  }

  void _onTelemetryFrame(StompFrame frame) {
    try {
      if (frame.body == null) return;
      final json = jsonDecode(frame.body!) as Map<String, dynamic>;
      final frameMachineId = json['machineId'] as String?;
      if (frameMachineId != machineId) return;
      state = Telemetry.fromJson(json);
    } catch (e) {
      // Never surface raw exceptions to the UI — log and swallow.
      debugPrint('TelemetrySocket: failed to handle telemetry frame: $e');
    }
  }

  void _scheduleReconnect() {
    if (_disposed) return;
    _reconnectTimer?.cancel();
    _reconnectTimer = Timer(const Duration(seconds: 5), () {
      connect();
    });
  }

  void disconnect() {
    _reconnectTimer?.cancel();
    _client?.deactivate();
    _client = null;
  }

  @override
  void dispose() {
    _disposed = true;
    _reconnectTimer?.cancel();
    _client?.deactivate();
    super.dispose();
  }
}

/// Latest telemetry pushed over the socket for a machine (null until a
/// frame arrives). Keeping this provider alive also keeps the underlying
/// WebSocket connected; widgets fall back to the REST snapshot while it
/// is still null.
final telemetrySocketProvider = StateNotifierProvider.autoDispose
    .family<TelemetrySocketController, Telemetry?, String>(
  (ref, machineId) => TelemetrySocketController(ref, machineId),
);
