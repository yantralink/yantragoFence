import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import 'package:yantrago/core/auth/auth_state.dart';
import 'package:yantrago/features/auth/providers/auth_provider.dart';
import 'package:yantrago/features/auth/pages/splash_page.dart';
import 'package:yantrago/features/auth/pages/login_page.dart';
import 'package:yantrago/features/machines/pages/machine_list_page.dart';
import 'package:yantrago/features/machines/pages/machine_detail_page.dart';
import 'package:yantrago/features/machines/pages/machine_location_map_page.dart';
import 'package:yantrago/features/alerts/pages/alerts_page.dart';
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
            path: '/app/profile',
            builder: (context, state) => const ProfilePage(),
          ),
        ],
      ),
    ],
    errorBuilder: (context, state) => Scaffold(
      body: Center(
        child: Text('Page not found: ${state.matchedLocation}'),
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
class _AppShell extends StatelessWidget {
  final Widget child;

  const _AppShell({required this.child});

  static const _routes = [
    '/app/machines',
    '/app/alerts',
    '/app/profile',
  ];

  int _currentIndex(BuildContext context) {
    final location = GoRouterState.of(context).matchedLocation;
    for (int i = 0; i < _routes.length; i++) {
      if (location.startsWith(_routes[i])) return i;
    }
    return 0;
  }

  @override
  Widget build(BuildContext context) {
    final index = _currentIndex(context);
    return Scaffold(
      body: child,
      bottomNavigationBar: NavigationBar(
        selectedIndex: index,
        onDestinationSelected: (i) => context.go(_routes[i]),
        destinations: const [
          NavigationDestination(
            icon: Icon(Icons.devices_outlined),
            selectedIcon: Icon(Icons.devices),
            label: 'Machines',
          ),
          NavigationDestination(
            icon: Icon(Icons.notifications_outlined),
            selectedIcon: Icon(Icons.notifications),
            label: 'Alerts',
          ),
          NavigationDestination(
            icon: Icon(Icons.person_outlined),
            selectedIcon: Icon(Icons.person),
            label: 'Profile',
          ),
        ],
      ),
    );
  }
}
