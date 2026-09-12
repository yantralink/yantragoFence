import 'dart:io';
import 'package:dio/dio.dart';
import 'package:firebase_messaging/firebase_messaging.dart';
import 'package:flutter_local_notifications/flutter_local_notifications.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:yantrago/core/config/app_config.dart';
import 'package:yantrago/core/storage/secure_storage.dart';

/// Push notification provider (Phase 5).
///
/// Handles:
/// - Firebase initialization
/// - Permission onboarding
/// - FCM token registration/rotation/logout
/// - Android notification channels
/// - Foreground/background/tap behavior
/// - Deduplication of push vs inbox
///
/// Per notification plan Phase 5:
/// - Token rotation/logout
/// - Foreground/background/tap behavior and deduplication
/// - No dependency on push permission for core app function
class PushNotificationService {
  final Dio _dio;
  final FlutterLocalNotificationsPlugin _localNotifications;

  PushNotificationService(this._dio)
      : _localNotifications = FlutterLocalNotificationsPlugin();

  /// Initializes Firebase Messaging, requests permission, sets up
  /// Android channels, and registers the FCM token with the backend.
  ///
  /// Returns the FCM token, or null if permission denied or unavailable.
  Future<String?> initialize() async {
    try {
      // Request permission (onboarding)
      final settings = await FirebaseMessaging.instance.requestPermission(
        alert: true,
        badge: true,
        sound: true,
        criticalAlert: false,
      );

      if (settings.authorizationStatus == AuthorizationStatus.denied) {
        return null;
      }

      // Set up Android notification channel
      if (Platform.isAndroid) {
        await _setupAndroidChannel();
      }

      // Get FCM token
      final token = await FirebaseMessaging.instance.getToken();
      if (token != null) {
        await _registerTokenWithBackend(token);
      }

      // Listen for token refresh (rotation)
      FirebaseMessaging.instance.onTokenRefresh.listen((newToken) {
        _registerTokenWithBackend(newToken);
      });

      // Foreground message handler
      FirebaseMessaging.onMessage.listen(_handleForegroundMessage);

      // Background/tap handler
      FirebaseMessaging.onMessageOpenedApp.listen(_handleMessageOpenedApp);

      // Check if app was opened from a terminated state by a notification
      final initialMessage =
          await FirebaseMessaging.instance.getInitialMessage();
      if (initialMessage != null) {
        _handleMessageOpenedApp(initialMessage);
      }

      return token;
    } catch (e) {
      // Push is optional — app works without it
      return null;
    }
  }

  /// Sets up the Android notification channel for alerts.
  Future<void> _setupAndroidChannel() async {
    const channel = AndroidNotificationChannel(
      'yantrago_alerts',
      'YantraGO Alerts',
      description: 'Machine alerts and notifications',
      importance: Importance.high,
    );

    await _localNotifications
        .resolvePlatformSpecificImplementation<
            AndroidFlutterLocalNotificationsPlugin>()
        ?.createNotificationChannel(channel);

    const initSettings = InitializationSettings(
      android: AndroidInitializationSettings('@mipmap/ic_launcher'),
      iOS: DarwinInitializationSettings(),
    );

    // SIG 24: wire foreground local-notification taps so tapping a
    // notification navigates to the detail page.
    await _localNotifications.initialize(
      initSettings,
      onDidReceiveNotificationResponse: (response) {
        final payload = response.payload;
        if (payload != null) {
          _handleNotificationTap(payload);
        }
      },
    );
  }

  /// Handles a local notification tap by parsing the inbox ID from the
  /// payload and triggering navigation to the detail page.
  ///
  /// SIG 24: foreground notification taps navigate to the inbox detail.
  void _handleNotificationTap(String payload) {
    // The payload is the inboxId (set in _showLocalNotification).
    _pendingNavigationInboxId = payload;
    // If a tap callback is registered (set by the app), invoke it
    // for immediate navigation in the foreground case.
    _onNotificationTap?.call(payload);
  }

  /// Callback invoked when a local notification is tapped while the
  /// app is in the foreground. Set by the app shell to enable immediate
  /// navigation to the notification detail page.
  void Function(String inboxId)? _onNotificationTap;

  /// Registers a callback for foreground notification taps.
  void setOnNotificationTap(void Function(String inboxId) callback) {
    _onNotificationTap = callback;
  }

