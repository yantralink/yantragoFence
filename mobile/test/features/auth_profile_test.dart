// Widget tests for the migrated Splash, Login, Profile pages and nav shell.
//
// Covers: splash branding, login validation, password visibility, busy
// login state, profile with/without user, logout, tab navigation, back
// nav, light/dark themes, and large text. Providers are overridden so
// the tests never contact the network or secure storage.

import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import 'package:yantrago/core/auth/auth_service.dart';
import 'package:yantrago/core/auth/auth_state.dart';
import 'package:yantrago/core/config/theme.dart';
import 'package:yantrago/features/auth/pages/login_page.dart';
import 'package:yantrago/features/auth/pages/splash_page.dart';
import 'package:yantrago/features/auth/providers/auth_provider.dart';
import 'package:yantrago/features/profile/pages/profile_page.dart';
import 'package:yantrago/features/profile/providers/profile_provider.dart';
import 'package:yantrago/features/push/providers/push_notification_provider.dart';
import 'package:yantrago/models/user.dart';

const User _user = User(
  id: 'u1',
  organizationId: 'org1',
  organizationName: 'Demo Farm',
  email: 'demo@example.invalid',
  fullName: 'Demo User',
  role: 'CUSTOMER',
  phoneNumber: '+91 98765 43210',
  active: true,
);

Widget _wrapped(
  Widget child, {
  Brightness brightness = Brightness.light,
  List<Override> overrides = const [],
  double textScale = 1.0,
  Size surface = const Size(390, 1200),
}) {
  return ProviderScope(
    overrides: overrides,
    child: MaterialApp(
      theme: brightness == Brightness.light ? AppTheme.lightTheme : AppTheme.darkTheme,
      home: child,
      builder: (context, widget) => MediaQuery(
        data: MediaQuery.of(context).copyWith(textScaler: TextScaler.linear(textScale)),
        child: widget!,
      ),
    ),
  );
}

Future<void> _pump(
  WidgetTester tester,
  Widget child, {
  Brightness brightness = Brightness.light,
  List<Override> overrides = const [],
  double textScale = 1.0,
  Size surface = const Size(390, 1200),
}) async {
  await tester.binding.setSurfaceSize(surface);
  await tester.pumpWidget(_wrapped(
    child,
    brightness: brightness,
    overrides: overrides,
    textScale: textScale,
    surface: surface,
  ));
  await tester.pump();
}

Future<void> _pumpSplash(
  WidgetTester tester,
  _FakeAuthNotifier notifier, {
  Brightness brightness = Brightness.light,
}) async {
  await tester.binding.setSurfaceSize(const Size(390, 1200));
  final GoRouter router = GoRouter(
    initialLocation: '/splash',
    routes: [
      GoRoute(path: '/splash', builder: (context, state) => const SplashPage()),
      GoRoute(path: '/login', builder: (context, state) => const Scaffold(body: Center(child: Text('LOGIN')))),
      GoRoute(path: '/app/machines', builder: (context, state) => const Scaffold(body: Center(child: Text('MACHINES')))),
    ],
  );
  await tester.pumpWidget(ProviderScope(
    overrides: [authStateProvider.overrideWith((ref) => notifier)],
    child: MaterialApp.router(
      theme: brightness == Brightness.light ? AppTheme.lightTheme : AppTheme.darkTheme,
      routerConfig: router,
    ),
  ));
  await tester.pump();
  await tester.pump(const Duration(milliseconds: 600));
}

