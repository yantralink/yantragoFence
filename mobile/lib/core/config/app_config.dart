/// App configuration — API URLs and environment settings.
///
/// Per AGENTS.md rule 20: no secrets committed. API URLs are configurable
/// via environment variables or build flavors.
class AppConfig {
  AppConfig._();

  // API base URL — change for different environments
  static const String apiBaseUrl = String.fromEnvironment(
    'API_BASE_URL',
    defaultValue: 'http://localhost:8080',
  );

  // WebSocket base URL
  static const String wsBaseUrl = String.fromEnvironment(
    'WS_BASE_URL',
    defaultValue: 'ws://localhost:8080/ws',
  );

  // API endpoints
  static const String loginEndpoint = '/api/auth/login';
  static const String refreshEndpoint = '/api/auth/refresh';
  static const String logoutEndpoint = '/api/auth/logout';
  static const String meEndpoint = '/api/auth/me';

  static const String machinesEndpoint = '/api/machines';
  static const String devicesEndpoint = '/api/devices';
  static const String commandsEndpoint = '/api/commands';
  static const String alertsEndpoint = '/api/alerts';
  static const String locationsEndpoint = '/api/locations';
  static const String telemetryEndpoint = '/api/telemetry';
  static const String reportsEndpoint = '/api/reports';
  static const String settingsEndpoint = '/api/settings';
  static const String rechargeEndpoint = '/api/recharge';
  static const String auditEndpoint = '/api/audit';

  // WebSocket STOMP destinations
  static const String wsTopicLocation = '/topic/locations';
  static const String wsTopicTelemetry = '/topic/telemetry';
  static const String wsTopicCommandResult = '/topic/commands';
  static const String wsTopicDeviceEvent = '/topic/devices';
}