  /// Handles a foreground FCM message by showing a local notification.
  void _handleForegroundMessage(RemoteMessage message) {
    final notification = message.notification;
    final data = message.data;

    if (notification != null) {
      // Deduplication: skip if this inbox item was already shown
      final inboxId = data['inboxId'];
      if (inboxId != null) {
        final inboxIdStr = inboxId.toString();
        // In-memory deduplication set prevents duplicate foreground notifications
        // for the same inbox item (e.g. WebSocket + FCM both delivering)
        if (_shownInboxIds.contains(inboxIdStr)) {
          return;
        }
        _shownInboxIds.add(inboxIdStr);
        // Keep the set bounded — remove old entries after 100
        if (_shownInboxIds.length > 100) {
          _shownInboxIds.remove(_shownInboxIds.first);
        }
        _showLocalNotification(
          notification.title ?? 'YantraGO Alert',
          notification.body ?? '',
          inboxIdStr,
          data,
        );
      }
    }
  }

  /// Set of inbox IDs already shown as local notifications (deduplication).
  /// Bounded to 100 entries to prevent unbounded memory growth.
  final Set<String> _shownInboxIds = {};

  /// Handles a notification tap (background or terminated).
  void _handleMessageOpenedApp(RemoteMessage message) {
    final data = message.data;
    final inboxId = data['inboxId'];
    // Navigation to detail page is handled by the app router
    // via a global navigator key or state provider
    _pendingNavigationInboxId = inboxId?.toString();
  }

  /// Shows a local notification for foreground FCM messages.
  Future<void> _showLocalNotification(
      String title, String body, String inboxId, Map<String, dynamic> data) async {
    const androidPlatformSpecifics = AndroidNotificationDetails(
      'yantrago_alerts',
      'YantraGO Alerts',
      channelDescription: 'Machine alerts and notifications',
      importance: Importance.high,
      priority: Priority.high,
    );

    const platformSpecifics = NotificationDetails(
      android: androidPlatformSpecifics,
      iOS: DarwinNotificationDetails(),
    );

    // Use inboxId as notification ID for deduplication
    final notifId = inboxId.hashCode;
    await _localNotifications.show(notifId, title, body, platformSpecifics,
        payload: inboxId);
  }

  /// Registers the FCM token with the backend.
  Future<void> _registerTokenWithBackend(String token) async {
    try {
      final accessToken = await SecureStorage.getAccessToken();
      if (accessToken == null) {
        // Not logged in — store token, will register after login
        await SecureStorage.setFcmToken(token);
        await SecureStorage.setFcmTokenRegistered(false);
        return;
      }

      final platform = Platform.isIOS ? 'IOS' : 'ANDROID';

      await _dio.post(
        '${AppConfig.apiBaseUrl}${AppConfig.deviceTokensEndpoint}',
        data: {
          'token': token,
          'platform': platform,
          'appVersion': '1.0.0',
        },
        options: Options(headers: {
          'Authorization': 'Bearer $accessToken',
        }),
      );

      await SecureStorage.setFcmToken(token);
      await SecureStorage.setFcmTokenRegistered(true);
    } catch (e) {
      // Registration failed — will retry on next app launch
      await SecureStorage.setFcmTokenRegistered(false);
    }
  }

  /// Deactivates all device tokens on logout.
  /// Called when the user logs out to prevent push to a shared device.
  Future<void> deactivateAllTokensOnLogout() async {
    try {
      final accessToken = await SecureStorage.getAccessToken();
      if (accessToken == null) return;

      await _dio.post(
        '${AppConfig.apiBaseUrl}${AppConfig.deviceTokensDeactivateAllEndpoint}',
        options: Options(headers: {
          'Authorization': 'Bearer $accessToken',
        }),
      );
    } catch (e) {
      // Best-effort — logout proceeds regardless
    }

    // Clear local token state
    await SecureStorage.setFcmToken(null);
    await SecureStorage.setFcmTokenRegistered(false);

    // Unregister from FCM
    await FirebaseMessaging.instance.deleteToken();
  }

  /// Re-registers the FCM token after login (for shared-phone account switch).
  Future<void> registerTokenAfterLogin() async {
    try {
      final token = await FirebaseMessaging.instance.getToken();
      if (token != null) {
        await _registerTokenWithBackend(token);
      }
    } catch (e) {
      // Best-effort
    }
  }

  /// The inbox ID to navigate to (set when app opened from notification tap).
  String? _pendingNavigationInboxId;
  String? consumePendingNavigationInboxId() {
    final id = _pendingNavigationInboxId;
    _pendingNavigationInboxId = null;
    return id;
  }
}

/// Riverpod provider for PushNotificationService.
final pushNotificationServiceProvider = Provider<PushNotificationService>((ref) {
  // The Dio instance is created here; in production it would come from
  // a shared provider with interceptors configured.
  return PushNotificationService(Dio());
});
