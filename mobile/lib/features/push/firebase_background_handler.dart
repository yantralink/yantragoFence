import 'dart:io';
import 'package:firebase_messaging/firebase_messaging.dart';
import 'package:flutter_local_notifications/flutter_local_notifications.dart';

/// Top-level background message handler for Firebase Messaging.
///
/// Per notification plan Phase 5: handle background/terminated-app notifications.
/// This must be a top-level function (not a class method or anonymous closure)
/// because it runs in a separate isolate when the app is terminated.
///
/// The handler shows a local notification with the alert content.
/// Tap behavior is handled by the main app via getInitialMessage().
final FlutterLocalNotificationsPlugin _localNotifications =
    FlutterLocalNotificationsPlugin();

Future<void> firebaseBackgroundMessageHandler(RemoteMessage message) async {
  final notification = message.notification;
  final data = message.data;

  if (notification == null) return;

  // Ensure the Android notification channel exists in the background isolate
  if (Platform.isAndroid) {
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
  }

  const initSettings = InitializationSettings(
    android: AndroidInitializationSettings('@mipmap/ic_launcher'),
    iOS: DarwinInitializationSettings(),
  );

  await _localNotifications.initialize(initSettings);

  final inboxId = data['inboxId']?.toString() ?? '';
  final notifId = inboxId.hashCode;

  const platformSpecifics = NotificationDetails(
    android: AndroidNotificationDetails(
      'yantrago_alerts',
      'YantraGO Alerts',
      channelDescription: 'Machine alerts and notifications',
      importance: Importance.high,
      priority: Priority.high,
    ),
    iOS: DarwinNotificationDetails(),
  );

  await _localNotifications.show(
    notifId,
    notification.title ?? 'YantraGO Alert',
    notification.body ?? '',
    platformSpecifics,
    payload: inboxId,
  );
}
