/// App configuration — API URLs and environment settings.
///
/// Per AGENTS.md rule 20: no secrets committed. API URLs are configurable
/// via environment variables or build flavors.
class AppConfig {
  AppConfig._();

  // API base URL — production server
  static const String apiBaseUrl = String.fromEnvironment(
    'API_BASE_URL',
    defaultValue: 'https://yantrago.com',
  );

  // WebSocket base URL
  static const String wsBaseUrl = String.fromEnvironment(
    'WS_BASE_URL',
    defaultValue: 'wss://yantrago.com/ws',
  );

  // API endpoints (all under /api/v1/)
  static const String loginEndpoint = '/api/v1/auth/login';
  static const String refreshEndpoint = '/api/v1/auth/refresh';
  static const String logoutEndpoint = '/api/v1/auth/logout';
  static const String meEndpoint = '/api/v1/auth/me';

  static const String machinesEndpoint = '/api/v1/machines';
  static const String commandsEndpoint = '/api/v1/commands';
  static const String alertsEndpoint = '/api/v1/alerts';
  static const String locationsEndpoint = '/api/v1/locations';
  static const String telemetryEndpoint = '/api/v1/telemetry';
  static const String reportsEndpoint = '/api/v1/reports';
  static const String settingsEndpoint = '/api/v1/settings';
  static const String rechargeEndpoint = '/api/v1/recharge';
  static const String auditEndpoint = '/api/v1/audit';

  // Notification inbox endpoints (Phase 3/4)
  static const String notificationsInboxEndpoint = '/api/v1/notifications/inbox';
  static const String notificationsUnreadCountEndpoint = '/api/v1/notifications/inbox/unread-count';
  static const String notificationsPreferencesEndpoint = '/api/v1/notifications/preferences';

  // Device token endpoints (Phase 5)
  static const String deviceTokensEndpoint = '/api/v1/device-tokens';
  static const String deviceTokensDeactivateAllEndpoint = '/api/v1/device-tokens/deactivate-all';

  // WebSocket STOMP destinations
  static const String wsTopicLocation = '/topic/locations';
  static const String wsTopicTelemetry = '/topic/telemetry';
  static const String wsTopicCommandResult = '/topic/commands';
  static const String wsTopicDeviceEvent = '/topic/devices';
  // User-specific notification invalidation queue (Phase 3/4)
  static const String wsUserQueueNotifications = '/user/queue/notifications';
}
