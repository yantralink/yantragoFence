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
import 'package:yantrago/models/machine_location.dart';

/// Real-time location socket controller.
///
/// Mirrors `TelemetrySocketController`: own `StompClient` on
/// `${AppConfig.wsBaseUrl}` with a Bearer token (SEC-09), 10s
/// heartbeats, 5s reconnect, `_disposed` guard.
///
/// Subscribes to `/topic/location/{machineId}` — the backend's
/// LocationBroadcastService pushes MachineLocation-shaped payloads
/// (latitude, longitude, speed, course, recordedAt). State is null
/// until the first valid frame; widgets fall back to the REST snapshot.
class LocationSocketController extends StateNotifier<MachineLocation?> {
  final Ref _ref;
  final String machineId;
  final bool autoConnect;

  StompClient? _client;
  Timer? _reconnectTimer;
  bool _disposed = false;

  LocationSocketController(
    this._ref,
    this.machineId, {
    this.autoConnect = true,
  }) : super(null) {
    if (autoConnect) {
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

    // SEC-09: pass token via Authorization header only — never via URL
    // query parameter (URL tokens leak through proxy/access logs).
    const wsUrl = AppConfig.wsBaseUrl;

    debugPrint('LocationSocket: connecting for machineId=$machineId');
    _client = StompClient(
      config: StompConfig(
        url: wsUrl,
        stompConnectHeaders: {'Authorization': 'Bearer $token'},
        webSocketConnectHeaders: {'Authorization': 'Bearer $token'},
        // STOMP heartbeats — required to detect half-dead mobile TCP
        // connections where neither side sees a clean close.
        heartbeatOutgoing: const Duration(seconds: 10),
        heartbeatIncoming: const Duration(seconds: 10),
        onConnect: (frame) {
          debugPrint(
              'LocationSocket: connected, subscribing for $machineId');
          // Clear any stale state carried over a reconnect — fresh
          // frames will repopulate it.
          state = null;
          _client?.subscribe(
            destination: AppConfig.wsTopicLocationMachine(machineId),
            callback: _onLocationFrame,
          );
        },
        onWebSocketDone: () {
          // Fired when the underlying socket closes — including the
          // heartbeat-timeout path in stomp_dart_client, which bypasses
          // onDisconnect entirely. Clear state so the UI falls back to
          // the REST snapshot instead of showing a frozen position.
          debugPrint(
              'LocationSocket: WebSocket done — clearing stale state');
          state = null;
          _scheduleReconnect();
        },
      ),
    );
    _client?.activate();
  }

  void _onLocationFrame(StompFrame frame) {
    try {
      if (frame.body == null) return;
      final json = jsonDecode(frame.body!) as Map<String, dynamic>;
      final frameMachineId = json['machineId'] as String?;
      if (frameMachineId != machineId) return;
      final incoming = MachineLocation.fromJson(json);
      // Frames without a valid coordinate carry nothing useful.
      if (!incoming.latitude.isFinite ||
          incoming.latitude.abs() > 90 ||
          !incoming.longitude.isFinite ||
          incoming.longitude.abs() > 180) {
        return;
      }
      // Merge with existing state — keep the last known values for
      // optional fields that arrive null in the new frame.
      final prev = state;
      state = MachineLocation(
        deviceId: incoming.deviceId ?? prev?.deviceId,
        machineId: incoming.machineId ?? prev?.machineId,
        latitude: incoming.latitude,
        longitude: incoming.longitude,
        speed: incoming.speed ?? prev?.speed,
        course: incoming.course ?? prev?.course,
        recordedAt: incoming.recordedAt ?? prev?.recordedAt,
        updatedAt: DateTime.now(),
      );
      if (prev == null) {
        debugPrint(
            'LocationSocket: first frame received for $machineId');
      }
    } catch (e) {
      // Never surface raw exceptions to the UI — log and swallow.
      // SEC-08: Don't log exception details — may contain location data.
      debugPrint('LocationSocket: failed to handle location frame');
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

/// Latest location pushed over the socket for a machine (null until a
/// frame arrives). Keeping this provider alive also keeps the
/// underlying WebSocket connected; widgets fall back to the REST
/// snapshot while it is still null.
final locationSocketProvider = StateNotifierProvider.autoDispose
    .family<LocationSocketController, MachineLocation?, String>(
  (ref, machineId) => LocationSocketController(ref, machineId),
);
