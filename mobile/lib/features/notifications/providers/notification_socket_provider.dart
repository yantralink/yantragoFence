import 'dart:async';

import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:stomp_dart_client/stomp.dart';
import 'package:stomp_dart_client/stomp_config.dart';
import 'package:stomp_dart_client/stomp_frame.dart';
import 'package:yantrago/core/auth/token_manager.dart';
import 'package:yantrago/core/config/app_config.dart';
import 'package:yantrago/core/auth/auth_state.dart';
import 'package:yantrago/features/auth/providers/auth_provider.dart';
import 'package:yantrago/features/notifications/providers/notification_provider.dart';

/// WebSocket notification invalidation controller.
///
/// Per notification plan Phase 4:
/// - Private socket invalidation via /user/queue/notifications
/// - REST refresh on resume/reconnect
/// - Clear all scoped state on account switching
///
/// Connects to the backend WebSocket and subscribes to the user-specific
/// notification queue. When a notification event arrives, it invalidates
/// the inbox list and unread count providers so they re-fetch via REST.
class NotificationSocketController
    extends StateNotifier<NotificationSocketState> {
  final Ref _ref;
  StompClient? _client;
  Timer? _reconnectTimer;
  bool _disposed = false;

  NotificationSocketController(this._ref)
      : super(const NotificationSocketState.disconnected()) {
    _init();
  }

  void _init() {
    // Listen to auth state changes — connect on auth, disconnect on logout.
    // This clears all scoped state on account switching.
    // ref.listen auto-disposes when this provider is disposed.
    _ref.listen<AuthState>(authStateProvider, (prev, next) {
      if (next is Authenticated) {
        connect();
      } else if (next is Unauthenticated || next is AuthInitial) {
        disconnect();
        // Clear all scoped notification state on account switching
        _ref.invalidate(unreadCountProvider);
        _ref.invalidate(notificationInboxProvider);
        _ref.invalidate(notificationPreferencesProvider);
      }
    });

    // Auto-connect if already authenticated at init time
    final currentState = _ref.read(authStateProvider);
    if (currentState is Authenticated) {
      connect();
    }
  }

  /// Connects to the WebSocket and subscribes to the user notification queue.
  Future<void> connect() async {
    if (_disposed) return;
    if (_client?.isActive == true) return; // already connected

    state = const NotificationSocketState.connecting();

    final token = await TokenManager.getAccessToken();
    if (token == null) {
      state = const NotificationSocketState.disconnected();
      return;
    }

    // Phase 3 fix: pass token as query parameter (SockJS-safe) instead of
    // relying on Authorization headers which SockJS may not reliably transmit.
    final wsUrl = '${AppConfig.wsBaseUrl}?token=$token';

    _client = StompClient(
      config: StompConfig(
        url: wsUrl,
        // Keep Authorization header as fallback for non-SockJS clients
        stompConnectHeaders: {'Authorization': 'Bearer $token'},
        webSocketConnectHeaders: {'Authorization': 'Bearer $token'},
        onConnect: (frame) {
          _onConnect();
        },
        onDisconnect: (frame) {
          state = const NotificationSocketState.disconnected();
          _scheduleReconnect();
        },
        onStompError: (frame) {
          state = NotificationSocketState.error(
            'STOMP error: ${frame.body}',
          );
          _scheduleReconnect();
        },
        onWebSocketError: (error) {
          state = NotificationSocketState.error('WebSocket error: $error');
          _scheduleReconnect();
        },
      ),
    );
    _client?.activate();
  }

  void _onConnect() {
    state = const NotificationSocketState.connected();

    // Subscribe to user-specific notification queue
    _client?.subscribe(
      destination: AppConfig.wsUserQueueNotifications,
      callback: (frame) {
        _onNotificationEvent(frame);
      },
    );

    // REST refresh on reconnect — invalidate providers to re-fetch
    _ref.invalidate(unreadCountProvider);
    _ref.invalidate(notificationInboxProvider);
  }

  void _onNotificationEvent(StompFrame frame) {
    // The event contains eventType (NEW, READ, READ_ALL) and unreadCount.
    // Invalidate the providers so they re-fetch via REST.
    // This avoids duplicate list items by relying on server-side dedup.
    _ref.invalidate(unreadCountProvider);
    _ref.invalidate(notificationInboxProvider);
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
    state = const NotificationSocketState.disconnected();
  }

  /// Manual REST refresh — called on app resume.
  void refresh() {
    _ref.invalidate(unreadCountProvider);
    _ref.invalidate(notificationInboxProvider);
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
sealed class NotificationSocketState {
  const NotificationSocketState();

  const factory NotificationSocketState.disconnected() =
      _Disconnected;
  const factory NotificationSocketState.connecting() = _Connecting;
  const factory NotificationSocketState.connected() = _Connected;
  const factory NotificationSocketState.error(String message) = _Error;
}

class _Disconnected extends NotificationSocketState {
  const _Disconnected();
}

class _Connecting extends NotificationSocketState {
  const _Connecting();
}

class _Connected extends NotificationSocketState {
  const _Connected();
}

class _Error extends NotificationSocketState {
  final String message;
  const _Error(this.message);
}

/// Provider for the notification socket controller.
final notificationSocketProvider = StateNotifierProvider<
    NotificationSocketController, NotificationSocketState>(
  (ref) => NotificationSocketController(ref),
);
