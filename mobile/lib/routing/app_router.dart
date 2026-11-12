import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import 'package:yantrago/core/auth/auth_state.dart';
import 'package:yantrago/core/services/map_consent_service.dart';
import 'package:yantrago/core/widgets/app_state_panel.dart';
import 'package:yantrago/features/auth/providers/auth_provider.dart';
import 'package:yantrago/features/auth/pages/splash_page.dart';
import 'package:yantrago/features/auth/pages/login_page.dart';
import 'package:yantrago/features/commands/pages/command_history_page.dart';
import 'package:yantrago/features/machines/pages/machine_list_page.dart';
import 'package:yantrago/features/machines/pages/machine_detail_page.dart';
import 'package:yantrago/features/machines/pages/machine_location_map_page.dart';
import 'package:yantrago/features/schedules/pages/schedule_edit_page.dart';
import 'package:yantrago/features/alerts/pages/alert_detail_page.dart';
import 'package:yantrago/features/alerts/pages/alerts_page.dart';
import 'package:yantrago/features/analytics/pages/analytics_page.dart';
import 'package:yantrago/features/notifications/pages/notification_detail_page.dart';
import 'package:yantrago/features/notifications/pages/notification_preferences_page.dart';
import 'package:yantrago/features/notifications/providers/notification_socket_provider.dart';
import 'package:yantrago/features/notifications/widgets/notification_badge.dart';
import 'package:yantrago/features/profile/pages/profile_page.dart';
import 'package:yantrago/features/profile/pages/account_details_page.dart';
import 'package:yantrago/features/profile/pages/language_settings_page.dart';
import 'package:yantrago/features/profile/pages/theft_protection_settings_page.dart';
import 'package:yantrago/l10n/l10n.dart';

/// App router — GoRouter with auth guards.
///
/// Per AGENTS.md rule 9: sensitive operations require authorization.
/// Routes under /app require authentication; /login is public.
///
/// IMPORTANT: We do NOT watch authStateProvider here (that would rebuild
/// the entire GoRouter on every state change, causing an infinite reload
/// loop). Instead, we use refreshListenable to trigger re-evaluation of
/// the redirect, and read authState inside the redirect callback.
final goRouterProvider = Provider<GoRouter>((ref) {
  return GoRouter(
    initialLocation: '/splash',
    refreshListenable: _AuthStateListenable(ref),
    redirect: (context, state) {
      // Read (not watch) auth state inside the redirect
      final authState = ref.read(authStateProvider);
      final isLoggedIn = authState is Authenticated;
      final goingToLogin = state.matchedLocation == '/login';
      final goingToSplash = state.matchedLocation == '/splash';

      // Allow splash page to stay while auth is being checked
      if (goingToSplash) return null;

      // If auth is still loading (initial check), stay on splash
      if (authState is AuthInitial || authState is AuthLoading) {
        return '/splash';
      }

      if (!isLoggedIn && !goingToLogin) {
        return '/login';
      }

      if (isLoggedIn && goingToLogin) {
        return '/app/machines';
      }

      return null;
    },
    routes: [
      GoRoute(
        path: '/splash',
        builder: (context, state) => const SplashPage(),
      ),
      GoRoute(
        path: '/login',
        builder: (context, state) => const LoginPage(),
      ),
      ShellRoute(
        builder: (context, state, child) => _AppShell(child: child),
        routes: [
          GoRoute(
            path: '/app/machines',
            builder: (context, state) => const MachineListPage(),
          ),
          GoRoute(
            path: '/app/machines/:id',
            builder: (context, state) => MachineDetailPage(
              machineId: state.pathParameters['id']!,
            ),
          ),
          GoRoute(
            path: '/app/machines/:id/location',
            builder: (context, state) => MachineLocationMapPage(
              machineId: state.pathParameters['id']!,
            ),
          ),
          GoRoute(
            path: '/app/machines/:id/schedule',
            builder: (context, state) => ScheduleEditPage(
              machineId: state.pathParameters['id']!,
            ),
          ),
          GoRoute(
            path: '/app/alerts',
            builder: (context, state) => const AlertsPage(),
          ),
          GoRoute(
            path: '/app/alerts/:id',
            builder: (context, state) => AlertDetailPage(
              alertId: state.pathParameters['id']!,
            ),
          ),
          GoRoute(
            path: '/app/notifications',
            builder: (context, state) => const AlertsPage(),
          ),
          GoRoute(
            path: '/app/analytics',
            builder: (context, state) => const AnalyticsPage(),
          ),
          GoRoute(
            path: '/app/notifications/preferences',
            builder: (context, state) =>
                const NotificationPreferencesPage(),
          ),
          GoRoute(
            path: '/app/notifications/:id',
            builder: (context, state) => NotificationDetailPage(
              notificationId: state.pathParameters['id']!,
            ),
          ),
          GoRoute(
            path: '/app/profile',
            builder: (context, state) => const ProfilePage(),
          ),
          GoRoute(
            path: '/app/profile/account',
            builder: (context, state) => const AccountDetailsPage(),
          ),
          GoRoute(
            path: '/app/profile/language',
            builder: (context, state) => const LanguageSettingsPage(),
          ),
          GoRoute(
            path: '/app/profile/theft-protection',
            builder: (context, state) => const TheftProtectionSettingsPage(),
          ),
          GoRoute(
            path: '/app/profile/commands',
            builder: (context, state) => const CommandHistoryPage(),
          ),
        ],
      ),
    ],
    errorBuilder: (context, state) => Scaffold(
      appBar: AppBar(title: Text(context.l10n.errorPageNotFound)),
      body: AppStatePanel.error(
        message: context.l10n
            .errorMessagePageNotFound(state.matchedLocation),
        onRetry: () => context.go('/app/machines'),
      ),
    ),
  );
});

