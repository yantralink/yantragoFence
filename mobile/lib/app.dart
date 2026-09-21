import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter_localizations/flutter_localizations.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:yantrago/core/auth/auth_service.dart';
import 'package:yantrago/core/config/theme.dart';
import 'package:yantrago/core/locale/locale_controller.dart';
import 'package:yantrago/core/locale/locale_sync_provider.dart';
import 'package:yantrago/features/auth/providers/auth_provider.dart';
import 'package:yantrago/l10n/generated/app_localizations.dart';
import 'package:yantrago/features/commands/providers/command_provider.dart';
import 'package:yantrago/features/notifications/providers/notification_provider.dart';
import 'package:yantrago/features/notifications/providers/notification_socket_provider.dart';
import 'package:yantrago/features/push/providers/push_notification_provider.dart';
import 'package:yantrago/models/command_push_event.dart';
import 'package:yantrago/models/command_status_update.dart';
import 'package:yantrago/routing/app_router.dart';

/// Root MaterialApp widget — sets up theme and GoRouter.
///
/// Also initializes the notification socket controller for real-time
/// notification invalidation via WebSocket, the push notification service
/// for FCM delivery (Phase 5), and command push event handling so FCM
/// command lifecycle alerts refresh the command UI (Phase 6).
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

    // Register callbacks BEFORE initialize(): initialize() processes
    // getInitialMessage() (cold start from a notification tap), which must
    // already be able to dispatch command events.
    // SIG 24: register a foreground notification tap callback so tapping
    // a local notification navigates to the detail page immediately.
    pushService.setOnNotificationTap((inboxId) {
      final router = ref.read(goRouterProvider);
      router.push('/app/notifications/$inboxId');
    });

    // Phase 6: refresh the command UI when an FCM command lifecycle event
    // arrives (foreground or app-opened-from-push). The push payload's
    // alertId is the commandId for command alerts.
    pushService.setOnCommandEvent((CommandPushEvent event) {
      final commandId = event.commandId;
      if (commandId != null) {
        ref.read(commandProvider.notifier).applyStatusUpdate(
              CommandStatusUpdate(
                commandId: commandId,
                machineId: event.machineId,
                status: event.commandStatus,
                attemptCount:
                    ref.read(commandProvider).lastCommand?.attemptCount ?? 0,
              ),
            );
      }
      ref.invalidate(commandHistoryProvider);
    });

    await pushService.initialize();

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
      // Phase 6: refresh command history on resume so a push that landed
      // while backgrounded is reflected in the UI.
      ref.invalidate(commandHistoryProvider);
      // Phase 5: re-attempt a pending language sync after reconnecting.
      ref.read(localeSyncProvider.notifier).resumed();
      // Phase 5: converge with the server's last-accepted preference.
      unawaited(_refreshLocaleFromServer());
    }
  }

  /// Best-effort warm-resume locale convergence — never blocks or surfaces
  /// errors; an unauthenticated or failing session simply keeps the current
  /// device preference.
  Future<void> _refreshLocaleFromServer() async {
    try {
      final user = ref.read(currentUserProvider);
      if (user == null) return;
      final fresh = await ref.read(authServiceProvider).getCurrentUser();
      await ref.read(localeSyncProvider.notifier).serverLocaleRefreshed(
            userId: fresh.id,
            organizationId: fresh.organizationId,
            serverPreferredLocale: fresh.preferredLocale,
          );
    } catch (_) {
      // Offline or unauthenticated — keep the current locale.
    }
  }

  @override
  Widget build(BuildContext context) {
    final router = ref.watch(goRouterProvider);
    // Phase 3: effective UI locale (saved choice → device fallback → en).
    // Watching the controller's state rebuilds MaterialApp on change; child
    // screens rebuild via Localizations — navigation, form input, WebViews
    // and pending commands are not reset by a locale switch.
    final locale = ref.watch(localeControllerProvider).locale;

    return MaterialApp.router(
      // Brand name — always English.
      title: 'YantraGO', // always-en: brand name
      debugShowCheckedModeBanner: false,
      theme: AppTheme.lightTheme,
      darkTheme: AppTheme.darkTheme,
      locale: locale,
      localizationsDelegates: const <LocalizationsDelegate<Object>>[
        AppLocalizations.delegate,
        GlobalMaterialLocalizations.delegate,
        GlobalWidgetsLocalizations.delegate,
        GlobalCupertinoLocalizations.delegate,
      ],
      supportedLocales: AppLocalizations.supportedLocales,
      routerConfig: router,
    );
  }
}
