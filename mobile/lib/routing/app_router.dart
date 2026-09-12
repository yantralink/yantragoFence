import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import 'package:yantrago/core/auth/auth_state.dart';
import 'package:yantrago/core/widgets/app_state_panel.dart';
import 'package:yantrago/features/auth/providers/auth_provider.dart';
import 'package:yantrago/features/auth/pages/splash_page.dart';
import 'package:yantrago/features/auth/pages/login_page.dart';
import 'package:yantrago/features/machines/pages/machine_list_page.dart';
import 'package:yantrago/features/machines/pages/machine_detail_page.dart';
import 'package:yantrago/features/machines/pages/machine_location_map_page.dart';
import 'package:yantrago/features/alerts/pages/alert_detail_page.dart';
import 'package:yantrago/features/alerts/pages/alerts_page.dart';
import 'package:yantrago/features/notifications/pages/notification_detail_page.dart';
import 'package:yantrago/features/notifications/pages/notification_preferences_page.dart';
import 'package:yantrago/features/notifications/providers/notification_socket_provider.dart';
import 'package:yantrago/features/notifications/widgets/notification_badge.dart';
import 'package:yantrago/features/profile/pages/profile_page.dart';

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
            builder: (context, state) => const AlertsPage(initialTab: 1),
          ),
          GoRoute(
            path: '/app/notifications/:id',
            builder: (context, state) => NotificationDetailPage(
              notificationId: state.pathParameters['id']!,
            ),
          ),
          GoRoute(
            path: '/app/notifications/preferences',
            builder: (context, state) =>
                const NotificationPreferencesPage(),
          ),
          GoRoute(
            path: '/app/profile',
            builder: (context, state) => const ProfilePage(),
          ),
        ],
      ),
    ],
    errorBuilder: (context, state) => Scaffold(
      appBar: AppBar(title: const Text('Page Not Found')),
      body: AppStatePanel.error(
        message: 'The page "${state.matchedLocation}" could not be found.',
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
class _AppShell extends ConsumerWidget {
  final Widget child;

  const _AppShell({required this.child});

  static const _routes = [
    '/app/machines',
    '/app/alerts',
    '/app/profile',
  ];

  int _currentIndex(BuildContext context) {
    final location = GoRouterState.of(context).matchedLocation;
    // /app/notifications maps to the Alerts tab (inbox view)
    final normalizedLocation = location.startsWith('/app/notifications')
        ? '/app/alerts'
        : location;
    for (int i = 0; i < _routes.length; i++) {
      if (normalizedLocation.startsWith(_routes[i])) return i;
    }
    return 0;
  }

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    // SIG 19: watch the notification socket provider so it stays alive
    // for the lifetime of the authenticated session.
    ref.watch(notificationSocketProvider);

    final index = _currentIndex(context);
    return Scaffold(
      body: child,
      bottomNavigationBar: NavigationBar(
        selectedIndex: index,
        onDestinationSelected: (i) => context.go(_routes[i]),
        destinations: [
          const NavigationDestination(
            icon: Icon(Icons.devices_outlined),
            selectedIcon: Icon(Icons.devices),
            label: 'Machines',
          ),
          NavigationDestination(
            icon: NotificationBadge(
              child: const Icon(Icons.warning_amber_outlined),
            ),
            selectedIcon: const Icon(Icons.warning),
            label: 'Alerts',
          ),
          const NavigationDestination(
            icon: Icon(Icons.person_outlined),
            selectedIcon: Icon(Icons.person),
            label: 'Profile',
          ),
        ],
      ),
    );
  }
}