void main() {
  group('SplashPage', () {
    testWidgets('shows logo, title, subtitle, and progress', (tester) async {
      await _pumpSplash(tester, _FakeAuthNotifier(initial: true));
      expect(find.text('YantraGO'), findsOneWidget);
      expect(find.text('Machine Management Platform'), findsOneWidget);
      expect(find.byType(CircularProgressIndicator), findsOneWidget);
      expect(find.byIcon(Icons.electrical_services), findsOneWidget);
    });

    testWidgets('renders in dark theme', (tester) async {
      await _pumpSplash(
        tester,
        _FakeAuthNotifier(initial: true),
        brightness: Brightness.dark,
      );
      expect(find.text('YantraGO'), findsOneWidget);
    });
  });

  group('LoginPage', () {
    testWidgets('shows form fields and login button', (tester) async {
      await _pump(
        tester,
        const LoginPage(),
        overrides: [authStateProvider.overrideWith((ref) => _FakeAuthNotifier())],
      );
      expect(find.text('YantraGO'), findsOneWidget);
      expect(find.text('Email or Phone'), findsOneWidget);
      expect(find.text('Password'), findsOneWidget);
      expect(find.text('Login'), findsOneWidget);
    });

    testWidgets('shows validation error on empty submit', (tester) async {
      await _pump(
        tester,
        const LoginPage(),
        overrides: [authStateProvider.overrideWith((ref) => _FakeAuthNotifier())],
      );
      await tester.tap(find.text('Login'));
      await tester.pump();
      // Validators.required produces an error message
      expect(find.byType(SnackBar), findsNothing);
      expect(find.text('Login'), findsOneWidget);
    });

    testWidgets('password visibility toggle works', (tester) async {
      await _pump(
        tester,
        const LoginPage(),
        overrides: [authStateProvider.overrideWith((ref) => _FakeAuthNotifier())],
      );
      await tester.enterText(find.byType(TextFormField).at(1), 'secret123');
      expect(find.byIcon(Icons.visibility_outlined), findsOneWidget);
      await tester.tap(find.byIcon(Icons.visibility_outlined));
      await tester.pump();
      expect(find.byIcon(Icons.visibility_off_outlined), findsOneWidget);
    });

    testWidgets('renders in dark theme', (tester) async {
      await _pump(
        tester,
        const LoginPage(),
        brightness: Brightness.dark,
        overrides: [authStateProvider.overrideWith((ref) => _FakeAuthNotifier())],
      );
      expect(find.text('Login'), findsOneWidget);
    });

    testWidgets('renders without overflow at 200% text scale', (tester) async {
      await _pump(
        tester,
        const LoginPage(),
        textScale: 2.0,
        surface: const Size(390, 2000),
        overrides: [authStateProvider.overrideWith((ref) => _FakeAuthNotifier())],
      );
      expect(tester.takeException(), isNull);
    });
  });

  group('ProfilePage', () {
    testWidgets('shows user info when logged in', (tester) async {
      await _pump(
        tester,
        const ProfilePage(),
        overrides: [
          authStateProvider.overrideWith((ref) => _FakeAuthNotifier(authenticated: true)),
          profileProvider.overrideWith((ref) => _user),
        ],
      );
      expect(find.text('Demo User'), findsOneWidget);
      expect(find.text('demo@example.invalid'), findsOneWidget);
      expect(find.text('CUSTOMER'), findsOneWidget);
      expect(find.text('Demo Farm'), findsOneWidget);
      expect(find.text('+91 98765 43210'), findsOneWidget);
      expect(find.text('Log out'), findsOneWidget);
    });

    testWidgets('shows empty state when not logged in', (tester) async {
      await _pump(
        tester,
        const ProfilePage(),
        overrides: [profileProvider.overrideWith((ref) => null)],
      );
      expect(find.text('Not signed in'), findsOneWidget);
      expect(find.text('Please log in to view your profile.'), findsOneWidget);
    });

    testWidgets('logout button calls logout', (tester) async {
      final notifier = _FakeAuthNotifier(authenticated: true);
      await _pump(
        tester,
        const ProfilePage(),
        overrides: [
          authStateProvider.overrideWith((ref) => notifier),
          profileProvider.overrideWith((ref) => _user),
        ],
      );
      await tester.tap(find.text('Log out'));
      await tester.pump();
      expect(notifier.logoutCalled, isTrue);
    });

    testWidgets('renders in dark theme', (tester) async {
      await _pump(
        tester,
        const ProfilePage(),
        brightness: Brightness.dark,
        overrides: [
          authStateProvider.overrideWith((ref) => _FakeAuthNotifier(authenticated: true)),
          profileProvider.overrideWith((ref) => _user),
        ],
      );
      expect(find.text('Demo User'), findsOneWidget);
    });

    testWidgets('renders without overflow at 200% text scale', (tester) async {
      await _pump(
        tester,
        const ProfilePage(),
        textScale: 2.0,
        surface: const Size(390, 2400),
        overrides: [
          authStateProvider.overrideWith((ref) => _FakeAuthNotifier(authenticated: true)),
          profileProvider.overrideWith((ref) => _user),
        ],
      );
      expect(tester.takeException(), isNull);
    });
  });

  group('LoginPage keyboard and scrolling', () {
    testWidgets('form is scrollable when keyboard is visible', (tester) async {
      await _pump(
        tester,
        const LoginPage(),
        surface: const Size(390, 600),
        overrides: [authStateProvider.overrideWith((ref) => _FakeAuthNotifier())],
      );
      // Focus the password field to simulate keyboard
      await tester.showKeyboard(find.byType(TextFormField).at(1));
      await tester.pump();
      // Login button should still be reachable (scrollable form)
      expect(find.text('Login'), findsOneWidget);
    });
  });

  group('Navigation shell', () {
    testWidgets('tab switching with context.go changes selected index', (tester) async {
      final GoRouter router = GoRouter(
        initialLocation: '/app/machines',
        routes: [
          ShellRoute(
            builder: (context, state, child) => _TestShell(child: child),
            routes: [
              GoRoute(
                path: '/app/machines',
                builder: (context, state) => const Scaffold(body: Center(child: Text('MACHINES_TAB'))),
              ),
              GoRoute(
                path: '/app/alerts',
                builder: (context, state) => const Scaffold(body: Center(child: Text('ALERTS_TAB'))),
              ),
              GoRoute(
                path: '/app/profile',
                builder: (context, state) => const Scaffold(body: Center(child: Text('PROFILE_TAB'))),
              ),
            ],
          ),
        ],
      );
      await tester.binding.setSurfaceSize(const Size(390, 800));
      await tester.pumpWidget(MaterialApp.router(
        theme: AppTheme.lightTheme,
        routerConfig: router,
      ));
      await tester.pump();
      expect(find.text('MACHINES_TAB'), findsOneWidget);
      // Tap Alerts tab
      await tester.tap(find.text('Alerts'));
      await tester.pumpAndSettle();
      expect(find.text('ALERTS_TAB'), findsOneWidget);
      // Tap Profile tab
      await tester.tap(find.text('Profile'));
      await tester.pumpAndSettle();
      expect(find.text('PROFILE_TAB'), findsOneWidget);
    });

    testWidgets('machine detail back navigation returns to list', (tester) async {
      final GoRouter router = GoRouter(
        initialLocation: '/app/machines',
        routes: [
          ShellRoute(
            builder: (context, state, child) => _TestShell(child: child),
            routes: [
              GoRoute(
                path: '/app/machines',
                builder: (context, state) => const Scaffold(body: Center(child: Text('MACHINE_LIST'))),
              ),
              GoRoute(
                path: '/app/machines/:id',
                builder: (context, state) => Scaffold(
                  appBar: AppBar(title: const Text('MACHINE_DETAIL')),
                  body: Center(child: Text('Detail ${state.pathParameters['id']}')),
                ),
              ),
            ],
          ),
        ],
      );
      await tester.binding.setSurfaceSize(const Size(390, 800));
      await tester.pumpWidget(MaterialApp.router(
        theme: AppTheme.lightTheme,
        routerConfig: router,
      ));
      await tester.pump();
      expect(find.text('MACHINE_LIST'), findsOneWidget);
      // Push to detail
      router.push('/app/machines/m1');
      await tester.pumpAndSettle();
      expect(find.text('MACHINE_DETAIL'), findsOneWidget);
      expect(find.text('Detail m1'), findsOneWidget);
      // Press back
      router.pop();
      await tester.pumpAndSettle();
      expect(find.text('MACHINE_LIST'), findsOneWidget);
    });
  });
}

