import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import 'package:yantrago/core/auth/auth_state.dart';
import 'package:yantrago/core/widgets/app_page_body.dart';
import 'package:yantrago/features/auth/providers/auth_provider.dart';

/// Splash page — shown on app start while checking existing auth session.
///
/// The GoRouter redirect handles navigation to /login or /app/machines
/// when auth state changes. This page just shows a loading indicator.
/// We use a small delay to ensure the auth check has time to run.
class SplashPage extends ConsumerStatefulWidget {
  const SplashPage({super.key});

  @override
  ConsumerState<SplashPage> createState() => _SplashPageState();
}

class _SplashPageState extends ConsumerState<SplashPage> {
  bool _navigated = false;

  @override
  void initState() {
    super.initState();
    // Give the auth check a moment, then let the router redirect handle it
    Future.delayed(const Duration(milliseconds: 500), () {
      _checkAndNavigate();
    });
  }

  void _checkAndNavigate() {
    if (_navigated || !mounted) return;

    final state = ref.read(authStateProvider);

    if (state is Authenticated) {
      _navigated = true;
      context.go('/app/machines');
    } else if (state is Unauthenticated || state is AuthError) {
      _navigated = true;
      context.go('/login');
    }
    // If still loading, the refreshListenable in the router will
    // trigger a redirect when authState changes.
  }

  @override
  Widget build(BuildContext context) {
    // Listen for auth state changes to navigate when ready
    ref.listen<AuthState>(authStateProvider, (previous, next) {
      if (_navigated || !mounted) return;
      if (next is Authenticated) {
        _navigated = true;
        context.go('/app/machines');
      } else if (next is Unauthenticated || next is AuthError) {
        _navigated = true;
        context.go('/login');
      }
    });

    final ColorScheme colors = Theme.of(context).colorScheme;
    final TextTheme text = Theme.of(context).textTheme;

    return Scaffold(
      body: AppPageBody(
        safeArea: true,
        child: Center(
          child: Column(
            mainAxisAlignment: MainAxisAlignment.center,
            children: <Widget>[
              Container(
                width: 96,
                height: 96,
                decoration: BoxDecoration(
                  color: colors.primaryContainer,
                  borderRadius: BorderRadius.circular(24),
                ),
                child: Icon(
                  Icons.electrical_services,
                  size: 48,
                  color: colors.onPrimaryContainer,
                ),
              ),
              const SizedBox(height: 24),
              Text(
                'YantraGO',
                style: text.headlineMedium?.copyWith(
                  fontWeight: FontWeight.bold,
                  color: colors.primary,
                ),
              ),
              const SizedBox(height: 8),
              Text(
                'Machine Management Platform',
                style: text.bodyMedium?.copyWith(
                  color: colors.onSurfaceVariant,
                ),
              ),
              const SizedBox(height: 32),
              const CircularProgressIndicator(),
            ],
          ),
        ),
      ),
    );
  }
}
