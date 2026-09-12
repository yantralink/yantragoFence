import 'package:stomp_dart_client/stomp.dart';
import 'package:stomp_dart_client/stomp_config.dart';
import 'package:stomp_dart_client/stomp_frame.dart';
import 'package:stomp_dart_client/stomp_handler.dart';
import 'package:yantrago/core/config/app_config.dart';

/// STOMP WebSocket client for real-time updates.
///
/// Connects to the backend WebSocket endpoint and subscribes to topics:
/// - /topic/locations — device location updates
/// - /topic/telemetry — telemetry readings
/// - /topic/commands — command result updates
/// - /topic/devices — device event updates
class WebSocketClient {
  StompClient? _client;
  final Map<String, StompUnsubscribe> _subscriptions = {};

  void connect({
    required Map<String, String> headers,
    required VoidCallback onConnect,
    required void Function(StompFrame) onDisconnect,
  }) {
    _client = StompClient(
      config: StompConfig(
        url: AppConfig.wsBaseUrl,
        stompConnectHeaders: headers,
        webSocketConnectHeaders: headers,
        onConnect: (frame) {
          onConnect();
        },
        onDisconnect: (frame) {
          onDisconnect(frame);
        },
        onStompError: (frame) {
          print('[WS] STOMP error: ${frame.body}');
        },
        onWebSocketError: (dynamic error) {
          print('[WS] WebSocket error: $error');
        },
      ),
    );
    _client?.activate();
  }

  void subscribe(String destination, void Function(StompFrame) callback) {
    if (_client?.isActive != true) return;
    final unsub = _client?.subscribe(
      destination: destination,
      callback: callback,
    );
    if (unsub != null) {
      _subscriptions[destination] = unsub;
    }
  }

  void unsubscribe(String destination) {
    _subscriptions[destination]?.call();
    _subscriptions.remove(destination);
  }

  void disconnect() {
    _subscriptions.clear();
    _client?.deactivate();
  }

  bool get isConnected => _client?.isActive == true;
}

typedef VoidCallback = void Function();