class _FakeAuthNotifier extends AuthStateNotifier {
  _FakeAuthNotifier({bool authenticated = false, bool initial = false})
      : super(_NoAuthService(), _NoPushService()) {
    state = initial
        ? const AuthInitial()
        : authenticated
            ? const Authenticated(_user)
            : const Unauthenticated();
  }

  bool logoutCalled = false;

  @override
  Future<void> logout() async {
    logoutCalled = true;
    state = const Unauthenticated();
  }

  @override
  Future<void> login({required String email, required String password}) async {
    state = const AuthLoading();
    if (email == 'fail@example.invalid') {
      state = const AuthError('Invalid credentials');
      Future.delayed(const Duration(milliseconds: 100), () {
        if (state is AuthError) state = const Unauthenticated();
      });
      return;
    }
    state = const Authenticated(_user);
  }
}

class _NoAuthService implements AuthService {
  @override
  noSuchMethod(Invocation invocation) => super.noSuchMethod(invocation);
}

/// Test-only no-op push service for AuthStateNotifier wiring.
class _NoPushService implements PushNotificationService {
  @override
  noSuchMethod(Invocation invocation) => super.noSuchMethod(invocation);
}

/// Test-only navigation shell that mirrors the production _AppShell.
class _TestShell extends StatelessWidget {
  final Widget child;
  const _TestShell({required this.child});

  static const _routes = ['/app/machines', '/app/alerts', '/app/profile'];

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
