import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:yantrago/core/config/theme.dart';
import 'package:yantrago/features/notifications/providers/notification_provider.dart';
import 'package:yantrago/features/notifications/providers/notification_socket_provider.dart';
import 'package:yantrago/features/push/providers/push_notification_provider.dart';
import 'package:yantrago/routing/app_router.dart';

/// Root MaterialApp widget — sets up theme and GoRouter.
///
/// Also initializes the notification socket controller for real-time
/// notification invalidation via WebSocket, and the push notification
/// service for FCM delivery (Phase 5).
class YantraGoApp extends ConsumerStatefulWidget {
  const YantraGoApp({super.key});

  @override
  ConsumerState<YantraGoApp> createState() => _YantraGoAppState();
}

class _YantraGoAppState extends ConsumerState<YantraGoApp>
    with WidgetsBindingObserver {
  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addObserver(this);
    // SIG 19: The notification socket controller is watched by the _AppShell
    // widget (via ref.watch in app_router.dart) so it stays alive for the
    // authenticated session. Here we eagerly read the notifier to trigger
    // the initial connection before the first frame.
    WidgetsBinding.instance.addPostFrameCallback((_) {
      ref.read(notificationSocketProvider.notifier);
      // Initialize push notifications (Phase 5)
      // This is best-effort — app works without push permission.
      _initializePush();
    });
  }

  /// Initializes push notifications and handles pending navigation
  /// from a notification tap (terminated-app launch).
  Future<void> _initializePush() async {
    final pushService = ref.read(pushNotificationServiceProvider);
    await pushService.initialize();

    // SIG 24: register a foreground notification tap callback so tapping
    // a local notification navigates to the detail page immediately.
    pushService.setOnNotificationTap((inboxId) {
      final router = ref.read(goRouterProvider);
      router.push('/app/notifications/$inboxId');
    });

    // If the app was opened from a notification tap, navigate to the detail page
    final pendingInboxId = pushService.consumePendingNavigationInboxId();
    if (pendingInboxId != null) {
      final router = ref.read(goRouterProvider);
      router.go('/app/notifications/$pendingInboxId');
    }
  }

  @override
  void dispose() {
    WidgetsBinding.instance.removeObserver(this);
    super.dispose();
  }

  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    // SIG 20: REST refresh on resume — invalidate inbox and unread count
    // so they re-fetch via REST when the app returns to the foreground.
    if (state == AppLifecycleState.resumed) {
      ref.invalidate(notificationInboxProvider);
      ref.invalidate(unreadCountProvider);
      // Also refresh the socket controller (reconnect if needed)
      ref.read(notificationSocketProvider.notifier).refresh();
    }
  }

  @override
  Widget build(BuildContext context) {
    final router = ref.watch(goRouterProvider);

    return MaterialApp.router(
      title: 'YantraGO',
      debugShowCheckedModeBanner: false,
      theme: AppTheme.lightTheme,
      darkTheme: AppTheme.darkTheme,
      routerConfig: router,
    );
  }
}