class _AuthStateListenable extends ChangeNotifier {
  _AuthStateListenable(Ref ref) {
    ref.listen<AuthState>(authStateProvider, (_, __) {
      notifyListeners();
    });
  }
}

/// App shell with bottom navigation bar.
class _AppShell extends ConsumerStatefulWidget {
  final Widget child;

  const _AppShell({required this.child});

  static const _routes = [
    '/app/machines',
    '/app/alerts',
    '/app/analytics',
    '/app/profile',
  ];

  @override
  ConsumerState<_AppShell> createState() => _AppShellState();
}

class _AppShellState extends ConsumerState<_AppShell> {
  /// Guards the post-login map consent prompt so it is shown at most
  /// once per authenticated session.
  bool _mapConsentPrompted = false;

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addPostFrameCallback(
      (_) => _maybePromptMapConsent(),
    );
  }

  int _currentIndex(BuildContext context) {
    final location = GoRouterState.of(context).matchedLocation;
    // /app/notifications maps to the Alerts tab (inbox view)
    final normalizedLocation = location.startsWith('/app/notifications')
        ? '/app/alerts'
        : location;
    for (int i = 0; i < _AppShell._routes.length; i++) {
      if (normalizedLocation.startsWith(_AppShell._routes[i])) return i;
    }
    return 0;
  }

  /// SEC-15: ask for map/geocoding consent right after login instead of
  /// waiting for the map screen — an affirmative choice here means the
  /// machine's location is visible the first time the user opens it.
  /// Skips while consent is still resolving or already recorded.
  void _maybePromptMapConsent() {
    if (_mapConsentPrompted || !mounted) return;
    if (ref.read(currentUserProvider) == null) return;
    if (ref.read(mapConsentProvider).valueOrNull != MapConsent.undecided) {
      return;
    }
    _mapConsentPrompted = true;
    _showMapConsentPrompt();
  }

  Future<void> _showMapConsentPrompt() async {
    final l10n = context.l10n;
    final allow = await showDialog<bool>(
      context: context,
      barrierDismissible: false,
      builder: (dialogContext) => AlertDialog(
        icon: const Icon(Icons.privacy_tip_outlined),
        title: Text(l10n.mapConsentTitle, textAlign: TextAlign.center),
        content: Text(l10n.mapConsentMessage, textAlign: TextAlign.center),
        actions: [
          TextButton(
            onPressed: () => Navigator.of(dialogContext).pop(false),
            child: Text(l10n.mapConsentNotNow),
          ),
          FilledButton(
            onPressed: () => Navigator.of(dialogContext).pop(true),
            child: Text(l10n.mapConsentAllow),
          ),
        ],
      ),
    );
    if (!mounted || allow == null) return;
    if (allow) {
      await ref.read(mapConsentProvider.notifier).grant();
    } else {
      await ref.read(mapConsentProvider.notifier).decline();
    }
  }

  @override
  Widget build(BuildContext context) {
    // SIG 19: watch the notification socket provider so it stays alive
    // for the lifetime of the authenticated session.
    ref.watch(notificationSocketProvider);

    // Consent may still be resolving when the shell first builds after
    // login — prompt once it settles on undecided.
    ref.listen(mapConsentProvider, (_, next) {
      if (next.valueOrNull == MapConsent.undecided) {
        WidgetsBinding.instance.addPostFrameCallback(
          (_) => _maybePromptMapConsent(),
        );
      }
    });

    final index = _currentIndex(context);
    final l10n = context.l10n;
    return Scaffold(
      body: widget.child,
      bottomNavigationBar: NavigationBar(
        selectedIndex: index,
        onDestinationSelected: (i) => context.go(_AppShell._routes[i]),
        destinations: [
          NavigationDestination(
            icon: const Icon(Icons.devices_outlined),
            selectedIcon: const Icon(Icons.devices),
            label: l10n.navMachines,
          ),
          NavigationDestination(
            icon: const NotificationBadge(
              child: Icon(Icons.warning_amber_outlined),
            ),
            selectedIcon: const Icon(Icons.warning),
            label: l10n.navAlerts,
          ),
          NavigationDestination(
            icon: const Icon(Icons.insights_outlined),
            selectedIcon: const Icon(Icons.insights),
            label: l10n.navAnalytics,
          ),
          NavigationDestination(
            icon: const Icon(Icons.person_outlined),
            selectedIcon: const Icon(Icons.person),
            label: l10n.navProfile,
          ),
        ],
      ),
    );
  }
}
